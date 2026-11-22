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
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Point
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
        "com.amazon.firetv.youtube" to R.drawable.logo_youtube,
        "com.netflix.ninja" to R.drawable.logo_netflix,
        "com.wbd.stream" to R.drawable.logo_hbomax,
        "com.hbo.hbonow" to R.drawable.logo_hbomax,
        "com.amazon.amazonvideo.livingroom" to R.drawable.logo_prime,
        "com.amazon.avod" to R.drawable.logo_prime,
        "com.amazon.firebat" to R.drawable.logo_prime,
        "com.apple.atve.androidtv.appletv" to R.drawable.logo_appletv,
        "com.cbs.ca" to R.drawable.logo_paramount,
        "com.cbs.ott" to R.drawable.logo_paramount,
        "tv.twitch.android.app" to R.drawable.logo_twitch,
        "tv.twitch.android.viewer" to R.drawable.logo_twitch,
        "com.kick.mobile" to R.drawable.logo_kick,
        "com.spotify.tv.android" to R.drawable.logo_spotify,
        "org.videolan.vlc" to R.drawable.logo_vlc,
        "com.android.vending" to R.drawable.logo_play,
        "com.google.android.youtube.tvmusic" to R.drawable.logo_ytmusic,
    )

    // Los mismos logos sin fondo, para la cabecera.
    private val logosCabecera = mapOf(
        "com.google.android.youtube.tv" to R.drawable.hero_youtube,
        "com.amazon.firetv.youtube" to R.drawable.hero_youtube,
        "com.netflix.ninja" to R.drawable.hero_netflix,
        "com.wbd.stream" to R.drawable.hero_hbomax,
        "com.hbo.hbonow" to R.drawable.hero_hbomax,
        "com.amazon.amazonvideo.livingroom" to R.drawable.hero_prime,
        "com.amazon.avod" to R.drawable.hero_prime,
        // Prime Video en el Fire TV se llama así; sin esto mostraba el banner recortado.
        "com.amazon.firebat" to R.drawable.hero_prime,
        "com.apple.atve.androidtv.appletv" to R.drawable.hero_appletv,
        "com.cbs.ca" to R.drawable.hero_paramount,
        "com.cbs.ott" to R.drawable.hero_paramount,
        "tv.twitch.android.app" to R.drawable.hero_twitch,
        "tv.twitch.android.viewer" to R.drawable.hero_twitch,
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
    /** Temas neón: una sola letra (Montserrat) en toda la pantalla; la jerarquía la dan el tamaño y el grosor. */
    private val conMontserrat get() = formal || Tema.neon

    private fun fuenteDeMarca(archivo: String): Typeface? =
        try { Typeface.createFromAsset(assets, "fuentes/$archivo") } catch (e: Exception) { null }

    /** Para pantallas que cambian su contenido solas (como Ajustes al entrar a una sección). */
    fun ponerFuente(v: View) = aplicarFuente(v)

    /** Recorre la pantalla y le pone la tipografía de la marca a cada texto que todavía no la tiene. */
    private fun aplicarFuente(v: View) {
        if (!conMontserrat) return
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
        Tema.cargar(this, if (formal) Estilo.CLASICO else Estilo.PRISMA)
        primeraVez = savedInstanceState == null
        val pantalla = armarPantalla()
        setContentView(pantalla)
        if (conMontserrat) pantalla.viewTreeObserver.addOnGlobalLayoutListener { aplicarFuente(pantalla) }
        recibirPcs(intent)
        recibirConfiguracion(intent)
        Informes.instalar(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recibirPcs(intent)
        recibirConfiguracion(intent)
        if (intent.getBooleanExtra(EXTRA_DESDE_HOME, false) || intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Inicio dos veces seguidas: apps abiertas.
            val ahora = android.os.SystemClock.uptimeMillis()
            // El Fire Stick avisa dos veces por cada Inicio (con ~50 ms de diferencia): eso es UNA sola
            // apretada. Doble Inicio de verdad = dos avisos separados por más de 400 ms y menos de 1,5 s.
            val pasaron = ahora - ultimoInicio
            if (pasaron < 400) return
            abrirVisorAlVolver = !formal && pasaron < 1500
            ultimoInicio = ahora
            pagina = Pagina.INICIO
            enVitrina = true
            ultimaTarjeta = null
            moviendo = null
            cerrarPanel(enfocar = false)
        }
    }

    override fun onResume() {
        super.onResume()
        adelante = true
        vitrina?.arrancarVideo()
        asegurarServicioInicio()
        cargarApps()
        Clima.guardado(this)?.let { pintarClima(it) }
        Clima.actualizar(this) { clima -> runOnUiThread { pintarClima(clima) } }
        isla.iniciar()
        if (!panelAbierto) mostrar(foco = ultimaTarjeta, arriba = ultimaTarjeta == null)
        ultimaTecla = android.os.SystemClock.uptimeMillis()
        quieto.removeCallbacksAndMessages(null)
        quieto.postDelayed(vigilarQuieto, 2000)
        if (reabrirTemas) {
            reabrirTemas = false
            contenido.postDelayed({ menuAjustes(enTemas = true) }, 250)
        }
        if (abrirVisorAlVolver) {
            abrirVisorAlVolver = false
            contenido.postDelayed({ abrirVisor() }, 150)
        }
    }

    override fun onPause() {
        adelante = false
        espera.removeCallbacksAndMessages(null)
        quieto.removeCallbacksAndMessages(null)
        isla.detener()
        vitrina?.pararVideo() // que el video no siga corriendo detrás de otra app
        super.onPause()
    }

    /**
     * Con otra app adelante, Ziota suelta las tarjetas, los logos grandes y la lista de apps:
     * en el Fire Stick la memoria es poca y la app que se está usando la necesita más.
     * Al volver, onResume arma todo de nuevo (ya lo hacía siempre).
     */
    override fun onStop() {
        super.onStop()
        cerrarCentro()
        cerrarVisor()
        cerrarMusica()
        cerrarAjustes()
        if (panelAbierto) cerrarPanel(enfocar = false)
        contenido.removeAllViews()
        resultados = null
        destacada.setImageDrawable(null)
        destacadaActual = null
        imagenDeFondo = null
        apps = emptyList()
        recortes.clear()
        fondo.soltar()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        ultimaTecla = android.os.SystemClock.uptimeMillis()
        // Música en pantalla: OK pausa, ← → cambian de tema; cualquier otro botón vuelve (al soltarlo, para que no haga nada más).
        if (musica != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) isla.alternar()
                KeyEvent.KEYCODE_DPAD_LEFT -> if (event.action == KeyEvent.ACTION_DOWN) isla.temaAnterior()
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (event.action == KeyEvent.ACTION_DOWN) isla.temaSiguiente()
                else -> if (event.action == KeyEvent.ACTION_UP) cerrarMusica()
            }
            return true
        }
        // Ajustes: Atrás los cierra.
        if (ajustes != null) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                // Desde una sección, Atrás vuelve a la lista de secciones; desde la lista, cierra.
                if (event.action == KeyEvent.ACTION_UP && ajustes?.atras() != true) cerrarAjustes()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        // Apps abiertas: Atrás lo cierra sin abrir nada (se comen las dos mitades de la tecla).
        if (visor != null) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) cerrarVisor()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        // Centro de Control abierto: Atrás lo cierra; el resto lo manejan sus módulos.
        if (centro != null) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) cerrarCentro()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
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
            // Desde cualquier tarjeta de la primera fila, subir llega a la isla (o, sin música, al Centro de Control);
            // subir otra vez desde la isla abre el Centro de Control.
            if (!panelAbierto && event.keyCode == KeyEvent.KEYCODE_DPAD_UP && currentFocus?.tag is String) {
                val siguiente = currentFocus?.focusSearch(View.FOCUS_UP)
                if (siguiente == null || siguiente.tag !is String) {
                    if (isla.visibility == View.VISIBLE) isla.requestFocus() else if (!formal) abrirCentro() else return super.dispatchKeyEvent(event)
                    return true
                }
            }
            if (!formal && currentFocus === isla && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                abrirCentro()
                return true
            }
            if (currentFocus === isla && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (vitrinaActiva()) vitrina?.requestFocus()
                else (ultimaTarjeta?.let { contenido.findViewWithTag<View>(it) } ?: buscarPrimera(contenido))?.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------- Centro de Control ----------

    private var centro: CentroDeControl? = null
    private var focoAntesDelCentro: View? = null

    private fun abrirCentro() {
        if (centro != null) return
        focoAntesDelCentro = currentFocus
        // Fondo difuminado: en Google TV lo hace el sistema; en el Fire Stick, una foto chiquita borrosa (una sola vez).
        val foto = if (hayDifuminado) null else Tema.fotoBorrosa(capa)
        if (hayDifuminado) capa.setRenderEffect(RenderEffect.createBlurEffect(dp(24).toFloat(), dp(24).toFloat(), Shader.TileMode.CLAMP))
        // Todo lo de atrás queda sin foco (si no, el control se va a la lista de apps, que no se ve).
        capa.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        val c = CentroDeControl(this, foto, CentroDeControl.Acciones(
            wifi = { abrirIntent(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)) },
            bluetooth = { abrirBluetooth() },
            sonido = { abrirSalidaDeSonido() },
            prenderPc = { menuPcs() },
            cantidadPcs = { Pcs.lista(this).size },
            optimizar = { cerrarCentro(); abrirVisor() },
            ajustes = { menuAjustes() },
            protector = { startActivity(Intent(this, ProtectorActivity::class.java)) },
        ))
        centro = c
        (capa.parent as ViewGroup).addView(c, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        aplicarFuente(c)
        c.alpha = 0f
        c.animate().alpha(1f).setDuration(200).start()
        c.post { c.enfocar() }
    }

    private fun cerrarCentro() {
        val c = centro ?: return
        centro = null
        (c.parent as? ViewGroup)?.removeView(c)
        if (hayDifuminado && !panelAbierto && dialogosAbiertos == 0) capa.setRenderEffect(null)
        capa.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        (focoAntesDelCentro?.takeIf { it.isAttachedToWindow } ?: focoInicial())?.requestFocus()
        focoAntesDelCentro = null
    }

    // ---------- Música en pantalla ----------

    private var musica: MusicaEnPantalla? = null
    private var ultimaTecla = 0L
    private val quieto = Handler(Looper.getMainLooper())

    /** Cada 2 segundos: si suena música y hace 10 segundos que no se toca el control, el tema ocupa la pantalla. */
    private val vigilarQuieto = object : Runnable {
        override fun run() {
            val libre = musica == null && centro == null && visor == null && ajustes == null && !panelAbierto && dialogosAbiertos == 0 && moviendo == null
            if (!formal && libre && isla.sonando && hasWindowFocus() && android.os.SystemClock.uptimeMillis() - ultimaTecla >= 10_000) mostrarMusica()
            quieto.postDelayed(this, 2000)
        }
    }

    private fun mostrarMusica() {
        val m = MusicaEnPantalla(this, isla)
        musica = m
        (capa.parent as ViewGroup).addView(m, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        aplicarFuente(m)
        m.alpha = 0f
        m.animate().alpha(1f).setDuration(600).start()
    }

    private fun cerrarMusica() {
        val m = musica ?: return
        musica = null
        (m.parent as? ViewGroup)?.removeView(m)
    }

    // ---------- Apps abiertas ----------

    private var visor: AppsAbiertas? = null
    private var ultimoInicio = 0L
    private var abrirVisorAlVolver = false

    private fun abrirVisor() {
        if (visor != null || centro != null) return
        if (panelAbierto) cerrarPanel(enfocar = false)
        val foto = if (hayDifuminado) null else Tema.fotoBorrosa(capa)
        if (hayDifuminado) capa.setRenderEffect(RenderEffect.createBlurEffect(dp(24).toFloat(), dp(24).toFloat(), Shader.TileMode.CLAMP))
        capa.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        val pm = packageManager
        val v = AppsAbiertas(this, foto, AppsAbiertas.Datos(
            // Solo las que siguen instaladas: si no, quedaba en la lista una app ya borrada (un launcher viejo).
            recientes = leerLista(RECIENTES).filter { pkg ->
                pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null
            },
            nombre = { pkg -> try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg } },
            icono = { pkg -> try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null } },
            logo = { pkg -> logoGrande(pkg) },
            abrir = { pkg -> cerrarVisor(); abrir(pkg) },
            informacion = { pkg -> abrirDesdeVisor(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) },
            todas = { abrirDesdeVisor(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) },
            musica = { isla.appActual },
            ultimaMusica = { isla.ultimaApp },
        ))
        visor = v
        (capa.parent as ViewGroup).addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        aplicarFuente(v)
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(200).start()
        v.requestFocus()
    }

    /** Abre una pantalla de los ajustes; al volver con Atrás se vuelve a Apps abiertas. */
    private fun abrirDesdeVisor(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            abrirVisorAlVolver = true
        } catch (e: Exception) {
            Toast.makeText(this, "La tele no deja abrir esa pantalla", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cerrarVisor() {
        val v = visor ?: return
        visor = null
        (v.parent as? ViewGroup)?.removeView(v)
        if (hayDifuminado && !panelAbierto && dialogosAbiertos == 0 && centro == null) capa.setRenderEffect(null)
        capa.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        focoInicial()?.requestFocus()
    }

    /** Logo de una app para mostrar grande: el propio sin fondo, el banner recortado o el ícono. */
    private fun logoGrande(pkg: String): Drawable? = try {
        logosCabecera[pkg]?.let { getDrawable(it) }
            ?: packageManager.getApplicationBanner(pkg)?.let { b -> recortarLogo(b, dp(ALTO_CABECERA) * 32 / 9, dp(ALTO_CABECERA) * 2)?.let { BitmapDrawable(resources, it) } }
            ?: packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
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

    /** AMOLED: la tarjeta elegida sube un poco y su sombra toma el color del tema (Android 9 en adelante). */
    private fun levantar(v: View, foco: Boolean) {
        if (Build.VERSION.SDK_INT >= 28) {
            v.outlineSpotShadowColor = if (foco) Tema.color else Color.BLACK
            v.outlineAmbientShadowColor = if (foco) Tema.color else Color.BLACK
        }
        val y0 = v.translationY
        val destino = if (foco) -dp(6).toFloat() else 0f
        animar(v, 240, ::salidaSuave) { c -> v.translationY = y0 + (destino - y0) * c }
    }

    /** El dibujo del ícono sin su cuadrado de fondo: en los íconos adaptables, solo la capa de adelante. */
    private fun simboloSuelto(icono: Drawable, lado: Int): Drawable {
        if (Build.VERSION.SDK_INT < 26 || icono !is android.graphics.drawable.AdaptiveIconDrawable) return icono
        val frente = icono.foreground ?: return icono
        return try {
            val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
            // La capa mide 108 y lo visible son los 72 del medio: se agranda 1,5 veces y se centra.
            frente.setBounds(-lado / 4, -lado / 4, lado + lado / 4, lado + lado / 4)
            frente.draw(Canvas(bmp))
            BitmapDrawable(resources, bmp)
        } catch (e: Exception) {
            icono
        }
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
        val raiz = FrameLayout(this).apply { setBackgroundColor(Tema.fondo) }
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
            // Temas neón: al bajar, la fila de arriba se desvanece bajo la cabecera en vez de quedar cortada de golpe.
            if (Tema.neon) {
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(40))
            }
            addView(contenido)
        }
        columna.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        capa.addView(columna, llenar())

        val info = armarInfo()
        capa.addView(info, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(24)
            marginEnd = dp(MARGEN)
        })
        relojIzquierda?.let { reloj ->
            capa.addView(reloj, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                topMargin = dp(24)
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
            alpha = if (Tema.neon) 0.3f else 0.7f
        }, FrameLayout.LayoutParams(dp(4), dp(52), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(8) })

        isla = IslaMusica(this) { focoInicial()?.requestFocus() }
        isla.alCambiar = {
            musica?.let { m -> if (isla.hayMusica) m.refrescar() else cerrarMusica() }
            if (panelAbierto) pintarMusicaMenu()
            pintarFondo()
        }
        capa.addView(isla, FrameLayout.LayoutParams(dp(IslaMusica.CHICA), dp(38), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(16)
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
            setTextColor(Color.WHITE)
            includeFontPadding = false
            typeface = medium
            // Temas neón: la hora se destaca por el tamaño, en blanco liso y con la misma letra que el resto.
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (Tema.neon) 44f else 26f)
        }
        val fecha = TextClock(this).apply {
            format12Hour = "EEEE d 'de' MMMM"
            format24Hour = "EEEE d 'de' MMMM"
            setTextColor(0xB3FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        // Arriba todo en blanco y gris: el único color de la pantalla es el del foco.
        climaAhora = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(3), 0, 0)
            if (Tema.neon) typeface = medium
        }
        climaDetalle = TextView(this).apply {
            setTextColor(if (Tema.neon) 0x8CFFFFFF.toInt() else 0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        val separador = if (Tema.neon) 0x40FFFFFF else 0x55FFFFFF
        val altoSeparador = if (Tema.neon) 24 else 26
        val aireSeparador = if (Tema.neon) 16 else 14
        // A la izquierda: la hora con la fecha al lado. A la derecha: el clima junto al logo.
        relojIzquierda = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(hora)
            addView(View(this@MainActivity).apply { setBackgroundColor(separador) },
                LinearLayout.LayoutParams(dp(1), dp(altoSeparador)).apply { marginStart = dp(aireSeparador); marginEnd = dp(aireSeparador) })
            addView(fecha.apply {
                setTextColor(if (Tema.neon) 0x99FFFFFF.toInt() else 0xB3FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
        }
        val clima = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            // Con tope de ancho: un clima con texto largo no se mete debajo de la isla.
            addView(climaAhora.apply {
                setPadding(0, 0, 0, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (Tema.neon) 12f else 14f)
                maxWidth = dp(220)
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
            addView(View(this@MainActivity).apply { setBackgroundColor(separador) },
                LinearLayout.LayoutParams(dp(1), dp(altoSeparador)).apply { marginStart = dp(aireSeparador); marginEnd = dp(aireSeparador) })
            // Temas neón: el logo va chico y a media luz, para que no le gane a la hora.
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.logo_chico)
                contentDescription = getString(R.string.app_name)
                adjustViewBounds = true
                if (Tema.neon) alpha = 0.5f
            }, LinearLayout.LayoutParams(dp(if (formal) 92 else if (Tema.neon) 40 else 68), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun pintarClima(clima: Pair<String, String>) {
        // Temas neón: sin el emoji del tiempo (era el único dibujo de colores arriba); el texto ya dice cómo está.
        climaAhora.text = if (Tema.neon) clima.first.dropWhile { !it.isDigit() && it != '-' }.trim().ifBlank { clima.first } else clima.first
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

    private val vidrioPanel by lazy { Vidrio(dp(20).toFloat(), Tema.rellenoPanel(hayDifuminado)) }

    /**
     * Equipos que no difuminan (Fire Stick): el vidrio lleva una foto borrosa de lo que tiene atrás.
     * [fuente] es lo que se fotografía; [dondeEsta] da la posición de la pieza en la pantalla.
     */
    private fun esmerilar(vidrio: Vidrio, fuente: View, dondeEsta: () -> Point) {
        if (!Tema.neon || hayDifuminado) return
        vidrio.sinFoto = Tema.PANEL_SIN_FOTO
        vidrio.foto = Tema.fotoBorrosa(fuente)
        vidrio.posicion = dondeEsta
        vidrio.invalidateSelf()
    }

    private fun armarPanel(): LinearLayout {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(10), dp(6), dp(8))
            // Vidrio como el de la app del celular: transparente, con lo de atrás difuminado.
            background = vidrioPanel
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
        ponerTapa(tapaMenu, isla.tapaActual, dp(10))
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
        // Neón: al enfocarlo el vidrio se enciende con un anillo del degradado. Clásico: círculo crema.
        val enfocado: Drawable = if (Tema.neon) Vidrio(0f, 0x40FFFFFF, redondo = true, grosorAnillo = dp(2).toFloat(), anillo = Tema.degradado)
        else GradientDrawable().apply {
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
            imagen.imageTintList = ColorStateList.valueOf(if (foco && !Tema.neon) 0xFF111114.toInt() else Color.WHITE)
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
    /** «System X-Ray» de Amazon; null si esta tele no lo tiene. */
    private fun rayosX(): Intent? = Intent().setClassName("com.amazon.ssm", "com.amazon.ssm.ControlPanel")
        .takeIf { packageManager.resolveActivity(it, 0)?.activityInfo?.exported == true }

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
        item.vista.background = when {
            foco && Tema.neon -> Vidrio(dp(9).toFloat(), 0x2EFFFFFF, grosorAnillo = dp(2).toFloat(), anillo = Tema.degradado)
            foco || actual -> GradientDrawable().apply {
                setColor(if (foco) CREMA else 0x1FFFFFFF)
                cornerRadius = dp(9).toFloat()
            }
            else -> null
        }
        val color = if (foco && !Tema.neon) 0xFF111114.toInt() else Color.WHITE
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
        // Posición sin contar el deslizamiento de entrada: la foto queda quieta detrás del panel.
        esmerilar(vidrioPanel, capa) { Point(panel.left, panel.top) }
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
                agregarRecomendacion()
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
                    nombreApp.visibility = View.VISIBLE
                    subtitulo.visibility = View.VISIBLE
                    // Algo que se pueda elegir con el control: si no, el foco queda en ningún lado.
                    contenido.addView(botonVolverAInicio(), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(48),
                    ).apply {
                        marginStart = dp(MARGEN)
                        topMargin = dp(12)
                    })
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
                    // Se espera un ratito a que termine de escribir: redibujar todo por cada letra traba el teclado.
                    espera.removeCallbacks(redibujarBusqueda)
                    espera.postDelayed(redibujarBusqueda, 250)
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

    private val redibujarBusqueda = Runnable { if (pagina == Pagina.BUSCAR) pintarResultados() }

    private fun pintarResultados() {
        val caja = resultados ?: return
        caja.removeAllViews()
        val texto = busqueda.trim()
        if (texto.isEmpty()) return
        val lista = visibles().filter { it.label.contains(texto, ignoreCase = true) }
        if (lista.isEmpty()) {
            caja.addView(TextView(this).apply {
                text = "No encontramos ninguna app con «$texto»"
                setTextColor(0xB3FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(MARGEN)
                topMargin = dp(12)
            })
            aplicarFuente(caja)
            return
        }
        lista.chunked(columnas).forEach { agregarFila(null, it, BUSQUEDA, caja) }
    }

    private fun botonVolverAInicio(): View = TextView(this).apply {
        text = "Volver a Inicio"
        tag = "volver_inicio"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        gravity = Gravity.CENTER
        setPadding(dp(26), 0, dp(26), 0)
        isFocusable = true
        isClickable = true
        val reposo = GradientDrawable().apply {
            setColor(0x22FFFFFF)
            cornerRadius = dp(24).toFloat()
        }
        background = reposo
        setOnFocusChangeListener { v, foco ->
            v.background = when {
                foco && Tema.neon -> Vidrio(dp(24).toFloat(), 0x2EFFFFFF, grosorAnillo = dp(2).toFloat(), anillo = Tema.degradado)
                foco -> GradientDrawable().apply {
                    setColor(CREMA)
                    cornerRadius = dp(24).toFloat()
                }
                else -> reposo
            }
            setTextColor(if (foco && !Tema.neon) 0xFF111114.toInt() else Color.WHITE)
        }
        setOnClickListener {
            pagina = Pagina.INICIO
            mostrar(arriba = true)
        }
    }

    private fun buscarPrimera(grupo: ViewGroup): View? {
        for (i in 0 until grupo.childCount) {
            val v = grupo.getChildAt(i)
            if (v.tag is String) return v
            if (v is ViewGroup) buscarPrimera(v)?.let { return it }
        }
        return null
    }

    /** La recomendación de hoy, abajo de Recientes. Si todavía no se buscó, la busca y redibuja al llegar. */
    private fun agregarRecomendacion() {
        if (formal || !Recomendacion.activa(this)) return
        val s = Recomendacion.guardada(this)
        if (s == null) {
            Recomendacion.buscar(this) { if (pagina == Pagina.INICIO && !panelAbierto && Recomendacion.guardada(this) != null) mostrar(foco = ultimaTarjeta) }
            return
        }
        contenido.addView(TextView(this).apply {
            text = "PARA HOY"
            setTextColor(0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.2f
            typeface = medium
            setPadding(dp(MARGEN), dp(16), 0, 0)
        })
        val radio = dp(14).toFloat()
        val tarjeta = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            tag = "reco|hoy"
            setPadding(dp(12), dp(12), dp(24), dp(12))
            background = Vidrio(radio, Tema.estilo.tarjeta)
            foreground = Vidrio(radio, 0, grosorAnillo = dp(3).toFloat(), punto = Tema.color)
            outlineProvider = bordesRedondos(radio)
            clipToOutline = true
            addView(ImageView(this@MainActivity).apply {
                setImageBitmap(s.poster)
                scaleType = ImageView.ScaleType.CENTER_CROP
                outlineProvider = bordesRedondos(dp(8).toFloat())
                clipToOutline = true
                setBackgroundColor(0x22FFFFFF)
            }, LinearLayout.LayoutParams(dp(88), dp(124)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = s.titulo
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    typeface = medium
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(this@MainActivity).apply {
                    text = listOf(if (s.nota > 0) "★ ${s.nota}" else "", s.anio, s.generos, s.canal).filter { it.isNotBlank() }.joinToString("  ·  ")
                    setTextColor(0xB3FFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, dp(4), 0, dp(8))
                })
                addView(TextView(this@MainActivity).apply {
                    text = s.resumen
                    setTextColor(0x8CFFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(20) })
            setOnFocusChangeListener { v, foco ->
                (foreground as? Vidrio)?.anillo = if (foco) Tema.degradado else null
                escalar(v, if (foco) 1.03f else 1f, if (foco) dp(12).toFloat() else 0f)
            }
            // OK abre la app donde se ve, si está instalada.
            setOnClickListener {
                val pkg = CANALES.entries.firstOrNull { s.canal.contains(it.key, ignoreCase = true) }?.value?.firstOrNull { p -> apps.any { it.pkg == p } }
                if (pkg != null) abrir(pkg) else Toast.makeText(this@MainActivity, "Buscala en tus apps: ${s.titulo}", Toast.LENGTH_LONG).show()
            }
        }
        contenido.addView(tarjeta, LinearLayout.LayoutParams(dp(620), dp(148)).apply {
            setMargins(dp(MARGEN), dp(10), dp(MARGEN), dp(8))
        })
    }

    private val CANALES = mapOf(
        "Netflix" to listOf("com.netflix.ninja"),
        "Prime" to listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod"),
        "Max" to listOf("com.wbd.stream", "com.hbo.hbonow"),
        "HBO" to listOf("com.wbd.stream", "com.hbo.hbonow"),
        "Apple" to listOf("com.apple.atve.androidtv.appletv"),
        "Paramount" to listOf("com.cbs.ott", "com.cbs.ca"),
        "YouTube" to listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv"),
    )

    private fun agregarFila(titulo: String?, lista: List<App>, contenedor: String, destino: LinearLayout, porFila: Int = columnas) {
        if (titulo != null) {
            destino.addView(TextView(this).apply {
                if (Tema.neon) {
                    // Neón: título chico, en mayúsculas, separado y en blanco suave. Las apps son lo importante.
                    text = titulo.uppercase()
                    setTextColor(0x99FFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    letterSpacing = 0.2f
                    typeface = medium
                    setPadding(dp(MARGEN), dp(16), 0, 0)
                } else {
                    text = titulo
                    setTextColor(CREMA)
                    alpha = 0.8f
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = medium
                    setPadding(dp(MARGEN), dp(10), 0, 0)
                }
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
            if (Tema.neon) {
                // Temas neón: todas las tarjetas son del mismo vidrio; lo único que cambia es el logo.
                background = Vidrio(radio, Tema.estilo.tarjeta)
                foreground = Vidrio(radio, 0, grosorAnillo = dp(3).toFloat(), punto = Tema.color)
            } else {
                setBackgroundColor(if (app.banner == null) colorDeMarca(app.icono) else colorDeBorde(app.banner))
                // Vidrio: un brillo suave desde arriba y un borde finito, igual para todas las tarjetas.
                foreground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x24FFFFFF, 0x00FFFFFF, 0x00000000)).apply {
                    cornerRadius = radio
                    setStroke(1, 0x2EFFFFFF)
                }
            }
            if (contenedor == OCULTAS) alpha = 0.5f
        }
        // Temas neón: logo suelto sobre el vidrio. Los propios ya vienen sin fondo; a los banners se les saca
        // el fondo liso que traen pintado. Si no se puede, la tarjeta muestra el ícono y el nombre.
        val logoSuelto: Drawable? = if (!Tema.neon) null
        else logosCabecera[app.pkg]?.let { getDrawable(it) }
            ?: app.banner?.let { banner -> recorteDe(app.pkg, banner, ancho, ancho * 9 / 16)?.let { BitmapDrawable(resources, it) } }
        val nombreAdentro = logoSuelto == null && (Tema.neon || app.banner == null)
        if (logoSuelto != null) {
            // Todos los logos ocupan más o menos la misma superficie: uno alto (Apple TV) no se ve más grande que uno largo (Netflix).
            val alto = ancho * 9 / 16
            val (w, h) = medidaOptica(logoSuelto, ancho * 2f / 3, alto / 2f, ancho * alto * 0.2f)
            tarjeta.addView(ImageView(this).apply {
                setImageDrawable(logoSuelto)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(w, h, Gravity.CENTER))
        } else if (app.banner != null && !Tema.neon) {
            tarjeta.addView(ImageView(this).apply {
                setImageDrawable(app.banner)
                scaleType = ImageView.ScaleType.FIT_XY
            }, llenar())
        } else {
            tarjeta.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                // AMOLED: el dibujo de la app suelto, sin el cuadrado de fondo del ícono, y un poco más grande.
                val icono = if (Tema.amoled) minOf(dp(56), ancho * 9 / 16 - dp(40)) else minOf(dp(44), ancho * 9 / 16 - dp(44))
                val imagen = if (Tema.amoled) simboloSuelto(app.icono, icono) else app.icono
                addView(ImageView(this@MainActivity).apply { setImageDrawable(imagen) }, LinearLayout.LayoutParams(icono, icono))
                // Nombres largos ("Configuración de Amazon"): centrados, en hasta dos renglones y sin salirse de la tarjeta.
                addView(TextView(this@MainActivity).apply {
                    text = app.label
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = medium
                    gravity = Gravity.CENTER_HORIZONTAL
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setLineSpacing(0f, 0.95f)
                    setPadding(dp(12), dp(6), dp(12), 0)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, llenar())
        }

        val nombre = TextView(this).apply {
            // Las tarjetas sin logo ya muestran el nombre adentro.
            text = if (nombreAdentro) "" else app.label
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
            // Neón: la tarjeta elegida se marca con un anillo del degradado.
            (tarjeta.foreground as? Vidrio)?.anillo = if (foco) Tema.degradado else null
            escalar(tarjeta, if (foco) 1.1f else 1f, if (foco) dp(18).toFloat() else 0f)
            if (Tema.amoled) levantar(tarjeta, foco)
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
            if (!Tema.neon) fondo.cambiarColor(color)
        }
    }

    private fun verDestacada(app: App, seccion: String) {
        subtitulo.text = seccion
        if (destacadaActual == app.pkg) return
        destacadaActual = app.pkg
        val imagen = app.banner ?: app.icono
        imagenDeFondo = imagen
        pintarFondo()
        val propio = logosCabecera[app.pkg]?.let { getDrawable(it) }
        // Copia propia: si la cabecera y la tarjeta comparten el mismo dibujo, se pelean por el tamaño y los
        // íconos adaptables rehacen su máscara una y otra vez hasta llenar la memoria (pasó en el Fire Stick).
        val grande = if (propio != null) null else try {
            imagenGrande(app)
        } catch (e: OutOfMemoryError) {
            null
        }
        // Temas neón: al banner se le saca el fondo liso, igual que en las tarjetas, y queda el logo suelto.
        val sinFondo = propio ?: if (Tema.neon && app.banner != null) {
            recortarLogo(grande ?: app.banner, dp(ALTO_CABECERA) * 32 / 9, dp(ALTO_CABECERA) * 2)?.let { BitmapDrawable(resources, it) }
        } else null
        // Ziota muestra solo el logo; Solari deja el nombre al lado.
        val conNombre = if (formal) View.VISIBLE else View.GONE
        val medidas = destacada.layoutParams
        if (sinFondo != null) {
            // Logo suelto sobre la franja de color, alineado abajo a la izquierda.
            val logo = sinFondo
            if (Tema.neon) {
                // Mismo "peso" para todos los logos, como en las tarjetas.
                val (w, h) = medidaOptica(logo, dp(ANCHO_CABECERA).toFloat(), dp(ALTO_CABECERA).toFloat(), dp(ANCHO_CABECERA) * dp(ALTO_CABECERA) * 0.55f)
                medidas.width = w
                medidas.height = h
            } else {
                val escala = minOf(dp(ANCHO_CABECERA).toFloat() / logo.intrinsicWidth, dp(ALTO_CABECERA).toFloat() / logo.intrinsicHeight)
                medidas.width = (logo.intrinsicWidth * escala).toInt()
                medidas.height = (logo.intrinsicHeight * escala).toInt()
            }
            destacada.setImageDrawable(logo)
            destacada.background = null
            destacada.clipToOutline = false
            destacada.setPadding(0, 0, 0, 0)
            nombreApp.visibility = View.GONE
            subtitulo.visibility = View.GONE
        } else if (Tema.neon) {
            // Temas neón, apps sin logo recortable: el ícono suelto, como un logo más y igual que en su tarjeta
            // (antes quedaba chiquito en un cuadro, o el banner con su propio fondo).
            medidas.width = dp(ALTO_CABECERA)
            medidas.height = dp(ALTO_CABECERA)
            try {
                destacada.setImageDrawable(copiaDe(app.icono))
            } catch (e: OutOfMemoryError) {
                destacada.setImageDrawable(null)
            }
            destacada.background = null
            destacada.clipToOutline = false
            destacada.setPadding(0, 0, 0, 0)
            nombreApp.visibility = View.GONE
            subtitulo.visibility = View.GONE
        } else {
            medidas.width = dp(ALTO_CABECERA) * 16 / 9
            medidas.height = dp(ALTO_CABECERA)
            try {
                destacada.setImageDrawable(grande ?: copiaDe(imagen))
            } catch (e: OutOfMemoryError) {
                destacada.setImageDrawable(null) // mejor sin imagen que cerrar el launcher
            }
            destacada.setBackgroundColor(if (app.banner == null) colorDeMarca(imagen) else colorDeBorde(imagen))
            destacada.clipToOutline = true
            val relleno = if (app.banner == null) dp(28) else 0
            destacada.setPadding(relleno, relleno, relleno, relleno)
            nombreApp.visibility = conNombre
            subtitulo.visibility = conNombre
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

    /** Logos de las tarjetas ya recortados, por app (null: no se pudo y va con ícono y nombre). Se sueltan en onStop. */
    private val recortes = HashMap<String, Bitmap?>()

    private fun recorteDe(pkg: String, banner: Drawable, ancho: Int, alto: Int): Bitmap? {
        if (recortes.containsKey(pkg)) return recortes[pkg]
        return recortarLogo(banner, ancho, alto).also { recortes[pkg] = it }
    }

    /**
     * Saca el fondo liso que muchas apps traen pintado en su banner (ClipBox en negro, por ejemplo) y deja
     * el logo solo, recortado a su tamaño. Devuelve null si el fondo no es liso (una foto, un degradado) o si
     * el logo que queda es oscuro y no se vería sobre el vidrio.
     */
    private fun recortarLogo(d: Drawable, ancho: Int, alto: Int): Bitmap? {
        if (ancho < 8 || alto < 8) return null
        val px = IntArray(ancho * alto)
        try {
            val bmp = aBitmap(d, ancho, alto)
            bmp.getPixels(px, 0, ancho, 0, 0, ancho, alto)
            bmp.recycle()
        } catch (e: OutOfMemoryError) {
            return null
        }
        // El fondo es el color que más se repite en el borde.
        val borde = IntArray(2 * (ancho + alto))
        var n = 0
        for (x in 0 until ancho) {
            borde[n++] = px[x]
            borde[n++] = px[(alto - 1) * ancho + x]
        }
        for (y in 0 until alto) {
            borde[n++] = px[y * ancho]
            borde[n++] = px[y * ancho + ancho - 1]
        }
        val transparente = borde.count { Color.alpha(it) < 128 } > borde.size * 0.8f
        var fondo = 0
        if (!transparente) {
            val cuenta = HashMap<Int, Int>()
            for (p in borde) if (Color.alpha(p) >= 128) cuenta[p and 0xE0E0E0] = (cuenta[p and 0xE0E0E0] ?: 0) + 1
            val grupo = cuenta.maxByOrNull { it.value }?.key ?: return null
            var r = 0L; var g = 0L; var b = 0L; var k = 0
            for (p in borde) if (Color.alpha(p) >= 128 && (p and 0xE0E0E0) == grupo) {
                r += Color.red(p); g += Color.green(p); b += Color.blue(p); k++
            }
            fondo = Color.rgb((r / k).toInt(), (g / k).toInt(), (b / k).toInt())
            // Fondo no liso: mejor no tocarlo.
            if (borde.count { Color.alpha(it) >= 128 && distancia(it, fondo) < 48 } < borde.size * 0.85f) return null
        }
        val fr = Color.red(fondo); val fg = Color.green(fondo); val fb = Color.blue(fondo)
        var izq = ancho; var der = -1; var arriba = alto; var abajo = -1
        var luz = 0.0; var peso = 0.0
        for (y in 0 until alto) for (x in 0 until ancho) {
            val i = y * ancho + x
            val p = px[i]
            var a = Color.alpha(p) / 255f
            var r = Color.red(p); var g = Color.green(p); var b = Color.blue(p)
            if (!transparente) {
                // Cuanto más se parece al fondo, más transparente; en los bordes del logo se le quita el tinte del fondo.
                val f = ((distancia(p, fondo) - 40) / 80f).coerceIn(0f, 1f)
                if (f in 0.05f..0.999f) {
                    r = ((r - (1 - f) * fr) / f).toInt().coerceIn(0, 255)
                    g = ((g - (1 - f) * fg) / f).toInt().coerceIn(0, 255)
                    b = ((b - (1 - f) * fb) / f).toInt().coerceIn(0, 255)
                }
                a *= f
            }
            px[i] = Color.argb((a * 255).toInt(), r, g, b)
            if (a > 0.15f) {
                if (x < izq) izq = x
                if (x > der) der = x
                if (y < arriba) arriba = y
                if (y > abajo) abajo = y
                luz += a * (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                peso += a
            }
        }
        if (der < 0 || peso < ancho * alto * 0.02) return null
        // Un logo oscuro (letras negras de un banner blanco) no se vería sobre el vidrio.
        if (luz / peso < 0.3) return null
        val w = der - izq + 1
        val h = abajo - arriba + 1
        return try {
            Bitmap.createBitmap(px, arriba * ancho + izq, ancho, w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /**
     * Medida de un logo suelto según su superficie y no según la caja: así todos pesan lo mismo a la vista
     * y uno alto no queda enorme al lado de uno largo. Nunca pasa de [maxAncho] x [maxAlto].
     */
    private fun medidaOptica(d: Drawable, maxAncho: Float, maxAlto: Float, superficie: Float): Pair<Int, Int> {
        val w = d.intrinsicWidth.toFloat()
        val h = d.intrinsicHeight.toFloat()
        if (w <= 0f || h <= 0f) return maxAncho.toInt() to maxAlto.toInt()
        val escala = minOf(Math.sqrt((superficie / (w * h)).toDouble()).toFloat(), maxAncho / w, maxAlto / h)
        return (w * escala).toInt() to (h * escala).toInt()
    }

    private fun distancia(a: Int, b: Int) =
        Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b)) + Math.abs(Color.blue(a) - Color.blue(b))

    private fun aBitmap(d: Drawable, ancho: Int, alto: Int): Bitmap {
        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        // Copia del drawable: cambiarle el tamaño al original rompería la tarjeta que lo está mostrando.
        val copia = d.constantState?.newDrawable(resources)?.mutate() ?: d
        copia.setBounds(0, 0, ancho, alto)
        copia.draw(Canvas(bmp))
        return bmp
    }

    // ---------- Optimizar ----------

    private fun protegidas(): Set<String> = prefs.getStringSet(AppsAbiertas.CLAVE_PROTEGIDAS, emptySet()) ?: emptySet()

    private fun memoriaLibreMb(): Long {
        val info = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    /** Cierra lo que otras apps tienen en segundo plano; Android no deja tocar lo que está en uso. */
    private fun optimizar() {
        // En Fire TV ese pedido se rechaza siempre: ahí se cierran desde Apps abiertas.
        if (AppsAbiertas.SOLO_DESDE_AJUSTES) {
            abrirVisor()
            return
        }
        val antes = memoriaLibreMb()
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (ai in packageManager.getInstalledApplications(0)) {
            val pkg = ai.packageName
            if (pkg == packageName || pkg in AppsAbiertas.NUNCA_MOSTRAR || pkg == isla.appActual || pkg in protegidas()) continue
            am.killBackgroundProcesses(pkg)
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
        // La señal se manda siempre: si la PC ya estaba prendida no le hace nada,
        // y si el estado se equivocó, igual la prende.
        Toast.makeText(
            this,
            if (yaPrendida) "${pc.nombre} parece prendida; igual le mando la señal" else "Prendiendo ${pc.nombre}…",
            Toast.LENGTH_SHORT,
        ).show()
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
            (if (AppsAbiertas.SOLO_DESDE_AJUSTES) "Ver apps abiertas (${memoriaLibreMb()} MB libres)" else "Optimizar ahora (${memoriaLibreMb()} MB libres)") to { optimizar() },
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

    // ---------- Ajustes ----------

    private var ajustes: AjustesPantalla? = null

    private fun menuAjustes(enTemas: Boolean = false) {
        if (ajustes != null) return
        cerrarCentro()
        if (panelAbierto) cerrarPanel(enfocar = false)
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val azul = 0xFF3B82F6.toInt()
        val violeta = 0xFF8B5CF6.toInt()
        val naranja = 0xFFF59E0B.toInt()
        val celeste = 0xFF0EA5E9.toInt()
        val gris = 0xFF6B7280.toInt()
        val rojo = 0xFFEF4444.toInt()
        val pasarConfiguracion = AjustesPantalla.Opcion(R.drawable.ic_apps, azul, "Pasar tu configuración a otra tele",
            "Guarda el orden de las apps, las categorías, las ocultas, el tema y tus PCs. Después la app del celular la copia a otra tele con Ziota.",
            { "Guardar" }, null) {
            Toast.makeText(this, if (Configuracion.guardar(this)) "Configuración guardada" else "No se pudo guardar", Toast.LENGTH_SHORT).show()
        }
        lateinit var seccionZiota: AjustesPantalla.Seccion
        val temas = Tema.disponibles(formal)
        // Elegir un tema rearma la pantalla, pero vuelve a esta misma lista para poder seguir probando.
        val seccionTemas = AjustesPantalla.Seccion(R.drawable.ic_estrella, violeta, "Tema", "", temas.map { t ->
            AjustesPantalla.Opcion(R.drawable.ic_estrella, Tema.colorDe(t), t.nombre, Tema.descripcion(t),
                { if (Tema.estilo == t) "Elegido" else "" }, null) {
                if (Tema.estilo != t) {
                    Tema.elegir(this, t)
                    reabrirTemas = true
                    recreate()
                }
            }
        }, padre = { seccionZiota }, elegida = { temas.indexOf(Tema.estilo) })
        val secciones = listOf(
            AjustesPantalla.Seccion(R.drawable.ic_estrella, violeta, "Ziota", "El tema, las animaciones, el botón Inicio y el protector de pantalla.", listOf(
                AjustesPantalla.Opcion(R.drawable.ic_estrella, violeta, "Tema",
                    "Cambia el fondo y el color de lo elegido. OK muestra la lista para elegir.",
                    { Tema.estilo.nombre + "  ›" }, null) { ajustes?.mostrarSeccion(seccionTemas) },
                AjustesPantalla.Opcion(R.drawable.ic_rayo, naranja, "Animaciones",
                    "Lo rápido que se mueven los menús y las apps de toda la tele. Rápida hace que todo se sienta más ágil.",
                    { if (escalaAnimacion() >= 1f) "Normal" else "Rápida" }, null) {
                    val siguiente = if (escalaAnimacion() >= 1f) 0.5f else 1f
                    conPermiso { for (clave in escalasAnimacion) Settings.Global.putFloat(contentResolver, clave, siguiente) }
                },
                AjustesPantalla.Opcion(R.drawable.ic_inicio, azul, "Abrir Ziota con Inicio",
                    "En el Fire Stick, el botón Inicio abre la pantalla de Amazon. Prendido, Inicio te trae siempre a Ziota.",
                    { "" }, { servicioInicioActivo() }) {
                    if (tienePermisoAjustes()) {
                        val activar = !servicioInicioActivo()
                        prefs.edit().putBoolean("boton_inicio", activar).apply()
                        conPermiso { cambiarServicioInicio(activar) }
                    } else {
                        abrirIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                },
                AjustesPantalla.Opcion(R.drawable.ic_estrella, celeste, "Protector de pantalla",
                    "Cuando la tele queda quieta un rato, muestra el logo de Ziota en vez del protector de la tele.",
                    { "" }, { protectorActivo() }) { conPermiso { cambiarProtector(!protectorActivo()) } },
            )),
            AjustesPantalla.Seccion(R.drawable.ic_apps, gris, "Apps", "Las apps ocultas y el orden de las categorías.", listOf(
                AjustesPantalla.Opcion(R.drawable.ic_ocultar, gris, "Apps ocultas",
                    "Las apps que escondiste del inicio. OK las vuelve a mostrar todas.",
                    { ocultas().size.toString() }, null) { guardarLista("ocultas", emptyList()); refrescar() },
                AjustesPantalla.Opcion(R.drawable.ic_mover, gris, "Restablecer orden y categorías",
                    "Vuelve a poner las apps en el orden y las categorías de fábrica. Las ocultas siguen ocultas.",
                    { "" }, null) {
                    val editor = prefs.edit()
                    for (clave in prefs.all.keys) {
                        if (clave.startsWith("cat_") || Categoria.values().any { it.name == clave }) editor.remove(clave)
                    }
                    editor.commit()
                    refrescar()
                    Toast.makeText(this, "Listo: orden de fábrica", Toast.LENGTH_SHORT).show()
                },
            )),
            AjustesPantalla.Seccion(R.drawable.ic_pelis, naranja, "La recomendación de hoy", "La serie del día: si se muestra, el género, la nota y los años.", listOf(
                AjustesPantalla.Opcion(R.drawable.ic_estrella, naranja, "Mostrar en el inicio",
                    "Una serie por día abajo de Recientes, elegida a tu gusto. Sale de TVmaze, que es gratis y solo tiene series.",
                    { "" }, { Recomendacion.activa(this) }) {
                    Recomendacion.cambiar(this) { it.putBoolean("reco_activa", !Recomendacion.activa(this)) }
                    refrescar()
                },
                AjustesPantalla.Opcion(R.drawable.ic_pelis, naranja, "Género", "Qué tipo de serie te recomienda. Todos no pone límite.",
                    { Recomendacion.nombreGenero(this) }, null) {
                    val lista = Recomendacion.GENEROS
                    val i = (lista.indexOfFirst { it.first == Recomendacion.genero(this) } + 1) % lista.size
                    Recomendacion.cambiar(this) { it.putString("reco_genero", lista[i].first) }
                },
                AjustesPantalla.Opcion(R.drawable.ic_estrella, naranja, "Nota mínima", "Solo series con esa nota o más en TVmaze (de 1 a 10).",
                    { "★ " + Recomendacion.nota(this) }, null) {
                    val lista = Recomendacion.NOTAS
                    val i = (lista.indexOf(Recomendacion.nota(this)) + 1) % lista.size
                    Recomendacion.cambiar(this) { it.putFloat("reco_nota", lista[i].toFloat()) }
                },
                AjustesPantalla.Opcion(R.drawable.ic_info, naranja, "Años", "Desde qué año se estrenó. Todos incluye las viejas.",
                    { Recomendacion.desde(this).let { if (it == 0) "Todos" else "Desde $it" } }, null) {
                    val lista = Recomendacion.ANIOS
                    val i = (lista.indexOf(Recomendacion.desde(this)) + 1) % lista.size
                    Recomendacion.cambiar(this) { it.putInt("reco_desde", lista[i]) }
                },
                AjustesPantalla.Opcion(R.drawable.ic_buscar, naranja, "Buscar otra", "Cambia la de hoy por otra con los mismos gustos.",
                    { Recomendacion.estado(this) }, null) {
                    Recomendacion.cambiar(this) { it.putInt("reco_salto", getSharedPreferences("launcher", MODE_PRIVATE).getInt("reco_salto", 0) + 1) }
                    Recomendacion.buscar(this) { ajustes?.refrescar(); refrescar() }
                },
            )),
            AjustesPantalla.Seccion(R.drawable.ic_config, celeste, "Esta tele", "Los ajustes del Fire Stick o del Google TV, pasar tu configuración a otra tele y los informes de problemas.", listOfNotNull(
                AjustesPantalla.Opcion(R.drawable.ic_config, gris, "Ajustes de la tele",
                    "Abre los ajustes del Fire Stick o del Google TV: red, pantalla, sonido y cuentas.",
                    { "" }, null) { abrirAjustesTv() },
                pasarConfiguracion,
                // Solo aparece donde existe (Fire TV).
                rayosX()?.let { intent ->
                    AjustesPantalla.Opcion(R.drawable.ic_memoria, celeste, "Rayos X del sistema",
                        "Una ventanita de Amazon con el uso del procesador, la memoria y la red, arriba de cualquier app. Al entrar puede aparecer sola. Apagala ahí mismo, en X-Ray sistema, porque también consume.",
                        { "Abrir" }, null) { abrirIntent(intent) }
                },
                AjustesPantalla.Opcion(R.drawable.ic_herramientas, rojo, "Informes de problemas",
                    "Lo que falló últimamente, guardado en esta tele. La app del celular lo puede leer para contar qué pasó. OK los borra.",
                    {
                        when (val n = Informes.lista(this).size) {
                            0 -> "Ninguno"
                            1 -> "1 error"
                            else -> "$n errores"
                        }
                    }, null) {
                    if (Informes.lista(this).isNotEmpty()) {
                        Informes.borrar(this)
                        Toast.makeText(this, "Informes borrados", Toast.LENGTH_SHORT).show()
                    }
                },
            )),
            AjustesPantalla.Seccion(R.drawable.ic_info, azul, "Sobre Ziota", "Qué versión tenés y cómo se actualiza.", listOf(
                AjustesPantalla.Opcion(R.drawable.ic_info, azul, "Versión",
                    "Ziota se actualiza desde la app Shikitari del celular, que instala la última versión en cada tele.",
                    { version ?: "" }, null) {},
            )),
        )
        val foto = if (hayDifuminado) null else Tema.fotoBorrosa(capa)
        if (hayDifuminado) capa.setRenderEffect(RenderEffect.createBlurEffect(dp(24).toFloat(), dp(24).toFloat(), Shader.TileMode.CLAMP))
        capa.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        seccionZiota = secciones.first()
        val a = AjustesPantalla(this, foto, secciones)
        ajustes = a
        if (enTemas) a.mostrarSeccion(seccionTemas)
        (capa.parent as ViewGroup).addView(a, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        aplicarFuente(a)
        a.alpha = 0f
        a.animate().alpha(1f).setDuration(200).start()
        a.post { a.enfocar() }
    }

    private fun cerrarAjustes() {
        val a = ajustes ?: return
        ajustes = null
        (a.parent as? ViewGroup)?.removeView(a)
        if (hayDifuminado && !panelAbierto && dialogosAbiertos == 0 && centro == null) capa.setRenderEffect(null)
        capa.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        focoInicial()?.requestFocus()
    }

    /** La app del celular puede pasar una configuración guardada en otra tele (extra "configuracion"). */
    private fun recibirConfiguracion(intent: Intent?) {
        val texto = intent?.getStringExtra("configuracion") ?: return
        intent.removeExtra("configuracion")
        if (Configuracion.cargar(this, texto)) {
            Toast.makeText(this, "Configuración cargada", Toast.LENGTH_SHORT).show()
            recreate()
        }
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
        // En Fire TV se cierra desde "Información de la app" (Forzar detención).
        if (!AppsAbiertas.SOLO_DESDE_AJUSTES) opciones += "Cerrar en segundo plano" to {
            when {
                app.pkg == isla.appActual -> Toast.makeText(this, "${app.label} está con música: no se cierra", Toast.LENGTH_SHORT).show()
                !AppsAbiertas.sePuedeCerrar(this, app.pkg) -> Toast.makeText(this, "${app.label} no se puede cerrar", Toast.LENGTH_SHORT).show()
                else -> {
                    (getSystemService(ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(app.pkg)
                    Toast.makeText(this, "Se pidió cerrar ${app.label}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        opciones += "Optimizar" to { menuOptimizar() }
        opciones += (if (AppsAbiertas.SOLO_DESDE_AJUSTES) "Cerrar o ver información" else "Información de la app") to {
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val opcion = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(10), 0)
                isFocusable = true
                isClickable = true
                if (dibujo != null) addView(imagen, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(12) })
                addView(etiqueta)
                setOnFocusChangeListener { v, foco ->
                    v.background = when {
                        foco && Tema.neon -> Vidrio(dp(9).toFloat(), 0x2EFFFFFF, grosorAnillo = dp(2).toFloat(), anillo = Tema.degradado)
                        foco -> GradientDrawable().apply {
                            setColor(CREMA)
                            cornerRadius = dp(9).toFloat()
                        }
                        else -> null
                    }
                    val color = if (foco && !Tema.neon) 0xFF111114.toInt() else Color.WHITE
                    etiqueta.setTextColor(color)
                    imagen.imageTintList = ColorStateList.valueOf(color)
                }
                setOnClickListener {
                    dialogo.dismiss()
                    accion()
                }
            }
            if (primera == null) primera = opcion
            caja.addView(opcion, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        }

        aplicarFuente(caja)
        val vidrio = Vidrio(dp(20).toFloat(), Tema.rellenoPanel(hayDifuminado))
        val marco = ScrollView(this).apply {
            background = vidrio
            clipToOutline = true
            addView(caja)
        }
        // La foto es de toda la pantalla de atrás (con el menú lateral, si está abierto).
        esmerilar(vidrio, capa.parent as View) { IntArray(2).let { l -> marco.getLocationOnScreen(l); Point(l[0], l[1]) } }
        dialogo.setContentView(marco, ViewGroup.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT))
        dialogo.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.25f)
        }
        // Un menú puede abrir otro: el difuminado se saca recién cuando se cerró el último.
        dialogosAbiertos++
        if (hayDifuminado) capa.setRenderEffect(RenderEffect.createBlurEffect(dp(12).toFloat(), dp(12).toFloat(), Shader.TileMode.CLAMP))
        dialogo.setOnDismissListener {
            dialogosAbiertos--
            if (hayDifuminado && dialogosAbiertos == 0 && !panelAbierto && centro == null) capa.setRenderEffect(null)
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
        AppsAbiertas.seAbrio(this, pkg)
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
        /** Si Ziota está en pantalla ahora (lo mira el servicio de Inicio). */
        @Volatile var adelante = false
        /** Se acaba de cambiar el tema desde Ajustes: al rearmar la pantalla se vuelve a esa lista. */
        private var reabrirTemas = false
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
