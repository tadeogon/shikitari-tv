package com.pachi.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
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
        if (Tema.vice) {
            // Vice City: azul noche con un atardecer violeta y rosa que baja desde arriba.
            canvas.drawColor(Tema.NOCHE)
            pincel.shader = LinearGradient(0f, 0f, 0f, h * 0.6f, intArrayOf(0xFF4C1658.toInt(), 0xFF2A1250.toInt(), Tema.NOCHE), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h * 0.6f, pincel)
            pincel.shader = null
            return
        }
        canvas.drawColor(BASE)
        pincel.shader = LinearGradient(0f, 0f, 0f, h * 0.52f, color, BASE, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.52f, pincel)
        pincel.shader = null
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
