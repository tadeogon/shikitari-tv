package com.pachi.tv

import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sin

/**
 * Android solo deja ver qué música suena a las apps con este servicio habilitado (permiso que se da una vez
 * por ADB: `cmd notification allow_listener com.pachi.tv/com.pachi.tv.MusicaListener`).
 * También le pasa al Centro de Control las notificaciones de la tele.
 */
class MusicaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        activo = this
    }

    override fun onListenerDisconnected() {
        if (activo === this) activo = null
    }

    companion object {
        /** El servicio conectado; null si Android todavía no le dio permiso o no lo arrancó. */
        @Volatile
        var activo: MusicaListener? = null
            private set
    }
}

/**
 * Isla dinámica: píldora negra que aparece cuando suena música. Es chica y se abre al cambiar de tema
 * o al enfocarla; ahí OK pausa o reanuda, y izquierda y derecha cambian de tema.
 */
class IslaMusica(context: Context, private val alIrse: () -> Unit) : LinearLayout(context) {

    private val tapa = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        clipToOutline = true
        setBackgroundColor(if (Tema.neon) 0x26FFFFFF else 0xFF2C2C2E.toInt())
    }
    private val titulo = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val artista = TextView(context).apply {
        setTextColor(0x99FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val textos = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(titulo)
        addView(artista)
    }
    private val barras = Ecualizador(context)
    private val anterior = iconoControl(R.drawable.ic_anterior)
    private val pausa = iconoControl(R.drawable.ic_pause)
    private val siguiente = iconoControl(R.drawable.ic_siguiente)
    private val controles = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        addView(anterior)
        addView(pausa)
        addView(siguiente)
    }
    private val espera = Handler(Looper.getMainLooper())
    private val administrador = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val componente = ComponentName(context, MusicaListener::class.java)
    private var controlador: MediaController? = null
    private val observadas = mutableListOf<MediaController>()
    private var temaActual: String? = null
    private var ancho = 0
    private var animando = Any()

    private val alCambiarSesiones = MediaSessionManager.OnActiveSessionsChangedListener { refrescar() }
    private val alCambiarEstado = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refrescar()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refrescar()
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        setPadding(dp(5), 0, dp(12), 0)
        isFocusable = true
        isClickable = true
        elevation = dp(10).toFloat()
        pintarFondo(false)
        addView(tapa, LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(9) })
        addView(textos, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        addView(barras, LayoutParams(dp(18), dp(16)))
        addView(controles)
        textos.alpha = 0f
        ancho = CHICA
        setOnFocusChangeListener { _, foco ->
            pintarFondo(foco)
            // Enfocada, las barritas dejan lugar a los botones de control.
            barras.visibility = if (foco) GONE else VISIBLE
            controles.visibility = if (foco) VISIBLE else GONE
            if (foco) abrir(sinCerrar = true) else cerrarEnUnRato()
        }
        setOnClickListener {
            alternar()
            marcar(pausa)
        }
        setOnKeyListener { _, codigo, evento ->
            if (evento.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (codigo) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    temaAnterior()
                    marcar(anterior)
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    temaSiguiente()
                    marcar(siguiente)
                    true
                }
                else -> false
            }
        }
    }

    private fun iconoControl(icono: Int) = ImageView(context).apply {
        setImageResource(icono)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        layoutParams = LayoutParams(dp(26), dp(26)).apply { marginStart = dp(3) }
    }

    /** Destello corto en crema para mostrar qué botón se apretó. */
    private fun marcar(icono: ImageView) {
        icono.imageTintList = ColorStateList.valueOf(MainActivity.CREMA)
        icono.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x40FFFFFF)
        }
        icono.postDelayed({
            icono.imageTintList = ColorStateList.valueOf(Color.WHITE)
            icono.background = null
        }, 220)
    }

    /** Empieza a escuchar. Sin el permiso de música se queda oculta, sin molestar. */
    fun iniciar() {
        try {
            administrador.addOnActiveSessionsChangedListener(alCambiarSesiones, componente)
            refrescar()
        } catch (e: SecurityException) {
            visibility = GONE
        }
    }

    fun detener() {
        administrador.removeOnActiveSessionsChangedListener(alCambiarSesiones)
        observadas.forEach { it.unregisterCallback(alCambiarEstado) }
        observadas.clear()
        controlador = null
        espera.removeCallbacksAndMessages(null)
    }

    private fun refrescar() {
        // Se avisa después de actualizar la isla, para que el menú lateral muestre lo mismo.
        post { alCambiar?.invoke() }
        val sesiones = try {
            administrador.getActiveSessions(componente)
        } catch (e: SecurityException) {
            emptyList()
        }
        // Se escuchan todas las sesiones: así la isla se entera aunque una empiece a sonar más tarde.
        for (sesion in sesiones) {
            if (observadas.none { it.sessionToken == sesion.sessionToken }) {
                sesion.registerCallback(alCambiarEstado)
                observadas.add(sesion)
            }
        }
        // Lo que está sonando siempre cuenta, aunque la app (como SmartTube) no diga el nombre del tema.
        // La sesión actual sigue valiendo mientras carga o cambia de tema, para que la isla no parpadee.
        // De las demás en pausa, solo las que dicen qué tema tienen: algunas apps dejan sesiones viejas vacías.
        val elegido = sesiones.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sesiones.firstOrNull { it.sessionToken == controlador?.sessionToken && it.playbackState?.state in ESTADOS_DE_PASO }
            ?: sesiones.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PAUSED &&
                    !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
            }
        controlador = elegido
        val datos = elegido?.metadata
        val nombre = datos?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: datos?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: elegido?.let { nombreDeApp(it.packageName) }
        if (elegido == null || nombre.isNullOrBlank()) {
            controlador = null
            // Spotify suelta la música al rato de pausarla: la isla queda con el último tema, lista para seguir.
            if (mostrarDormida()) return
            dormida = false
            if (visibility != GONE) {
                val teniaFoco = isFocused
                visibility = GONE
                temaActual = null
                if (teniaFoco) alIrse()
            }
            return
        }
        dormida = false
        titulo.text = nombre
        artista.text = datos?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: datos?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val imagen: Bitmap? = datos?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: datos?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: datos?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        // La tapa que manda la app puede ser enorme (1000×1000 o más): se guarda una copia chica
        // para no tener esa foto ocupando memoria mientras Ziota está detrás de otra app.
        val firma = imagen?.let { System.identityHashCode(it) to it.generationId }
        if (firma != firmaTapa) {
            firmaTapa = firma
            tapaActual = imagen?.let { achicarTapa(it) }
        }
        ponerTapa(tapa, tapaActual, dp(6))
        recordar(elegido.packageName, nombre, artista.text.toString())
        barras.sonando = elegido.playbackState?.state == PlaybackState.STATE_PLAYING
        pausa.setImageResource(if (barras.sonando) R.drawable.ic_pause else R.drawable.ic_play)
        if (visibility != VISIBLE) visibility = VISIBLE
        if (nombre != temaActual) {
            temaActual = nombre
            abrir()
        }
    }

    private fun nombreDeApp(paquete: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(paquete, 0)).toString()
    } catch (e: Exception) {
        "Música"
    }

    // ---------- Último tema: la isla no desaparece cuando la app de música suelta la sesión ----------

    private val guardado = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Sin música activa, mostrando el último tema que sonó (OK lo retoma). */
    private var dormida = false

    private fun recordar(paquete: String, nombre: String, artistaTexto: String) {
        if (paquete !in APPS_DE_MUSICA) return
        if (guardado.getString("isla_titulo", null) == nombre && guardado.getString("isla_paquete", null) == paquete) return
        guardado.edit().putString("isla_paquete", paquete).putString("isla_titulo", nombre).putString("isla_artista", artistaTexto).apply()
    }

    /** Muestra el último tema con el botón de play. false si no hay nada para recordar o la app ya no está. */
    private fun mostrarDormida(): Boolean {
        val paquete = guardado.getString("isla_paquete", null) ?: return false
        val nombre = guardado.getString("isla_titulo", null) ?: return false
        if (context.packageManager.getLaunchIntentForPackage(paquete) == null &&
            context.packageManager.getLeanbackLaunchIntentForPackage(paquete) == null) return false
        dormida = true
        titulo.text = nombre
        artista.text = guardado.getString("isla_artista", "") ?: ""
        ponerTapa(tapa, tapaActual, dp(6))
        barras.sonando = false
        pausa.setImageResource(R.drawable.ic_play)
        if (visibility != VISIBLE) visibility = VISIBLE
        temaActual = nombre
        return true
    }

    /**
     * Retoma la música sin abrir la app: la tele le manda "play" a la última app que sonó
     * (la misma tecla que el botón ⏯ del control). Si en un rato no arrancó, abre la app.
     */
    private fun retomar() {
        tecla(KeyEvent.KEYCODE_MEDIA_PLAY)
        val paquete = guardado.getString("isla_paquete", null) ?: return
        postDelayed({
            if (!sonando) {
                val pm = context.packageManager
                val abrir = pm.getLeanbackLaunchIntentForPackage(paquete) ?: pm.getLaunchIntentForPackage(paquete)
                abrir?.let { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }, 3000)
    }

    private fun tecla(codigo: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, codigo))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, codigo))
    }

    var alCambiar: (() -> Unit)? = null
    var tapaActual: Bitmap? = null
        private set
    private var firmaTapa: Pair<Int, Int>? = null
        private set
    val tituloActual: CharSequence get() = titulo.text
    val artistaActual: CharSequence get() = artista.text

    val hayMusica get() = controlador != null || dormida

    /** App que está sonando (null si no hay ninguna). */
    val appActual: String? get() = controlador?.packageName

    /** Por dónde va el tema y cuánto dura, en milisegundos; null si la app no lo dice. */
    fun progreso(): Pair<Long, Long>? {
        val c = controlador ?: return null
        val total = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val estado = c.playbackState ?: return null
        if (total <= 0) return null
        var pos = estado.position
        if (estado.state == PlaybackState.STATE_PLAYING && estado.lastPositionUpdateTime > 0) {
            pos += ((SystemClock.elapsedRealtime() - estado.lastPositionUpdateTime) * estado.playbackSpeed).toLong()
        }
        return pos.coerceIn(0, total) to total
    }

    val sonando get() = controlador?.playbackState?.state == PlaybackState.STATE_PLAYING

    fun temaAnterior() {
        controlador?.transportControls?.skipToPrevious() ?: tecla(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun temaSiguiente() {
        controlador?.transportControls?.skipToNext() ?: tecla(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun alternar() {
        val c = controlador ?: return retomar()
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun permitirFoco(permitir: Boolean) {
        isFocusable = permitir
    }

    private fun abrir(sinCerrar: Boolean = false) {
        animarA(GRANDE, 1f)
        espera.removeCallbacksAndMessages(null)
        if (!sinCerrar) cerrarEnUnRato()
    }

    private fun cerrarEnUnRato() {
        espera.removeCallbacksAndMessages(null)
        espera.postDelayed({ if (!isFocused) animarA(CHICA, 0f) }, 4000)
    }

    private fun animarA(destino: Int, alfa: Float) {
        val token = Any()
        animando = token
        val ancho0 = ancho
        val alfa0 = textos.alpha
        val inicio = SystemClock.uptimeMillis()
        val paso = object : Runnable {
            override fun run() {
                if (animando !== token) return
                val t = ((SystemClock.uptimeMillis() - inicio) / 340f).coerceAtMost(1f)
                // Se pasa apenas y vuelve, como la isla de los iPhone.
                val u = t - 1
                val c = 1 + 2.2f * u * u * u + 1.2f * u * u
                ancho = (ancho0 + (destino - ancho0) * c).toInt()
                layoutParams = layoutParams.apply { width = dp(ancho) }
                textos.alpha = (alfa0 + (alfa - alfa0) * t)
                if (t < 1f) postOnAnimation(this)
            }
        }
        postOnAnimation(paso)
    }

    private fun pintarFondo(foco: Boolean) {
        if (Tema.neon) {
            // Neón: vidrio oscuro; enfocada se enciende con un anillo del degradado.
            background = Vidrio(dp(19).toFloat(), Tema.rellenoIsla, redondo = true, grosorAnillo = dp(2).toFloat(),
                anillo = if (foco) Tema.degradado else null)
            return
        }
        background = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = dp(19).toFloat()
            if (foco) setStroke(dp(2), MainActivity.CREMA)
        }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        /** Solo estas se recuerdan al soltar la sesión: un video o una peli no tiene que quedar en la isla. */
        private val APPS_DE_MUSICA = setOf(
            "com.spotify.tv.android", "com.spotify.music", "com.google.android.youtube.tvmusic",
            "com.amazon.mp3", "deezer.android.tv", "deezer.android.app", "com.aspiro.tidal",
            "com.apple.android.music", "com.soundcloud.android",
        )
        const val CHICA = 92
        const val GRANDE = 240 // angosta: abierta no llega al clima de la derecha
        private val ESTADOS_DE_PASO = setOf(
            PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        )
    }
}

/**
 * Carátula del tema. Muchas apps (Spotify en el Fire Stick, por ejemplo) no la pasan: en vez de un círculo
 * vacío queda una nota musical, para que se entienda qué es.
 */
/** Achica la carátula a 512 px como máximo (alcanza para la pantalla de música; la isla la usa más chica). */
fun achicarTapa(b: Bitmap, maximo: Int = 512): Bitmap {
    val lado = maxOf(b.width, b.height)
    if (lado <= maximo) return b
    val f = maximo.toFloat() / lado
    return try {
        Bitmap.createScaledBitmap(b, maxOf(1, (b.width * f).toInt()), maxOf(1, (b.height * f).toInt()), true)
    } catch (e: Throwable) {
        b
    }
}

fun ponerTapa(v: ImageView, imagen: Bitmap?, relleno: Int) {
    if (imagen != null) {
        v.setImageBitmap(imagen)
        v.scaleType = ImageView.ScaleType.CENTER_CROP
        v.imageTintList = null
        v.setPadding(0, 0, 0, 0)
    } else {
        v.setImageResource(R.drawable.ic_musica)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.imageTintList = ColorStateList.valueOf(0xB3FFFFFF.toInt())
        v.setPadding(relleno, relleno, relleno, relleno)
    }
}

/** Tres barritas que suben y bajan mientras suena la música. */
private class Ecualizador(context: Context) : View(context) {
    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MainActivity.CREMA }

    var sonando = false
        set(valor) {
            field = valor
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val ancho = width / 5f
        val t = SystemClock.uptimeMillis() / 170.0
        for (i in 0..2) {
            val nivel = if (sonando) 0.45f + 0.55f * ((sin(t + i * 1.9) + 1) / 2).toFloat() else 0.2f
            val alto = height * nivel
            val x = i * 2 * ancho
            canvas.drawRoundRect(x, height - alto, x + ancho, height.toFloat(), ancho / 2, ancho / 2, pincel)
        }
        if (sonando) postInvalidateOnAnimation()
    }
}
