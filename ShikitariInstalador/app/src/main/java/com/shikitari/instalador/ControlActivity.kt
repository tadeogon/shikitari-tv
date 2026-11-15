package com.shikitari.instalador

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Control de la TV desde el celular:
 *  - arriba, la pantalla de la TV casi en tiempo real (una captura por segundo);
 *  - en el medio, el control remoto (flechas, OK, Atrás, Inicio);
 *  - abajo, las apps instaladas para abrirlas con un toque (las favoritas, arriba de todo).
 */
class ControlActivity : Activity() {

    private val tinta = Color.WHITE
    private val tintaSuave = 0xA6FFFFFF.toInt()
    private val crema = 0xFFF3E9D2.toInt()

    private val tv by lazy { ConexionTv.obtener(this) }
    private val prefs by lazy { getSharedPreferences("app", MODE_PRIVATE) }
    private val principal = Handler(Looper.getMainLooper())

    // Cada cosa en su propio hilo: la captura no frena las teclas, y las teclas salen en orden.
    private val hiloCaptura = Executors.newSingleThreadExecutor()
    private val hiloTeclas = Executors.newSingleThreadExecutor()
    private val hiloApps = Executors.newSingleThreadExecutor()

    private lateinit var pantalla: ImageView
    private lateinit var aviso: TextView
    private lateinit var botonPausa: Button
    private lateinit var grilla: LinearLayout
    private lateinit var estadoApps: TextView

    private var enPausa = false
    private var visible = false
    private val capturando = AtomicBoolean(false)
    private var apps: List<Pair<String, String>> = emptyList() // paquete, nombre
    private var instaladasPorVos: Set<String> = emptySet() // las de "-3"; el resto vino de fábrica

