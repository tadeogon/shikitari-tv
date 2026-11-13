package com.shikitari.instalador

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import java.io.File
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.conscrypt.Conscrypt
import java.security.Security
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Shikitari Instalador: desde el celular, conecta con una TV por depuración inalámbrica,
 * instala Shikitari TV o Pachi TV, les da permisos, optimiza la TV y ofrece ayuda con Claude.
 */
class MainActivity : Activity() {

    private val vidrio = 0x1FFFFFFF
    private val borde = 0x33FFFFFF
    private val tinta = Color.WHITE
    private val tintaSuave = 0xA6FFFFFF.toInt()
    private val crema = 0xFFF3E9D2.toInt()

    private val prefs by lazy { getSharedPreferences("app", MODE_PRIVATE) }
    private val trabajo = Executors.newSingleThreadExecutor()
    private val tv by lazy { ConexionTv.obtener(this) }
    private val acciones by lazy { AccionesTv(this, tv) }

    private lateinit var estado: TextView
    private lateinit var registro: TextView
    private lateinit var chat: TextView
    private lateinit var scroll: ScrollView
    private val botonesTv = mutableListOf<Button>()
    private var ayuda: AyudaClaude? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Conscrypt hace posible el emparejamiento seguro (TLS) con la TV.
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        setContentView(armar())
    }

    private fun armar(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(32))
        }
        col.addView(TextView(this).apply {
            text = "Instalador"
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        col.addView(TextView(this).apply {
            text = "Shikitari TV · Pachi TV · Solari"
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        estado = TextView(this).apply {
            text = "Sin conectar"
            setTextColor(tintaSuave)
            setPadding(0, dp(4), 0, dp(8))
        }
        col.addView(estado)

        // 1. Conexión
        val conexion = seccion(col, "1. Conectar con la TV")
        conexion.addView(nota(
            "En la TV: Opciones para desarrolladores → Depuración inalámbrica activada. La primera vez, en la TV tocá " +
                "\"Vincular dispositivo con código\" y acá escribí la IP y el código (el puerto lo busco solo). " +
                "Después, la TV queda en Mis TVs: la tocás y se conecta, aunque el puerto haya cambiado.",
        ))
        // Mis TVs: tocás una y se conecta sola (si el puerto cambió, lo busca en la red)
        conexion.addView(subtitulo("Mis TVs"))
        listaTvs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        conexion.addView(listaTvs)
        boton(conexion, "🔍  Buscar TVs en el Wi-Fi", false) {
            val halladas = BuscadorTvs.buscar(this)
            val guardadas = MisTvs.cargar(this).associateBy { it.ip }
            runOnUiThread { pintarTvs(halladas.filterKeys { it !in guardadas }) }
            if (halladas.isEmpty()) {
                "No apareció ninguna TV. Revisá que esté prendida, en el mismo Wi-Fi y con la Depuración inalámbrica activada."
            } else {
                "Encontré ${halladas.size} TV(s) con la Depuración inalámbrica activada: " +
                    halladas.entries.joinToString { (ip, p) -> (guardadas[ip]?.nombre ?: ip) + " (puerto $p)" }
            }
        }
        pintarTvs()

        conexion.addView(subtitulo("Agregar una TV a mano"))
        val ip = campo(conexion, "IP de la TV (ej. 192.168.100.2)", prefs.getString("ip", "") ?: "", InputType.TYPE_CLASS_PHONE)
        campoIp = ip
        val puertoVincular = campo(conexion, "Puerto de vinculación (vacío = lo busco solo)", "", InputType.TYPE_CLASS_NUMBER)
        val codigo = campo(conexion, "Código de vinculación (6 números)", "", InputType.TYPE_CLASS_NUMBER)
        boton(conexion, "Vincular", false) {
            val host = ip.text.toString().trim()
            prefs.edit().putString("ip", host).apply()
            // Si no escribiste el puerto del código, lo busco: la TV lo anuncia mientras muestra el código.
            val puertoV = puertoVincular.text.toString().trim().toIntOrNull()
                ?: BuscadorTvs.buscar(this, BuscadorTvs.VINCULACION)[host]
                ?: return@boton "No encontré la pantalla del código en esa IP. Escribí el puerto de vinculación a mano."
            val ok = tv.pair(host, puertoV, codigo.text.toString().trim())
            if (!ok) return@boton "No se pudo vincular. Revisá el código y el puerto."
            // Recién vinculada: busco su puerto de conexión y conecto de una.
            "Vinculado ✓\n" + conectarA(host, prefs.getString("puerto", "")?.toIntOrNull() ?: 0)
        }
        val puerto = campo(conexion, "Puerto de conexión (vacío = lo busco solo)", prefs.getString("puerto", "") ?: "", InputType.TYPE_CLASS_NUMBER)
        campoPuerto = puerto
        boton(conexion, "Conectar", false) {
            conectarA(ip.text.toString().trim(), puerto.text.toString().trim().toIntOrNull() ?: 0)
        }
        conexion.addView(nota("Con la TV conectada: mirá su pantalla en el celular, usalo de control remoto y abrí apps."))
        boton(conexion, "📺  Control de la TV") {
            runOnUiThread { startActivity(Intent(this, ControlActivity::class.java)) }
            "Control de la TV abierto"
        }

        // 2. Shikitari TV
        val launcher = seccion(col, "2. Shikitari TV")
        launcher.addView(nota("Instala el launcher con tus gatos, le da los permisos y activa el botón Inicio y el protector de pantalla."))
        boton(launcher, "Todo en uno (recomendado)") { acciones.todoEnUno() }
        boton(launcher, "Solo instalar Shikitari TV") { acciones.instalarShikitari() }
        boton(launcher, "Solo dar permisos") { acciones.darPermisos() }
        boton(launcher, "Solo activar Inicio y protector") { acciones.activarInicioYProtector() }
        launcher.addView(nota("Portada: la imagen grande del Inicio y del protector de pantalla. Puede ser cualquier foto del celular."))
        boton(launcher, "Cambiar portada (elegir foto)") {
            runOnUiThread {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PEDIDO_FOTO)
            }
            "Elegí la foto…"
        }
        boton(launcher, "Volver a la portada original") { acciones.portadaOriginal() }

        // 3. Pachi TV
        val pachi = seccion(col, "3. Pachi TV")
        pachi.addView(nota(
            "El launcher estilo Apple TV, con isla dinámica para la música, menú de vidrio y protector con el logo. " +
                "Se puede tener junto a Shikitari TV; el botón Inicio abre uno solo de los dos.",
        ))
        botonesDeModelo(pachi, AccionesTv.PACHI)

        // 4. Solari (el mismo launcher con la marca del trabajo)
        val solari = seccion(col, "4. Solari · Bienes Raíces")
        solari.addView(nota(
            "La versión formal para las TVs del trabajo: mismo launcher, con el logo y la tipografía de Solari " +
                "y un protector de pantalla sobrio. Se puede instalar junto a Pachi TV.",
        ))
        botonesDeModelo(solari, AccionesTv.SOLARI)

        // 4. Optimizar
        val optimizar = seccion(col, "5. Optimizar la TV")
        optimizar.addView(nota(
            "Limpieza segura: quita ${AccionesTv.LIMPIEZA_SEGURA.size} apps de Google que no hacen falta (juegos, comentarios, impresión, " +
                "lector de pantalla, etc.), libera caché y acelera las animaciones. Todo se puede deshacer.",
        ))
        boton(optimizar, "Optimizar") { acciones.optimizar() }
        boton(optimizar, "Deshacer optimización") { acciones.deshacer() }

        // 5. Ayuda con Claude
        val claude = seccion(col, "6. Ayuda con Claude")
        claude.addView(nota(
            "Contale el problema y Claude revisa la TV. Las consultas que solo leen se hacen solas; " +
                "cualquier cambio te lo pregunta antes. Necesita tu clave de API de Anthropic (console.anthropic.com, se paga por uso). " +
                "Lo que Claude consulta de la TV se envía a Anthropic.",
        ))
        val clave = campo(claude, "Clave de API (sk-ant-...)", prefs.getString("clave", "") ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        chat = TextView(this).apply {
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(8), 0, dp(8))
            setTextIsSelectable(true)
        }
        claude.addView(chat)
        val pregunta = campo(claude, "Ej.: el perfil de YouTube no carga", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        botonesTv += Button(this).apply {
            text = "Preguntar a Claude"
            estilo(this, true)
            activar(this, false)
            setOnClickListener {
                val texto = pregunta.text.toString().trim()
                val claveApi = clave.text.toString().trim()
                if (texto.isEmpty() || claveApi.isEmpty()) {
                    agregarChat("Escribí tu clave de API y la pregunta.")
                    return@setOnClickListener
                }
                prefs.edit().putString("clave", claveApi).apply()
                pregunta.setText("")
                agregarChat("Vos: $texto")
                activar(this, false)
                trabajo.execute {
                    try {
                        val asistente = ayuda ?: AyudaClaude(claveApi, tv, { agregarChat("Claude: $it") }, ::confirmarComando).also { ayuda = it }
                        asistente.preguntar(texto)
                    } catch (e: Exception) {
                        agregarChat("Error: ${e.message}")
                    } finally {
                        runOnUiThread { activar(this, true) }
                    }
                }
            }
            claude.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })
        }

        // Registro
        registro = TextView(this).apply {
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(12), 0, 0)
            setTextIsSelectable(true)
        }
        col.addView(registro)

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            fitsSystemWindows = true
            addView(col)
        }
        return android.widget.FrameLayout(this).apply {
            addView(FondoVidrio(this@MainActivity))
            addView(scroll)
        }
    }

    @Deprecated("Usado solo para elegir la foto de portada")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            // 1) Eligió la foto: abrir el editor de encuadre.
            PEDIDO_FOTO -> data?.data?.let { uri ->
                startActivityForResult(Intent(this, RecorteActivity::class.java).setData(uri), PEDIDO_RECORTE)
            }
            // 2) Encuadró la foto: mandarla a la TV.
            PEDIDO_RECORTE -> {
                val ruta = data?.getStringExtra(RecorteActivity.EXTRA_RUTA) ?: return
                anotar("… Mandando la foto a la TV")
                trabajo.execute {
                    anotar(
                        try {
                            acciones.cambiarPortada(File(ruta).readBytes())
                        } catch (e: Exception) {
                            explicarError(e)
                        },
                    )
                }
            }
        }
    }

    // ---------- Mis TVs ----------

    private lateinit var listaTvs: LinearLayout
    private var campoIp: EditText? = null
    private var campoPuerto: EditText? = null

    /**
     * Conecta con la TV. Si con el puerto guardado no anda (cambia cada vez que la TV se reinicia),
     * lo busca en el Wi-Fi y prueba de nuevo. Al conectar, la TV queda guardada en "Mis TVs".
     */
    private fun conectarA(host: String, puertoGuardado: Int): String {
        if (host.isBlank()) return "Escribí la IP de la TV o elegí una de Mis TVs."
        var puerto = puertoGuardado
        var ok = puerto > 0 && intentar(host, puerto)
        if (!ok) {
            val nuevo = BuscadorTvs.buscar(this)[host]
            if (nuevo != null) {
                puerto = nuevo
                ok = intentar(host, puerto)
            } else if (puerto == 0) {
                puerto = 5555 // TVs viejas con ADB por red
                ok = intentar(host, puerto)
            }
        }
        if (!ok) return "No se pudo conectar con $host. Revisá que la TV esté prendida y con la Depuración inalámbrica activada; si es nueva, primero tocá Vincular."
        prefs.edit().putString("ip", host).putString("puerto", puerto.toString()).apply()
        val modelo = try { tv.ejecutar("getprop ro.product.model").trim() } catch (e: Exception) { "" }
        val guardada = MisTvs.recordar(this, host, puerto, modelo)
        val info = acciones.infoTv()
        runOnUiThread {
            campoIp?.setText(host)
            campoPuerto?.setText(puerto.toString())
            estado.text = "${guardada.nombre} · $info"
            botonesTv.forEach { activar(it, true) }
            pintarTvs()
        }
        return "${guardada.nombre}: $info"
    }

    private fun intentar(host: String, puerto: Int): Boolean =
        try { tv.reconectar(host, puerto) } catch (e: Exception) { false }

    /** Dibuja las TVs guardadas, y abajo las nuevas que encontró la búsqueda (para tocarlas y conectar). */
    private fun pintarTvs(nuevas: Map<String, Int> = emptyMap()) {
        listaTvs.removeAllViews()
        val guardadas = MisTvs.cargar(this)
        if (guardadas.isEmpty() && nuevas.isEmpty()) {
            listaTvs.addView(nota("Todavía no hay TVs guardadas. Conectate una vez (abajo) y queda acá para la próxima."))
        }
        guardadas.forEach { t ->
            listaTvs.addView(filaTv("📺  ${t.nombre}", "${t.ip} · ${t.modelo.ifBlank { "TV" }}", true,
                alTocar = { conectarA(t.ip, t.puerto) },
                alMantener = { opcionesTv(t) }))
        }
        nuevas.forEach { (ip, p) ->
            listaTvs.addView(filaTv("＋  TV nueva en $ip", "Tocá para conectar y guardarla", false,
                alTocar = { conectarA(ip, p) }, alMantener = null))
        }
    }

    private fun filaTv(titulo: String, detalle: String, guardada: Boolean, alTocar: () -> String, alMantener: (() -> Unit)?): View {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(if (guardada) 0x26FFFFFF else 0x14FFFFFF)
                cornerRadius = dp(18).toFloat()
                if (!guardada) setStroke(dp(1), 0x55F3E9D2)
            }
            addView(TextView(this@MainActivity).apply {
                text = titulo
                setTextColor(tinta)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(this@MainActivity).apply {
                text = detalle
                setTextColor(tintaSuave)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
        }
        fila.setOnClickListener {
            anotar("… Conectando con ${titulo.substringAfter("  ")}")
            trabajo.execute {
                anotar(try { alTocar() } catch (e: Exception) { explicarError(e) })
            }
        }
        if (alMantener != null) fila.setOnLongClickListener { alMantener(); true }
        fila.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        return fila
    }

    /** Mantener apretada una TV: cambiarle el nombre o sacarla de la lista. */
    private fun opcionesTv(t: TvGuardada) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(t.nombre)
            .setItems(arrayOf("Cambiar el nombre", "Sacar de Mis TVs")) { _, i ->
                if (i == 0) {
                    val campo = EditText(this).apply {
                        setText(t.nombre)
                        setSelection(t.nombre.length)
                    }
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Nombre de la TV (ej. Living, Oficina)")
                        .setView(campo)
                        .setPositiveButton("Guardar") { _, _ ->
                            val nombre = campo.text.toString().trim()
                            if (nombre.isNotBlank()) MisTvs.renombrar(this, t.ip, nombre)
                            pintarTvs()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    MisTvs.borrar(this, t.ip)
                    pintarTvs()
                }
            }
            .show()
    }

    private fun subtitulo(texto: String) = TextView(this).apply {
        text = texto
        setTextColor(crema)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(12), 0, dp(2))
    }

    // ---------- Piezas de la interfaz ----------

    private fun seccion(padre: LinearLayout, titulo: String): LinearLayout {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(vidrio)
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), borde)
            }
        }
        caja.addView(TextView(this).apply {
            text = titulo
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        padre.addView(caja, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        return caja
    }

    private fun nota(texto: String) = TextView(this).apply {
        text = texto
        setTextColor(tintaSuave)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, dp(4), 0, dp(6))
    }

    private fun campo(padre: LinearLayout, pista: String, valor: String, tipo: Int): EditText {
        val e = EditText(this).apply {
            hint = pista
            setText(valor)
            inputType = tipo
            setTextColor(tinta)
            setHintTextColor(0x80FFFFFF.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(0x1AFFFFFF)
                cornerRadius = dp(14).toFloat()
            }
        }
        padre.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        return e
    }

    /** Botón que ejecuta una acción en segundo plano y anota el resultado. Si [requiereTv], se habilita al conectar. */
    /** Los mismos tres botones para Pachi TV y para Solari. */
    private fun botonesDeModelo(padre: LinearLayout, m: Modelo) {
        boton(padre, "Todo en uno (también cambia el botón Inicio)") { acciones.todoEnUnoModelo(m) }
        boton(padre, "Instalar y preparar la isla dinámica") {
            listOf(acciones.instalarModelo(m), acciones.prepararModelo(m)).joinToString("\n")
        }
        boton(padre, "Usar ${m.nombre} con el botón Inicio") { acciones.usarModeloConInicio(m) }
    }

    private fun boton(padre: LinearLayout, texto: String, requiereTv: Boolean = true, accion: () -> String) {
        val b = Button(this).apply {
            text = texto
            estilo(this, requiereTv)
        }
        activar(b, !requiereTv)
        b.setOnClickListener {
            activar(b, false)
            anotar("… $texto")
            trabajo.execute {
                val resultado = try {
                    accion()
                } catch (e: Exception) {
                    explicarError(e)
                }
                anotar(resultado)
                runOnUiThread { activar(b, true) }
            }
        }
        if (requiereTv) botonesTv += b
        padre.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })
    }

    /** Traduce los errores más comunes de la conexión a algo entendible. */
    private fun explicarError(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("pairing is required", true) -> "La TV todavía no conoce este celular: tocá Vincular primero."
            m.contains("connect() must be called", true) || m.contains("Se perdió la conexión", true) ->
                "Se perdió la conexión con la TV y no se pudo reconectar. Revisá que la Depuración inalámbrica siga " +
                    "activada (si la TV se reinició, el puerto cambia) y tocá Conectar."
            m.contains("Primero tocá Conectar", true) -> "Primero tocá Conectar."
            m.contains("refused", true) || m.contains("ECONNREFUSED", true) -> "La TV rechazó la conexión: revisá el puerto (cambia al reiniciar la TV)."
            m.contains("timed out", true) || m.contains("timeout", true) -> "No hubo respuesta: revisá la IP y que el celular esté en el mismo Wi-Fi."
            e is NumberFormatException -> "Completá el puerto con números."
            else -> "Error: ${m.ifBlank { e.javaClass.simpleName }}"
        }
    }

    private fun activar(b: Button, activo: Boolean) {
        b.isEnabled = activo
        b.alpha = if (activo) 1f else 0.45f
    }

    private fun estilo(b: Button, principal: Boolean) {
        b.isAllCaps = false
        b.setTextColor(if (principal) 0xFF111114.toInt() else tinta)
        b.stateListAnimator = null
        b.background = GradientDrawable().apply {
            setColor(if (principal) crema else 0x26FFFFFF)
            cornerRadius = dp(24).toFloat()
        }
        b.gravity = Gravity.CENTER
    }

    private fun anotar(texto: String) = runOnUiThread {
        registro.append(texto + "\n\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun agregarChat(texto: String) = runOnUiThread {
        chat.append(texto + "\n\n")
    }

    /** Muestra el comando que Claude quiere ejecutar y espera tu respuesta. */
    private fun confirmarComando(comando: String, motivo: String): Boolean {
        val respuesta = CountDownLatch(1)
        var aprobado = false
        runOnUiThread {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Claude quiere ejecutar")
                .setMessage("$comando\n\nPor qué: $motivo")
                .setPositiveButton("Ejecutar") { _, _ -> aprobado = true; respuesta.countDown() }
                .setNegativeButton("No") { _, _ -> respuesta.countDown() }
                .setOnCancelListener { respuesta.countDown() }
                .show()
        }
        respuesta.await()
        return aprobado
    }

    private fun dp(valor: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val PEDIDO_FOTO = 1
        const val PEDIDO_RECORTE = 2
    }
}

/** Fondo casi negro con manchas suaves de color: a través de las tarjetas translúcidas da el efecto de vidrio. */
class FondoVidrio(contexto: android.content.Context) : View(contexto) {
    private val pincel = android.graphics.Paint().apply { isDither = true }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(0xFF0B0B0F.toInt())
        mancha(canvas, w * 0.1f, h * 0.08f, w * 0.95f, 0xB04C3CFF.toInt())
        mancha(canvas, w * 0.95f, h * 0.4f, w * 0.9f, 0x90C8372D.toInt())
        mancha(canvas, w * 0.05f, h * 0.8f, w * 1.0f, 0x801E9CE6.toInt())
    }

    private fun mancha(canvas: android.graphics.Canvas, x: Float, y: Float, radio: Float, color: Int) {
        pincel.shader = android.graphics.RadialGradient(x, y, radio, color, Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pincel)
    }
}
