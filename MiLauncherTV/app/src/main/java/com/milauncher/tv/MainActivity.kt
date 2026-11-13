package com.milauncher.tv

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * Launcher liviano para Android TV con estética japonesa: barra superior con menú de pincelada,
 * ilustración de portada, favoritas y filas por categoría. Se navega con el D-pad.
 * OK abre la app; mantener OK apretado abre un menú (favoritas, mover, categoría, ocultar, etc.).
 */
class MainActivity : Activity() {

    private enum class Categoria(val titulo: String, val icono: Int) {
        STREAMING("Streaming", R.drawable.ic_streaming),
        PELIS("Pelis y series", R.drawable.ic_pelis),
        MUSICA("Música", R.drawable.ic_musica),
        JUEGOS("Juegos", R.drawable.ic_juegos),
        HERRAMIENTAS("Herramientas y más", R.drawable.ic_herramientas),
    }

    /** Páginas del menú superior. Las que tienen categoría muestran solo esa categoría. */
    private enum class Pagina(val nombre: String, val categoria: Categoria?) {
        INICIO("Inicio", null),
        STREAMING("Streaming", Categoria.STREAMING),
        PELIS("Pelis", Categoria.PELIS),
        JUEGOS("Juegos", Categoria.JUEGOS),
        TODAS("Apps", null),
        AJUSTES("Ajustes", null),
    }

    private class App(
        val pkg: String,
        val label: String,
        val banner: Drawable?,
        val icono: Drawable,
        val sistema: Boolean,
        val categoriaAndroid: Int,
    )

    private class ItemMenu(val vista: LinearLayout, val icono: ImageView?, val texto: TextView?, val pagina: Pagina?)

    // Colores del tema elegido en Ajustes (claro/oscuro y color de acento). Cambiarlo recrea la pantalla.
    private val tema by lazy { Tema.de(this) }
    private val washi get() = tema.fondo
    private val tinta get() = tema.tinta
    private val tintaSuave get() = tema.tintaSuave
    private val rojo get() = tema.acento

    // Favoritas iniciales (solo las que estén instaladas), la primera vez que se abre.
    private val favoritasIniciales = listOf(
        "com.google.android.youtube.tv",
        "com.amazon.firetv.youtube",
        "com.wbd.stream",
        "com.hbo.hbonow",
        "com.disney.disneyplus",
        "com.amazon.avod",
        "com.amazon.amazonvideo.livingroom",
        "com.apple.atve.androidtv.appletv",
        "com.cbs.ca",
        "tv.twitch.android.app",
    )

    // Categoría sugerida para apps conocidas; el resto se deduce de lo que declara la app.
    private val categoriasConocidas = mapOf(
        "com.wbd.stream" to Categoria.STREAMING,
        "com.hbo.hbonow" to Categoria.STREAMING,
        "com.amazon.firetv.youtube" to Categoria.STREAMING,
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
        "com.google.android.videos" to Categoria.STREAMING,
        "tv.pluto.android" to Categoria.STREAMING,
        "tv.mxlmovies.app" to Categoria.PELIS,
        "com.android.mgstv" to Categoria.PELIS,
        "com.stremio.one" to Categoria.PELIS,
        "org.xbmc.kodi" to Categoria.PELIS,
        "com.plexapp.android" to Categoria.PELIS,
        "com.google.android.youtube.tv" to Categoria.STREAMING,
        "tv.twitch.android.app" to Categoria.STREAMING,
        "com.kick.mobile" to Categoria.STREAMING,
        "com.github.andreyasadchy.xtra" to Categoria.STREAMING,
        "com.google.android.youtube.tvmusic" to Categoria.MUSICA,
        "com.spotify.tv.android" to Categoria.MUSICA,
        "com.limelight" to Categoria.JUEGOS,
        "com.valvesoftware.steamlink" to Categoria.JUEGOS,
        "com.nvidia.geforcenow" to Categoria.JUEGOS,
    )

    private val columnas get() = if (prefs.getBoolean("apps_grandes", false)) 5 else 6
    private val margenLateral = 32
    private val altoPortada = 290
    private val FAV = "favoritas"
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private lateinit var scroll: ScrollView
    private lateinit var contenido: LinearLayout
    private val itemsMenu = mutableListOf<ItemMenu>()
    private var pagina = Pagina.INICIO
    private var apps: List<App> = emptyList()
    private var ultimaApp: String? = null