    private val refrescar = object : Runnable {
        override fun run() {
            if (!visible || enPausa) return
            pedirCaptura()
            principal.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(armar())
        cargarApps()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        principal.removeCallbacks(refrescar)
        principal.post(refrescar)
    }

    override fun onPause() {
        // Si el celular no está mirando esta pantalla, no se piden capturas.
        visible = false
        principal.removeCallbacks(refrescar)
        super.onPause()
    }

    override fun onDestroy() {
        hiloCaptura.shutdownNow()
        hiloTeclas.shutdownNow()
        hiloApps.shutdownNow()
        super.onDestroy()
    }

    // ---------- Armado ----------

    private fun armar(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(32))
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@ControlActivity).apply {
                text = "‹"
                setTextColor(tinta)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
                setPadding(dp(4), 0, dp(14), dp(4))
                setOnClickListener { finish() }
            })
            addView(TextView(this@ControlActivity).apply {
                text = "Control de la TV"
                setTextColor(tinta)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
        })

        // 1. Pantalla de la TV
        val marco = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        pantalla = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        marco.addView(pantalla, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        aviso = TextView(this).apply {
            text = "Conectando con la pantalla…"
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
        }
        marco.addView(aviso, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // 16:9, como la TV
        val anchoPantalla = resources.displayMetrics.widthPixels - dp(32)
        col.addView(marco, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, anchoPantalla * 9 / 16).apply { topMargin = dp(14) })

        botonPausa = boton("⏸  Pausar la vista", false) { alternarPausa() }
        col.addView(botonPausa, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })

        // 2. Control remoto
        col.addView(armarControl(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        // 3. Apps
        val cajaApps = tarjeta()
        cajaApps.addView(TextView(this).apply {
            text = "Apps"
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        estadoApps = TextView(this).apply {
            text = "Buscando las apps de la TV…"
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2), 0, dp(6))
        }
        cajaApps.addView(estadoApps)
        grilla = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cajaApps.addView(grilla)
        col.addView(cajaApps, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        return FrameLayout(this).apply {
            addView(FondoVidrio(this@ControlActivity))
            addView(ScrollView(this@ControlActivity).apply { addView(col) })
        }
    }

    private fun armarControl(): LinearLayout {
        val caja = tarjeta()
        caja.gravity = Gravity.CENTER_HORIZONTAL
        val lado = dp(64)
        fun fila(vararg vistas: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            vistas.forEach { addView(it, LinearLayout.LayoutParams(lado, lado).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }) }
        }
        fun hueco() = View(this)
        caja.addView(fila(hueco(), tecla("▲", "KEYCODE_DPAD_UP"), hueco()))
        caja.addView(fila(tecla("◀", "KEYCODE_DPAD_LEFT"), tecla("OK", "KEYCODE_DPAD_CENTER", true), tecla("▶", "KEYCODE_DPAD_RIGHT")))
        caja.addView(fila(hueco(), tecla("▼", "KEYCODE_DPAD_DOWN"), hueco()))
        caja.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            addView(teclaAncha("↩  Atrás", "KEYCODE_BACK"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
            addView(teclaAncha("⌂  Inicio", "KEYCODE_HOME"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) })
        })
        caja.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
            addView(teclaAncha("🔉  Volumen −", "KEYCODE_VOLUME_DOWN"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
            addView(teclaAncha("🔊  Volumen +", "KEYCODE_VOLUME_UP"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) })
        })
        return caja
    }

    private fun tecla(texto: String, codigo: String, principal: Boolean = false) = Button(this).apply {
        text = texto
        isAllCaps = false
        stateListAnimator = null
        setTextColor(if (principal) 0xFF111114.toInt() else tinta)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (principal) 17f else 20f)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (principal) crema else 0x26FFFFFF)
        }
        setOnClickListener { mandarTecla(codigo) }
    }

    private fun teclaAncha(texto: String, codigo: String) = boton(texto, false) { mandarTecla(codigo) }

    private fun boton(texto: String, principal: Boolean, accion: () -> Unit) = Button(this).apply {
        text = texto
        isAllCaps = false
        stateListAnimator = null
        setTextColor(if (principal) 0xFF111114.toInt() else tinta)
        background = GradientDrawable().apply {
            setColor(if (principal) crema else 0x26FFFFFF)
            cornerRadius = dp(22).toFloat()
        }
        setOnClickListener { accion() }
    }

    private fun tarjeta() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(0x1FFFFFFF)
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), 0x33FFFFFF)
        }
    }

    // ---------- Vista de la TV ----------

    private fun alternarPausa() {
        enPausa = !enPausa
        botonPausa.text = if (enPausa) "▶  Reanudar la vista" else "⏸  Pausar la vista"
        principal.removeCallbacks(refrescar)
        if (enPausa) {
            aviso.text = "Vista en pausa"
            aviso.visibility = View.VISIBLE
            aviso.setBackgroundColor(0x99000000.toInt())
        } else {
            principal.post(refrescar)
        }
    }

    /** Pide una captura; si la anterior todavía no llegó, saltea esta vuelta para no amontonar pedidos. */
    private fun pedirCaptura() {
        if (!capturando.compareAndSet(false, true)) return
        hiloCaptura.execute {
            try {
                val datos = tv.leerBinario("screencap -p")
                val opciones = BitmapFactory.Options().apply { inSampleSize = 2 } // la mitad alcanza para el celular
                val imagen = BitmapFactory.decodeByteArray(datos, 0, datos.size, opciones)
                runOnUiThread {
                    if (enPausa) return@runOnUiThread
                    when {
                        imagen == null -> mostrarAviso("No llegó la imagen de la TV. Reintentando…")
                        esNegra(imagen) -> {
                            pantalla.setImageBitmap(imagen)
                            mostrarAviso("Contenido protegido, no se puede mostrar")
                        }
                        else -> {
                            pantalla.setImageBitmap(imagen)
                            aviso.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { mostrarAviso("No se pudo ver la pantalla: ${e.message ?: "sin conexión"}") }
            } finally {
                capturando.set(false)
            }
        }
    }

    private fun mostrarAviso(texto: String) {
        aviso.text = texto
        aviso.setBackgroundColor(0x99000000.toInt())
        aviso.visibility = View.VISIBLE
    }

    /** Netflix y otras apps con DRM salen totalmente negras en la captura. */
    private fun esNegra(b: Bitmap): Boolean {
        val pasosX = 16
        val pasosY = 9
        for (i in 0 until pasosX) for (j in 0 until pasosY) {
            val p = b.getPixel(b.width * (2 * i + 1) / (2 * pasosX), b.height * (2 * j + 1) / (2 * pasosY))
            if (Color.red(p) + Color.green(p) + Color.blue(p) > 36) return false
        }
        return true
    }

    // ---------- Control remoto ----------

    private fun mandarTecla(codigo: String) {
        hiloTeclas.execute {
            try {
                tv.ejecutar("input keyevent $codigo", segundos = 10)
                // Que la vista muestre enseguida el cambio, sin esperar al próximo segundo.
                runOnUiThread { if (!enPausa && visible) pedirCaptura() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "No llegó a la TV: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // ---------- Apps ----------

    private fun cargarApps() {
        hiloApps.execute {
            try {
                val terceros = paquetes(tv.ejecutar("pm list packages -3"))
                instaladasPorVos = terceros.toSet()
                // YouTube, Netflix y otras vienen de fábrica en el Chromecast: "-3" no las lista, así que se suman aparte.
                val todas = paquetes(tv.ejecutar("pm list packages")).toSet()
                val deFabrica = NOMBRES.keys.filter { it in todas && it !in OCULTAR }
                val lista = (terceros + deFabrica).distinct()
                    .filter { it !in OCULTAR }
                    .map { it to nombreLegible(it) }
                    .sortedBy { it.second.lowercase() }
                // La primera vez, las favoritas son las conocidas que estén instaladas.
                if (!prefs.contains(CLAVE_FAVORITAS)) {
                    guardarFavoritas(FAVORITAS_INICIALES.filter { p -> lista.any { it.first == p } })
                }
                runOnUiThread {
                    apps = lista
                    pintarApps()
                }
            } catch (e: Exception) {
                runOnUiThread { estadoApps.text = "No se pudieron leer las apps: ${e.message}" }
            }
        }
    }

    private fun paquetes(salida: String) =
        salida.lines().map { it.trim().removePrefix("package:") }.filter { it.isNotBlank() && !it.contains(" ") }

    private fun pintarApps() {
        grilla.removeAllViews()
        val favs = favoritas()
        val fijadas = favs.mapNotNull { p -> apps.find { it.first == p } }
        val resto = apps.filter { it.first !in favs }
        estadoApps.text = "Tocá para abrir. Dejá apretada una app para fijarla arriba o desinstalarla."
        if (fijadas.isNotEmpty()) {
            grilla.addView(subtitulo("★ Favoritas"))
            agregarGrilla(fijadas, true)
        }
        if (resto.isNotEmpty()) {
            grilla.addView(subtitulo("Todas"))
            agregarGrilla(resto, false)
        }
        if (apps.isEmpty()) estadoApps.text = "No se encontraron apps en la TV."
    }

    private fun subtitulo(texto: String) = TextView(this).apply {
        text = texto
        setTextColor(crema)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(2), dp(12), 0, dp(4))
    }

    private fun agregarGrilla(lista: List<Pair<String, String>>, favorita: Boolean) {
        lista.chunked(COLUMNAS).forEach { parte ->
            val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            parte.forEach { (paquete, nombre) ->
                fila.addView(Button(this).apply {
                    text = nombre
                    isAllCaps = false
                    stateListAnimator = null
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(if (favorita) 0xFF111114.toInt() else tinta)
                    setPadding(dp(6), 0, dp(6), 0)
                    background = GradientDrawable().apply {
                        setColor(if (favorita) crema else 0x26FFFFFF)
                        cornerRadius = dp(16).toFloat()
                    }
                    setOnClickListener { abrirApp(paquete, nombre) }
                    setOnLongClickListener {
                        opcionesApp(paquete, nombre)
                        true
                    }
                }, LinearLayout.LayoutParams(0, dp(60), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            // Completa la última fila para que los botones no queden estirados.
            repeat(COLUMNAS - parte.size) { fila.addView(View(this), LinearLayout.LayoutParams(0, dp(60), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            grilla.addView(fila)
        }
    }

    private fun abrirApp(paquete: String, nombre: String) {
        Toast.makeText(this, "Abriendo $nombre…", Toast.LENGTH_SHORT).show()
        hiloTeclas.execute {
            try {
                var salida = tv.ejecutar("monkey -p $paquete 1 2>&1", segundos = 15)
                if (!abrio(salida)) {
                    // Algunas apps de TV solo tienen la entrada de Android TV.
                    salida = tv.ejecutar("monkey -p $paquete -c android.intent.category.LEANBACK_LAUNCHER 1 2>&1", segundos = 15)
                }
                if (!abrio(salida)) {
                    runOnUiThread { Toast.makeText(this, "$nombre no se pudo abrir (no tiene pantalla de inicio).", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "No llegó a la TV: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    /** monkey siempre avisa "no activities found for category MONKEY"; lo que importa es que haya mandado el evento. */
    private fun abrio(salida: String) =
        salida.contains("Events injected", true) && !salida.contains("monkey aborted", true)

    private fun favoritas(): List<String> =
        (prefs.getString(CLAVE_FAVORITAS, "") ?: "").split(',').filter { it.isNotBlank() }

    private fun guardarFavoritas(lista: List<String>) {
        prefs.edit().putString(CLAVE_FAVORITAS, lista.distinct().joinToString(",")).apply()
    }

    private fun opcionesApp(paquete: String, nombre: String) {
        val esFavorita = paquete in favoritas()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(nombre)
            .setItems(arrayOf(if (esFavorita) "Quitar de favoritas" else "★  Fijar arriba", "🗑  Desinstalar de la TV")) { _, i ->
                if (i == 0) alternarFavorita(paquete, nombre) else confirmarDesinstalar(paquete, nombre)
            }
            .show()
    }

    private fun confirmarDesinstalar(paquete: String, nombre: String) {
        if (paquete in PROTEGIDAS) {
            Toast.makeText(this, "$nombre es parte del sistema de la TV: no conviene sacarla.", Toast.LENGTH_LONG).show()
            return
        }
        val deFabrica = paquete !in instaladasPorVos
        val detalle = if (deFabrica)
            "Vino con la TV, así que se quita solo para tu usuario (no se rompe nada y se puede recuperar)."
        else
            "Se borra de la TV junto con sus datos. Si la querés de nuevo, hay que volver a instalarla."
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("¿Desinstalar $nombre?")
            .setMessage(detalle)
            .setPositiveButton("Desinstalar") { _, _ -> desinstalar(paquete, nombre, deFabrica) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun desinstalar(paquete: String, nombre: String, deFabrica: Boolean) {
        Toast.makeText(this, "Desinstalando $nombre…", Toast.LENGTH_SHORT).show()
        hiloApps.execute {
            val comando = if (deFabrica) "pm uninstall -k --user 0 $paquete" else "pm uninstall $paquete"
            val salida = try {
                tv.ejecutar("$comando 2>&1", segundos = 60)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            val ok = salida.contains("Success")
            Bitacora.anotar(this, "▸ Desinstalar $nombre ($paquete)\n$ $comando\n$salida")
            runOnUiThread {
                if (ok) {
                    Toast.makeText(this, "✓ $nombre desinstalada", Toast.LENGTH_SHORT).show()
                    apps = apps.filter { it.first != paquete }
                    guardarFavoritas(favoritas() - paquete)
                    pintarApps()
                } else {
                    Toast.makeText(this, "No se pudo desinstalar $nombre: $salida", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun alternarFavorita(paquete: String, nombre: String) {
        val actuales = favoritas().toMutableList()
        if (paquete in actuales) {
            actuales.remove(paquete)
            Toast.makeText(this, "$nombre ya no está en favoritas", Toast.LENGTH_SHORT).show()
        } else {
            actuales.add(paquete)
            Toast.makeText(this, "$nombre fijada arriba ★", Toast.LENGTH_SHORT).show()
        }
        guardarFavoritas(actuales)
        pintarApps()
    }

    /** Nombre para mostrar: los conocidos con su nombre real; el resto, armado a partir del paquete. */
    private fun nombreLegible(paquete: String): String {
        NOMBRES[paquete]?.let { return it }
        val ignorar = setOf("com", "org", "net", "tv", "android", "app", "apps", "mobile", "google", "atv", "androidtv", "leanback", "ui", "client", "launcher", "release", "player")
        val parte = paquete.split('.').lastOrNull { it.lowercase() !in ignorar && it.length > 1 }
            ?: paquete.substringAfterLast('.')
        return parte.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { it.uppercase() }
    }

    private fun dp(valor: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val COLUMNAS = 3
        const val CLAVE_FAVORITAS = "control_favoritas"

        val FAVORITAS_INICIALES = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.firetv.youtube",
            "com.amazon.avod",
            "com.disney.disneyplus",
            "com.hbo.hbonow",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
        )

        /** Apps de fábrica que conviene mostrar aunque no sean "-3", con su nombre real. */
        val NOMBRES = mapOf(
            "com.netflix.ninja" to "Netflix",
            "com.google.android.youtube.tv" to "YouTube",
            "com.amazon.firetv.youtube" to "YouTube",
            "com.amazon.avod" to "Prime Video",
            "com.amazon.venezia" to "Appstore",
            "com.amazon.tv.settings.v2" to "Ajustes",
            "tv.twitch.android.viewer" to "Twitch",
            "org.clipbox.player" to "ClipBox",
            "dev.dworks.apps.anexplorer" to "AnExplorer",
            "com.estrongs.android.pop" to "ES Explorador",
            "com.precisecontrol.filemanager.free" to "Administrador de archivos",
            "com.google.android.youtube.tvmusic" to "YouTube Music",
            "com.google.android.youtube.tvkids" to "YouTube Kids",
            "com.disney.disneyplus" to "Disney+",
            "com.hbo.hbonow" to "HBO Max",
            "com.wbd.stream" to "Max",
            "com.amazon.amazonvideo.livingroom" to "Prime Video",
            "com.apple.atve.androidtv.appletv" to "Apple TV",
            "com.spotify.tv.android" to "Spotify",
            "com.cbs.ott" to "Paramount+",
            "tv.twitch.android.app" to "Twitch",
            "com.kick.mobile" to "Kick",
            "com.plexapp.android" to "Plex",
            "org.videolan.vlc" to "VLC",
            "com.teamsmart.videomanager.tv" to "SmartTube",
            "com.liskovsoft.smarttubetv.beta" to "SmartTube Beta",
            "com.google.android.play.games" to "Play Juegos",
            "com.android.vending" to "Play Store",
            "com.google.android.tv" to "Google TV",
            "com.android.tv.settings" to "Ajustes",
            "com.milauncher.tv" to "Shikitari TV",
            "com.pachi.tv" to "Pachi TV",
            "com.pachi.tv.trabajo" to "Solari",
            "com.limelight" to "Moonlight",
            "com.mxlmovies.app" to "MXL Movies",
            "com.fylmo.tv" to "Fylmo TV",
            "org.smarttube.stable" to "SmartTube",
            "com.stremio.one" to "Stremio",
            "com.esaba.downloader" to "Downloader",
            "ar.wolpuente" to "Wolpuente",
            "com.yablio.sendfilestotv" to "Send Files",
            "tv.mxlmovies.app" to "MXL Movies",
            "com.cbs.ca" to "Paramount+",
            "com.apgsolutionsllc.APGSOLUTIONSLLC0007" to "APG",
        )

        /** Paquetes de "-3" que no son apps para abrir (servicios, actualizadores). */
        /** No se ofrecen para desinstalar: sin ellas la TV queda sin tienda, sin ajustes o sin inicio. */
        val PROTEGIDAS = setOf("com.android.vending", "com.google.android.tv", "com.android.tv.settings", "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher",
            "com.amazon.tv.launcher", "com.amazon.tv.settings.v2", "com.amazon.venezia")

        val OCULTAR = setOf("com.shikitari.instalador", "com.shikitari.instalador.nuevo", "com.android.mgstxNF")
    }
}
