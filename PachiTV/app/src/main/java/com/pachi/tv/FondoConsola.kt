package com.pachi.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
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
        if (Tema.amoled) {
            dibujarFoto(canvas, w, h)
            return
        }
        canvas.drawColor(BASE)
        pincel.shader = LinearGradient(0f, 0f, 0f, h * 0.52f, color, BASE, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.52f, pincel)
        pincel.shader = null
    }

    // Temas AMOLED: la foto con su velo se arma UNA vez en una imagen de 16 bits del tamaño de la pantalla.
    // Antes cada cuadro estiraba la foto y pintaba dos degradados encima: mucho trabajo para el Fire Stick.
    private var armada: Bitmap? = null
    private var armadaDe = 0

    private fun dibujarFoto(canvas: Canvas, w: Float, h: Float) {
        val id = Tema.estilo.imagen
        val actual = armada
        if (actual == null || armadaDe != id || actual.width != width || actual.height != height) {
            armada = armar(id, w, h)
            armadaDe = id
        }
        val lista = armada
        if (lista != null) canvas.drawBitmap(lista, 0f, 0f, null) else canvas.drawColor(Color.BLACK)
    }

    private fun armar(id: Int, w: Float, h: Float): Bitmap? = try {
        val f = BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
        Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.RGB_565).also { bmp ->
            val c = Canvas(bmp)
            c.drawColor(Color.BLACK)
            val escala = maxOf(w / f.width, h / f.height)
            val matriz = Matrix()
            matriz.setScale(escala, escala)
            matriz.postTranslate((w - f.width * escala) / 2, (h - f.height * escala) / 2)
            c.drawBitmap(f, matriz, Paint(Paint.FILTER_BITMAP_FLAG).apply { isDither = true })
            f.recycle()
            // Velo a la izquierda y abajo: la hora y los nombres se leen siempre, y la foto luce a la derecha.
            pincel.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(0xE6000000.toInt(), 0x8C000000.toInt(), 0x1A000000), floatArrayOf(0f, 0.38f, 0.7f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, pincel)
            pincel.shader = LinearGradient(0f, h, 0f, h * 0.5f, 0xD9000000.toInt(), 0, Shader.TileMode.CLAMP)
            c.drawRect(0f, h * 0.5f, w, h, pincel)
            pincel.shader = null
        }
    } catch (e: Throwable) {
        null // sin memoria: queda negro
    }

    /** Con otra app adelante no hace falta: se suelta y al volver se arma de nuevo. */
    fun soltar() {
        armada = null
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
