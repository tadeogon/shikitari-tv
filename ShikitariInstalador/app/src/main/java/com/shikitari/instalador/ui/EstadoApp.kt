package com.shikitari.instalador.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shikitari.instalador.AccionesTv
import com.shikitari.instalador.AyudaClaude
import com.shikitari.instalador.Bitacora
import com.shikitari.instalador.BuscadorTvs
import com.shikitari.instalador.ConexionTv
import com.shikitari.instalador.ControlActivity
import com.shikitari.instalador.MisTvs
import com.shikitari.instalador.TvGuardada
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Todo lo que la app sabe y hace, separado de cómo se dibuja: la TV conectada, Mis TVs,
 * el cartel de abajo, los botones del control y la charla con Claude.
 * Es la misma lógica que tenía la pantalla vieja, pasada en limpio.
 */
class EstadoApp(contextoActividad: Context) {

    val contexto: Context = contextoActividad
    private val prefs = contexto.getSharedPreferences("app", Context.MODE_PRIVATE)
    val tv: ConexionTv by lazy { ConexionTv.obtener(contexto) }
    val acciones by lazy { AccionesTv(contexto, tv) }
    val cartel = EstadoCartel()
    val dialogos = Dialogos()
    private val trabajo = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())

    var conectada by mutableStateOf<TvGuardada?>(null)
        private set
    var infoConectada by mutableStateOf("")
        private set
    var tvs by mutableStateOf(MisTvs.cargar(contexto))
        private set
    /** TVs que aparecieron en la búsqueda y todavía no están guardadas (IP → puerto). */
    var nuevas by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    fun enUi(f: () -> Unit) {
        principal.post(f)
    }

    fun cerrar() {
        pararVigia()
        tv.alFallar = null
        trabajo.shutdownNow()
        vigia.shutdownNow()
    }

    // ---------- Vigía de la conexión ----------
    // La conexión con la TV a veces queda «colgada» (la TV entró en reposo, el celular se bloqueó,
    // la TV cambió de puerto). Antes la app se quedaba esperando sin avisar. Ahora:
    // 1) cada 20 segundos, y al volver a la app, prueba si la TV contesta;
    // 2) si un pedido falla, prueba enseguida;
    // 3) si no contesta, reconecta sola (buscando el puerto nuevo si cambió);
    // 4) si tampoco puede, muestra arriba «Se desconectó» con un botón para volver a conectar.

    enum class Enlace { BIEN, RECONECTANDO, PERDIDO }

    var enlace by mutableStateOf(Enlace.BIEN)
        private set

    private val vigia = Executors.newSingleThreadExecutor()
    private val revisando = java.util.concurrent.atomic.AtomicBoolean(false)
    private val candado = Any()
    private var vigiaPrendido = false
    @Volatile
    private var ultimaRevision = 0L
    private val vuelta = object : Runnable {
        override fun run() {
            revisarConexion()
            principal.postDelayed(this, 20_000)
        }
    }

    init {
        tv.alFallar = { revisarConexion() }
    }

    /** Se llama al volver a la app: revisa enseguida y después cada 20 s. */
    fun iniciarVigia() {
        if (vigiaPrendido) return
        vigiaPrendido = true
        principal.post(vuelta)
    }

    /** Se llama al salir de la app (no tiene sentido revisar con la pantalla apagada). */
    fun pararVigia() {
        vigiaPrendido = false
        principal.removeCallbacks(vuelta)
    }

    /** Revisa la conexión en segundo plano (si ya se está revisando, no hace nada). */
    fun revisarConexion() {
        if (conectada == null) return
        // Si ya está perdida, no insisto más de una vez cada 15 s (si la TV vuelve, se reconecta sola).
        val ahora = System.currentTimeMillis()
        if (enlace == Enlace.PERDIDO && ahora - ultimaRevision < 15_000) return
        if (!revisando.compareAndSet(false, true)) return
        ultimaRevision = ahora
        try {
            vigia.execute {
                try {
                    tvLista()
                } finally {
                    revisando.set(false)
                }
            }
        } catch (e: Exception) {
            revisando.set(false)
        }
    }

    /**
     * Devuelve true si la TV contesta; si no, intenta reconectar sola. Se llama desde un hilo de fondo.
     * Si no hay forma, deja el aviso de «Se desconectó» y devuelve false.
     */
    private fun tvLista(): Boolean = synchronized(candado) {
        val t = conectada ?: return false
        if (tv.responde()) {
            if (enlace != Enlace.BIEN) enUi { enlace = Enlace.BIEN }
            return true
        }
        if (enlace != Enlace.PERDIDO) enUi { enlace = Enlace.RECONECTANDO }
        tv.soltar()
        val r = try {
            conectarA(t.ip, t.puerto)
        } catch (e: Exception) {
            ""
        }
        val ok = r.contains("✓")
        Bitacora.anotar(contexto, "▸ Se cortó la conexión con ${t.nombre}\n" + if (ok) "Reconectada sola ✓" else "No se pudo reconectar sola.")
        enUi { enlace = if (ok) Enlace.BIEN else Enlace.PERDIDO }
        ok
    }

    /** Botón «Volver a conectar» del aviso. */
    fun volverAConectar() {
        val t = conectada ?: return
        conectar(t)
    }

    // ---------- Hacer cosas en segundo plano con el cartel ----------

    /** Si no hay TV conectada, avisa y devuelve false. */
    fun hayTv(): Boolean {
        if (conectada != null) return true
        cartel.avisar(false, "Primero conectá una TV: tocala en Mis TVs.")
        return false
    }

    /**
     * Hace algo en segundo plano mostrando el cartel, y guarda el resultado completo en el Historial.
     * Si [requiereTv] y no hay TV conectada, avisa en vez de intentarlo.
     */
    fun ejecutar(titulo: String, requiereTv: Boolean = true, alTerminar: ((String) -> Unit)? = null, accion: () -> String) {
        if (requiereTv && !hayTv()) return
        cartel.trabajando(titulo)
        trabajo.execute {
            val resultado = try {
                // Antes de mandar algo, confirmo que la TV contesta (si no, reconecto sola).
                if (requiereTv && !tvLista()) {
                    "Se perdió la conexión con la TV y no se pudo reconectar sola. Tocá «Volver a conectar» arriba."
                } else {
                    accion()
                }
            } catch (e: Exception) {
                explicarError(e)
            }
            Bitacora.anotar(contexto, "▸ $titulo\n$resultado")
            val fallo = Regex("No se pudo|Error|Primero|No encontré|No apareció|rechazó|No hubo|Se perdió").containsMatchIn(resultado)
            // En el cartel va un resumen; el detalle completo queda en el Historial.
            val resumen = resultado.lines().filter { it.isNotBlank() }.let { l ->
                if (l.size <= 3) l.joinToString("\n") else l.take(2).joinToString("\n") + "\n(ver detalle en Historial)"
            }
            enUi {
                cartel.avisar(!fallo, resumen.ifBlank { "Listo" })
                alTerminar?.invoke(resultado)
            }
        }
    }

    /** Traduce los errores más comunes de la conexión a algo entendible. */
    fun explicarError(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("pairing is required", true) -> "La TV todavía no conoce este celular: agregala con el código (Agregar TV)."
            m.contains("connect() must be called", true) || m.contains("Se perdió la conexión", true) ->
                "Se perdió la conexión con la TV. Tocala de nuevo en Mis TVs."
            m.contains("Primero tocá Conectar", true) -> "Primero conectá una TV: tocala en Mis TVs."
            m.contains("refused", true) || m.contains("ECONNREFUSED", true) -> "La TV rechazó la conexión. Tocala de nuevo en Mis TVs (busca el puerto nuevo)."
            m.contains("timed out", true) || m.contains("timeout", true) -> "No hubo respuesta: revisá que el celular esté en el mismo Wi-Fi que la TV."
            e is NumberFormatException -> "Completá los números."
            else -> "Error: ${m.ifBlank { e.javaClass.simpleName }}"
        }
    }

    // ---------- Mis TVs ----------

    fun buscarTvs() {
        ejecutar("Buscando TVs en el Wi-Fi", requiereTv = false) {
            // Las nuevas se anuncian solas; Fire TV y las viejas (puerto 5555) hay que buscarlas una por una.
            // (si una TV aparece de las dos formas, gana el puerto anunciado)
            val halladas = BuscadorTvs.buscarViejas(contexto) + BuscadorTvs.buscar(contexto)
            val guardadas = MisTvs.cargar(contexto).associateBy { it.ip }
            enUi { nuevas = halladas.filterKeys { it !in guardadas } }
            if (halladas.isEmpty()) {
                "No apareció ninguna TV. Revisá que esté prendida, en el mismo Wi-Fi y con la Depuración inalámbrica activada."
            } else {
                "Encontré ${halladas.size} TV(s): " + halladas.keys.joinToString { guardadas[it]?.nombre ?: it }
            }
        }
    }

    fun conectar(t: TvGuardada) = ejecutar("Conectando con ${t.nombre}", requiereTv = false) { conectarA(t.ip, t.puerto) }

    fun conectarNueva(ip: String, puerto: Int) = ejecutar("Conectando con $ip", requiereTv = false) { conectarA(ip, puerto) }

    /**
     * Conecta con la TV. Si con el puerto guardado no anda (cambia cada vez que la TV se reinicia),
     * lo busca en el Wi-Fi y prueba de nuevo. Al conectar, la TV queda guardada en "Mis TVs".
     * Se llama desde un hilo de fondo.
     */
    private fun conectarA(hostGuardado: String, puertoGuardado: Int): String {
        if (hostGuardado.isBlank()) return "Escribí la IP de la TV."
        var host = hostGuardado
        var puerto = puertoGuardado
        var ok = puerto > 0 && intentar(host, puerto)
        if (!ok) {
            val nuevo = BuscadorTvs.buscar(contexto)[host]
            if (nuevo != null) {
                puerto = nuevo
                ok = intentar(host, puerto)
            } else if (puerto == 0) {
                puerto = 5555 // TVs viejas con ADB por red
                ok = intentar(host, puerto)
            }
        }
        if (!ok && puertoGuardado == 5555) {
            // Fire TV / TV vieja: puede que el router le haya dado otra IP. La busco en la red
            // y confirmo que sea la misma (mismo modelo) antes de mover el guardado.
            val guardadas = MisTvs.cargar(contexto)
            val esta = guardadas.firstOrNull { it.ip == hostGuardado }
            val ocupadas = guardadas.map { it.ip }.toSet()
            for (ip in BuscadorTvs.buscarViejas(contexto).keys - ocupadas) {
                if (!intentar(ip, 5555)) continue
                val modelo = try { tv.ejecutar("getprop ro.product.model", segundos = 8).trim() } catch (e: Exception) { "" }
                if (esta == null || esta.modelo.isBlank() || modelo == esta.modelo) {
                    MisTvs.mudar(contexto, hostGuardado, ip)
                    Bitacora.anotar(contexto, "▸ ${esta?.nombre ?: "La TV"} cambió de IP: $hostGuardado → $ip")
                    host = ip
                    puerto = 5555
                    ok = true
                    break
                }
            }
        }
        if (!ok) return "No se pudo conectar con $host. Revisá que la TV esté prendida y con la depuración activada. " +
            "Si en la TV aparece «¿Permitir depuración?», marcá «Siempre» y aceptá; después tocala de nuevo."
        prefs.edit().putString("ip", host).putString("puerto", puerto.toString()).apply()
        val modelo = try { tv.ejecutar("getprop ro.product.model").trim() } catch (e: Exception) { "" }
        val guardada = MisTvs.recordar(contexto, host, puerto, modelo)
        val info = acciones.infoTv().removePrefix("Conectado a ")
        enUi {
            conectada = guardada
            enlace = Enlace.BIEN
            infoConectada = info
            tvs = MisTvs.cargar(contexto)
            nuevas = nuevas - host
        }
        return "Conectado a ${guardada.nombre} ✓"
    }

    private fun intentar(host: String, puerto: Int): Boolean =
        try { tv.reconectar(host, puerto) } catch (e: Exception) { false }

    /** Mantener apretada una TV: cambiarle el nombre o sacarla de la lista. */
    fun opcionesTv(t: TvGuardada) {
        dialogos.hoja = HojaPedida(
            t.nombre, "${t.modelo.ifBlank { "TV" }} · ${t.ip}",
            listOf(
                Opcion("Cambiar el nombre", Iconos.lapiz) {
                    dialogos.texto = TextoPedido("Nombre de la TV", t.nombre, "Ej. Living, Oficina") { nombre ->
                        if (nombre.isNotBlank()) {
                            MisTvs.renombrar(contexto, t.ip, nombre)
                            if (conectada?.ip == t.ip) conectada = conectada?.copy(nombre = nombre)
                            tvs = MisTvs.cargar(contexto)
                        }
                    }
                },
                Opcion("Sacar de Mis TVs", Iconos.basura, peligro = true) {
                    MisTvs.borrar(contexto, t.ip)
                    tvs = MisTvs.cargar(contexto)
                },
            ),
        )
    }

    fun ipGuardada(): String = prefs.getString("ip", "") ?: ""

    /** Vincula con el código de 6 números y conecta. [alListo] corre en pantalla si quedó conectada. */
    fun vincular(ip: String, codigo: String, alListo: () -> Unit) {
        val host = ip.trim()
        prefs.edit().putString("ip", host).apply()
        ejecutar("Vinculando con $host", requiereTv = false, alTerminar = { if (it.contains("✓")) alListo() }) {
            // La TV anuncia el puerto de vinculación mientras muestra el código.
            val puertoV = BuscadorTvs.buscar(contexto, BuscadorTvs.VINCULACION)[host]
                ?: return@ejecutar "No encontré la pantalla del código en $host. Revisá la IP y que el código siga en la TV."
            if (!tv.pair(host, puertoV, codigo.trim())) return@ejecutar "No se pudo vincular. Revisá el código."
            val r = conectarA(host, 0)
            if (r.contains("✓")) "Vinculada ✓ $r" else r
        }
    }

    /** TVs viejas con "Depuración por red": IP y puerto (0 = 5555 o lo busco solo). */
    fun conectarManual(ip: String, puerto: Int, alListo: () -> Unit) {
        ejecutar("Conectando con ${ip.trim()}", requiereTv = false, alTerminar = { if (it.contains("✓")) alListo() }) {
            conectarA(ip.trim(), puerto)
        }
    }

    // ---------- Botones del control ----------

    /** Botones del control que se pueden cambiar (los de apps del Fire TV se llaman APP_1..APP_4 en el sistema). */
    val botonesControl = listOf(
        "KEYCODE_APP_1" to "Botón Prime Video",
        "KEYCODE_APP_2" to "Botón Netflix",
        "KEYCODE_APP_3" to "Botón Disney+",
        "KEYCODE_APP_4" to "Botón Peacock",
        "KEYCODE_LIVE_TV" to "Botón TV (guía)",
        "KEYCODE_CHANNEL_UP" to "Canal +",
        "KEYCODE_CHANNEL_DOWN" to "Canal −",
    )

    private fun claveBotones() = "botones_${conectada?.ip}"

    fun mapaBotones(): Map<String, String> =
        (prefs.getString(claveBotones(), "") ?: "").split(';').mapNotNull {
            it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] }
        }.toMap()

    fun nombreDeApp(paquete: String) =
        ControlActivity.NOMBRES[paquete] ?: paquete.split('.').last { it.length > 2 }.replaceFirstChar { it.uppercase() }

    /** Lo que esté configurado en la TV manda (puede haberse cambiado desde otro celular o la PC). */
    fun leerBotonesDeTv(alLeer: (Map<String, String>) -> Unit) {
        if (conectada == null) return
        trabajo.execute {
            try {
                val salida = tv.ejecutar("am broadcast -n ${AccionesTv.PACHI.paquete}/com.pachi.tv.ReceptorBotones")
                val enTv = Regex("data=\"([^\"]*)\"").find(salida)?.groupValues?.get(1) ?: return@execute
                prefs.edit().putString(claveBotones(), enTv).apply()
                val mapa = enTv.split(';').mapNotNull { par -> par.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
                enUi { alLeer(mapa) }
            } catch (_: Exception) {
            }
        }
    }

    fun guardarBotones(mapa: Map<String, String>) {
        ejecutar("Guardar en la TV") {
            val texto = mapa.entries.joinToString(";") { "${it.key}=${it.value}" }
            prefs.edit().putString(claveBotones(), texto).apply()
            val instalados = tv.ejecutar("pm list packages").lines().map { it.trim().removePrefix("package:") }.toSet()
            val launchers = listOf(AccionesTv.PACHI, AccionesTv.SOLARI).filter { it.paquete in instalados }
            if (launchers.isEmpty()) return@ejecutar "Primero instalá Ziota o Solari en esta TV."
            val res = StringBuilder()
            // En Fire TV, Amazon se queda con esos botones: Ziota los reconoce leyendo el registro del sistema.
            val esFire = tv.ejecutar("getprop ro.product.manufacturer").trim().equals("Amazon", ignoreCase = true)
            launchers.forEach { m ->
                if (esFire) tv.ejecutar("pm grant ${m.paquete} android.permission.READ_LOGS")
                val r = tv.ejecutar("am broadcast -n ${m.paquete}/com.pachi.tv.ReceptorBotones --es mapa ${ConexionTv.comillas(texto)}")
                val activo = tv.ejecutar("settings get secure enabled_accessibility_services").contains(m.servicioInicio)
                res.append(if (r.contains("data=\"ok\"")) "Botones guardados en ${m.nombre} ✓" else "No se pudo guardar en ${m.nombre}: $r").append('\n')
                if (!activo) res.append("Ojo: el botón Inicio de ${m.nombre} no está activado; activalo en su pantalla para que los botones funcionen.\n")
            }
            res.append(if (mapa.isEmpty()) "Todos los botones quedaron como vienen." else mapa.entries.joinToString("\n") { (t, p) ->
                "${botonesControl.first { it.first == t }.second} → ${nombreDeApp(p)}"
            })
            res.toString().trim()
        }
    }

    /** Lista las apps de la TV para elegir una. [listo] recibe null si eligió "como viene de fábrica". */
    fun elegirAppPara(boton: String, listo: (String?) -> Unit) {
        if (!hayTv()) return
        cartel.trabajando("Buscando las apps de la TV")
        trabajo.execute {
            val apps = try {
                val todas = tv.ejecutar("pm list packages").lines().map { it.trim().removePrefix("package:") }.filter { it.isNotBlank() }.toSet()
                val terceros = tv.ejecutar("pm list packages -3").lines().map { it.trim().removePrefix("package:") }.filter { it.isNotBlank() }
                (terceros + ControlActivity.NOMBRES.keys.filter { it in todas })
                    .distinct().filter { it !in ControlActivity.OCULTAR }
                    .map { it to nombreDeApp(it) }.sortedBy { it.second.lowercase() }
            } catch (e: Exception) {
                enUi { cartel.avisar(false, explicarError(e)) }
                return@execute
            }
            enUi {
                cartel.cerrar()
                dialogos.hoja = HojaPedida(
                    "$boton abre…", null,
                    listOf(Opcion("Como viene de fábrica", Iconos.volver) { listo(null) }) +
                        apps.map { (p, n) -> Opcion(n) { listo(p) } },
                )
            }
        }
    }

    // ---------- Ayuda con Claude ----------

    data class Mensaje(val deClaude: Boolean, val texto: String)

    val chat = mutableStateListOf<Mensaje>()
    var pensando by mutableStateOf(false)
        private set
    private var ayuda: AyudaClaude? = null

    fun claveGuardada(): String = prefs.getString("clave", "") ?: ""

    fun preguntarAClaude(texto: String, clave: String) {
        if (!hayTv()) return
        if (texto.isBlank() || clave.isBlank()) {
            cartel.avisar(false, "Escribí tu clave de API y la pregunta.")
            return
        }
        if (clave != claveGuardada()) ayuda = null
        prefs.edit().putString("clave", clave).apply()
        chat += Mensaje(false, texto)
        pensando = true
        trabajo.execute {
            try {
                val asistente = ayuda ?: AyudaClaude(clave, tv, { r -> enUi { chat += Mensaje(true, r) } }, ::confirmarComando).also { ayuda = it }
                asistente.preguntar(texto)
            } catch (e: Exception) {
                enUi { chat += Mensaje(true, "Error: ${e.message}") }
            } finally {
                enUi { pensando = false }
            }
        }
    }

    /** Muestra el comando que Claude quiere ejecutar y espera tu respuesta (bloquea el hilo de fondo). */
    private fun confirmarComando(comando: String, motivo: String): Boolean {
        val respuesta = CountDownLatch(1)
        var aprobado = false
        enUi {
            dialogos.confirmacion = ConfirmacionPedida(
                "Claude quiere ejecutar", "$comando\n\nPor qué: $motivo", "Ejecutar", no = "No",
            ) { si ->
                aprobado = si
                respuesta.countDown()
            }
        }
        respuesta.await()
        return aprobado
    }
}
