package com.pachi.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.random.Random

/**
 * Música en pantalla: si suena música y no se toca el control por 10 segundos, el tema ocupa toda la pantalla.
 * Sale de la isla de música. En el Fire Stick Spotify no manda la tapa del disco: queda una nota en su lugar
 * y la pantalla se sostiene con el título grande. Para no marcar la tele, todo se corre un poco cada minuto.
 * La idea viene de Aura TV (github.com/cookedzera/aura-tv, licencia MIT); el código es propio de Ziota.
 */
class MusicaEnPantalla(context: Context, private val isla: IslaMusica) : FrameLayout(context) {

    private val tapa = ImageView(context).apply {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
            }
        }
        clipToOutline = true
        setBackgroundColor(0xFF16181D.toInt())
    }
    private val titulo = texto(40f, Color.WHITE, true).apply { maxLines = 2 }
    private val artista = texto(20f, CentroDeControl.GRIS).apply { maxLines = 1 }
    private val relleno = View(context)
    private val tiempos = texto(14f, CentroDeControl.GRIS)
    private val bloque = LinearLayout(context)
    private var ultimaTapa: Bitmap? = null
    private var segundos = 0

    private val tic = object : Runnable {
        override fun run() {
            pintarProgreso()
            // Cada minuto, todo se corre unos puntos para que la imagen quieta no marque la pantalla.
            if (++segundos % 60 == 0) {
                bloque.translationX = Random.nextInt(-dp(16), dp(16)).toFloat()
                bloque.translationY = Random.nextInt(-dp(12), dp(12)).toFloat()
            }
            postDelayed(this, 1000)
        }
    }

    init {
        setBackgroundColor(FONDO)
        bloque.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(96), 0, dp(96), 0)
            addView(tapa, LinearLayout.LayoutParams(dp(220), dp(220)).apply { marginEnd = dp(48) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titulo)
                addView(artista, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
                addView(FrameLayout(context).apply {
                    background = redondo(0xFF2A2C33.toInt())
                    addView(relleno, LayoutParams(0, LayoutParams.MATCH_PARENT))
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(32) })
                addView(tiempos, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(bloque, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        addView(TextClock(context).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(CentroDeControl.GRIS)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { topMargin = dp(32); rightMargin = dp(48) })
        addView(texto(13f, 0xFF5B5F68.toInt()).apply {
            gravity = Gravity.CENTER
            text = "OK pausa  ·  ← → cambian de tema  ·  cualquier otro botón vuelve"
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(24) })
        relleno.background = redondo(Tema.color)
        refrescar()
    }

    /** Vuelve a leer el tema de la isla (se llama cuando cambia la canción o la pausa). */
    fun refrescar() {
        titulo.text = isla.tituloActual
        val app = isla.appActual?.let { pkg ->
            try {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                null
            }
        }
        artista.text = listOfNotNull(isla.artistaActual.toString().takeIf { it.isNotBlank() }, app).joinToString(" · ")
        val imagen = isla.tapaActual
        if (imagen !== ultimaTapa || tapa.drawable == null) {
            ultimaTapa = imagen
            ponerTapa(tapa, imagen, dp(64))
            // Con tapa, el fondo toma apenas su color; sin tapa, queda casi negro.
            setBackgroundColor(imagen?.let { mezclar(colorPromedio(it), FONDO, 0.86f) } ?: FONDO)
        }
        pintarProgreso()
    }

    private fun pintarProgreso() {
        val p = isla.progreso()
        val ancho = (relleno.parent as View).width
        relleno.layoutParams = relleno.layoutParams.apply { width = if (p == null || ancho == 0) 0 else (ancho * p.first / p.second).toInt() }
        tiempos.text = when {
            !isla.sonando -> "En pausa"
            p == null -> ""
            else -> "${minutos(p.first)}  /  ${minutos(p.second)}"
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(tic)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tic)
        super.onDetachedFromWindow()
    }

    private fun minutos(ms: Long): String {
        val s = ms / 1000
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    private fun colorPromedio(b: Bitmap): Int = try {
        Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
    } catch (e: Exception) {
        FONDO
    }

    private fun mezclar(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private fun redondo(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(2).toFloat()
    }

    private fun texto(sp: Float, color: Int, medio: Boolean = false) = TextView(context).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (medio) typeface = Typeface.DEFAULT_BOLD
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        val FONDO = 0xFF0A0A0C.toInt()
    }
}
