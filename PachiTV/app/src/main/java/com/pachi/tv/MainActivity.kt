package com.pachi.tv

import android.app.Activity
import android.app.ActivityManager
import android.app.Dialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.WeakHashMap

/**
 * Launcher para Android TV con estilo Apple TV: logo grande de la app enfocada, filas limpias
 * y un menú lateral flotante que se abre con Atrás o yendo a la izquierda. Se navega con el D-pad.
 * OK abre; mantener OK abre un menú de opciones. Las animaciones van a mano para no depender del sistema.
 */
class MainActivity : Activity() {

    private enum class Categoria(val titulo: String, val icono: Int) {
        STREAMING("Streaming", R.drawable.ic_streaming),
        PELIS("Pelis y series", R.drawable.ic_pelis),
        MUSICA("Música", R.drawable.ic_musica),
        JUEGOS("Juegos", R.drawable.ic_juegos),
        HERRAMIENTAS("Herramientas y más", R.drawable.ic_herramientas),
    }

    /** Páginas del menú lateral. Las que tienen categoría muestran solo esa categoría. */
    private enum class Pagina(val titulo: String, val icono: Int, val categoria: Categoria?) {
        BUSCAR("Buscar", R.drawable.ic_buscar, null),
        INICIO("Inicio", R.drawable.ic_inicio, null),
        STREAMING("Streaming", R.drawable.ic_streaming, Categoria.STREAMING),
        PELIS("Pelis y series", R.drawable.ic_pelis, Categoria.PELIS),
        JUEGOS("Juegos", R.drawable.ic_juegos, Categoria.JUEGOS),
        TODAS("Todas las apps", R.drawable.ic_apps, null),
    }

    private class App(
        val pkg: String,
        val label: String,
        val banner: Drawable?,
        val icono: Drawable,
        val sistema: Boolean,
        val categoriaAndroid: Int,
        val bannerId: Int,
    )

    private class ItemLateral(val vista: LinearLayout, val pagina: Pagina?, val texto: TextView, val icono: ImageView)

