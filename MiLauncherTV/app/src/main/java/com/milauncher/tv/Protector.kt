package com.milauncher.tv

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock

/**
 * Protector de pantalla: la ilustración de los gatos a pantalla completa con un paneo muy lento,
 * más reloj y fecha. El reloj cambia de esquina cada pocos minutos para no marcar la pantalla.
 */
class VistaProtector(context: Context) : FrameLayout(context) {

    private val imagen = ImageView(context).apply {
        setImageDrawable(Portada.cargar(context))
        scaleType = ImageView.ScaleType.MATRIX
        val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        if (Tema.de(context).oscuro && prefs.getBoolean("portada_noche", true)) colorFilter = Tema.filtroNoche()
    }
    private val reloj = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var animador: ValueAnimator? = null
    private var relojALaDerecha = true

    private val cambiarEsquina = object : Runnable {
        override fun run() {
            relojALaDerecha = !relojALaDerecha
            ubicarReloj()
            postDelayed(this, 3 * 60_000L)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(imagen, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xB0000000.toInt(), 0x00000000))
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280), Gravity.BOTTOM))

        reloj.addView(TextClock(context).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setShadowLayer(16f, 0f, 4f, 0xAA000000.toInt())
        })
        reloj.addView(TextClock(context).apply {
            format12Hour = "EEEE d 'de' MMMM"
            format24Hour = "EEEE d 'de' MMMM"
            setTextColor(0xEEFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setShadowLayer(12f, 0f, 3f, 0xAA000000.toInt())
        })
        addView(reloj)
        ubicarReloj()

        // Con una foto propia, el logo de Shikitari TV arriba a la izquierda.
        val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        if (Portada.esPersonalizada(context) && prefs.getBoolean("logo_portada", true)) {
            addView(ImageView(context).apply {
                setImageResource(R.drawable.logo_completo)
                adjustViewBounds = true
                alpha = 0.92f
            }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(170), Gravity.TOP or Gravity.START).apply {
                setMargins(dp(56), dp(40), 0, 0)
            })
        }
    }

    private fun ubicarReloj() {
        val lado = if (relojALaDerecha) Gravity.END else Gravity.START
        reloj.gravity = lado
        reloj.layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or lado).apply {
            setMargins(dp(64), 0, dp(64), dp(48))
        }
    }

    fun iniciar() {
        post {
            val d = imagen.drawable ?: return@post
            // Cubre toda la pantalla y recorre la ilustración de un lado al otro en 2 minutos.
            val escala = maxOf(height.toFloat() / d.intrinsicHeight, width.toFloat() / d.intrinsicWidth)
            val sobranteX = d.intrinsicWidth * escala - width
            val sobranteY = d.intrinsicHeight * escala - height
            animador?.cancel()
            animador = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 120_000L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val t = it.animatedValue as Float
                    imagen.imageMatrix = Matrix().apply {
                        setScale(escala, escala)
                        postTranslate(-sobranteX * t, -sobranteY * 0.6f)
                    }
                }
                start()
            }
            postDelayed(cambiarEsquina, 3 * 60_000L)
        }
    }

    fun detener() {
        animador?.cancel()
        animador = null
        removeCallbacks(cambiarEsquina)
    }

    private fun dp(valor: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), resources.displayMetrics).toInt()
}

/** El protector que usa el sistema cuando la TV queda sin uso. */
class ProtectorService : DreamService() {

    private var vista: VistaProtector? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        vista = VistaProtector(this)
        setContentView(vista)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        vista?.iniciar()
    }

    override fun onDreamingStopped() {
        vista?.detener()
        super.onDreamingStopped()
    }
}

/** Vista previa del protector desde los Ajustes del launcher; cualquier tecla la cierra. */
class ProtectorActivity : Activity() {

    private lateinit var vista: VistaProtector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vista = VistaProtector(this)
        setContentView(vista)
    }

    override fun onResume() {
        super.onResume()
        vista.iniciar()
    }

    override fun onPause() {
        vista.detener()
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        finish()
        return true
    }
}
