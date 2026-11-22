package com.pachi.tv

import android.app.ActivityManager
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/**
 * Centro de Control: se abre subiendo desde la primera fila del inicio. A la izquierda la hora grande, la fecha
 * y las notificaciones de la tele; a la derecha una grilla de módulos. Lo encendido lleva el color del tema y lo
 * elegido se pone blanco. La idea viene de Aura TV (github.com/cookedzera/aura-tv, licencia MIT); el código es
 * propio de Ziota.
 */
class CentroDeControl(context: Context, fondo: Bitmap?, private val acciones: Acciones) : FrameLayout(context) {

    class Acciones(
        val wifi: () -> Unit,
        val bluetooth: () -> Unit,
        val sonido: () -> Unit,
        val prenderPc: () -> Unit,
        val cantidadPcs: () -> Int,
        val optimizar: () -> Unit,
        val ajustes: () -> Unit,
        val protector: () -> Unit,
    )

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val modulos = mutableListOf<Modulo>()
    private val notificaciones = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var primero: View? = null

    private class Modulo(val vista: LinearLayout, val icono: ImageView?, val titulo: TextView, val detalle: TextView,
                         val encendido: () -> Boolean, val texto: () -> String)

    init {
        // Lo de atrás difuminado una sola vez (Fire Stick) y oscurecido; en Google TV difumina el sistema.
        if (fondo != null) addView(ImageView(context).apply {
            setImageBitmap(fondo)
            scaleType = ImageView.ScaleType.FIT_XY
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(View(context).apply { setBackgroundColor(if (fondo != null) 0xB30A0A0C.toInt() else 0x990A0A0C.toInt()) },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val fila = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(56), dp(48), dp(56), dp(48))
        }
        fila.addView(armarIzquierda(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(40) })
        fila.addView(armarGrilla())
        addView(fila, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isClickable = true // que nada de atrás reciba el control
        refrescar()
    }

    /** Pone el foco en el primer módulo (Wi-Fi). */
    fun enfocar() {
        (primero ?: modulos.firstOrNull()?.vista)?.requestFocus()
    }

    // ---------- Izquierda: hora, fecha y notificaciones ----------

    private fun armarIzquierda() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.TOP
        setPadding(0, dp(24), 0, 0)
        addView(TextClock(context).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            letterSpacing = -0.02f
        })
        addView(TextClock(context).apply {
            format12Hour = "EEEE d 'de' MMMM"
            format24Hour = "EEEE d 'de' MMMM"
            setTextColor(GRIS)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(8), 0, 0)
        })
        addView(rotulo("NOTIFICACIONES"), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(32) })
        // Si no entran todas, la lista se desplaza al bajar con el control.
        addView(android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(24))
            addView(notificaciones)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
    }

    private fun pintarNotificaciones() {
        notificaciones.removeAllViews()
        val servicio = MusicaListener.activo
        if (servicio == null) {
            notificaciones.addView(aviso("Para ver las notificaciones, Ziota necesita un permiso que se da una sola vez desde la app del celular."))
            return
        }
        val lista = try {
            servicio.activeNotifications.filter(::sirve).sortedByDescending { it.postTime }.take(6)
        } catch (e: Exception) {
            emptyList()
        }
        if (lista.isEmpty()) {
            notificaciones.addView(aviso("No hay notificaciones nuevas."))
            return
        }
        for (n in lista) notificaciones.addView(tarjeta(n), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }

    /** Solo lo que una persona querría leer: sin las fijas (música sonando, descargas en curso) ni las de Ziota. */
    private fun sirve(n: StatusBarNotification): Boolean {
        if (n.isOngoing || n.packageName == context.packageName) return false
        // Los "resúmenes" de grupo no traen texto propio.
        if (n.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return false
        // Las "recomendaciones" (Netflix las manda así) son propaganda, no avisos.
        if (n.notification.category == android.app.Notification.CATEGORY_RECOMMENDATION) return false
        val extras = n.notification.extras
        return primero(extras, "android.title", "android.bigText", "android.text").isNotBlank()
    }

    /** El primer texto con algo escrito: hay apps que mandan uno vacío y el real en otro lugar. */
    private fun primero(extras: android.os.Bundle, vararg claves: String): String =
        claves.firstNotNullOfOrNull { clave ->
            // Sin saltos de línea ni espacios de más: algunas apps empiezan el texto con un renglón vacío.
            extras.getCharSequence(clave)?.toString()?.replace(Regex("\\s+"), " ")?.trim()?.takeIf(String::isNotBlank)
        }.orEmpty()

    private fun tarjeta(n: StatusBarNotification): View {
        val extras = n.notification.extras
        val app = try {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(n.packageName, 0)).toString()
        } catch (e: Exception) {
            n.packageName
        }
        val cuando = DateUtils.getRelativeTimeSpanString(n.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
        val cabeza = texto("$app · $cuando", 12f, GRIS)
        // Algunas apps mandan solo el texto, o el texto largo aparte: se arma con lo que haya.
        val textoLargo = primero(extras, "android.bigText", "android.text")
        val tituloApp = primero(extras, "android.title")
        val titulo = texto(tituloApp.ifBlank { textoLargo }, 14f, Color.WHITE, true).apply { maxLines = 1 }
        val cuerpo = texto(if (tituloApp.isBlank()) "" else textoLargo, 12f, GRIS).apply { maxLines = 2 }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isFocusable = true
            isClickable = true
            addView(cabeza)
            addView(titulo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            if (cuerpo.text.isNotBlank()) addView(cuerpo)
            pintar(this, false, listOf(cabeza, titulo, cuerpo), null, false)
            setOnFocusChangeListener { _, foco -> pintar(this, foco, listOf(cabeza, titulo, cuerpo), null, false) }
            // OK la abre; mantener OK la descarta.
            setOnClickListener {
                try {
                    n.notification.contentIntent?.send()
                } catch (e: Exception) {
                }
            }
            setOnLongClickListener {
                try {
                    MusicaListener.activo?.cancelNotification(n.key)
                } catch (e: Exception) {
                }
                postDelayed({ pintarNotificaciones(); enfocar() }, 300)
                true
            }
        }
    }

    private fun aviso(mensaje: String) = texto(mensaje, 14f, GRIS).apply {
        maxLines = 4
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = Vidrio(dp(16).toFloat(), 0x14FFFFFF)
    }

    // ---------- Derecha: los módulos ----------

    private fun armarGrilla(): GridLayout {
        val grilla = GridLayout(context).apply {
            columnCount = 4
            rowCount = 3
        }
        val wifi = modulo(grilla, R.drawable.ic_wifi, "Wi-Fi", 0, 0, encendido = ::conectado, texto = { if (conectado()) "Conectado" else "Sin conexión" }) { acciones.wifi() }
        primero = wifi
        modulo(grilla, R.drawable.ic_bluetooth, "Bluetooth", 0, 1, encendido = ::bluetooth, texto = { if (bluetooth()) "Prendido" else "" }) { acciones.bluetooth() }
        moduloVolumen(grilla)
        modulo(grilla, R.drawable.ic_power, "Prender PC", 0, 3, filas = 2, texto = {
            when (val n = acciones.cantidadPcs()) {
                0 -> "Sin PCs"
                1 -> "1 PC"
                else -> "$n PCs"
            }
        }) { acciones.prenderPc() }
        modulo(grilla, R.drawable.ic_luna, "Dormir", 1, 0, encendido = { Dormir.activo }, texto = { Dormir.texto() }) {
            Dormir.siguiente(context)
            refrescar()
        }
        modulo(grilla, R.drawable.ic_pantalla_apagada, "Pantalla apagada", 1, 1, texto = { "Con sonido" }) { apagarPantalla() }
        modulo(grilla, R.drawable.ic_sonido, "Sonido", 2, 0, texto = { "Salida" }) { acciones.sonido() }
        modulo(grilla, R.drawable.ic_memoria, "Memoria", 2, 1, texto = { "${memoriaLibre()} MB libres" }) {
            acciones.optimizar()
            postDelayed({ refrescar() }, 1500)
        }
        modulo(grilla, R.drawable.ic_config, "Ajustes", 2, 2) { acciones.ajustes() }
        modulo(grilla, R.drawable.ic_estrella, "Protector", 2, 3, texto = { "Pantalla de descanso" }) { acciones.protector() }
        return grilla
    }

    private fun modulo(grilla: GridLayout, icono: Int?, titulo: String, fila: Int, columna: Int, filas: Int = 1,
                       encendido: () -> Boolean = { false }, texto: () -> String = { "" }, accion: () -> Unit): LinearLayout {
        val imagen = icono?.let { ImageView(context).apply { setImageResource(it) } }
        val nombre = texto(titulo, 13f, Color.WHITE, true).apply { gravity = Gravity.CENTER; maxLines = 2 }
        val detalle = texto("", 11f, GRIS).apply { gravity = Gravity.CENTER; maxLines = 2 }
        val vista = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isFocusable = true
            isClickable = true
            if (imagen != null) addView(imagen, LinearLayout.LayoutParams(dp(28), dp(28)).apply { bottomMargin = dp(8) })
            addView(nombre)
            addView(detalle)
            setOnClickListener { accion() }
        }
        val m = Modulo(vista, imagen, nombre, detalle, encendido, texto)
        vista.setOnFocusChangeListener { _, _ -> pintarModulo(m) }
        modulos += m
        grilla.addView(vista, GridLayout.LayoutParams(GridLayout.spec(fila, filas), GridLayout.spec(columna, 1)).apply {
            width = dp(CELDA)
            height = dp(CELDA) * filas + dp(ESPACIO) * (filas - 1)
            setMargins(if (columna == 0) 0 else dp(ESPACIO), if (fila == 0) 0 else dp(ESPACIO), 0, 0)
        })
        return vista
    }

    /** Volumen: alto, con el número grande; con el foco encima, arriba y abajo lo cambian. */
    private fun moduloVolumen(grilla: GridLayout) {
        val fijo = audio.isVolumeFixed
        val vista = modulo(grilla, null, "Volumen", 0, 2, filas = 2, texto = {
            if (fijo) "Lo maneja la tele" else "↑ ↓ para cambiar"
        }) {}
        vista.addView(texto("", 40f, Color.WHITE, true).apply { gravity = Gravity.CENTER; tag = "nivel" }, 0)
        if (fijo) return
        val releer = Runnable { pintarNivel(vista) }
        vista.setOnKeyListener { _, codigo, evento ->
            if (evento.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val direccion = when (codigo) {
                KeyEvent.KEYCODE_DPAD_UP -> AudioManager.ADJUST_RAISE
                KeyEvent.KEYCODE_DPAD_DOWN -> AudioManager.ADJUST_LOWER
                else -> return@setOnKeyListener false
            }
            val antes = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direccion, 0)
            // Por Bluetooth, si no cambió es que ya está al máximo o en cero: no hay tecla que mandar.
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == antes && !suenaPorBluetooth()) {
                // Con el sonido saliendo por la tele (HDMI), Android no deja tocar su volumen: se manda
                // la tecla de volumen como si fuera el control, y el Fire Stick se la pasa a la tele.
                val tecla = if (direccion == AudioManager.ADJUST_RAISE) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, tecla))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, tecla))
            }
            // Solo cambia el número: repintar los diez cuadros por cada toque (y otra vez al rato) era lo que
            // hacía trabajar de más y subir la memoria al dejar apretado.
            pintarNivel(vista)
            // Algunas salidas (Bluetooth) tardan un instante en informar el número nuevo.
            vista.removeCallbacks(releer)
            vista.postDelayed(releer, 250)
            true
        }
        // Al abrir, el primer número a veces sale viejo: se relee enseguida.
        vista.postDelayed(releer, 300)
    }

    private fun pintarNivel(vista: View) {
        vista.findViewWithTag<TextView>("nivel")?.text =
            if (audio.isVolumeFixed) "–" else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toString()
    }

    private fun suenaPorBluetooth(): Boolean = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }

    fun refrescar() {
        pintarModulos()
        pintarNotificaciones()
    }

    private fun pintarModulos() {
        for (m in modulos) pintarModulo(m)
    }

    private fun pintarModulo(m: Modulo) {
        val foco = m.vista.isFocused
        m.detalle.text = m.texto()
        m.detalle.visibility = if (m.detalle.text.isBlank()) View.GONE else View.VISIBLE
        m.vista.findViewWithTag<TextView>("nivel")?.text =
            if (audio.isVolumeFixed) "–" else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toString()
        pintar(m.vista, foco, listOfNotNull(m.titulo, m.detalle, m.vista.findViewWithTag("nivel")), m.icono, m.encendido())
    }

    /** Lo elegido se pone blanco con letras oscuras; lo encendido lleva el ícono del color del tema. */
    private fun pintar(v: View, foco: Boolean, textos: List<TextView>, icono: ImageView?, encendido: Boolean) {
        v.background = if (foco) GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(RADIO).toFloat()
        } else Vidrio(dp(RADIO).toFloat(), 0x14FFFFFF)
        for (t in textos) {
            val gris = t.tag == "gris"
            t.setTextColor(if (foco) (if (gris) 0xFF55575E.toInt() else 0xFF111114.toInt()) else (if (gris) GRIS else Color.WHITE))
        }
        icono?.imageTintList = ColorStateList.valueOf(
            when {
                encendido && !(foco && Tema.estilo == Estilo.CLASICO) -> Tema.color
                foco -> 0xFF111114.toInt()
                else -> Color.WHITE
            },
        )
    }

    private fun conectado(): Boolean = try {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo?.isConnected == true
    } catch (e: Exception) {
        false
    }

    private fun bluetooth(): Boolean = try {
        @Suppress("DEPRECATION")
        BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
    } catch (e: Exception) {
        false // sin permiso para leerlo: queda como apagado, igual se puede abrir
    }

    private fun memoriaLibre(): Long {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    /** Pantalla negra encima de todo; la música sigue. Cualquier botón la saca. */
    private fun apagarPantalla() {
        val negra = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        negra.setContentView(View(context).apply { setBackgroundColor(Color.BLACK) })
        negra.setOnKeyListener { d, _, evento ->
            if (evento.action == KeyEvent.ACTION_UP) d.dismiss()
            true
        }
        negra.show()
    }

    private fun rotulo(t: String) = texto(t, 12f, GRIS, true).apply { letterSpacing = 0.2f }

    private fun texto(t: String, sp: Float, color: Int, medio: Boolean = false) = TextView(context).apply {
        text = t
        setTextColor(color)
        if (color == GRIS) tag = "gris"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (medio) typeface = Typeface.DEFAULT_BOLD
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        const val GRIS = 0xFF8B8D94.toInt()
        const val CELDA = 104
        const val ESPACIO = 12
        const val RADIO = 20
    }
}

