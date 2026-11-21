package com.pachi.tv

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Apps abiertas, como en el celular: la última usada grande en el medio y las anteriores apiladas a la izquierda.
 * ← → se mueve, OK la abre, ↑ la cierra, ↓ la protege; a la derecha, "Cerrar todo" con la memoria.
 * Se abre con Inicio dos veces.
 *
 * Protegidas: las que elegiste con ↓ y la que está sonando música nunca se cierran (ni con "Cerrar todo").
 * Las de [NUNCA_MOSTRAR] (como WOL Puente, que prende la PC) ni aparecen, para no cerrarlas sin querer.
 * La idea viene de Aura TV (github.com/cookedzera/aura-tv, licencia MIT); el código es propio de Ziota.
 *
 * Qué apps aparecen: con el permiso de uso (`appops set com.pachi.tv GET_USAGE_STATS allow`, una vez por ADB)
 * son las usadas en las últimas horas; sin él, las que se abrieron desde Ziota.
 */
class AppsAbiertas(
    context: Context,
    fondo: Bitmap?,
    private val app: Datos,
) : FrameLayout(context) {

    class Datos(
        val recientes: List<String>,
        val nombre: (String) -> String,
        val icono: (String) -> Drawable?,
        val logo: (String) -> Drawable?,
        val abrir: (String) -> Unit,
        /** La app que está pasando música ahora (se protege sola). */
        val musica: String? = null,
    )

    private class Abierta(val pkg: String, val cuando: Long)

    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val elegidas = prefs.getStringSet(CLAVE_PROTEGIDAS, emptySet())!!.toMutableSet()
    private fun protegida(pkg: String) = pkg in elegidas || pkg == app.musica
    private val lista = buscar().toMutableList()
    private var indice = 0
    private var enBoton = false

    private val icono = ImageView(context)
    private val nombre = texto(24f, Color.WHITE, true)
    private val cuando = texto(15f, CentroDeControl.GRIS)
    private val cartas = Array(3) { FrameLayout(context) }
    private val logos = Array(3) { ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }
    private val boton = FrameLayout(context)
    private val iconoBoton = ImageView(context).apply { setImageResource(R.drawable.ic_borrar) }
    private val memoria = texto(14f, CentroDeControl.GRIS).apply { gravity = Gravity.CENTER }
    private val barra = View(context)
    private val vacio = texto(18f, CentroDeControl.GRIS).apply { gravity = Gravity.CENTER; text = "No hay apps abiertas" }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        if (fondo != null) addView(ImageView(context).apply {
            setImageBitmap(fondo)
            scaleType = ImageView.ScaleType.FIT_XY
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(View(context).apply { setBackgroundColor(if (fondo != null) 0xBF0A0A0C.toInt() else 0x990A0A0C.toInt()) },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Cabecera: ícono, nombre y cuándo se usó.
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(icono, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })
            addView(nombre)
            addView(cuando, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(16) })
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(128); topMargin = dp(48) })

        // Cartas: de atrás para adelante (2 y 1 son las anteriores, apiladas a la izquierda).
        for (i in 2 downTo 0) {
            cartas[i].addView(logos[i], LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(150), dp(90), dp(150), dp(90))
            })
            addView(cartas[i], LayoutParams(dp(ANCHO), dp(ALTO)).apply { leftMargin = dp(128); topMargin = dp(120) })
        }

        // Derecha: Cerrar todo y la memoria.
        boton.addView(iconoBoton, LayoutParams(dp(28), dp(28), Gravity.CENTER))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(boton, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(texto(15f, Color.WHITE, true).apply { text = "Cerrar todo"; gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(24)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(memoria)
            addView(FrameLayout(context).apply {
                background = redondo(0x33FFFFFF, dp(2))
                addView(barra, LayoutParams(0, LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(dp(96), dp(4)).apply { topMargin = dp(8) })
        }, LayoutParams(dp(160), LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(40) })

        addView(vacio, LayoutParams(dp(ANCHO), dp(ALTO)).apply { leftMargin = dp(128); topMargin = dp(120) })
        addView(texto(13f, CentroDeControl.GRIS).apply {
            gravity = Gravity.CENTER
            text = "OK abre  ·  ↑ cierra esta app  ·  ↓ la protege  ·  → Cerrar todo  ·  Atrás vuelve"
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(24) })
        enBoton = lista.isEmpty()
        pintar()
        pintarMemoria(null)
    }

    /** Las teclas del visor. Atrás lo maneja la pantalla de inicio (cierra sin abrir nada). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (enBoton) { if (lista.isNotEmpty()) enBoton = false } else indice = minOf(indice + 1, lista.lastIndex)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!enBoton) { if (indice > 0) indice-- else enBoton = true }
            KeyEvent.KEYCODE_DPAD_UP -> if (!enBoton && lista.isNotEmpty()) cerrarUna()
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!enBoton) lista.getOrNull(indice)?.let { alternarProteccion(it.pkg) }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount > 0) return true // mantener OK no hace nada raro
                if (enBoton) cerrarTodo() else lista.getOrNull(indice)?.let { app.abrir(it.pkg) }
                return true
            }
            else -> return true
        }
        pintar()
        return true
    }

    private fun pintar() {
        vacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        val actual = lista.getOrNull(indice)
        icono.setImageDrawable(actual?.let { app.icono(it.pkg) })
        nombre.text = actual?.let { app.nombre(it.pkg) } ?: ""
        cuando.text = when {
            actual == null -> ""
            actual.pkg == app.musica -> "Pasando música · no se cierra"
            actual.pkg in elegidas -> "Protegida · no se cierra  (↓ para sacarla)"
            actual.cuando == 0L -> if (indice == 0) "La última que usaste" else ""
            System.currentTimeMillis() - actual.cuando < DateUtils.MINUTE_IN_MILLIS -> "Ahora mismo"
            else -> DateUtils.getRelativeTimeSpanString(actual.cuando, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
        for (i in 0..2) {
            val a = lista.getOrNull(indice + i)
            val carta = cartas[i]
            carta.visibility = if (a == null) View.GONE else View.VISIBLE
            if (a == null) continue
            logos[i].setImageDrawable(app.logo(a.pkg))
            // La de adelante, entera; las anteriores, más chicas, corridas a la izquierda y apagadas.
            val escala = 1f - 0.08f * i
            carta.scaleX = escala
            carta.scaleY = escala
            carta.translationX = -dp(56).toFloat() * i
            carta.alpha = if (i == 0) 1f else 0.5f - 0.2f * (i - 1)
            val color = if (protegida(a.pkg)) VERDE else Color.WHITE
            carta.background = if (i == 0 && !enBoton) GradientDrawable().apply {
                setColor(0xFF15161B.toInt())
                cornerRadius = dp(24).toFloat()
                setStroke(dp(3), color)
            } else Vidrio(dp(24).toFloat(), 0xF015161B.toInt())
        }
        boton.background = if (enBoton) redondo(Color.WHITE, dp(36)) else Vidrio(0f, 0x26FFFFFF, redondo = true)
        iconoBoton.imageTintList = android.content.res.ColorStateList.valueOf(if (enBoton) 0xFF111114.toInt() else Color.WHITE)
    }

    private fun alternarProteccion(pkg: String) {
        if (pkg == app.musica) return // la de la música se protege sola mientras suena
        if (!elegidas.remove(pkg)) elegidas += pkg
        prefs.edit().putStringSet(CLAVE_PROTEGIDAS, elegidas.toSet()).apply()
    }

    private fun cerrarUna() {
        if (protegida(lista[indice].pkg)) {
            // No se cierra: se avisa con un temblorcito de la carta.
            cartas[0].animate().translationX(dp(12).toFloat()).setDuration(60).withEndAction {
                cartas[0].animate().translationX(0f).setDuration(60).start()
            }.start()
            return
        }
        val a = lista.removeAt(indice)
        am.killBackgroundProcesses(a.pkg)
        indice = indice.coerceAtMost((lista.size - 1).coerceAtLeast(0))
        if (lista.isEmpty()) enBoton = true
        postDelayed({ pintarMemoria(null) }, 800)
    }

    private fun cerrarTodo() {
        val antes = libre()
        lista.removeAll { a ->
            if (protegida(a.pkg)) false else { am.killBackgroundProcesses(a.pkg); true }
        }
        indice = 0
        if (lista.isEmpty()) enBoton = true
        pintar()
        postDelayed({ pintarMemoria((libre() - antes).coerceAtLeast(0)) }, 1500)
    }

    private fun pintarMemoria(liberados: Long?) {
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val usada = info.totalMem - info.availMem
        memoria.text = if (liberados != null) "Liberaste ${liberados / MB} MB" else
            String.format(Locale("es", "AR"), "%.1f de %.1f GB\nen uso", usada / GB, info.totalMem / GB)
        barra.background = redondo(Color.WHITE, dp(2))
        barra.layoutParams = barra.layoutParams.apply { width = (dp(96) * usada / info.totalMem).toInt() }
    }

    private fun libre(): Long = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem

    /** Últimas apps usadas: con el permiso de uso, las de las últimas 6 horas; si no, la lista de Ziota. */
    private fun buscar(): List<Abierta> {
        val propio = context.packageName
        fun mostrable(pkg: String) = pkg != propio && pkg !in NUNCA_MOSTRAR
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ahora = System.currentTimeMillis()
            val usadas = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, ahora - 6 * DateUtils.HOUR_IN_MILLIS, ahora)
                .filter { it.lastTimeUsed > ahora - 6 * DateUtils.HOUR_IN_MILLIS && mostrable(it.packageName) && it.totalTimeInForeground > 0 }
                .groupBy { it.packageName }.map { (pkg, s) -> Abierta(pkg, s.maxOf { it.lastTimeUsed }) }
                .filter { context.packageManager.getLaunchIntentForPackage(it.pkg) != null || context.packageManager.getLeanbackLaunchIntentForPackage(it.pkg) != null }
                .sortedByDescending { it.cuando }
            if (usadas.isNotEmpty()) return usadas.take(MAXIMO)
        } catch (e: Exception) {
        }
        return app.recientes.filter { mostrable(it) }.take(MAXIMO).map { Abierta(it, 0L) }
    }

    private fun redondo(color: Int, radio: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radio.toFloat()
    }

    private fun texto(sp: Float, color: Int, medio: Boolean = false) = TextView(context).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (medio) typeface = Typeface.DEFAULT_BOLD
        maxLines = 2
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        /** Apps que trabajan de fondo y no hay que cerrar nunca: ni se muestran. */
        val NUNCA_MOSTRAR = setOf("ar.wolpuente")
        const val CLAVE_PROTEGIDAS = "protegidas"
        private const val VERDE = 0xFF4ADE80.toInt()
        const val ANCHO = 600
        const val ALTO = 340
        const val MAXIMO = 8
        const val MB = 1024 * 1024
        const val GB = 1024.0 * 1024 * 1024
    }
}