    // Favoritas iniciales (solo las que estén instaladas), la primera vez que se abre.
    private val favoritasIniciales = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.wbd.stream",
        "com.hbo.hbonow",
        "com.disney.disneyplus",
        "com.amazon.amazonvideo.livingroom",
        "com.apple.atve.androidtv.appletv",
    )

    private val categoriasConocidas = mapOf(
        "com.wbd.stream" to Categoria.STREAMING,
        "com.hbo.hbonow" to Categoria.STREAMING,
        "com.amazon.avod" to Categoria.STREAMING,
        "com.amazon.amazonvideo.livingroom" to Categoria.STREAMING,
        "com.apple.atve.androidtv.appletv" to Categoria.STREAMING,
        "com.cbs.ca" to Categoria.STREAMING,
        "com.cbs.ott" to Categoria.STREAMING,
        "com.netflix.ninja" to Categoria.STREAMING,
        "com.disney.disneyplus" to Categoria.STREAMING,
        "com.crunchyroll.crunchyroid" to Categoria.STREAMING,
        "ar.com.flow.androidtv" to Categoria.STREAMING,
        "com.amco.clarovideo_atv" to Categoria.STREAMING,
        "tv.pluto.android" to Categoria.STREAMING,
        "com.google.android.youtube.tv" to Categoria.STREAMING,
        "tv.twitch.android.app" to Categoria.STREAMING,
        "com.kick.mobile" to Categoria.STREAMING,
        "com.github.andreyasadchy.xtra" to Categoria.STREAMING,
        "tv.mxlmovies.app" to Categoria.PELIS,
        "com.stremio.one" to Categoria.PELIS,
        "org.xbmc.kodi" to Categoria.PELIS,
        "com.plexapp.android" to Categoria.PELIS,
        "com.google.android.youtube.tvmusic" to Categoria.MUSICA,
        "com.spotify.tv.android" to Categoria.MUSICA,
        "com.limelight" to Categoria.JUEGOS,
        "com.valvesoftware.steamlink" to Categoria.JUEGOS,
        "com.nvidia.geforcenow" to Categoria.JUEGOS,
    )

    // Logos en alta calidad incluidos en la app: los que traen estas apps miden apenas 320x180.
    private val logosPropios = mapOf(
        "com.google.android.youtube.tv" to R.drawable.logo_youtube,
        "com.netflix.ninja" to R.drawable.logo_netflix,
        "com.wbd.stream" to R.drawable.logo_hbomax,
        "com.hbo.hbonow" to R.drawable.logo_hbomax,
        "com.amazon.amazonvideo.livingroom" to R.drawable.logo_prime,
        "com.amazon.avod" to R.drawable.logo_prime,
        "com.apple.atve.androidtv.appletv" to R.drawable.logo_appletv,
        "com.cbs.ca" to R.drawable.logo_paramount,
        "com.cbs.ott" to R.drawable.logo_paramount,
        "tv.twitch.android.app" to R.drawable.logo_twitch,
        "com.kick.mobile" to R.drawable.logo_kick,
        "com.spotify.tv.android" to R.drawable.logo_spotify,
        "org.videolan.vlc" to R.drawable.logo_vlc,
        "com.android.vending" to R.drawable.logo_play,
        "com.google.android.youtube.tvmusic" to R.drawable.logo_ytmusic,
    )

    // Los mismos logos sin fondo, para la cabecera.
    private val logosCabecera = mapOf(
        "com.google.android.youtube.tv" to R.drawable.hero_youtube,
        "com.netflix.ninja" to R.drawable.hero_netflix,
        "com.wbd.stream" to R.drawable.hero_hbomax,
        "com.hbo.hbonow" to R.drawable.hero_hbomax,
        "com.amazon.amazonvideo.livingroom" to R.drawable.hero_prime,
        "com.amazon.avod" to R.drawable.hero_prime,
        "com.apple.atve.androidtv.appletv" to R.drawable.hero_appletv,
        "com.cbs.ca" to R.drawable.hero_paramount,
        "com.cbs.ott" to R.drawable.hero_paramount,
        "tv.twitch.android.app" to R.drawable.hero_twitch,
        "com.kick.mobile" to R.drawable.hero_kick,
        "com.spotify.tv.android" to R.drawable.hero_spotify,
        "org.videolan.vlc" to R.drawable.hero_vlc,
        "com.android.vending" to R.drawable.hero_play,
        "com.google.android.youtube.tvmusic" to R.drawable.hero_ytmusic,
    )

    // Apps con banner poco prolijo: se muestran con una tarjeta pareja de ícono y nombre.
    private val tarjetaSimple = setOf(
        "com.android.tv.settings",
        "ar.wolpuente",
        "com.apgsolutionsllc.APGSOLUTIONSLLC0007",
        "de.blinkt.openvpn",
        "tv.mxlmovies.app",
        "com.limelight",
    )

    private val nombresCortos = mapOf(
        "com.android.tv.settings" to "Ajustes",
        "ar.wolpuente" to "Navegador",
        "com.yablio.sendfilestotv" to "Send Files",
        "tv.mxlmovies.app" to "MXL Movies",
        "com.google.android.youtube.tvmusic" to "YouTube Music",
    )

    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }
    private val columnas get() = if (prefs.getBoolean("grandes", false)) 4 else 5
    private val espera = Handler(Looper.getMainLooper())
    private val medium by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    // Versión formal (Solari): todo el texto con la tipografía de la marca.
    private val formal by lazy { resources.getBoolean(R.bool.marca_formal) }
    private val vitrina by lazy { if (formal) Vitrina(this) else null }

    /** Solari: en el Inicio la vitrina ocupa toda la pantalla y las apps quedan abajo, bajando. */
    private var enVitrina = true
    private var relojIzquierda: LinearLayout? = null
    private fun vitrinaActiva() = vitrina != null && pagina == Pagina.INICIO && enVitrina

    /** Adónde va el foco por defecto: la vitrina si está a la vista, si no la primera app. */
    private fun focoInicial(): View? = if (vitrinaActiva()) vitrina else buscarPrimera(contenido)

    private fun entrarVitrina() {
        enVitrina = true
        destacado.visibility = View.GONE
        vitrina?.requestFocus()
        scroll.post { scroll.smoothScrollTo(0, 0) }
    }

    private fun salirVitrina() {
        enVitrina = false
        destacado.visibility = View.VISIBLE
        (ultimaTarjeta?.let { contenido.findViewWithTag<View>(it) } ?: buscarPrimera(contenido))?.requestFocus()
    }
    private val fuenteNormal by lazy { fuenteDeMarca("Montserrat-Regular.ttf") }
    private val fuenteMedia by lazy { fuenteDeMarca("Montserrat-Medium.ttf") }

    private fun fuenteDeMarca(archivo: String): Typeface? =
        try { Typeface.createFromAsset(assets, "fuentes/$archivo") } catch (e: Exception) { null }

    /** Recorre la pantalla y le pone la tipografía de la marca a cada texto que todavía no la tiene. */
    private fun aplicarFuente(v: View) {
        if (!formal) return
        if (v is TextView) {
            val actual = v.typeface
            if (actual !== fuenteNormal && actual !== fuenteMedia) {
                val nueva = if (actual === medium || actual?.isBold == true) fuenteMedia else fuenteNormal
                if (nueva != null) v.typeface = nueva
            }
        } else if (v is ViewGroup) {
            for (i in 0 until v.childCount) aplicarFuente(v.getChildAt(i))
        }
    }

    private lateinit var fondo: FondoConsola
    private lateinit var destacado: View
    private lateinit var destacada: ImageView
    private lateinit var scroll: ScrollView
    private lateinit var contenido: LinearLayout
    private lateinit var nombreApp: TextView
    private lateinit var subtitulo: TextView
    private lateinit var climaAhora: TextView
    private lateinit var climaDetalle: TextView
    private var primeraVez = false
    private lateinit var velo: View
    private lateinit var capa: FrameLayout
    private lateinit var isla: IslaMusica
    private lateinit var etiquetaRapida: TextView
    private val botonesRapidos = mutableListOf<View>()
    private val botonesArriba = mutableListOf<View>()
    private val botonesMusica = mutableListOf<View>()
    private var iconoPausaMenu: ImageView? = null
    private lateinit var filaMusica: LinearLayout
    private lateinit var tapaMenu: ImageView
    private lateinit var temaMenu: TextView
    private lateinit var artistaMenu: TextView
    private lateinit var panel: LinearLayout
    private val itemsLaterales = mutableListOf<ItemLateral>()
    private val animaciones = WeakHashMap<View, Any>()

    private var pagina = Pagina.INICIO
    private var apps: List<App> = emptyList()
    private var panelAbierto = false
    private var ultimaTarjeta: String? = null
    private var busqueda = ""
    private var resultados: LinearLayout? = null

    /** Tarjeta que se está moviendo: fila (FAV o nombre de categoría) y paquete. */
    private var moviendo: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tema.cargar(this)
        primeraVez = savedInstanceState == null
        val pantalla = armarPantalla()
        setContentView(pantalla)
        if (formal) pantalla.viewTreeObserver.addOnGlobalLayoutListener { aplicarFuente(pantalla) }
        recibirPcs(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recibirPcs(intent)
        if (intent.getBooleanExtra(EXTRA_DESDE_HOME, false) || intent.hasCategory(Intent.CATEGORY_HOME)) {
            pagina = Pagina.INICIO
            enVitrina = true
            ultimaTarjeta = null
            moviendo = null
            cerrarPanel(enfocar = false)
        }
    }

    override fun onResume() {
        super.onResume()
        vitrina?.arrancarVideo()
        asegurarServicioInicio()
        cargarApps()
        Clima.guardado(this)?.let { pintarClima(it) }
        Clima.actualizar(this) { clima -> runOnUiThread { pintarClima(clima) } }
        isla.iniciar()
        if (!panelAbierto) mostrar(foco = ultimaTarjeta, arriba = ultimaTarjeta == null)
    }

    override fun onPause() {
        espera.removeCallbacksAndMessages(null)
        isla.detener()
        vitrina?.pararVideo() // que el video no siga corriendo detrás de otra app
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val mov = moviendo
        if (mov != null) {
            // Modo mover: las flechas cambian el lugar; OK o Atrás terminan.
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> mover(mov.first, mov.second, -1)
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> mover(mov.first, mov.second, 1)
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> {
                        moviendo = null
                        mostrar(foco = "${mov.first}|${mov.second}")
                    }
                }
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (panelAbierto && (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                val derecha = event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                val fila = (if (currentFocus in botonesArriba) botonesArriba else botonesRapidos).filter { it.visibility == View.VISIBLE }
                val i = fila.indexOf(currentFocus)
                when {
                    // En las filas de botones las flechas se mueven; pasando el último, se cierra.
                    i >= 0 && derecha && i < fila.lastIndex -> fila[i + 1].requestFocus()
                    i >= 0 && !derecha -> fila.getOrNull(i - 1)?.requestFocus()
                    derecha -> cerrarPanel()
                }
                return true
            }
            // Solari: en la vitrina, abajo muestra las apps, derecha pasa de propiedad, izquierda abre el menú.
            val v = vitrina
            if (v != null && !panelAbierto && currentFocus === v) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> salirVitrina()
                    KeyEvent.KEYCODE_DPAD_RIGHT -> v.siguiente()
                    KeyEvent.KEYCODE_DPAD_LEFT -> abrirPanel()
                    KeyEvent.KEYCODE_DPAD_UP -> if (isla.visibility == View.VISIBLE) isla.requestFocus()
                    else -> return super.dispatchKeyEvent(event)
                }
                return true
            }
            // Y desde la primera fila de apps, subir vuelve a la vitrina.
            if (v != null && !panelAbierto && pagina == Pagina.INICIO && event.keyCode == KeyEvent.KEYCODE_DPAD_UP && currentFocus?.tag is String) {
                val siguiente = currentFocus?.focusSearch(View.FOCUS_UP)
                if (siguiente == null || siguiente.tag !is String) {
                    ultimaTarjeta = currentFocus?.tag as? String
                    entrarVitrina()
                    return true
                }
            }
            // Desde la primera tarjeta de una fila, ir a la izquierda abre el menú lateral.
            if (!panelAbierto && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && esPrimeraDeFila(currentFocus)) {
                abrirPanel()
                return true
            }
            // Desde cualquier tarjeta de la primera fila, subir llega a la isla; bajar desde ella vuelve.
            if (!panelAbierto && event.keyCode == KeyEvent.KEYCODE_DPAD_UP && isla.visibility == View.VISIBLE && currentFocus?.tag is String) {
                val siguiente = currentFocus?.focusSearch(View.FOCUS_UP)
                if (siguiente == null || siguiente.tag !is String) {
                    isla.requestFocus()
                    return true
                }
            }
            if (currentFocus === isla && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (vitrinaActiva()) vitrina?.requestFocus()
                else (ultimaTarjeta?.let { contenido.findViewWithTag<View>(it) } ?: buscarPrimera(contenido))?.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun esPrimeraDeFila(v: View?): Boolean {
        if (v?.tag !is String) return false
        val envoltura = v.parent as? ViewGroup ?: return false
        val fila = envoltura.parent as? ViewGroup ?: return false
        return fila.indexOfChild(envoltura) == 0
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (panelAbierto) cerrarPanel() else abrirPanel()
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                val etiqueta = currentFocus?.tag as? String
                val app = etiqueta?.let { e -> apps.find { it.pkg == e.substringAfter('|') } }
                if (app != null) menuApp(app, etiqueta.substringBefore('|')) else menuOptimizar()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Movimiento ----------

    /** Anima por cuadros con la curva dada; una animación nueva sobre la misma vista reemplaza a la anterior. */
    private fun animar(v: View, ms: Int, curva: (Float) -> Float = ::salidaSuave, cuadro: (Float) -> Unit) {
        val token = Any()
        animaciones[v] = token
        val inicio = SystemClock.uptimeMillis()
        val paso = object : Runnable {
            override fun run() {
                if (animaciones[v] !== token) return
                val t = ((SystemClock.uptimeMillis() - inicio) / ms.toFloat()).coerceAtMost(1f)
                cuadro(curva(t))
                if (t < 1f) v.postOnAnimation(this) else animaciones.remove(v)
            }
        }
        v.postOnAnimation(paso)
    }

    private fun salidaSuave(t: Float) = 1 - (1 - t) * (1 - t) * (1 - t)

    /** Se pasa apenas del destino y vuelve: el pequeño rebote de las tarjetas de Apple TV. */
    private fun rebote(t: Float): Float {
        val s = 1.1f
        val u = t - 1
        return 1 + (s + 1) * u * u * u + s * u * u
    }

    private fun escalar(v: View, destino: Float, elevacion: Float) {
        val escala0 = v.scaleX
        val z0 = v.translationZ
        animar(v, 240, if (destino > 1f) ::rebote else ::salidaSuave) { c ->
            val e = escala0 + (destino - escala0) * c
            v.scaleX = e
            v.scaleY = e
            v.translationZ = z0 + (elevacion - z0) * c
        }
    }

    private fun aparecer(v: View, ms: Int = 260, desplazar: Float = dp(8).toFloat()) {
        v.alpha = 0f
        v.translationY = desplazar
        animar(v, ms) { c ->
            v.alpha = c
            v.translationY = desplazar * (1 - c)
        }
    }

    // ---------- Armado de la pantalla ----------

    private fun armarPantalla(): View {
        val raiz = FrameLayout(this).apply { setBackgroundColor(if (Tema.vice) Tema.NOCHE else 0xFF0B0B0F.toInt()) }
        // Todo lo que queda atrás del menú lateral va junto, para poder difuminarlo cuando se abre.
        capa = FrameLayout(this)
        raiz.addView(capa, llenar())
        fondo = FondoConsola(this)
        capa.addView(fondo, llenar())

        // Recorta al bajar: las filas no pueden pasar por encima del logo grande.
        val columna = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        destacado = armarDestacado()
        // Más alto que antes: el logo de la app arranca debajo de la hora y nunca la tapa.
        columna.addView(destacado, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(168)))

        contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(20))
        }
        scroll = object : ScrollView(this) {
            // Deja lugar arriba y abajo para que la tarjeta agrandada y su nombre no se corten.
            override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
                rect.inset(0, -dp(30))
                return super.computeScrollDeltaToGetChildRectOnScreen(rect)
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            addView(contenido)
        }
        columna.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        capa.addView(columna, llenar())

        val info = armarInfo()
        capa.addView(info, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(22)
            marginEnd = dp(MARGEN)
        })
        relojIzquierda?.let { reloj ->
            capa.addView(reloj, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                topMargin = dp(22)
                marginStart = dp(MARGEN)
            })
            // La hora queda a la misma altura que el clima y el logo de la derecha.
            capa.viewTreeObserver.addOnGlobalLayoutListener {
                reloj.translationY = (info.top + info.height / 2f) - (reloj.top + reloj.height / 2f)
            }
        }

        // Marquita al borde: avisa que a la izquierda hay un menú.
        capa.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, Tema.rayita).apply {
                cornerRadius = dp(3).toFloat()
            }
            alpha = 0.7f
        }, FrameLayout.LayoutParams(dp(4), dp(52), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(8) })

        isla = IslaMusica(this) { focoInicial()?.requestFocus() }
        isla.alCambiar = {
            if (panelAbierto) pintarMusicaMenu()
            pintarFondo()
        }
        capa.addView(isla, FrameLayout.LayoutParams(dp(IslaMusica.CHICA), dp(38), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(14)
        })

        velo = View(this).apply {
            setBackgroundColor(if (hayDifuminado) 0x55000000 else 0x99000000.toInt())
            visibility = View.GONE
        }
        raiz.addView(velo, llenar())
        panel = armarPanel()
        raiz.addView(panel, FrameLayout.LayoutParams(dp(224), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = dp(24)
        })
        return raiz
    }

    /** Arriba a la derecha: el clima y el logo. La hora y la fecha van arriba a la izquierda (relojIzquierda). */
    private fun armarInfo(): View {
        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val hora = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(if (Tema.vice) Tema.ROSA else Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = medium
            includeFontPadding = false
        }
        val fecha = TextClock(this).apply {
            format12Hour = "EEEE d 'de' MMMM"
            format24Hour = "EEEE d 'de' MMMM"
            setTextColor(0xB3FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        climaAhora = TextView(this).apply {
            setTextColor(if (Tema.vice) Tema.DURAZNO else Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(3), 0, 0)
        }
        climaDetalle = TextView(this).apply {
            setTextColor(0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        // A la izquierda: la hora con la fecha al lado. A la derecha: el clima junto al logo.
        relojIzquierda = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(hora)
            addView(View(this@MainActivity).apply { setBackgroundColor(0x55FFFFFF) },
                LinearLayout.LayoutParams(dp(1), dp(26)).apply { marginStart = dp(14); marginEnd = dp(14) })
            addView(fecha.apply { setTextColor(0xB3FFFFFF.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) })
        }
        val clima = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            // Con tope de ancho: un clima con texto largo no se mete debajo de la isla.
            addView(climaAhora.apply {
                setPadding(0, 0, 0, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxWidth = dp(200)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(climaDetalle)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // El logo queda centrado a la altura del clima.
            gravity = Gravity.CENTER_VERTICAL
            addView(clima)
            addView(View(this@MainActivity).apply { setBackgroundColor(0x55FFFFFF) },
                LinearLayout.LayoutParams(dp(1), dp(26)).apply { marginStart = dp(14); marginEnd = dp(14) })
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.logo_chico)
                contentDescription = getString(R.string.app_name)
                adjustViewBounds = true
            }, LinearLayout.LayoutParams(dp(if (formal) 92 else 68), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun pintarClima(clima: Pair<String, String>) {
        climaAhora.text = clima.first
        climaDetalle.text = clima.second
    }

    /** Parte de arriba: el logo grande de la app enfocada, con su nombre al lado. */
    private fun armarDestacado(): View {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(MARGEN), dp(26), dp(MARGEN), dp(10))
        }
        destacada = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            outlineProvider = bordesRedondos(dp(14).toFloat())
        }
        fila.addView(destacada, LinearLayout.LayoutParams(dp(ANCHO_CABECERA), dp(ALTO_CABECERA)).apply { marginEnd = dp(22) })
        val textos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nombreApp = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = medium
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textos.addView(nombreApp)
        subtitulo = TextView(this).apply {
            setTextColor(0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(2), 0, dp(4))
        }
        textos.addView(subtitulo)
        fila.addView(textos, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return fila
    }

    // ---------- Menú lateral ----------

    private fun armarPanel(): LinearLayout {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(10), dp(6), dp(8))
            // Vidrio como el de la app del celular: bien transparente donde el sistema puede difuminar lo de atrás.
            background = Vidrio(dp(20).toFloat(), Tema.rellenoPanel(hayDifuminado))
            visibility = View.GONE
            elevation = dp(24).toFloat()
        }
        val perfil = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), dp(8))
        }
        perfil.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo_chico)
            contentDescription = getString(R.string.app_name)
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT))
        perfil.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        // Música: la nota abre Spotify; los otros tres aparecen cuando hay algo sonando.
        botonesMusica.clear()
        botonesMusica += botonRapido(R.drawable.ic_anterior, "Anterior", { isla.temaAnterior() }, null, botonesArriba, 26)
        val pausa = botonRapido(R.drawable.ic_pause, "Pausa", {
            isla.alternar()
            iconoPausaMenu?.setImageResource(if (isla.sonando) R.drawable.ic_play else R.drawable.ic_pause)
        }, null, botonesArriba, 26)
        iconoPausaMenu = (pausa as ViewGroup).getChildAt(0) as ImageView
        botonesMusica += pausa
        botonesMusica += botonRapido(R.drawable.ic_siguiente, "Siguiente", { isla.temaSiguiente() }, null, botonesArriba, 26)
        botonesMusica.forEach { perfil.addView(it) }
        perfil.addView(botonRapido(R.drawable.ic_musica, "Spotify", { abrirSpotify() }, null, botonesArriba, 26))
        caja.addView(perfil)

        // Lo que está sonando: carátula, tema y artista, igual que en la isla.
        tapaMenu = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = bordesRedondos(dp(8).toFloat())
            clipToOutline = true
            setBackgroundColor(0x33FFFFFF)
        }
        temaMenu = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = medium
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        artistaMenu = TextView(this).apply {
            setTextColor(0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        filaMusica = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), dp(8))
            addView(tapaMenu, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(temaMenu)
                addView(artistaMenu)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        caja.addView(filaMusica)
        caja.addView(separador())

        // Buscar y Juegos no se usan: quedan fuera del menú (los juegos siguen en "Todas las apps").
        for (p in Pagina.values().filter { it != Pagina.BUSCAR && it != Pagina.JUEGOS }) {
            caja.addView(itemLateral(p.titulo, p.icono, p, {
                cambiarPagina(p)
            }, null))
        }
        caja.addView(itemLateral("Prender PC", R.drawable.ic_power, null, { menuPcs() }, null))
        caja.addView(separador())
        etiquetaRapida = TextView(this).apply {
            setTextColor(0xB3FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
        }
        caja.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
            addView(botonRapido(R.drawable.ic_sonido, "Salida de sonido", { abrirSalidaDeSonido() }, null))
            addView(botonRapido(R.drawable.ic_bluetooth, "Bluetooth", { abrirBluetooth() }, null))
            addView(botonRapido(R.drawable.ic_wifi, "Redes Wi-Fi", { abrirIntent(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)) }, null))
            addView(botonRapido(R.drawable.ic_rayo, "Optimizar", { optimizar() }, { menuOptimizar() }))
            addView(botonRapido(R.drawable.ic_config, "Ajustes", { menuAjustes() }, null))
        })
        caja.addView(etiquetaRapida, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)))
        return caja
    }

    /** Botón redondo como los del centro de control de iOS; el nombre aparece abajo al enfocarlo. */
    private fun pintarMusicaMenu() {
        val visible = if (isla.hayMusica) View.VISIBLE else View.GONE
        botonesMusica.forEach { it.visibility = visible }
        iconoPausaMenu?.setImageResource(if (isla.sonando) R.drawable.ic_pause else R.drawable.ic_play)
        filaMusica.visibility = visible
        tapaMenu.setImageBitmap(isla.tapaActual)
        temaMenu.text = isla.tituloActual
        artistaMenu.text = isla.artistaActual
        artistaMenu.visibility = if (isla.artistaActual.isBlank()) View.GONE else View.VISIBLE
    }

    private fun abrirSpotify() {
        if (packageManager.getLeanbackLaunchIntentForPackage(SPOTIFY) == null && packageManager.getLaunchIntentForPackage(SPOTIFY) == null) {
            Toast.makeText(this, "Spotify no está instalado en esta TV", Toast.LENGTH_LONG).show()
            return
        }
        abrir(SPOTIFY)
    }

    private fun botonRapido(
        icono: Int,
        titulo: String,
        click: () -> Unit,
        mantener: (() -> Unit)?,
        lista: MutableList<View> = botonesRapidos,
        lado: Int = 34,
    ): View {
        val imagen = ImageView(this).apply {
            setImageResource(icono)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
        val vidrio = Vidrio(0f, 0x1FFFFFFF, redondo = true)
        val enfocado = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(CREMA)
        }
        val boton = FrameLayout(this).apply {
            background = vidrio
            isFocusable = true
            isClickable = true
            addView(imagen, FrameLayout.LayoutParams(dp(lado / 2), dp(lado / 2), Gravity.CENTER))
            val margen = if (lado < 34) 2 else 4
            layoutParams = LinearLayout.LayoutParams(dp(lado), dp(lado)).apply { setMargins(dp(margen), 0, dp(margen), 0) }
        }
        boton.setOnFocusChangeListener { _, foco ->
            boton.background = if (foco) enfocado else vidrio
            imagen.imageTintList = ColorStateList.valueOf(if (foco) 0xFF111114.toInt() else Color.WHITE)
            if (foco) etiquetaRapida.text = titulo else if (botonesRapidos.none { it.isFocused } && botonesArriba.none { it.isFocused }) etiquetaRapida.text = ""
        }
        boton.setOnClickListener { click() }
        if (mantener != null) boton.setOnLongClickListener { mantener(); true }
        lista.add(boton)
        return boton
    }

    /**
     * Fire TV: los atajos comunes de Android no existen ahí o pasan por el inicio de Amazon (y Pachi lo tapa).
     * Estos son los de Amazon que sí abren directo.
     */
    private val esFireTv = Build.MANUFACTURER.equals("Amazon", ignoreCase = true)
    private fun ajustesFire() = Intent().setClassName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity")
    private fun bluetoothFire() = Intent("com.amazon.device.settings.action.INPUT_DEVICES")

    /** Abre los Ajustes generales de la TV. */
    private fun abrirAjustesTv() {
        if (esFireTv) abrirPrimera(ajustesFire(), Intent(Settings.ACTION_SETTINGS)) else abrirIntent(Intent(Settings.ACTION_SETTINGS))
    }

    /** Ventana de "Salida de audio" de la TV; donde no existe, los ajustes de sonido. */
    private fun abrirSalidaDeSonido() {
        if (esFireTv) {
            // En Fire TV la pantalla de audio solo la puede abrir Amazon. El sonido se manda a un parlante o
            // auriculares desde la lista de Bluetooth; sin nada conectado, sale por la TV.
            Toast.makeText(this, "Elegí el parlante o los auriculares Bluetooth. Sin ninguno, el sonido sale por la TV.", Toast.LENGTH_LONG).show()
            abrirPrimera(bluetoothFire(), ajustesFire())
            return
        }
        abrirPrimera(
            Intent("com.android.systemui.TV_MEDIA_OUTPUT_DIALOG"),
            Intent(Settings.ACTION_SOUND_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    /** Algunas TV no tienen pantalla de Bluetooth propia: ahí se va a la de vincular dispositivos. */
    private fun abrirBluetooth() {
        val permiso = android.Manifest.permission.BLUETOOTH_CONNECT
        if (hayDifuminado && checkSelfPermission(permiso) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permiso), PEDIDO_BLUETOOTH)
            return
        }
        irABluetooth()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PEDIDO_BLUETOOTH) irABluetooth()
    }

    private fun irABluetooth() {
        if (esFireTv) {
            abrirPrimera(bluetoothFire(), ajustesFire())
            return
        }
        abrirPrimera(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            Intent("com.google.android.intent.action.CONNECT_INPUT"),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    /** Abre la primera pantalla que este equipo permita. */
    private fun abrirPrimera(vararg opciones: Intent) {
        for (intent in opciones) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // Probar la siguiente.
            }
        }
    }

    /** Línea fina entre grupos, como en los menús de iOS. */
    private fun separador() = View(this).apply {
        setBackgroundColor(0x26FFFFFF)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            setMargins(dp(10), dp(4), dp(10), dp(4))
        }
    }

    private fun itemLateral(titulo: String, icono: Int, destino: Pagina?, click: () -> Unit, mantener: (() -> Unit)?): View {
        val imagen = ImageView(this).apply {
            setImageResource(icono)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
        val texto = TextView(this).apply {
            text = titulo
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val vista = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            isFocusable = true
            isClickable = true
            addView(imagen, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(10) })
            addView(texto)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34))
        }
        val item = ItemLateral(vista, destino, texto, imagen)
        itemsLaterales.add(item)
        // Sin agrandar: la selección queda siempre adentro del panel.
        vista.setOnFocusChangeListener { _, _ -> pintarItem(item) }
        vista.setOnClickListener { click() }
        if (mantener != null) vista.setOnLongClickListener { mantener(); true }
        return vista
    }

    /** Enfocado: fila blanca con letra oscura. Página actual: fila apenas marcada. */
    private fun pintarItem(item: ItemLateral) {
        val foco = item.vista.isFocused
        val actual = item.pagina == pagina
        item.vista.background = if (foco || actual) GradientDrawable().apply {
            setColor(if (foco) CREMA else 0x1FFFFFFF)
            cornerRadius = dp(9).toFloat()
        } else null
        val color = if (foco) 0xFF111114.toInt() else Color.WHITE
        item.texto.setTextColor(color)
        item.icono.imageTintList = ColorStateList.valueOf(color)
    }

    private fun abrirPanel() {
        if (panelAbierto) return
        panelAbierto = true
        ultimaTarjeta = currentFocus?.tag as? String
        isla.permitirFoco(false)
        pintarMusicaMenu()
        if (hayDifuminado) capa.setRenderEffect(RenderEffect.createBlurEffect(dp(12).toFloat(), dp(12).toFloat(), Shader.TileMode.CLAMP))
        // Mientras está abierto, el foco no puede escaparse a las tarjetas de atrás.
        contenido.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        itemsLaterales.forEach { pintarItem(it) }
        velo.visibility = View.VISIBLE
        panel.visibility = View.VISIBLE
        val distancia = -dp(320).toFloat()
        panel.translationX = distancia
        panel.alpha = 0f
        velo.alpha = 0f
        animar(panel, 300) { c ->
            panel.translationX = distancia * (1 - c)
            panel.alpha = c
            velo.alpha = c
        }
        (itemsLaterales.find { it.pagina == pagina } ?: itemsLaterales.first()).vista.requestFocus()
    }

    private fun cerrarPanel(enfocar: Boolean = true) {
        if (!panelAbierto) return
        panelAbierto = false
        isla.permitirFoco(true)
        if (hayDifuminado) capa.setRenderEffect(null)
        contenido.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        val distancia = -dp(320).toFloat()
        animar(panel, 220) { c ->
            panel.translationX = distancia * c
            panel.alpha = 1 - c
            velo.alpha = 1 - c
            if (c >= 1f) {
                panel.visibility = View.GONE
                velo.visibility = View.GONE
            }
        }
        if (enfocar) {
            val destino = if (vitrinaActiva()) vitrina else ultimaTarjeta?.let { contenido.findViewWithTag<View>(it) } ?: buscarPrimera(contenido)
            destino?.requestFocus()
        }
    }

    private fun cambiarPagina(p: Pagina) {
        val cambio = p != pagina
        pagina = p
        ultimaTarjeta = null
        if (p == Pagina.INICIO) enVitrina = true
        cerrarPanel(enfocar = false)
        if (cambio) mostrar(arriba = true) else if (vitrinaActiva()) entrarVitrina() else buscarPrimera(contenido)?.requestFocus()
    }

    // ---------- Datos ----------

    private fun cargarApps() {
        val pm = packageManager
        val encontradas = LinkedHashMap<String, App>()
        // Primero las apps hechas para TV (con banner); después las comunes que falten.
        for (categoria in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(categoria)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo
                if (ai.packageName == packageName || encontradas.containsKey(ai.packageName)) continue
                @Suppress("DEPRECATION")
                val esJuego = (ai.applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                encontradas[ai.packageName] = App(
                    ai.packageName,
                    nombresCortos[ai.packageName] ?: ri.loadLabel(pm).toString().substringBefore(":").substringBefore(" - ").trim(),
                    if (ai.packageName in tarjetaSimple) null
                    else logosPropios[ai.packageName]?.let { getDrawable(it) } ?: ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm),
                    ri.loadIcon(pm),
                    (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    if (esJuego) ApplicationInfo.CATEGORY_GAME else if (android.os.Build.VERSION.SDK_INT >= 26) ai.applicationInfo.category else -1,
                    if (ai.packageName in logosPropios) 0 else if (ai.banner != 0) ai.banner else ai.applicationInfo.banner,
                )
            }
        }
        apps = encontradas.values.sortedBy { it.label.lowercase() }

        if (!prefs.getBoolean("iniciado", false)) {
            guardarLista(FAV, favoritasIniciales.filter { p -> apps.any { it.pkg == p } })
            prefs.edit().putBoolean("iniciado", true).apply()
        }
    }

    private fun categoriaDe(app: App): Categoria {
        prefs.getString("cat_${app.pkg}", null)?.let { guardada ->
            Categoria.values().find { it.name == guardada }?.let { return it }
        }
        categoriasConocidas[app.pkg]?.let { return it }
        return when (app.categoriaAndroid) {
            ApplicationInfo.CATEGORY_GAME -> Categoria.JUEGOS
            ApplicationInfo.CATEGORY_AUDIO -> Categoria.MUSICA
            ApplicationInfo.CATEGORY_VIDEO -> Categoria.STREAMING
            else -> Categoria.HERRAMIENTAS
        }
    }

    private fun ocultas(): Set<String> = leerLista("ocultas").toSet()

    private fun visibles() = ocultas().let { o -> apps.filter { it.pkg !in o } }

    private fun favoritas(): List<App> {
        val porPkg = visibles().associateBy { it.pkg }
        return leerLista(FAV).mapNotNull { porPkg[it] }
    }

    /** Apps de una categoría en el orden que eligió el usuario; las nuevas van al final por nombre. */
    private fun appsDe(cat: Categoria): List<App> {
        val lista = visibles().filter { categoriaDe(it) == cat }
        val orden = leerLista(cat.name)
        val porPkg = lista.associateBy { it.pkg }
        return orden.mapNotNull { porPkg[it] } + lista.filter { it.pkg !in orden }
    }

    private fun appsDeContenedor(contenedor: String): List<App> =
        if (contenedor == FAV) favoritas() else appsDe(Categoria.valueOf(contenedor))

    private fun leerLista(clave: String): MutableList<String> =
        (prefs.getString(clave, "") ?: "").split(',').filter { it.isNotBlank() }.toMutableList()

    private fun guardarLista(clave: String, lista: List<String>) {
        prefs.edit().putString(clave, lista.joinToString(",")).apply()
    }

    // ---------- Contenido ----------

    private fun mostrar(foco: String? = null, arriba: Boolean = false) {
        val scrollAntes = scroll.scrollY
        contenido.removeAllViews()
        resultados = null

        when (val p = pagina) {
            Pagina.BUSCAR -> armarBusqueda()
            Pagina.INICIO -> {
                // Solari: la vitrina de propiedades arriba de todo.
                vitrina?.let { v ->
                    (v.parent as? ViewGroup)?.removeView(v)
                    // Llena la pantalla: debajo del reloj hasta casi abajo, dejando asomar "Favoritas".
                    val alto = resources.displayMetrics.heightPixels - dp(TOPE_VITRINA) - dp(52)
                    contenido.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, alto).apply {
                        marginStart = dp(MARGEN)
                        marginEnd = dp(MARGEN)
                        topMargin = dp(TOPE_VITRINA)
                        bottomMargin = dp(10)
                    })
                }
                val favs = favoritas()
                if (favs.isNotEmpty()) agregarFila("Favoritas", favs, FAV, contenido)
                val porPkg = visibles().associateBy { it.pkg }
                val recientes = leerLista(RECIENTES).mapNotNull { porPkg[it] }.filter { it !in favs }
                if (recientes.isNotEmpty()) agregarFila("Recientes", recientes, RECIENTES, contenido, columnas + 1)
                if (favs.isEmpty() && recientes.isEmpty()) agregarFila("Apps", visibles(), BUSQUEDA, contenido)
            }
            Pagina.TODAS -> {
                for (cat in Categoria.values()) {
                    val lista = appsDe(cat)
                    if (lista.isNotEmpty()) agregarFila(cat.titulo, lista, cat.name, contenido)
                }
                val escondidas = apps.filter { it.pkg in ocultas() }
                if (escondidas.isNotEmpty()) agregarFila("Ocultas", escondidas, OCULTAS, contenido)
            }
            else -> {
                // Una categoría sola: grilla, para ver todas sus apps de un vistazo.
                val cat = p.categoria!!
                val lista = appsDe(cat)
                if (lista.isEmpty()) {
                    nombreApp.text = cat.titulo
                    subtitulo.text = "Todavía no hay apps acá"
                }
                lista.chunked(columnas).forEachIndexed { i, parte ->
                    agregarFila(if (i == 0) cat.titulo else null, parte, cat.name, contenido)
                }
            }
        }

        destacado.visibility = if (vitrinaActiva()) View.GONE else View.VISIBLE
        if (arriba || vitrinaActiva()) scroll.scrollTo(0, 0) else scroll.post { scroll.scrollTo(0, scrollAntes) }
        val destino = foco?.let { contenido.findViewWithTag<View>(it) } ?: focoInicial()
        destino?.post { if (!panelAbierto) destino.requestFocus() }
        if (arriba) aparecer(contenido)
    }

    private fun armarBusqueda() {
        nombreApp.text = "Buscar"
        subtitulo.text = "Escribí el nombre de una app"
        val campo = EditText(this).apply {
            setText(busqueda)
            hint = "Nombre de la app"
            setHintTextColor(0x80FFFFFF.toInt())
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            maxLines = 1
            isSingleLine = true
            setPadding(dp(22), 0, dp(22), 0)
            tag = "buscar"
            val reposo = GradientDrawable().apply {
                setColor(0x22FFFFFF)
                cornerRadius = dp(24).toFloat()
            }
            background = reposo
            setOnFocusChangeListener { _, foco ->
                background = if (foco) GradientDrawable().apply {
                    setColor(0x44FFFFFF)
                    cornerRadius = dp(24).toFloat()
                } else reposo
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    busqueda = s?.toString().orEmpty()
                    pintarResultados()
                }
            })
        }
        contenido.addView(campo, LinearLayout.LayoutParams(dp(560), dp(48)).apply {
            marginStart = dp(MARGEN)
            topMargin = dp(8)
            bottomMargin = dp(8)
        })
        resultados = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        contenido.addView(resultados)
        pintarResultados()
    }

    private fun pintarResultados() {
        val caja = resultados ?: return
        caja.removeAllViews()
        val texto = busqueda.trim()
        if (texto.isEmpty()) return
        val lista = visibles().filter { it.label.contains(texto, ignoreCase = true) }
        lista.chunked(columnas).forEach { agregarFila(null, it, BUSQUEDA, caja) }
    }

    private fun buscarPrimera(grupo: ViewGroup): View? {
        for (i in 0 until grupo.childCount) {
            val v = grupo.getChildAt(i)
            if (v.tag is String) return v
            if (v is ViewGroup) buscarPrimera(v)?.let { return it }
        }
        return null
    }

    private fun agregarFila(titulo: String?, lista: List<App>, contenedor: String, destino: LinearLayout, porFila: Int = columnas) {
        if (titulo != null) {
            destino.addView(TextView(this).apply {
                text = titulo
                setTextColor(if (Tema.vice) Color.WHITE else CREMA)
                alpha = 0.8f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = medium
                setPadding(dp(MARGEN), dp(10), 0, 0)
            })
        }
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(MARGEN - 8), dp(10), dp(MARGEN), 0)
        }
        val margen = dp(8)
        val ancho = (resources.displayMetrics.widthPixels - dp((MARGEN - 8) * 2)) / porFila - 2 * margen
        for (app in lista) {
            fila.addView(crearTarjeta(app, contenedor, titulo, ancho), LinearLayout.LayoutParams(ancho, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(margen, 0, margen, 0)
            })
        }
        destino.addView(object : HorizontalScrollView(this) {
            // Al llegar al borde, deja ver un poco de la tarjeta siguiente.
            override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
                rect.inset(-dp(MARGEN + 16), 0)
                return super.computeScrollDeltaToGetChildRectOnScreen(rect)
            }
        }.apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(fila)
        })
    }

    /** Tarjeta sin marco: al enfocarla crece con un rebote suave, gana sombra y aparece su nombre abajo. */
    private fun crearTarjeta(app: App, contenedor: String, titulo: String?, ancho: Int): View {
        val radio = dp(14).toFloat()
        val tarjeta = FrameLayout(this).apply {
            isFocusable = true
            isClickable = true
            tag = "$contenedor|${app.pkg}"
            outlineProvider = bordesRedondos(radio)
            clipToOutline = true
            setBackgroundColor(if (app.banner == null) colorDeMarca(app.icono) else colorDeBorde(app.banner))
            // Vidrio: un brillo suave desde arriba y un borde finito, igual para todas las tarjetas.
            foreground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x24FFFFFF, 0x00FFFFFF, 0x00000000)).apply {
                cornerRadius = radio
                setStroke(1, 0x2EFFFFFF)
            }
            if (contenedor == OCULTAS) alpha = 0.5f
        }
        if (app.banner != null) {
            tarjeta.addView(ImageView(this).apply {
                setImageDrawable(app.banner)
                scaleType = ImageView.ScaleType.FIT_XY
            }, llenar())
        } else {
            tarjeta.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ImageView(this@MainActivity).apply { setImageDrawable(app.icono) }, LinearLayout.LayoutParams(minOf(dp(50), ancho * 9 / 16 - dp(36)), minOf(dp(50), ancho * 9 / 16 - dp(36))))
                addView(TextView(this@MainActivity).apply {
                    text = app.label
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = medium
                    setPadding(0, dp(6), 0, 0)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, llenar())
        }

        val nombre = TextView(this).apply {
            // Las tarjetas sin logo ya muestran el nombre adentro.
            text = if (app.banner == null) "" else app.label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            alpha = 0f
        }
        val envoltura = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(tarjeta, LinearLayout.LayoutParams(ancho, ancho * 9 / 16))
            addView(nombre, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)).apply { topMargin = dp(2) })
        }

        val enMovimiento = moviendo == (contenedor to app.pkg)
        if (enMovimiento) {
            tarjeta.scaleX = 1.14f
            tarjeta.scaleY = 1.14f
            nombre.alpha = 1f
            nombre.text = "‹  ${app.label}  ›"
        }
        tarjeta.setOnFocusChangeListener { _, foco ->
            escalar(tarjeta, if (foco) 1.1f else 1f, if (foco) dp(18).toFloat() else 0f)
            val alfa0 = nombre.alpha
            val destino = if (foco) 1f else 0f
            animar(nombre, 200) { c -> nombre.alpha = alfa0 + (destino - alfa0) * c }
            if (foco) {
                ultimaTarjeta = "$contenedor|${app.pkg}"
                // Espera un instante: al pasar rápido por la fila no se dibuja cada logo.
                espera.removeCallbacksAndMessages(null)
                espera.postDelayed({ verDestacada(app, titulo ?: pagina.titulo) }, 130)
            }
        }
        tarjeta.setOnClickListener { abrir(app.pkg) }
        tarjeta.setOnLongClickListener { menuApp(app, contenedor); true }
        return envoltura
    }

    private var destacadaActual: String? = null

    private var imagenDeFondo: Drawable? = null
    private var colorDeFondo = 0

    /** La franja de arriba toma el color de la carátula mientras suena música; si no, el de la app enfocada. */
    private fun pintarFondo() {
        val tapa = isla.tapaActual
        val imagen = if (isla.sonando && tapa != null) android.graphics.drawable.BitmapDrawable(resources, tapa) else imagenDeFondo
        val color = colorDeMarca(imagen ?: return)
        if (color != colorDeFondo) {
            colorDeFondo = color
            if (!Tema.vice) fondo.cambiarColor(color)
        }
    }

    private fun verDestacada(app: App, seccion: String) {
        subtitulo.text = seccion
        if (destacadaActual == app.pkg) return
        destacadaActual = app.pkg
        val imagen = app.banner ?: app.icono
        imagenDeFondo = imagen
        pintarFondo()
        val sinFondo = logosCabecera[app.pkg]
        val medidas = destacada.layoutParams
        if (sinFondo != null) {
            // Logo suelto sobre la franja de color, alineado abajo a la izquierda.
            val logo = getDrawable(sinFondo)!!
            val escala = minOf(dp(ANCHO_CABECERA).toFloat() / logo.intrinsicWidth, dp(ALTO_CABECERA).toFloat() / logo.intrinsicHeight)
            medidas.width = (logo.intrinsicWidth * escala).toInt()
            medidas.height = (logo.intrinsicHeight * escala).toInt()
            destacada.setImageDrawable(logo)
            destacada.background = null
            destacada.clipToOutline = false
            destacada.setPadding(0, 0, 0, 0)
            nombreApp.visibility = View.GONE
            subtitulo.visibility = View.GONE
        } else {
            medidas.width = dp(ALTO_CABECERA) * 16 / 9
            medidas.height = dp(ALTO_CABECERA)
            // Copia propia: si la cabecera y la tarjeta comparten el mismo dibujo, se pelean por el tamaño y los
            // íconos adaptables rehacen su máscara una y otra vez hasta llenar la memoria (pasó en el Fire Stick).
            val grande = try {
                imagenGrande(app)
            } catch (e: OutOfMemoryError) {
                null
            }
            try {
                destacada.setImageDrawable(grande ?: copiaDe(imagen))
            } catch (e: OutOfMemoryError) {
                destacada.setImageDrawable(null) // mejor sin imagen que cerrar el launcher
            }
            destacada.setBackgroundColor(if (app.banner == null) colorDeMarca(imagen) else colorDeBorde(imagen))
            destacada.clipToOutline = true
            val relleno = if (app.banner == null) dp(28) else 0
            destacada.setPadding(relleno, relleno, relleno, relleno)
            nombreApp.visibility = View.VISIBLE
            subtitulo.visibility = View.VISIBLE
        }
        destacada.layoutParams = medidas
        nombreApp.text = app.label
        aparecer(destacada, 280, dp(6).toFloat())
        aparecer(nombreApp, 280, dp(6).toFloat())
    }

    /**
     * Color de fondo sacado del tono más vivo del logo, no del promedio: así YouTube da rojo oscuro y no gris.
     * Un logo sin color (blanco, gris o negro) deja el fondo casi negro.
     */
    private fun colorDeMarca(d: Drawable): Int {
        val bmp = aBitmap(d, 32, 18)
        val pesos = FloatArray(24)
        val tonos = FloatArray(24)
        val hsv = FloatArray(3)
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            if (hsv[1] < 0.35f || hsv[2] < 0.2f) continue
            val i = (hsv[0] / 15f).toInt().coerceAtMost(23)
            val peso = hsv[1] * hsv[2]
            pesos[i] += peso
            tonos[i] += hsv[0] * peso
        }
        val mejor = pesos.indices.maxByOrNull { pesos[it] } ?: return COLOR_NEUTRO
        if (pesos[mejor] < 6f) return COLOR_NEUTRO
        return Color.HSVToColor(floatArrayOf(tonos[mejor] / pesos[mejor], 0.85f, 0.28f))
    }

    /** Pide a la app la versión más grande de su banner; null si no trae una mejor que la que ya se ve. */
    private fun copiaDe(d: Drawable): Drawable = d.constantState?.newDrawable(resources)?.mutate() ?: d

    /** Equipos con poca memoria (Fire Stick, TVs baratas): ahí no se cargan las versiones en alta resolución. */
    private val pocaMemoria by lazy {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        am.isLowRamDevice || info.totalMem < 2_500L * 1024 * 1024
    }

    private fun imagenGrande(app: App): Drawable? {
        if (app.bannerId == 0 || pocaMemoria) return null
        return try {
            val res = packageManager.getResourcesForApplication(app.pkg)
            val valor = TypedValue()
            res.getValueForDensity(app.bannerId, 640, valor, true)
            if (valor.density > resources.displayMetrics.densityDpi) res.getDrawableForDensity(app.bannerId, valor.density, null) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun colorDeBorde(d: Drawable) = aBitmap(d, 32, 18).getPixel(1, 1).let { if (Color.alpha(it) < 128) 0xFF26262C.toInt() else it }

    private fun bordesRedondos(radio: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radio)
        }
    }

    private fun aBitmap(d: Drawable, ancho: Int, alto: Int): Bitmap {
        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        // Copia del drawable: cambiarle el tamaño al original rompería la tarjeta que lo está mostrando.
        val copia = d.constantState?.newDrawable(resources)?.mutate() ?: d
        copia.setBounds(0, 0, ancho, alto)
        copia.draw(Canvas(bmp))
        return bmp
    }

    // ---------- Optimizar ----------

    private fun memoriaLibreMb(): Long {
        val info = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    /** Cierra lo que otras apps tienen en segundo plano; Android no deja tocar lo que está en uso. */
    private fun optimizar() {
        val antes = memoriaLibreMb()
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (ai in packageManager.getInstalledApplications(0)) {
            if (ai.packageName != packageName) am.killBackgroundProcesses(ai.packageName)
        }
        contenido.postDelayed({
            val ganados = (memoriaLibreMb() - antes).coerceAtLeast(0)
            Toast.makeText(this, "Memoria liberada: $ganados MB · ${memoriaLibreMb()} MB libres", Toast.LENGTH_LONG).show()
        }, 1500)
    }

    private val escalasAnimacion = listOf(
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.ANIMATOR_DURATION_SCALE,
    )

    private fun escalaAnimacion() = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    private fun nombreAnimacion() = when {
        escalaAnimacion() == 0f -> "sin animaciones"
        escalaAnimacion() < 1f -> "rápidas"
        else -> "normales"
    }

    private fun textoAlmacenamiento(): String {
        val datos = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        return String.format("%.1f GB libres", datos.availableBytes / (1024.0 * 1024 * 1024))
    }

    // ---------- Prender PC (Wake on LAN) ----------

    /** Lista de PC con su estado (prendida o apagada); al elegir una, la prende. */
    private fun menuPcs() {
        val pcs = Pcs.lista(this)
        if (pcs.isEmpty()) {
            Toast.makeText(this, "Todavía no hay PC cargadas", Toast.LENGTH_LONG).show()
            return
        }
        Thread {
            // Se revisan todas a la vez para no hacer esperar.
            val estados = BooleanArray(pcs.size)
            pcs.mapIndexed { i, pc -> Thread { estados[i] = Pcs.prendida(pc) }.apply { start() } }.forEach { it.join() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                mostrarDialogo("Prender PC", null, pcs.mapIndexed { i, pc ->
                    (if (estados[i]) "${pc.nombre}  ·  prendida" else "${pc.nombre}  ·  apagada") to { prenderPc(pc, estados[i]) }
                })
            }
        }.start()
    }

    private fun prenderPc(pc: Pcs.Pc, yaPrendida: Boolean) {
        if (yaPrendida) {
            Toast.makeText(this, "${pc.nombre} ya está prendida", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Prendiendo ${pc.nombre}…", Toast.LENGTH_SHORT).show()
        val app = applicationContext
        Thread {
            val salio = try { Pcs.prender(pc) } catch (e: Exception) { false }
            if (!salio) {
                runOnUiThread { Toast.makeText(app, "No se pudo mandar la señal a ${pc.nombre}", Toast.LENGTH_LONG).show() }
                return@Thread
            }
            if (pc.ip.isBlank()) return@Thread
            // Espera hasta un minuto y medio a que la PC conteste, para avisar que ya arrancó.
            repeat(18) {
                Thread.sleep(5000)
                if (Pcs.prendida(pc)) {
                    runOnUiThread { Toast.makeText(app, "${pc.nombre} ya está prendida", Toast.LENGTH_LONG).show() }
                    return@Thread
                }
            }
            runOnUiThread { Toast.makeText(app, "${pc.nombre} todavía no contesta", Toast.LENGTH_LONG).show() }
        }.start()
    }

    /** Carga la lista de PC que llega desde la PC con ADB (am start ... --es pcs '[...]'). */
    private fun recibirPcs(intent: Intent?) {
        val json = intent?.getStringExtra("pcs") ?: return
        intent.removeExtra("pcs")
        if (Pcs.guardar(this, json)) Toast.makeText(this, "PC cargadas: ${Pcs.lista(this).size}", Toast.LENGTH_SHORT).show()
    }

    private fun menuOptimizar() {
        mostrarDialogo("Optimizar", null, listOf(
            "Optimizar ahora (${memoriaLibreMb()} MB libres)" to { optimizar() },
            "Animaciones del sistema: ${nombreAnimacion()}" to {
                // Normales → Rápidas → Sin animaciones → Normales
                val siguiente = when {
                    escalaAnimacion() >= 1f -> 0.5f
                    escalaAnimacion() > 0f -> 0f
                    else -> 1f
                }
                conPermiso {
                    for (clave in escalasAnimacion) Settings.Global.putFloat(contentResolver, clave, siguiente)
                    Toast.makeText(this, "Animaciones: ${nombreAnimacion()}", Toast.LENGTH_SHORT).show()
                }
            },
            "Almacenamiento: ${textoAlmacenamiento()}" to { abrirIntent(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) },
        ))
    }

    // ---------- Ajustes ----------

    private val servicioInicio get() = "$packageName/${InicioService::class.java.name}"

    private fun tienePermisoAjustes() =
        packageManager.checkPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS, packageName) == PackageManager.PERMISSION_GRANTED

    private fun servicioInicioActivo() =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(servicioInicio) == true

    /** Si el servicio del botón Inicio se apagó solo, lo vuelve a prender (solo si se había activado y hay permiso). */
    private fun asegurarServicioInicio() {
        if (tienePermisoAjustes() && prefs.getBoolean("boton_inicio", false) && !servicioInicioActivo()) cambiarServicioInicio(true)
    }

    private fun cambiarServicioInicio(activar: Boolean) {
        val actuales = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(':').filter { it.isNotBlank() && it != servicioInicio }
        val nuevos = if (activar) actuales + servicioInicio else actuales
        Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nuevos.joinToString(":"))
        Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (nuevos.isEmpty()) 0 else 1)
    }

    private val componenteProtector get() = "$packageName/${ProtectorService::class.java.name}"

    private fun protectorActivo() =
        Settings.Secure.getInt(contentResolver, "screensaver_enabled", 0) == 1 &&
            Settings.Secure.getString(contentResolver, "screensaver_components") == componenteProtector

    private fun cambiarProtector(activar: Boolean) {
        Settings.Secure.putString(contentResolver, "screensaver_components", componenteProtector)
        Settings.Secure.putInt(contentResolver, "screensaver_enabled", if (activar) 1 else 0)
        Settings.Secure.putInt(contentResolver, "screensaver_activate_on_sleep", if (activar) 1 else 0)
    }

    private val hayDifuminado get() = android.os.Build.VERSION.SDK_INT >= 31

    /** Cambia un ajuste del sistema; si falta el permiso, avisa en vez de cerrarse. */
    private fun conPermiso(accion: () -> Unit) {
        try {
            accion()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Falta el permiso de ajustes. Se otorga una sola vez desde la PC con ADB.", Toast.LENGTH_LONG).show()
        }
    }

    private fun menuAjustes() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        mostrarDialogo("Ajustes · ${getString(R.string.app_name)} $version", null, listOf(
            "Tarjetas: " + (if (prefs.getBoolean("grandes", false)) "grandes (4 por fila)" else "normales (5 por fila)") to {
                prefs.edit().putBoolean("grandes", !prefs.getBoolean("grandes", false)).commit()
                refrescar()
            },
            "Abrir ${getString(R.string.app_name)} con Inicio: " + (if (servicioInicioActivo()) "sí" else "no") to {
                if (tienePermisoAjustes()) {
                    val activar = !servicioInicioActivo()
                    prefs.edit().putBoolean("boton_inicio", activar).apply()
                    conPermiso { cambiarServicioInicio(activar) }
                } else {
                    abrirIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            },
            "Tema: " + (if (Tema.vice) "Vice City" else "Clásico") to {
                Tema.cambiar(this, !Tema.vice)
                recreate()
            },
            "Protector de pantalla: " + (if (protectorActivo()) "sí" else "no") to {
                conPermiso { cambiarProtector(!protectorActivo()) }
            },
            "Ver el protector ahora" to { startActivity(Intent(this, ProtectorActivity::class.java)) },
            "Volver a mostrar ocultas (${ocultas().size})" to {
                guardarLista("ocultas", emptyList())
                refrescar()
            },
            "Restablecer orden y categorías" to {
                val editor = prefs.edit()
                for (clave in prefs.all.keys) {
                    if (clave.startsWith("cat_") || Categoria.values().any { it.name == clave }) editor.remove(clave)
                }
                editor.commit()
                refrescar()
            },
            "Ajustes de la TV" to { abrirAjustesTv() },
        ))
    }

    /** Redibuja las filas sin sacar el foco del menú lateral si está abierto. */
    private fun refrescar() {
        ultimaTarjeta = null
        mostrar(arriba = true)
    }

    // ---------- Menú al mantener OK ----------

    private fun menuApp(app: App, contenedor: String) {
        val oculta = app.pkg in ocultas()
        val opciones = mutableListOf<Pair<String, () -> Unit>>()
        opciones += "Abrir" to { abrir(app.pkg) }
        if (oculta) {
            opciones += "Mostrar" to { ocultar(app, false) }
        } else {
            opciones += (if (app.pkg in leerLista(FAV)) "Quitar de favoritas" else "Agregar a favoritas") to { alternarFavorita(app, contenedor) }
            if (contenedor != BUSQUEDA && contenedor != RECIENTES) opciones += "Mover" to { empezarAMover(app, contenedor) }
            opciones += "Cambiar categoría" to { elegirCategoria(app) }
            opciones += "Ocultar" to { ocultar(app, true) }
        }
        opciones += "Cerrar en segundo plano" to {
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(app.pkg)
            Toast.makeText(this, "${app.label} cerrada", Toast.LENGTH_SHORT).show()
        }
        opciones += "Optimizar" to { menuOptimizar() }
        opciones += "Información de la app" to {
            abrirIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
        }
        if (!app.sistema) {
            opciones += "Desinstalar" to { abrirIntent(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))) }
        }
        mostrarDialogo(app.label, app.icono, opciones)
    }

    private fun elegirCategoria(app: App) {
        val actual = categoriaDe(app)
        mostrarDialogo("Categoría de ${app.label}", app.icono, Categoria.values().map { cat ->
            (if (cat == actual) "✓  ${cat.titulo}" else "     ${cat.titulo}") to {
                prefs.edit().putString("cat_${app.pkg}", cat.name).apply()
                mostrar(foco = "${cat.name}|${app.pkg}")
            }
        })
    }

    private var dialogosAbiertos = 0

    /** Ícono de cada opción, según lo que dice. */
    private fun iconoDeOpcion(texto: String): Int? = when {
        texto == "Abrir" -> R.drawable.ic_play
        texto.contains("favoritas") -> R.drawable.ic_estrella
        texto == "Mover" -> R.drawable.ic_mover
        texto.contains("categoría") -> R.drawable.ic_apps
        texto == "Ocultar" || texto == "Mostrar" -> R.drawable.ic_ocultar
        texto.startsWith("Cerrar") -> R.drawable.ic_cerrar
        texto.startsWith("Optimizar") -> R.drawable.ic_rayo
        texto.startsWith("Información") -> R.drawable.ic_info
        texto == "Desinstalar" -> R.drawable.ic_borrar
        texto.startsWith("Almacenamiento") -> R.drawable.ic_herramientas
        texto.startsWith("Ajustes") -> R.drawable.ic_config
        texto.endsWith("prendida") || texto.endsWith("apagada") -> R.drawable.ic_power
        else -> null
    }

    /** Menú con el mismo vidrio del menú lateral: fondo difuminado, íconos y fila crema al enfocar. */
    private fun mostrarDialogo(titulo: String, icono: Drawable?, opciones: List<Pair<String, () -> Unit>>) {
        val dialogo = Dialog(this)
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(12), dp(6), dp(6))
        }
        val encabezado = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), dp(8))
        }
        if (icono != null) {
            encabezado.addView(ImageView(this).apply {
                setImageDrawable(icono)
                outlineProvider = bordesRedondos(dp(8).toFloat())
                clipToOutline = true
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
        }
        encabezado.addView(TextView(this).apply {
            text = titulo
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = medium
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        caja.addView(encabezado)
        caja.addView(separador())

        var primera: View? = null
        for ((texto, accion) in opciones) {
            val dibujo = iconoDeOpcion(texto)
            val imagen = ImageView(this).apply {
                if (dibujo != null) setImageResource(dibujo)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            val etiqueta = TextView(this).apply {
                text = texto
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val opcion = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(10), 0)
                isFocusable = true
                isClickable = true
                if (dibujo != null) addView(imagen, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(10) })
                addView(etiqueta)
                setOnFocusChangeListener { v, foco ->
                    v.background = if (foco) GradientDrawable().apply {
                        setColor(CREMA)
                        cornerRadius = dp(9).toFloat()
                    } else null
                    val color = if (foco) 0xFF111114.toInt() else Color.WHITE
                    etiqueta.setTextColor(color)
                    imagen.imageTintList = ColorStateList.valueOf(color)
                }
                setOnClickListener {
                    dialogo.dismiss()
                    accion()
                }
            }
            if (primera == null) primera = opcion
            caja.addView(opcion, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
        }

        dialogo.setContentView(ScrollView(this).apply {
            background = Vidrio(dp(20).toFloat(), Tema.rellenoPanel(hayDifuminado))
            clipToOutline = true
            addView(caja)
        }, ViewGroup.LayoutParams(dp(264), ViewGroup.LayoutParams.WRAP_CONTENT))
        dialogo.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.25f)
        }
        // Un menú puede abrir otro: el difuminado se saca recién cuando se cerró el último.
        dialogosAbiertos++
        if (hayDifuminado) capa.setRenderEffect(RenderEffect.createBlurEffect(dp(12).toFloat(), dp(12).toFloat(), Shader.TileMode.CLAMP))
        dialogo.setOnDismissListener {
            dialogosAbiertos--
            if (hayDifuminado && dialogosAbiertos == 0 && !panelAbierto) capa.setRenderEffect(null)
        }
        aplicarFuente(caja)
        dialogo.show()
        primera?.requestFocus()
    }

    // ---------- Acciones ----------

    private fun abrir(pkg: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "No se puede abrir esta app", Toast.LENGTH_SHORT).show()
            return
        }
        guardarLista(RECIENTES, (listOf(pkg) + leerLista(RECIENTES).filter { it != pkg }).take(12))
        abrirIntent(intent)
    }

    private fun abrirIntent(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun alternarFavorita(app: App, contenedor: String) {
        val favs = leerLista(FAV)
        val mensaje = if (favs.remove(app.pkg)) {
            "${app.label} quitada de favoritas"
        } else {
            favs.add(app.pkg)
            "${app.label} agregada a favoritas"
        }
        guardarLista(FAV, favs)
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        // Si se quitó desde la fila de favoritas, el foco queda en la misma app en su categoría.
        val nuevo = if (contenedor == FAV) categoriaDe(app).name else contenedor
        mostrar(foco = "$nuevo|${app.pkg}")
    }

    private fun ocultar(app: App, ocultar: Boolean) {
        val lista = leerLista("ocultas")
        if (ocultar) lista.add(app.pkg) else lista.remove(app.pkg)
        guardarLista("ocultas", lista.distinct())
        Toast.makeText(this, if (ocultar) "${app.label} oculta. La encontrás en Inicio, al final." else "${app.label} vuelve a mostrarse", Toast.LENGTH_SHORT).show()
        mostrar(foco = if (ocultar) null else "${categoriaDe(app).name}|${app.pkg}")
    }

    private fun empezarAMover(app: App, contenedor: String) {
        moviendo = contenedor to app.pkg
        mostrar(foco = "$contenedor|${app.pkg}")
    }

    private fun mover(contenedor: String, pkg: String, delta: Int) {
        val lista = appsDeContenedor(contenedor).map { it.pkg }.toMutableList()
        val i = lista.indexOf(pkg)
        val j = (i + delta).coerceIn(0, lista.size - 1)
        if (i < 0 || i == j) return
        lista.removeAt(i)
        lista.add(j, pkg)
        guardarLista(contenedor, lista)
        mostrar(foco = "$contenedor|$pkg")
    }

    private fun llenar() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun dp(valor: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), resources.displayMetrics).toInt()

    companion object {
        const val EXTRA_DESDE_HOME = "desde_home"
        private const val COLOR_NEUTRO = 0xFF26262C.toInt()
        /** Color de selección: cian con el tema Vice, crema con el clásico. */
        val CREMA: Int get() = Tema.acento
        private const val PEDIDO_BLUETOOTH = 8
        private const val SPOTIFY = "com.spotify.tv.android"
        private const val ANCHO_CABECERA = 190
        private const val ALTO_CABECERA = 78
        private const val RECIENTES = "recientes"
        private const val MARGEN = 40
        private const val TOPE_VITRINA = 80
        private const val FAV = "favoritas"
        private const val OCULTAS = "ocultas"
        private const val BUSQUEDA = "busqueda"
    }
}