/**
 * Temporizador para dormir: cada OK pasa por apagado, 15, 30, 60 y 90 minutos. Al terminar baja el volumen de a poco
 * (si la tele deja), pausa lo que suena y deja el volumen como estaba.
 */
object Dormir {
    private val OPCIONES = intArrayOf(0, 15, 30, 60, 90)
    private val mano = Handler(Looper.getMainLooper())
    private var indice = 0
    private var termina = 0L

    val activo get() = termina > System.currentTimeMillis()

    fun texto(): String = if (!activo) "Apagado" else "Faltan ${(termina - System.currentTimeMillis()) / 60_000 + 1} min"

    fun siguiente(c: Context) {
        indice = if (activo) (indice + 1) % OPCIONES.size else 1
        mano.removeCallbacksAndMessages(null)
        val minutos = OPCIONES[indice]
        termina = if (minutos == 0) 0L else System.currentTimeMillis() + minutos * 60_000L
        val app = c.applicationContext
        if (minutos > 0) mano.postDelayed({ apagar(app) }, minutos * 60_000L)
    }

    private fun apagar(c: Context) {
        indice = 0
        termina = 0
        val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.isVolumeFixed) {
            pausar(audio)
            return
        }
        val original = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        var nivel = original
        mano.post(object : Runnable {
            override fun run() {
                if (nivel > 0) {
                    nivel--
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, nivel, 0)
                    mano.postDelayed(this, 1500)
                } else {
                    pausar(audio)
                    mano.postDelayed({ audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0) }, 1500)
                }
            }
        })
    }

    private fun pausar(audio: AudioManager) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }
}
