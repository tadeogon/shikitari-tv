package com.pachi.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View

/**
 * Fondo minimalista dibujado por código: casi negro, con una franja arriba a todo el ancho
 * del color de la app enfocada. El cambio se anima a mano para no depender de las animaciones del sistema.
 */
class FondoConsola(context: Context) : View(context) {

    private val pincel = Paint().apply { isDither = true }
    private var color = BASE
    private var desde = BASE
    private var hacia = BASE
    private var inicio = 0L

    private val paso = object : Runnable {
        override fun run() {
            val t = ((SystemClock.uptimeMillis() - inicio) / DURACION).coerceAtMost(1f)
            val curva = 1 - (1 - t) * (1 - t) * (1 - t)
            color = mezclar(desde, hacia, curva)
            invalidate()
            if (t < 1f) postOnAnimation(this)
        }
    }

    fun cambiarColor(nuevo: Int) {
        removeCallbacks(paso)
        desde = color
        hacia = nuevo
        inicio = SystemClock.uptimeMillis()
        postOnAnimation(paso)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (Tema.neon) {
            dibujarNeon(canvas, w, h, Tema.estilo)
            return
        }
        canvas.drawColor(BASE)
        pincel.shader = LinearGradient(0f, 0f, 0f, h * 0.52f, color, BASE, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.52f, pincel)
        pincel.shader = null
    }

    private val pincelLinea = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }

    /**
     * Temas neón, estilo atardecer retro pero tranquilo: cielo del color del tema, un brillo apenas visible
     * en el horizonte y una cuadrícula tenue en el piso que se desvanece hacia arriba. Nada compite con las apps.
     */
    private fun dibujarNeon(canvas: Canvas, w: Float, h: Float, e: Estilo) {
        canvas.drawColor(e.fondo)
        val arriba = mezclar(e.cielo, e.fondo, 0.3f)
        pincel.shader = LinearGradient(0f, 0f, 0f, h * 0.7f, intArrayOf(arriba, mezclar(arriba, e.fondo, 0.6f), e.fondo),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.7f, pincel)

        // Brillo suave sobre el horizonte, en vez del sol.
        val horizonte = h * 0.76f
        val color = e.piso and 0x00FFFFFF
        pincel.shader = RadialGradient(w / 2, horizonte, w * 0.42f, (color or 0x1F000000), color, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1f, 0.22f, w / 2, horizonte)
        canvas.drawCircle(w / 2, horizonte, w * 0.42f, pincel)
        canvas.restore()
        pincel.shader = null

        // Piso: cuadrícula tenue que se aclara hacia adelante y desaparece en el horizonte.
        pincelLinea.shader = LinearGradient(0f, horizonte, 0f, h, color, color or 0x2E000000, Shader.TileMode.CLAMP)
        for (k in 1..7) {
            val t = k / 7f
            val yy = horizonte + (h - horizonte) * t * t
            canvas.drawLine(0f, yy, w, yy, pincelLinea)
        }
        val fuga = horizonte - (h - horizonte) * 0.6f
        val proporcion = (horizonte - fuga) / (h - fuga)
        for (j in -12..12) {
            val abajo = w / 2 + j * w * 0.09f
            val enHorizonte = w / 2 + (abajo - w / 2) * proporcion
            canvas.drawLine(enHorizonte, horizonte, abajo, h, pincelLinea)
        }
    }

    private fun mezclar(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private companion object {
        const val DURACION = 420f
        val BASE = 0xFF0B0B0F.toInt()
    }
}
