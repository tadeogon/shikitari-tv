package com.shikitari.instalador

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.conscrypt.Conscrypt
import java.io.File
import java.security.Security
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Shikitari Instalador: desde el celular, conecta con una TV por depuración inalámbrica,
 * instala Shikitari TV, Pachi TV o Solari, optimiza la TV, la controla y ofrece ayuda con Claude.
 *
 * Está ordenado en pantallas: el Inicio (tus TVs y los accesos) y una pantalla por tema.
 * Lo que está pasando se muestra en un cartel abajo, que se va solo.
 */
class MainActivity : Activity() {

    private val vidrio = 0x1FFFFFFF
    private val borde = 0x33FFFFFF
    private val tinta = Color.WHITE
    private val tintaSuave = 0xA6FFFFFF.toInt()
    private val crema = 0xFFF3E9D2.toInt()
    private val verde = 0xFF34C759.toInt()
    private val rojo = 0xFFFF6B5E.toInt()

    private val prefs by lazy { getSharedPreferences("app", MODE_PRIVATE) }
    private val trabajo = Executors.newSingleThreadExecutor()
    private val tv by lazy { ConexionTv.obtener(this) }
    private val acciones by lazy { AccionesTv(this, tv) }
    private val principal = Handler(Looper.getMainLooper())

    private enum class Pantalla { INICIO, AGREGAR, SHIKITARI, PACHI, SOLARI, OPTIMIZAR, CLAUDE, HISTORIAL, BOTONES }

    private lateinit var contenedor: FrameLayout
    private val pantallas = HashMap<Pantalla, View>()
    private val pila = ArrayDeque<Pantalla>()

    // Estado de la conexión
    private var conectada: TvGuardada? = null
    private var infoConectada = ""
    private lateinit var tarjetaEstado: LinearLayout
    private lateinit var puntoEstado: View
    private lateinit var tituloEstado: TextView
    private lateinit var detalleEstado: TextView
    private lateinit var listaTvs: LinearLayout
    private val mosaicosTv = mutableListOf<View>()

    // Cartel de progreso
    private lateinit var cartel: LinearLayout
    private lateinit var cartelRueda: ProgressBar
    private lateinit var cartelIcono: TextView
    private lateinit var cartelTexto: TextView
    private val esconderCartel = Runnable { animarCartel(false) }