    /** Tile que se está moviendo: contenedor (FAV o nombre de categoría) y paquete. */
    private var moviendo: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tras cambiar el tema se recrea la pantalla: volver a la misma página y fila.
        savedInstanceState?.getString("pagina")?.let { p -> Pagina.values().find { it.name == p }?.let { pagina = it } }
        BotonInicioService.iniciarSiCorresponde(this)
        val focoGuardado = savedInstanceState?.getString("foco")
        setContentView(armarPantalla())
        if (focoGuardado != null) {
            window.decorView.post { mostrar(foco = { it.tag == focoGuardado }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Al apretar Home, volver siempre a Inicio con la portada arriba (onResume redibuja).
        if (intent.getBooleanExtra(EXTRA_DESDE_HOME, false) || intent.hasCategory(Intent.CATEGORY_HOME)) {
            pagina = Pagina.INICIO
            ultimaApp = null
            moviendo = null
            actualizarMenu()
        }
    }

    override fun onResume() {
        super.onResume()
        // Se recarga cada vez para reflejar apps instaladas o desinstaladas.
        asegurarServicioInicio()
        cargarApps()
        mostrar(foco = ultimaApp?.let { p -> { v: View -> (v.tag as? String)?.endsWith("|$p") == true } }, arriba = true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val (contenedor, pkg) = moviendo ?: return subirAlMenu(event) || super.dispatchKeyEvent(event)
        // Modo mover: las flechas cambian el lugar de la app y OK o Atrás terminan.
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> mover(contenedor, pkg, -1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> mover(contenedor, pkg, 1)
                KeyEvent.KEYCODE_DPAD_UP -> mover(contenedor, pkg, if (contenedor == FAV || pagina == Pagina.INICIO) -1 else -columnas)
                KeyEvent.KEYCODE_DPAD_DOWN -> mover(contenedor, pkg, if (contenedor == FAV || pagina == Pagina.INICIO) 1 else columnas)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> {
                    moviendo = null
                    mostrar(foco = tagIgual(contenedor, pkg))
                    Toast.makeText(this, "Lugar guardado", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    /** Al subir desde las apps hacia la barra, el foco va a la página actual y no a la más cercana. */
    private fun subirAlMenu(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.keyCode != KeyEvent.KEYCODE_DPAD_UP) return false
        val siguiente = currentFocus?.focusSearch(View.FOCUS_UP) ?: return false
        if (itemsMenu.none { it.vista == siguiente } || itemsMenu.any { it.vista == currentFocus }) return false
        val actual = itemsMenu.find { it.pagina == pagina } ?: return false
        actual.vista.requestFocus()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Atrás no cierra el launcher: desde otra página vuelve a Inicio, y en Inicio no hace nada.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (pagina != Pagina.INICIO) {
                pagina = Pagina.INICIO
                actualizarMenu()
                mostrar(arriba = true)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Armado de la pantalla ----------

    private fun armarPantalla(): View {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(washi)
        }
        raiz.addView(armarBarra(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)))

        contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(margenLateral), dp(4), dp(margenLateral), dp(28))
            clipChildren = false
            clipToPadding = false
        }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(contenido)
        }
        raiz.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return raiz
    }

    private fun armarBarra(): View {
        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(margenLateral), dp(6), dp(margenLateral), 0)
            clipChildren = false
        }

        // Logo de Shikitari TV (círculo de los gatos + nombre en pincel), en versión clara u oscura.
        barra.addView(ImageView(this).apply {
            setImageResource(tema.logoBarra)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_START
            contentDescription = getString(R.string.titulo)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(64)).apply { marginEnd = dp(18) })

        // Menú horizontal: solo texto, la pincelada marca dónde estás.
        for (p in Pagina.values()) if (p != Pagina.AJUSTES) agregarItemMenu(barra, p)
        agregarItemConfig(barra)
        actualizarMenu()

        barra.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        // Reloj.
        val reloj = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        reloj.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        reloj.addView(TextClock(this).apply {
            format12Hour = "EEE d 'de' MMMM"
            format24Hour = "EEE d 'de' MMMM"
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        barra.addView(reloj)
        return barra
    }

    private fun agregarItemMenu(barra: LinearLayout, destino: Pagina) {
        val texto = TextView(this).apply {
            text = destino.nombre
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        val vista = itemBase().apply { addView(texto) }
        itemsMenu.add(ItemMenu(vista, null, texto, destino))
        vista.setOnClickListener {
            if (destino != pagina) {
                pagina = destino
                actualizarMenu()
                mostrar(arriba = true)
            }
        }
        barra.addView(vista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { marginEnd = dp(2) })
    }

    private fun agregarItemConfig(barra: LinearLayout) {
        val icono = ImageView(this).apply { setImageResource(R.drawable.ic_config) }
        val vista = itemBase().apply { addView(icono, LinearLayout.LayoutParams(dp(20), dp(20))) }
        itemsMenu.add(ItemMenu(vista, icono, null, Pagina.AJUSTES))
        vista.setOnClickListener {
            if (pagina != Pagina.AJUSTES) {
                pagina = Pagina.AJUSTES
                actualizarMenu()
                mostrar(arriba = true)
            }
        }
        barra.addView(vista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
    }

    private fun itemBase() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(16), 0)
        isFocusable = true
        isClickable = true
        setOnFocusChangeListener { _, _ -> actualizarMenu() }
    }

    private fun actualizarMenu() {
        for (item in itemsMenu) {
            val elegido = item.pagina != null && item.pagina == pagina
            val foco = item.vista.isFocused
            item.vista.background = if (elegido || foco) pincelada(if (elegido) 255 else 70) else null
            val color = if (elegido) Color.WHITE else tinta
            item.texto?.setTextColor(color)
            item.texto?.typeface = if (elegido || foco) tema.fuenteTextoNegrita else tema.fuenteTexto
            item.icono?.imageTintList = ColorStateList.valueOf(color)
        }
    }

    private fun pincelada(alfa: Int): Drawable = getDrawable(R.drawable.pincelada)!!.mutate().apply {
        setTint(rojo)
        alpha = alfa
    }

    /**
     * Portada (la ilustración o la foto elegida): cubre todo el recuadro y recorta un poco más arriba
     * que abajo para no cortar las patitas. En modo oscuro puede llevar el filtro "noche".
     */
    private fun armarPortada(): View {
        val radio = dp(14).toFloat()
        val imagen = ImageView(this).apply {
            setImageDrawable(Portada.cargar(this@MainActivity))
            scaleType = ImageView.ScaleType.MATRIX
            outlineProvider = bordesRedondos(radio)
            clipToOutline = true
            if (tema.oscuro && prefs.getBoolean("portada_noche", true)) colorFilter = Tema.filtroNoche()
        }
        imagen.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val d = (v as ImageView).drawable ?: return@addOnLayoutChangeListener
            val escala = maxOf(v.width.toFloat() / d.intrinsicWidth, v.height.toFloat() / d.intrinsicHeight)
            val sobranteX = d.intrinsicWidth * escala - v.width
            val sobranteY = d.intrinsicHeight * escala - v.height
            v.imageMatrix = Matrix().apply {
                setScale(escala, escala)
                postTranslate(-sobranteX / 2, -sobranteY * 0.55f)
            }
        }
        if (!Portada.esPersonalizada(this) || !prefs.getBoolean("logo_portada", true)) return imagen

        // Con una foto propia, el logo de Shikitari TV va a la derecha sobre una sombra suave para que se lea.
        return FrameLayout(this).apply {
            outlineProvider = bordesRedondos(radio)
            clipToOutline = true
            addView(imagen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(View(this@MainActivity).apply {
                background = GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, intArrayOf(0x99000000.toInt(), 0x00000000))
            }, FrameLayout.LayoutParams(dp(360), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.logo_completo)
                adjustViewBounds = true
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(230), Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dp(36)
            })
        }
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
                    ri.loadLabel(pm).toString(),
                    ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm),
                    ri.loadIcon(pm),
                    (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    if (esJuego) ApplicationInfo.CATEGORY_GAME else if (android.os.Build.VERSION.SDK_INT >= 26) ai.applicationInfo.category else -1,
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

    private fun mostrar(foco: ((View) -> Boolean)? = null, arriba: Boolean = false) {
        val scrollAntes = scroll.scrollY
        contenido.removeAllViews()
        val anchoUtil = resources.displayMetrics.widthPixels - dp(margenLateral * 2) - dp(8)
        val margen = dp(6)
        val ancho = anchoUtil / columnas - 2 * margen
        val alto = ancho * 9 / 16

        when (pagina) {
            Pagina.AJUSTES -> armarAjustes()
            Pagina.INICIO -> {
                if (prefs.getBoolean("portada", true)) {
                    contenido.addView(armarPortada(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(altoPortada)))
                }
                contenido.addView(tituloSeccion("Favoritas", R.drawable.ic_estrella))
                val favs = favoritas()
                if (favs.isEmpty()) {
                    contenido.addView(textoSuave("Mantené apretado OK sobre una app y elegí \"Agregar a favoritas\"."))
                } else {
                    contenido.addView(fila(favs, FAV, ancho, alto, margen, primeraFila = true))
                }
                for (cat in Categoria.values()) {
                    val lista = appsDe(cat)
                    if (lista.isEmpty()) continue
                    contenido.addView(tituloSeccion(cat.titulo, cat.icono))
                    contenido.addView(fila(lista, cat.name, ancho, alto, margen, primeraFila = false))
                }
            }
            Pagina.TODAS -> {
                for (cat in Categoria.values()) {
                    val lista = appsDe(cat)
                    if (lista.isEmpty()) continue
                    contenido.addView(tituloSeccion(cat.titulo, cat.icono))
                    contenido.addView(grilla(lista, cat.name, ancho, alto, margen))
                }
                val ocultas = apps.filter { it.pkg in ocultas() }
                if (ocultas.isNotEmpty()) {
                    contenido.addView(tituloSeccion("Ocultas", R.drawable.ic_apps))
                    contenido.addView(textoSuave("Mantené apretado OK y elegí \"Mostrar\" para que vuelvan."))
                    contenido.addView(grilla(ocultas, null, ancho, alto, margen, atenuar = true))
                }
            }
            else -> {
                val cat = pagina.categoria!!
                val lista = appsDe(cat)
                contenido.addView(tituloSeccion(cat.titulo, cat.icono))
                if (lista.isEmpty()) {
                    contenido.addView(textoSuave("Todavía no hay apps acá. Mantené apretado OK sobre una app y elegí \"Cambiar categoría\"."))
                } else {
                    contenido.addView(grilla(lista, cat.name, ancho, alto, margen))
                }
            }
        }

        if (arriba) scroll.scrollTo(0, 0) else scroll.post { scroll.scrollTo(0, scrollAntes) }
        val destino = (foco?.let { buscar(contenido, it) }) ?: if (arriba) buscar(contenido) { it.tag is String } else null
        destino?.post { destino.requestFocus() }
    }

    private fun fila(lista: List<App>, contenedor: String, ancho: Int, alto: Int, margen: Int, primeraFila: Boolean): View {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        for (app in lista) {
            fila.addView(crearTile(app, contenedor, primeraFila), LinearLayout.LayoutParams(ancho, alto).apply {
                setMargins(margen, margen, margen, margen)
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(fila)
        }
    }

    private fun grilla(lista: List<App>, contenedor: String?, ancho: Int, alto: Int, margen: Int, atenuar: Boolean = false) =
        GridLayout(this).apply {
            columnCount = columnas
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), dp(4), dp(4), dp(4))
            for (app in lista) {
                addView(crearTile(app, contenedor, false).apply { if (atenuar) alpha = 0.5f }, GridLayout.LayoutParams().apply {
                    width = ancho
                    height = alto
                    setMargins(margen, margen, margen, margen)
                })
            }
        }

    private fun buscar(grupo: ViewGroup, condicion: (View) -> Boolean): View? {
        for (i in 0 until grupo.childCount) {
            val v = grupo.getChildAt(i)
            if (condicion(v)) return v
            if (v is ViewGroup) buscar(v, condicion)?.let { return it }
        }
        return null
    }

    private fun tagIgual(contenedor: String?, pkg: String): (View) -> Boolean = { it.tag == "${contenedor ?: "-"}|$pkg" }

    private fun tituloSeccion(texto: String, iconoRes: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(16), 0, dp(2))
        addView(ImageView(this@MainActivity).apply {
            setImageResource(iconoRes)
            imageTintList = ColorStateList.valueOf(rojo)
        }, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) })
        addView(TextView(this@MainActivity).apply {
            text = texto
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = tema.fuenteTitulo
        })
    }

    private fun textoSuave(texto: String) = TextView(this).apply {
        text = texto
        typeface = tema.fuenteTexto
        setTextColor(tintaSuave)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(8), dp(6), 0, dp(6))
    }

    private fun bordesRedondos(radio: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radio)
        }
    }

    /** contenedor: FAV o nombre de categoría si la app se puede mover ahí; null si no (apps ocultas). */
    private fun crearTile(app: App, contenedor: String?, primeraFila: Boolean): View {
        // El marco (tile) deja un borde fino alrededor; al enfocarlo se pinta del color de acento.
        // La imagen va recortada adentro (interiorTile), así nunca se asoma por fuera del borde.
        val grosor = dp(3)
        val radio = dp(9).toFloat()
        val tile = FrameLayout(this).apply {
            isFocusable = true
            isClickable = true
            tag = "${contenedor ?: "-"}|${app.pkg}"
            setPadding(grosor, grosor, grosor, grosor)
        }
        val interiorTile = FrameLayout(this).apply {
            outlineProvider = bordesRedondos(radio)
            clipToOutline = true
            elevation = dp(2).toFloat()
        }
        tile.addView(interiorTile, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        if (app.banner != null) {
            interiorTile.addView(ImageView(this).apply {
                setImageDrawable(app.banner)
                scaleType = ImageView.ScaleType.FIT_XY
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            // Apps sin banner: ícono y nombre sobre una tarjeta lisa (blanca, o gris oscuro en modo oscuro).
            interiorTile.background = GradientDrawable().apply {
                setColor(tema.tarjeta)
                cornerRadius = radio
            }
            val interior = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
            }
            interior.addView(ImageView(this).apply { setImageDrawable(app.icono) }, LinearLayout.LayoutParams(dp(40), dp(40)))
            interior.addView(TextView(this).apply {
                text = app.label
                setTextColor(tinta)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(8), 0, 0, 0)
            })
            interiorTile.addView(interior, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val enMovimiento = moviendo == (contenedor to app.pkg)
        // Enfocada: marco lleno del color de acento. Moviéndose: marco punteado.
        val borde = GradientDrawable().apply {
            cornerRadius = radio + grosor
            if (enMovimiento) {
                setColor(Color.TRANSPARENT)
                setStroke(grosor, rojo, dp(10).toFloat(), dp(6).toFloat())
            } else {
                setColor(rojo)
            }
        }
        tile.setOnFocusChangeListener { v, foco ->
            tile.background = if (foco || enMovimiento) borde else null
            v.animate().scaleX(if (foco) 1.08f else 1f).scaleY(if (foco) 1.08f else 1f)
                .translationZ(if (foco) dp(10).toFloat() else 0f).setDuration(120).start()
            // Al volver a las favoritas, mostrar de nuevo la ilustración entera.
            if (foco && primeraFila && moviendo == null) scroll.smoothScrollTo(0, 0)
        }
        if (enMovimiento) tile.background = borde
        tile.setOnClickListener { abrir(app.pkg) }
        tile.setOnLongClickListener { menuApp(app, contenedor); true }
        return tile
    }

    // ---------- Ajustes ----------

    private val servicioInicio get() = "$packageName/${InicioService::class.java.name}"
    private val componenteProtector get() = "$packageName/${ProtectorService::class.java.name}"
    private val minutosProtector = listOf(5, 10, 15, 30)

    private fun tienePermisoAjustes() =
        packageManager.checkPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS, packageName) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun servicioInicioActivo() =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(servicioInicio) == true

    /** Si el servicio del botón Inicio se apagó solo, lo vuelve a prender (solo con el permiso otorgado por ADB). */
    private fun asegurarServicioInicio() {
        if (BotonInicioService.esFireTv(this) || !tienePermisoAjustes() || !prefs.getBoolean("boton_inicio", true) || servicioInicioActivo()) return
        cambiarServicioInicio(true)
    }

    private fun cambiarServicioInicio(activar: Boolean) {
        val actuales = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(':').filter { it.isNotBlank() && it != servicioInicio }
        val nuevos = if (activar) actuales + servicioInicio else actuales
        Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nuevos.joinToString(":"))
        Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (nuevos.isEmpty()) 0 else 1)
    }

    private fun protectorActivo() =
        Settings.Secure.getInt(contentResolver, "screensaver_enabled", 0) == 1 &&
            Settings.Secure.getString(contentResolver, "screensaver_components") == componenteProtector

    private fun cambiarProtector(activar: Boolean) {
        Settings.Secure.putString(contentResolver, "screensaver_components", componenteProtector)
        Settings.Secure.putInt(contentResolver, "screensaver_enabled", if (activar) 1 else 0)
        Settings.Secure.putInt(contentResolver, "screensaver_activate_on_sleep", if (activar) 1 else 0)
    }

    private fun minutosActuales() =
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 600_000) / 60_000

    /** Cambia un ajuste del sistema; si falta el permiso, avisa en vez de cerrarse. */
    private fun conPermiso(accion: () -> Unit) {
        try {
            accion()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Falta el permiso de ajustes. Se otorga una sola vez desde la PC con ADB.", Toast.LENGTH_LONG).show()
        }
    }

    private fun memoriaLibreMb(): Long {
        val info = android.app.ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    private fun memoriaTotalMb(): Long {
        val info = android.app.ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    /** Cierra los procesos en segundo plano de las demás apps y avisa cuánta memoria quedó libre. */
    private fun liberarMemoria() {
        val antes = memoriaLibreMb()
        val am = (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager)
        for (ai in packageManager.getInstalledApplications(0)) {
            if (ai.packageName != packageName) am.killBackgroundProcesses(ai.packageName)
        }
        contenido.postDelayed({
            val liberados = (memoriaLibreMb() - antes).coerceAtLeast(0)
            Toast.makeText(this, "Memoria liberada: $liberados MB", Toast.LENGTH_SHORT).show()
            mostrar(foco = { it.tag == "ajuste_memoria" })
        }, 1500)
    }

    private fun textoAlmacenamiento(): String {
        val datos = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val gb = 1024.0 * 1024 * 1024
        return String.format("%.1f GB libres de %.0f GB", datos.availableBytes / gb, datos.totalBytes / gb)
    }

    private val escalasAnimacion = listOf(
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.ANIMATOR_DURATION_SCALE,
    )

    private fun escalaAnimacion() = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    private fun nombreAnimacion() = when {
        escalaAnimacion() == 0f -> "Sin animaciones"
        escalaAnimacion() < 1f -> "Rápidas"
        else -> "Normales"
    }

    /** Guarda un cambio de apariencia y redibuja todo con los colores nuevos, volviendo a la misma fila. */
    private fun cambiarApariencia(etiqueta: String, cambio: () -> Unit) {
        cambio()
        focoTrasRecrear = etiqueta
        recreate()
    }

    private var focoTrasRecrear: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pagina", pagina.name)
        outState.putString("foco", focoTrasRecrear)
    }

    private fun armarApariencia() {
        contenido.addView(tituloSeccion("Apariencia", R.drawable.ic_estrella))
        contenido.addView(filaAjuste("Modo", { if (tema.oscuro) "Oscuro" else "Claro" }, "ajuste_modo") {
            cambiarApariencia("ajuste_modo") { prefs.edit().putBoolean("oscuro", !tema.oscuro).apply() }
        })
        contenido.addView(filaAjuste("Color", { tema.nombreAcento }, "ajuste_color") {
            cambiarApariencia("ajuste_color") {
                val i = Tema.ACENTOS.indexOfFirst { it.first == tema.nombreAcento }
                prefs.edit().putInt("acento", (i + 1) % Tema.ACENTOS.size).apply()
            }
        })
        contenido.addView(filaAjuste("Letra", { tema.nombreLetra }, "ajuste_letra") {
            cambiarApariencia("ajuste_letra") {
                prefs.edit().putInt("letra", (Tema.LETRAS.indexOf(tema.nombreLetra) + 1) % Tema.LETRAS.size).apply()
            }
        })
        contenido.addView(filaAjuste("Imagen de portada", { if (Portada.esPersonalizada(this)) "Foto elegida" else "Ilustración de los gatos" }, "ajuste_portada") {
            mostrarDialogo("Imagen de portada", null, listOf(
                "Elegir una foto de la TV" to { elegirFotoDeLaTv() },
                "Volver a la ilustración de los gatos" to {
                    Portada.restaurar(this)
                    Toast.makeText(this, "Portada original", Toast.LENGTH_SHORT).show()
                    mostrar(foco = { it.tag == "ajuste_portada" })
                },
            ))
        })
        contenido.addView(filaAjuste("Logo sobre la foto de portada", { if (prefs.getBoolean("logo_portada", true)) "Sí" else "No" }) {
            prefs.edit().putBoolean("logo_portada", !prefs.getBoolean("logo_portada", true)).apply()
        })
        contenido.addView(filaAjuste("Portada en modo oscuro", { if (prefs.getBoolean("portada_noche", true)) "Efecto noche" else "Original" }) {
            prefs.edit().putBoolean("portada_noche", !prefs.getBoolean("portada_noche", true)).apply()
        })
        contenido.addView(filaAjuste("Mostrar portada", { if (prefs.getBoolean("portada", true)) "Sí" else "No" }) {
            prefs.edit().putBoolean("portada", !prefs.getBoolean("portada", true)).apply()
        })
    }

    private val permisoFotos
        get() = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
        else android.Manifest.permission.READ_EXTERNAL_STORAGE

    /** Muestra las fotos guardadas en la TV (por ejemplo, las que llegan con Send Files to TV) para elegir la portada. */
    private fun elegirFotoDeLaTv() {
        if (packageManager.checkPermission(permisoFotos, packageName) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permisoFotos), PEDIDO_FOTOS)
            return
        }
        val fotos = mutableListOf<Pair<Uri, String>>()
        contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.Images.Media._ID, android.provider.MediaStore.Images.Media.DISPLAY_NAME),
            null, null,
            "${android.provider.MediaStore.Images.Media.DATE_MODIFIED} DESC",
        )?.use { c ->
            while (c.moveToNext() && fotos.size < 40) {
                val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                fotos += uri to (c.getString(1) ?: "")
            }
        }
        if (fotos.isEmpty()) {
            Toast.makeText(
                this,
                "No hay fotos en la TV. Mandá una con Send Files to TV, o desde el celular con Shikitari Instalador.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        val dialogo = Dialog(this)
        val grilla = GridLayout(this).apply {
            columnCount = 4
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val hilo = java.util.concurrent.Executors.newSingleThreadExecutor()
        for ((uri, _) in fotos) {
            val miniatura = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                isFocusable = true
                isClickable = true
                setPadding(dp(3), dp(3), dp(3), dp(3))
                setOnFocusChangeListener { v, foco ->
                    v.background = if (foco) GradientDrawable().apply { setColor(rojo); cornerRadius = dp(8).toFloat() } else null
                }
                setOnClickListener {
                    dialogo.dismiss()
                    val ok = try { Portada.guardarDesde(this@MainActivity, uri) } catch (e: Exception) { false }
                    Toast.makeText(this@MainActivity, if (ok) "Portada cambiada" else "No se pudo usar esa foto", Toast.LENGTH_SHORT).show()
                    mostrar(foco = { it.tag == "ajuste_portada" })
                }
            }
            grilla.addView(miniatura, GridLayout.LayoutParams().apply {
                width = dp(160)
                height = dp(90)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            })
            hilo.execute {
                val bmp = try {
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        contentResolver.loadThumbnail(uri, android.util.Size(320, 180), null)
                    } else {
                        contentResolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })
                        }
                    }
                } catch (e: Exception) { null }
                if (bmp != null) runOnUiThread { miniatura.setImageBitmap(bmp) }
            }
        }
        dialogo.setOnDismissListener { hilo.shutdownNow() }
        dialogo.setContentView(ScrollView(this).apply {
            background = GradientDrawable().apply {
                setColor(washi)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), rojo)
            }
            addView(grilla)
        })
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogo.show()
        grilla.getChildAt(0)?.requestFocus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PEDIDO_FOTOS && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            elegirFotoDeLaTv()
        }
    }

    private fun armarAjustes() {
        val permiso = tienePermisoAjustes()

        armarApariencia()

        contenido.addView(tituloSeccion("Optimizar", R.drawable.ic_herramientas))
        contenido.addView(filaAjuste("Liberar memoria", { "${memoriaLibreMb()} MB libres de ${memoriaTotalMb()} MB" }, "ajuste_memoria") {
            liberarMemoria()
        })
        contenido.addView(filaAjuste("Almacenamiento", { textoAlmacenamiento() }) {
            abrirIntent(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        })
        contenido.addView(filaAjuste("Animaciones del sistema", { nombreAnimacion() }) {
            // Normales → Rápidas → Sin animaciones → Normales
            val siguiente = when {
                escalaAnimacion() >= 1f -> 0.5f
                escalaAnimacion() > 0f -> 0f
                else -> 1f
            }
            conPermiso { for (clave in escalasAnimacion) Settings.Global.putFloat(contentResolver, clave, siguiente) }
        })

        contenido.addView(tituloSeccion("Pantalla de inicio", R.drawable.ic_inicio))
        contenido.addView(filaAjuste("Tamaño de las apps", { if (prefs.getBoolean("apps_grandes", false)) "Grande (5 por fila)" else "Normal (6 por fila)" }) {
            prefs.edit().putBoolean("apps_grandes", !prefs.getBoolean("apps_grandes", false)).apply()
        })

        contenido.addView(tituloSeccion("Protector de pantalla", R.drawable.ic_estrella))
        contenido.addView(filaAjuste("Protector con la portada", { if (protectorActivo()) "Activado" else "Desactivado" }) {
            conPermiso { cambiarProtector(!protectorActivo()) }
        })
        contenido.addView(filaAjuste("Empieza después de", { "${minutosActuales()} minutos" }) {
            conPermiso {
                val siguiente = minutosProtector.firstOrNull { it > minutosActuales() } ?: minutosProtector.first()
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, siguiente * 60_000)
            }
        })
        contenido.addView(filaAjuste("Ver el protector ahora", { "▶" }) {
            startActivity(Intent(this, ProtectorActivity::class.java))
        })

        contenido.addView(tituloSeccion("Botón Inicio del control", R.drawable.ic_config))
        val fireTv = BotonInicioService.esFireTv(this)
        contenido.addView(filaAjuste("Abrir Shikitari TV con Inicio", {
            if (if (fireTv) BotonInicioService.activado(this) else servicioInicioActivo()) "Activado" else "Desactivado"
        }) {
            if (fireTv) {
                prefs.edit().putBoolean("boton_inicio", !BotonInicioService.activado(this)).commit()
                BotonInicioService.iniciarSiCorresponde(this)
            } else if (permiso) {
                val activar = !servicioInicioActivo()
                prefs.edit().putBoolean("boton_inicio", activar).apply()
                conPermiso { cambiarServicioInicio(activar) }
            } else {
                abrirIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        contenido.addView(tituloSeccion("Apps", R.drawable.ic_apps))
        contenido.addView(filaAjuste("Volver a mostrar las apps ocultas", { "${ocultas().size} ocultas" }) {
            guardarLista("ocultas", emptyList())
        })
        contenido.addView(filaAjuste("Restablecer orden y categorías", { "" }) {
            val editor = prefs.edit()
            for (clave in prefs.all.keys) {
                if (clave.startsWith("cat_") || Categoria.values().any { it.name == clave }) editor.remove(clave)
            }
            editor.apply()
            Toast.makeText(this, "Orden y categorías como al principio", Toast.LENGTH_SHORT).show()
        })

        contenido.addView(tituloSeccion("Sistema", R.drawable.ic_herramientas))
        contenido.addView(filaAjuste("Ajustes de la TV", { "›" }) { abrirIntent(Intent(Settings.ACTION_SETTINGS)) })
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        contenido.addView(textoSuave(
            "Shikitari TV $version · " + if (permiso) "Permiso de ajustes: otorgado" else "Permiso de ajustes: no otorgado (algunas opciones no funcionan)",
        ))
    }

    /** Fila de ajuste: nombre a la izquierda, valor a la derecha; OK ejecuta la acción y refresca el valor. */
    private fun filaAjuste(titulo: String, valor: () -> String, etiqueta: String? = null, accion: () -> Unit): View {
        val nombre = TextView(this).apply {
            text = titulo
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = tema.fuenteTexto
        }
        val estado = TextView(this).apply {
            text = valor()
            typeface = tema.fuenteTexto
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.END
        }
        val reposo = GradientDrawable().apply {
            setColor(tema.reposo)
            cornerRadius = dp(10).toFloat()
        }
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), 0, dp(26), 0)
            isFocusable = true
            isClickable = true
            tag = etiqueta
            background = reposo
            addView(nombre, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(estado)
        }
        fila.setOnFocusChangeListener { _, foco ->
            fila.background = if (foco) pincelada(255) else reposo
            nombre.setTextColor(if (foco) Color.WHITE else tinta)
            estado.setTextColor(if (foco) Color.WHITE else tintaSuave)
            nombre.typeface = if (foco) tema.fuenteTextoNegrita else tema.fuenteTexto
        }
        fila.setOnClickListener {
            accion()
            estado.text = valor()
        }
        return fila.apply {
            layoutParams = LinearLayout.LayoutParams(dp(720), dp(50)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }
        }
    }

    // ---------- Menú al mantener OK ----------

    private fun menuApp(app: App, contenedor: String?) {
        val esFav = app.pkg in leerLista(FAV)
        val oculta = app.pkg in ocultas()
        val opciones = mutableListOf<Pair<String, () -> Unit>>()
        opciones += "Abrir" to { abrir(app.pkg) }
        if (!oculta) {
            opciones += (if (esFav) "Quitar de favoritas" else "Agregar a favoritas") to { alternarFavorita(app, contenedor) }
            if (contenedor != null) opciones += "Mover" to { empezarAMover(app, contenedor) }
            opciones += "Cambiar categoría" to { elegirCategoria(app) }
            opciones += "Ocultar" to { ocultar(app, true) }
        } else {
            opciones += "Mostrar" to { ocultar(app, false) }
        }
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
        val opciones = Categoria.values().map { cat ->
            (if (cat == actual) "✓  ${cat.titulo}" else "     ${cat.titulo}") to {
                prefs.edit().putString("cat_${app.pkg}", cat.name).apply()
                Toast.makeText(this, "${app.label} ahora está en ${cat.titulo}", Toast.LENGTH_SHORT).show()
                mostrar(foco = { v -> (v.tag as? String)?.endsWith("|${app.pkg}") == true })
            }
        }
        mostrarDialogo("Categoría de ${app.label}", app.icono, opciones)
    }

    /** Diálogo con el estilo del launcher: papel, título en serif y opciones con pincelada al enfocarlas. */
    private fun mostrarDialogo(titulo: String, icono: Drawable?, opciones: List<Pair<String, () -> Unit>>) {
        val dialogo = Dialog(this)
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(16))
            background = GradientDrawable().apply {
                setColor(washi)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), rojo)
            }
        }
        val encabezado = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        if (icono != null) {
            encabezado.addView(ImageView(this).apply { setImageDrawable(icono) }, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(12)
            })
        }
        encabezado.addView(TextView(this).apply {
            text = titulo
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = tema.fuenteTitulo
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        caja.addView(encabezado)

        var primera: View? = null
        for ((texto, accion) in opciones) {
            val opcion = TextView(this).apply {
                text = texto
                typeface = tema.fuenteTexto
                setTextColor(tinta)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), 0, dp(18), 0)
                isFocusable = true
                isClickable = true
                setOnFocusChangeListener { v, foco ->
                    v.background = if (foco) pincelada(255) else null
                    (v as TextView).setTextColor(if (foco) Color.WHITE else tinta)
                    v.typeface = if (foco) tema.fuenteTextoNegrita else tema.fuenteTexto
                }
                setOnClickListener {
                    dialogo.dismiss()
                    accion()
                }
            }
            if (primera == null) primera = opcion
            caja.addView(opcion, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        }

        dialogo.setContentView(caja, ViewGroup.LayoutParams(dp(340), ViewGroup.LayoutParams.WRAP_CONTENT))
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
        ultimaApp = pkg
        abrirIntent(intent)
    }

    private fun abrirIntent(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun alternarFavorita(app: App, contenedor: String?) {
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
        val nuevoContenedor = if (contenedor == FAV) categoriaDe(app).name else contenedor
        mostrar(foco = tagIgual(nuevoContenedor, app.pkg))
    }

    private fun ocultar(app: App, ocultar: Boolean) {
        val lista = leerLista("ocultas")
        if (ocultar) lista.add(app.pkg) else lista.remove(app.pkg)
        guardarLista("ocultas", lista.distinct())
        Toast.makeText(
            this,
            if (ocultar) "${app.label} oculta. La encontrás al final de Apps." else "${app.label} vuelve a mostrarse",
            Toast.LENGTH_SHORT,
        ).show()
        mostrar(foco = if (ocultar) null else { v -> (v.tag as? String)?.endsWith("|${app.pkg}") == true })
    }

    private fun empezarAMover(app: App, contenedor: String) {
        moviendo = contenedor to app.pkg
        Toast.makeText(this, "Usá las flechas para mover ${app.label}. OK para terminar.", Toast.LENGTH_LONG).show()
        mostrar(foco = tagIgual(contenedor, app.pkg))
    }

    private fun mover(contenedor: String, pkg: String, delta: Int) {
        val lista = appsDeContenedor(contenedor).map { it.pkg }.toMutableList()
        val i = lista.indexOf(pkg)
        val j = (i + delta).coerceIn(0, lista.size - 1)
        if (i < 0 || i == j) return
        lista.removeAt(i)
        lista.add(j, pkg)
        guardarLista(contenedor, lista)
        mostrar(foco = tagIgual(contenedor, pkg))
    }

    private fun dp(valor: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), resources.displayMetrics).toInt()

    companion object {
        const val EXTRA_DESDE_HOME = "desde_home"
        private const val PEDIDO_FOTOS = 7
    }
}
