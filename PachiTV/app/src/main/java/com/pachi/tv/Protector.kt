package com.pachi.tv

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.os.SystemClock
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * El logo cruzando la pantalla en diagonal como el de los DVD: cambia de color en cada rebote
 * y, si pega justo en una esquina, festeja con papelitos de colores.
 */
class LogoRebotando(context: Context) : View(context) {

    private class Papelito(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, val lado: Float)

    private val pincel = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val pincelPapel = Paint()
    private val papelitos = mutableListOf<Papelito>()
    private var logo: Bitmap? = null
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var ultimo = 0L
    private var tono = 0f

    // Versión formal: más lento, sin cambiar de color ni tirar papelitos.
    private val formal = context.resources.getBoolean(R.bool.marca_formal)

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        val original = BitmapFactory.decodeResource(resources, R.drawable.logo_pachi)
        val ancho = w / 6
        val chico = Bitmap.createScaledBitmap(original, ancho, ancho * original.height / original.width, true)
        logo = chico
        x = Random.nextFloat() * (w - chico.width)
        y = Random.nextFloat() * (h - chico.height)
        val velocidad = if (formal) w / 20f else w / 12f
        vx = if (Random.nextBoolean()) velocidad else -velocidad
        vy = if (Random.nextBoolean()) velocidad * 0.75f else -velocidad * 0.75f
        ultimo = SystemClock.uptimeMillis()
    }

    override fun onDraw(canvas: Canvas) {
        val imagen = logo ?: return
        val ahora = SystemClock.uptimeMillis()
        val pasado = ((ahora - ultimo) / 1000f).coerceAtMost(0.1f)
        ultimo = ahora
        x += vx * pasado
        y += vy * pasado
        val maxX = (width - imagen.width).toFloat()
        val maxY = (height - imagen.height).toFloat()
        val reboteX = x <= 0f || x >= maxX
        val reboteY = y <= 0f || y >= maxY
        if (reboteX) {
            x = x.coerceIn(0f, maxX)
            vx = -vx
        }
        if (reboteY) {
            y = y.coerceIn(0f, maxY)
            vy = -vy
        }
        if (!formal && (reboteX || reboteY)) cambiarColor()
        if (!formal && reboteX && reboteY) festejar()

        dibujarPapelitos(canvas, pasado)
        canvas.drawBitmap(imagen, x, y, pincel)
        postInvalidateOnAnimation()
    }

    /** El logo es blanco: en cada rebote se pinta de un color nuevo, bien vivo para que se lea sobre el negro. */
    private fun cambiarColor() {
        tono = (tono + 50f + Random.nextFloat() * 80f) % 360f
        val color = Color.HSVToColor(floatArrayOf(tono, 0.65f, 1f))
        pincel.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun festejar() {
        repeat(120) {
            val angulo = Random.nextFloat() * 6.283f
            val fuerza = width * (0.1f + Random.nextFloat() * 0.5f)
            papelitos += Papelito(
                x + (logo?.width ?: 0) / 2f,
                y + (logo?.height ?: 0) / 2f,
                cos(angulo) * fuerza,
                sin(angulo) * fuerza,
                COLORES[it % COLORES.size],
                width / 240f + Random.nextFloat() * width / 240f,
            )
        }
    }

    private fun dibujarPapelitos(canvas: Canvas, pasado: Float) {
        val iterador = papelitos.iterator()
        while (iterador.hasNext()) {
            val p = iterador.next()
            p.vy += height * 0.9f * pasado
            p.x += p.vx * pasado
            p.y += p.vy * pasado
            if (p.y > height) {
                iterador.remove()
                continue
            }
            pincelPapel.color = p.color
            canvas.drawRect(p.x, p.y, p.x + p.lado, p.y + p.lado, pincelPapel)
        }
    }

    private companion object {
        val COLORES = intArrayOf(
            0xFF6CBE45.toInt(),
            0xFFFFE600.toInt(),
            0xFFEF3B2D.toInt(),
            0xFF8E2A9E.toInt(),
            0xFF1E9CE6.toInt(),
        )
    }
}

/** Protector de pantalla del sistema. */
class ProtectorService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        setContentView(LogoRebotando(this))
    }
}

/** Para ver el protector desde Ajustes: cualquier botón del control lo cierra. */
class ProtectorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LogoRebotando(this))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        finish()
        return true
    }
}