    private lateinit var registro: TextView
    private lateinit var chat: TextView
    private var ayuda: AyudaClaude? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Conscrypt hace posible el emparejamiento seguro (TLS) con la TV.
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        setContentView(armarRaiz())
        ir(Pantalla.INICIO)
    }

    // ---------- Navegación ----------

    private fun armarRaiz(): View {
        contenedor = FrameLayout(this)
        registro = TextView(this).apply {
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            text = ""
        }
        return FrameLayout(this).apply {
            addView(FondoVidrio(this@MainActivity))
            addView(contenedor)
            addView(armarCartel(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(dp(14), 0, dp(14), dp(20))
            })
        }
    }

    private fun ir(p: Pantalla) {
        if (pila.lastOrNull() != p) pila.addLast(p)
        mostrar(p)
    }

    private fun mostrar(p: Pantalla) {
        val vista = pantallas.getOrPut(p) { armarPantalla(p) }
        contenedor.removeAllViews()
        (vista.parent as? ViewGroup)?.removeView(vista)
        contenedor.addView(vista)
        // Entrada suave desde la derecha
        val desde = if (p == Pantalla.INICIO) 0f else dp(40).toFloat()
        vista.alpha = 0f
        vista.translationX = desde
        animar(220) { c ->
            vista.alpha = c
            vista.translationX = desde * (1 - c)
        }
        if (p == Pantalla.INICIO) pintarEstado()
    }

    @Deprecated("Atrás del sistema")
    override fun onBackPressed() {
        if (pila.size > 1) {
            pila.removeLast()
            mostrar(pila.last())
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun armarPantalla(p: Pantalla): View = when (p) {
        Pantalla.INICIO -> armarInicio()
        Pantalla.AGREGAR -> armarAgregar()
        Pantalla.SHIKITARI -> armarShikitari()
        Pantalla.PACHI -> armarModelo(AccionesTv.PACHI, "Ziota",
            "El launcher estilo Apple TV, con isla dinámica para la música, menú de vidrio y protector con el logo. " +
                "Se puede tener junto a Shikitari TV; el botón Inicio abre uno solo de los dos.")
        Pantalla.SOLARI -> armarModelo(AccionesTv.SOLARI, "Solari · Bienes Raíces",
            "La versión formal para las TVs del trabajo: vitrina de propiedades con el video y QR, logo y tipografía de Solari " +
                "y protector sobrio. Se puede instalar junto a Ziota.")
        Pantalla.OPTIMIZAR -> armarOptimizar()
        Pantalla.CLAUDE -> armarClaude()
        Pantalla.HISTORIAL -> armarHistorial()
        Pantalla.BOTONES -> armarBotones()
    }

    /** Columna con scroll; las pantallas internas llevan arriba la flecha para volver. */
    private fun pagina(titulo: String?, cuerpo: (LinearLayout) -> Unit): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(120))
        }
        if (titulo != null) {
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = "‹"
                    setTextColor(tinta)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
                    setPadding(dp(2), 0, dp(16), dp(6))
                    setOnClickListener { @Suppress("DEPRECATION") onBackPressed() }
                })
                addView(TextView(this@MainActivity).apply {
                    text = titulo
                    setTextColor(tinta)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                })
            })
        }
        cuerpo(col)
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            fitsSystemWindows = true
            addView(col)
        }
    }

    // ---------- Inicio ----------

    private fun armarInicio(): View = pagina(null) { col ->
        col.addView(TextView(this).apply {
            text = "Instalador"
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        col.addView(TextView(this).apply {
            text = "Shikitari TV · Ziota · Solari"
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })

        // Estado: a qué TV estás conectado
        tarjetaEstado = tarjeta(col).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        puntoEstado = View(this)
        tarjetaEstado.addView(puntoEstado, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(14) })
        tarjetaEstado.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tituloEstado = TextView(this@MainActivity).apply {
                setTextColor(tinta)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            detalleEstado = TextView(this@MainActivity).apply {
                setTextColor(tintaSuave)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            addView(tituloEstado)
            addView(detalleEstado)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // Mis TVs
        col.addView(subtitulo("Mis TVs"))
        listaTvs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listaTvs)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(botonChico("🔍  Buscar en el Wi-Fi") { buscarTvs() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
            addView(botonChico("＋  Agregar TV") { ir(Pantalla.AGREGAR) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(5) })
            setPadding(0, dp(8), 0, 0)
        })
        pintarTvs()

        // Accesos
        col.addView(subtitulo("¿Qué querés hacer?"))
        val mosaicos = listOf(
            Triple("📺", "Control", "Ver la TV y manejarla") to { abrirConTv(ControlActivity::class.java) },
            Triple("📁", "Archivos", "Ver, borrar y pasar archivos") to { abrirConTv(ArchivosActivity::class.java) },
            Triple("📦", "Instalar APK", "Desde tu celular a la TV") to { abrirConTv(ArchivosActivity::class.java, instalar = true) },
            Triple("🐱", "Shikitari TV", "Launcher con tus gatos") to { ir(Pantalla.SHIKITARI) },
            Triple("🔻", "Ziota", "Estilo Apple TV") to { ir(Pantalla.PACHI) },
            Triple("☀️", "Solari", "Launcher del trabajo") to { ir(Pantalla.SOLARI) },
            Triple("⚡", "Optimizar", "Más rápida y liviana") to { ir(Pantalla.OPTIMIZAR) },
            Triple("🎛", "Botones", "Cambiar los del control") to { ir(Pantalla.BOTONES) },
            Triple("✨", "Claude", "Ayuda con problemas") to { ir(Pantalla.CLAUDE) },
        )
        mosaicosTv.clear()
        mosaicos.chunked(2).forEach { par ->
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                par.forEachIndexed { i, (datos, accion) ->
                    val m = mosaico(datos.first, datos.second, datos.third, accion)
                    if (datos.second != "Claude") mosaicosTv += m
                    addView(m, LinearLayout.LayoutParams(0, dp(116), 1f).apply {
                        if (i == 0) marginEnd = dp(5) else marginStart = dp(5)
                        topMargin = dp(10)
                    })
                }
            })
        }
        col.addView(TextView(this).apply {
            text = "Ver historial de lo que se hizo"
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
            setOnClickListener { ir(Pantalla.HISTORIAL) }
        })
    }

    private fun mosaico(icono: String, titulo: String, detalle: String, accion: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(vidrio)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), borde)
            }
            addView(TextView(this@MainActivity).apply {
                text = icono
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            })
            addView(View(this@MainActivity), LinearLayout.LayoutParams(1, 0, 1f))
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
                maxLines = 1
            })
            setOnClickListener { accion() }
        }

    private fun abrirConTv(pantalla: Class<out Activity>, instalar: Boolean = false) {
        if (conectada == null) {
            avisar(false, "Primero conectá una TV: tocala en Mis TVs.")
            return
        }
        startActivity(Intent(this, pantalla).putExtra(ArchivosActivity.EXTRA_INSTALAR, instalar))
    }

    private fun pintarEstado() {
        if (!::tarjetaEstado.isInitialized) return
        val t = conectada
        puntoEstado.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (t != null) verde else 0x55FFFFFF)
        }
        tituloEstado.text = t?.let { "Conectado a ${it.nombre}" } ?: "Sin conectar"
        detalleEstado.text = if (t != null) infoConectada else "Tocá una TV de la lista para conectarte."
        mosaicosTv.forEach { it.alpha = if (t != null) 1f else 0.55f }
    }

    // ---------- Mis TVs ----------

    private fun buscarTvs() {
        ejecutar("Buscando TVs en el Wi-Fi", requiereTv = false) {
            val halladas = BuscadorTvs.buscar(this)
            val guardadas = MisTvs.cargar(this).associateBy { it.ip }
            runOnUiThread { pintarTvs(halladas.filterKeys { it !in guardadas }) }
            if (halladas.isEmpty()) {
                "No apareció ninguna TV. Revisá que esté prendida, en el mismo Wi-Fi y con la Depuración inalámbrica activada."
            } else {
                "Encontré ${halladas.size} TV(s): " + halladas.keys.joinToString { guardadas[it]?.nombre ?: it }
            }
        }
    }

    /**
     * Conecta con la TV. Si con el puerto guardado no anda (cambia cada vez que la TV se reinicia),
     * lo busca en el Wi-Fi y prueba de nuevo. Al conectar, la TV queda guardada en "Mis TVs".
     */
    private fun conectarA(host: String, puertoGuardado: Int): String {
        if (host.isBlank()) return "Escribí la IP de la TV."
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
        if (!ok) return "No se pudo conectar con $host. Revisá que la TV esté prendida y con la Depuración inalámbrica activada; si es nueva, agregala con el código."
        prefs.edit().putString("ip", host).putString("puerto", puerto.toString()).apply()
        val modelo = try { tv.ejecutar("getprop ro.product.model").trim() } catch (e: Exception) { "" }
        val guardada = MisTvs.recordar(this, host, puerto, modelo)
        val info = acciones.infoTv().removePrefix("Conectado a ")
        runOnUiThread {
            conectada = guardada
            infoConectada = info
            pintarEstado()
            pintarTvs()
        }
        return "Conectado a ${guardada.nombre} ✓"
    }

    private fun intentar(host: String, puerto: Int): Boolean =
        try { tv.reconectar(host, puerto) } catch (e: Exception) { false }

    /** Dibuja las TVs guardadas, y abajo las nuevas que encontró la búsqueda (para tocarlas y conectar). */
    private fun pintarTvs(nuevas: Map<String, Int> = emptyMap()) {
        if (!::listaTvs.isInitialized) return
        listaTvs.removeAllViews()
        val guardadas = MisTvs.cargar(this)
        if (guardadas.isEmpty() && nuevas.isEmpty()) {
            listaTvs.addView(nota("Todavía no hay TVs guardadas. Tocá \"Buscar en el Wi-Fi\" o \"Agregar TV\"."))
        }
        guardadas.forEach { t ->
            val activa = conectada?.ip == t.ip
            listaTvs.addView(filaTv(
                (if (activa) "● " else "📺  ") + t.nombre,
                "${t.modelo.ifBlank { "TV" }} · ${t.ip}" + if (activa) " · conectada" else "",
                if (activa) 1 else 0,
                alTocar = { ejecutar("Conectando con ${t.nombre}", requiereTv = false) { conectarA(t.ip, t.puerto) } },
                alMantener = { opcionesTv(t) },
            ))
        }
        nuevas.forEach { (ip, p) ->
            listaTvs.addView(filaTv("＋  TV nueva en $ip", "Tocá para conectar y guardarla", 2,
                alTocar = { ejecutar("Conectando con $ip", requiereTv = false) { conectarA(ip, p) } }, alMantener = null))
        }
    }

    /** tipo: 0 guardada, 1 conectada, 2 encontrada en la búsqueda. */
    private fun filaTv(titulo: String, detalle: String, tipo: Int, alTocar: () -> Unit, alMantener: (() -> Unit)?): View {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = GradientDrawable().apply {
                setColor(if (tipo == 1) 0x3334C759 else if (tipo == 2) 0x14FFFFFF else 0x26FFFFFF)
                cornerRadius = dp(18).toFloat()
                if (tipo == 2) setStroke(dp(1), 0x55F3E9D2)
                if (tipo == 1) setStroke(dp(1), 0x8834C759.toInt())
            }
            addView(TextView(this@MainActivity).apply {
                text = titulo
                setTextColor(if (tipo == 1) verde else tinta)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(this@MainActivity).apply {
                text = detalle
                setTextColor(tintaSuave)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
        }
        fila.setOnClickListener { alTocar() }
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
                            if (nombre.isNotBlank()) {
                                MisTvs.renombrar(this, t.ip, nombre)
                                if (conectada?.ip == t.ip) conectada = conectada?.copy(nombre = nombre)
                            }
                            pintarTvs()
                            pintarEstado()
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

    // ---------- Agregar una TV ----------

    private fun armarAgregar(): View = pagina("Agregar una TV") { col ->
        val caja = tarjeta(col)
        caja.addView(nota(
            "1. En la TV: Ajustes → Sistema → Opciones para desarrolladores → Depuración inalámbrica (activada).\n" +
                "2. Tocá \"Vincular dispositivo con código\": la TV muestra una IP y un código de 6 números.\n" +
                "3. Escribilos acá y tocá Vincular. El puerto lo busco solo.",
        ))
        val ip = campo(caja, "IP de la TV (ej. 192.168.100.2)", prefs.getString("ip", "") ?: "", InputType.TYPE_CLASS_PHONE)
        val codigo = campo(caja, "Código de vinculación (6 números)", "", InputType.TYPE_CLASS_NUMBER)
        boton(caja, "Vincular y conectar", principal = true, requiereTv = false) {
            val host = ip.text.toString().trim()
            prefs.edit().putString("ip", host).apply()
            // La TV anuncia el puerto de vinculación mientras muestra el código.
            val puertoV = BuscadorTvs.buscar(this, BuscadorTvs.VINCULACION)[host]
                ?: return@boton "No encontré la pantalla del código en $host. Revisá la IP y que el código siga en la TV."
            if (!tv.pair(host, puertoV, codigo.text.toString().trim())) return@boton "No se pudo vincular. Revisá el código."
            val r = conectarA(host, 0)
            if (r.contains("✓")) runOnUiThread { @Suppress("DEPRECATION") onBackPressed() }
            "Vinculada ✓ $r"
        }

        val avanzado = tarjeta(col)
        avanzado.addView(subtitulo("TVs viejas o sin código"))
        avanzado.addView(nota("Si la TV tiene \"Depuración por red\" (puerto 5555), escribí la IP y tocá Conectar; en la TV aceptá el aviso."))
        val ip2 = campo(avanzado, "IP de la TV", "", InputType.TYPE_CLASS_PHONE)
        val puerto = campo(avanzado, "Puerto (vacío = 5555 o lo busco solo)", "", InputType.TYPE_CLASS_NUMBER)
        boton(avanzado, "Conectar", requiereTv = false) {
            val r = conectarA(ip2.text.toString().trim(), puerto.text.toString().trim().toIntOrNull() ?: 0)
            if (r.contains("✓")) runOnUiThread { @Suppress("DEPRECATION") onBackPressed() }
            r
        }
    }

    // ---------- Launchers ----------

    private fun armarShikitari(): View = pagina("Shikitari TV") { col ->
        val caja = tarjeta(col)
        caja.addView(nota("El launcher con tus gatos: lo instala, le da permisos y activa el botón Inicio y el protector de pantalla."))
        boton(caja, "Todo en uno (recomendado)", principal = true) { acciones.todoEnUno() }
        boton(caja, "Solo instalar") { acciones.instalarShikitari() }
        boton(caja, "Solo dar permisos") { acciones.darPermisos() }
        boton(caja, "Solo activar Inicio y protector") { acciones.activarInicioYProtector() }

        val portada = tarjeta(col)
        portada.addView(subtitulo("Portada"))
        portada.addView(nota("La imagen grande del Inicio y del protector. Puede ser cualquier foto del celular."))
        boton(portada, "Elegir foto") {
            runOnUiThread { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PEDIDO_FOTO) }
            "Elegí la foto…"
        }
        boton(portada, "Volver a la portada original") { acciones.portadaOriginal() }
    }

    private fun armarModelo(m: Modelo, titulo: String, descripcion: String): View = pagina(titulo) { col ->
        val caja = tarjeta(col)
        caja.addView(nota(descripcion))
        boton(caja, "Todo en uno (también cambia el botón Inicio)", principal = true) { acciones.todoEnUnoModelo(m) }
        boton(caja, "Instalar y preparar la isla dinámica") {
            listOf(acciones.instalarModelo(m), acciones.prepararModelo(m)).joinToString("\n")
        }
        boton(caja, "Usar ${m.nombre} con el botón Inicio") { acciones.usarModeloConInicio(m) }
    }

    private fun armarOptimizar(): View = pagina("Optimizar la TV") { col ->
        val caja = tarjeta(col)
        caja.addView(nota(
            "La app se da cuenta sola de qué TV es:\n" +
                "• Google TV: quita ${AccionesTv.LIMPIEZA_SEGURA.size} apps de Google que no hacen falta (juegos, comentarios, impresión, etc.).\n" +
                "• Fire TV: apaga ${AccionesTv.LIMPIEZA_FIRE.size} apps de Amazon de rastreo, publicidad, compras y promos, y el rastreo " +
                "publicitario. Alexa, Prime Video, la tienda y el control no se tocan.\n" +
                "En las dos libera caché, acelera las animaciones y te muestra cuánta memoria ganaste. Todo se puede deshacer.",
        ))
        boton(caja, "Optimizar", principal = true) { acciones.optimizar() }
        boton(caja, "Cerrar apps abiertas") { acciones.cerrarApps() }
        boton(caja, "Deshacer optimización") { acciones.deshacer() }

        col.addView(subtitulo("Si algo sale mal"))
        val emergencia = tarjeta(col)
        emergencia.addView(nota("Solo Fire TV: si después de optimizar el inicio de Amazon se ve en negro, esto vuelve a prender lo que lo dibuja."))
        boton(emergencia, "El inicio de Amazon quedó en negro") { acciones.emergenciaFire() }
    }

    // ---------- Botones del control ----------

    /** Botones del control que se pueden cambiar (los de apps del Fire TV se llaman APP_1..APP_4 en el sistema). */
    private val botonesControl = listOf(
        "KEYCODE_APP_1" to "Botón Prime Video",
        "KEYCODE_APP_2" to "Botón Netflix",
        "KEYCODE_APP_3" to "Botón Disney+",
        "KEYCODE_APP_4" to "Botón Peacock",
        "KEYCODE_LIVE_TV" to "Botón TV (guía)",
        "KEYCODE_CHANNEL_UP" to "Canal +",
        "KEYCODE_CHANNEL_DOWN" to "Canal −",
    )

    private fun claveBotones() = "botones_${conectada?.ip}"

    private fun mapaBotones(): MutableMap<String, String> =
        (prefs.getString(claveBotones(), "") ?: "").split(';').mapNotNull {
            it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] }
        }.toMap().toMutableMap()

    private fun nombreDeApp(paquete: String) =
        ControlActivity.NOMBRES[paquete] ?: paquete.split('.').last { it.length > 2 }.replaceFirstChar { it.uppercase() }

    private fun armarBotones(): View = pagina("Botones del control") { col ->
        val caja = tarjeta(col)
        caja.addView(nota(
            "Elegí qué app abre cada botón de apps del control. Funciona con Ziota o Solari instalada y con su " +
                "botón Inicio activado (usa el mismo servicio). Los botones que dejes \"como viene\" hacen lo de siempre.",
        ))
        val mapa = mapaBotones()
        val pintores = mutableListOf<() -> Unit>()
        // Lo que esté configurado en la TV manda (puede haberse cambiado desde otro celular o la PC).
        if (conectada != null) trabajo.execute {
            try {
                val salida = tv.ejecutar("am broadcast -n ${AccionesTv.PACHI.paquete}/com.pachi.tv.ReceptorBotones")
                val enTv = Regex("data=\"([^\"]*)\"").find(salida)?.groupValues?.get(1) ?: return@execute
                runOnUiThread {
                    mapa.clear()
                    enTv.split(';').forEach { par -> par.split('=', limit = 2).takeIf { it.size == 2 }?.let { mapa[it[0]] = it[1] } }
                    prefs.edit().putString(claveBotones(), enTv).apply()
                    pintores.forEach { it() }
                }
            } catch (_: Exception) {
            }
        }
        botonesControl.forEach { (tecla, nombre) ->
            val b = Button(this).apply {
                estilo(this, false)
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(18), 0, dp(18), 0)
            }
            fun pintar() {
                b.text = "$nombre  →  " + (mapa[tecla]?.let { nombreDeApp(it) } ?: "como viene")
            }
            pintar()
            pintores += { pintar() }
            b.setOnClickListener { elegirAppPara(nombre) { paquete ->
                if (paquete == null) mapa.remove(tecla) else mapa[tecla] = paquete
                pintar()
            } }
            caja.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(8) })
        }
        boton(caja, "Guardar en la TV", principal = true) {
            val texto = mapa.entries.joinToString(";") { "${it.key}=${it.value}" }
            prefs.edit().putString(claveBotones(), texto).apply()
            val instalados = tv.ejecutar("pm list packages").lines().map { it.trim().removePrefix("package:") }.toSet()
            val launchers = listOf(AccionesTv.PACHI, AccionesTv.SOLARI).filter { it.paquete in instalados }
            if (launchers.isEmpty()) return@boton "Primero instalá Ziota o Solari en esta TV."
            val res = StringBuilder()
            // En Fire TV, Amazon se queda con esos botones: Pachi los reconoce leyendo el registro del sistema.
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
    private fun elegirAppPara(boton: String, listo: (String?) -> Unit) {
        if (conectada == null) {
            avisar(false, "Primero conectá una TV: tocala en Mis TVs.")
            return
        }
        trabajando("Buscando las apps de la TV")
        trabajo.execute {
            val apps = try {
                val todas = tv.ejecutar("pm list packages").lines().map { it.trim().removePrefix("package:") }.filter { it.isNotBlank() }.toSet()
                val terceros = tv.ejecutar("pm list packages -3").lines().map { it.trim().removePrefix("package:") }.filter { it.isNotBlank() }
                (terceros + ControlActivity.NOMBRES.keys.filter { it in todas })
                    .distinct().filter { it !in ControlActivity.OCULTAR }
                    .map { it to nombreDeApp(it) }.sortedBy { it.second.lowercase() }
            } catch (e: Exception) {
                runOnUiThread { avisar(false, explicarError(e)) }
                return@execute
            }
            runOnUiThread {
                animarCartel(false)
                val opciones = listOf("↺  Como viene de fábrica") + apps.map { it.second }
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("$boton abre…")
                    .setItems(opciones.toTypedArray()) { _, i -> listo(if (i == 0) null else apps[i - 1].first) }
                    .show()
            }
        }
    }

    // ---------- Claude ----------

    private fun armarClaude(): View = pagina("Ayuda con Claude") { col ->
        val caja = tarjeta(col)
        caja.addView(nota(
            "Contale el problema y Claude revisa la TV. Las consultas que solo leen se hacen solas; " +
                "cualquier cambio te lo pregunta antes. Necesita tu clave de API de Anthropic (console.anthropic.com, se paga por uso). " +
                "Lo que Claude consulta de la TV se envía a Anthropic.",
        ))
        val clave = campo(caja, "Clave de API (sk-ant-...)", prefs.getString("clave", "") ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        chat = TextView(this).apply {
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(8), 0, dp(8))
            setTextIsSelectable(true)
        }
        caja.addView(chat)
        val pregunta = campo(caja, "Ej.: el perfil de YouTube no carga", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        caja.addView(Button(this).apply {
            text = "Preguntar a Claude"
            estilo(this, true)
            setOnClickListener {
                val texto = pregunta.text.toString().trim()
                val claveApi = clave.text.toString().trim()
                if (conectada == null) {
                    avisar(false, "Primero conectá una TV.")
                    return@setOnClickListener
                }
                if (texto.isEmpty() || claveApi.isEmpty()) {
                    avisar(false, "Escribí tu clave de API y la pregunta.")
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
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })
    }

    private fun armarHistorial(): View = pagina("Historial") { col ->
        val caja = tarjeta(col)
        caja.addView(nota("Todo lo que se hizo en las TVs, con el detalle completo (también lo de Control y Archivos)."))
        (registro.parent as? ViewGroup)?.removeView(registro)
        registro.text = Bitacora.leer(this).ifEmpty { "Todavía no se hizo nada." }
        caja.addView(registro)
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
                ejecutar("Mandando la foto a la TV") { acciones.cambiarPortada(File(ruta).readBytes()) }
            }
        }
    }

    // ---------- Cartel de progreso ----------

    private fun armarCartel(): View {
        cartel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                setColor(0xF2202024.toInt())
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), borde)
            }
            elevation = dp(12).toFloat()
            visibility = View.GONE
            setOnClickListener { animarCartel(false) }
        }
        cartelRueda = ProgressBar(this).apply { isIndeterminate = true }
        cartel.addView(cartelRueda, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(14) })
        cartelIcono = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        cartel.addView(cartelIcono, LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(14) })
        cartelTexto = TextView(this).apply {
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 4
        }
        cartel.addView(cartelTexto, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return cartel
    }

    /** Cartel con la ruedita mientras trabaja. */
    private fun trabajando(texto: String) {
        principal.removeCallbacks(esconderCartel)
        cartelRueda.visibility = View.VISIBLE
        cartelIcono.visibility = View.GONE
        cartelTexto.text = "$texto…"
        animarCartel(true)
    }

    /** Cartel con el resultado: tilde verde o cruz roja. Se va solo (tocándolo también). */
    private fun avisar(ok: Boolean, texto: String) {
        principal.removeCallbacks(esconderCartel)
        cartelRueda.visibility = View.GONE
        cartelIcono.visibility = View.VISIBLE
        cartelIcono.text = if (ok) "✓" else "!"
        cartelIcono.setTextColor(if (ok) verde else rojo)
        cartelTexto.text = texto
        animarCartel(true)
        principal.postDelayed(esconderCartel, if (ok) 3500 else 7000)
    }

    private fun animarCartel(mostrar: Boolean) {
        if (mostrar && cartel.visibility == View.VISIBLE) return
        if (!mostrar && cartel.visibility != View.VISIBLE) return
        cartel.visibility = View.VISIBLE
        val alto = dp(90).toFloat()
        animar(240) { c ->
            val k = if (mostrar) c else 1 - c
            cartel.alpha = k
            cartel.translationY = alto * (1 - k)
            if (!mostrar && c >= 1f) cartel.visibility = View.GONE
        }
    }

    /**
     * Hace algo en segundo plano mostrando el cartel, y guarda el resultado completo en el Historial.
     * Si [requiereTv] y no hay TV conectada, avisa en vez de intentarlo.
     */
    private fun ejecutar(titulo: String, requiereTv: Boolean = true, accion: () -> String) {
        if (requiereTv && conectada == null) {
            avisar(false, "Primero conectá una TV: tocala en Mis TVs.")
            return
        }
        trabajando(titulo)
        trabajo.execute {
            val resultado = try {
                accion()
            } catch (e: Exception) {
                explicarError(e)
            }
            anotar("▸ $titulo\n$resultado")
            val fallo = Regex("No se pudo|Error|Primero|No encontré|No apareció|rechazó|No hubo|Se perdió").containsMatchIn(resultado)
            // En el cartel va un resumen; el detalle completo queda en el Historial.
            val resumen = resultado.lines().filter { it.isNotBlank() }.let { l ->
                if (l.size <= 3) l.joinToString("\n") else l.take(2).joinToString("\n") + "\n(ver detalle en Historial)"
            }
            runOnUiThread { avisar(!fallo, resumen.ifBlank { "Listo" }) }
        }
    }

    // ---------- Piezas de la interfaz ----------

    private fun tarjeta(padre: LinearLayout): LinearLayout {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(vidrio)
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), borde)
            }
        }
        padre.addView(caja, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        return caja
    }

    private fun subtitulo(texto: String) = TextView(this).apply {
        text = texto
        setTextColor(crema)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(2), dp(18), 0, dp(2))
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

    /** Botón que hace algo en segundo plano con el cartel de progreso. */
    private fun boton(padre: LinearLayout, texto: String, principal: Boolean = false, requiereTv: Boolean = true, accion: () -> String) {
        val b = Button(this).apply {
            text = texto
            estilo(this, principal)
            setOnClickListener { ejecutar(texto, requiereTv, accion) }
        }
        padre.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
    }

    private fun botonChico(texto: String, accion: () -> Unit) = Button(this).apply {
        text = texto
        estilo(this, false)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setOnClickListener { accion() }
    }

    /** Traduce los errores más comunes de la conexión a algo entendible. */
    private fun explicarError(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("pairing is required", true) -> "La TV todavía no conoce este celular: agregala con el código (＋ Agregar TV)."
            m.contains("connect() must be called", true) || m.contains("Se perdió la conexión", true) ->
                "Se perdió la conexión con la TV. Tocala de nuevo en Mis TVs."
            m.contains("Primero tocá Conectar", true) -> "Primero conectá una TV: tocala en Mis TVs."
            m.contains("refused", true) || m.contains("ECONNREFUSED", true) -> "La TV rechazó la conexión. Tocala de nuevo en Mis TVs (busca el puerto nuevo)."
            m.contains("timed out", true) || m.contains("timeout", true) -> "No hubo respuesta: revisá que el celular esté en el mismo Wi-Fi que la TV."
            e is NumberFormatException -> "Completá los números."
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

    private fun anotar(texto: String) {
        Bitacora.anotar(this, texto)
        runOnUiThread { registro.text = Bitacora.leer(this) }
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

    /** Animación a mano (en algunos celulares las animaciones del sistema están apagadas). */
    private fun animar(ms: Int, cuadro: (Float) -> Unit) {
        val inicio = SystemClock.uptimeMillis()
        val paso = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - inicio) / ms.toFloat()).coerceIn(0f, 1f)
                cuadro(1 - (1 - t) * (1 - t) * (1 - t))
                if (t < 1f) principal.postDelayed(this, 16)
            }
        }
        principal.post(paso)
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
