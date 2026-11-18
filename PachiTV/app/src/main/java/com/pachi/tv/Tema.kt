package com.pachi.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Colores de Ziota. "Vice" es la paleta Vice City (rosa, cian, violeta, durazno y azul noche);
 * "Clásico" es el tema de siempre (casi negro con selección crema). Se cambia en Ajustes.
 */
object Tema {
    const val ROSA = 0xFFFF5CA8.toInt()
    const val CIAN = 0xFF00F0FF.toInt()
    const val VIOLETA = 0xFFBC6CFF.toInt()
    const val DURAZNO = 0xFFFFB86B.toInt()
    const val NOCHE = 0xFF0B0F2B.toInt()
    const val CREMA_CLASICA = 0xFFF3E9D2.toInt()

    var vice = true
        private set

    fun cargar(c: Context) {
        vice = c.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean("tema_vice", true)
    }

    fun cambiar(c: Context, activar: Boolean) {
        c.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putBoolean("tema_vice", activar).commit()
        vice = activar
    }

    /** Color de la selección (fila del menú, botón enfocado, borde de la isla). */
    val acento get() = if (vice) CIAN else CREMA_CLASICA

    /** Rayita del costado que avisa que hay un menú. */
    val rayita get() = if (vice) intArrayOf(ROSA, VIOLETA, CIAN)
    else intArrayOf(0xFF6CBE45.toInt(), 0xFFFFE600.toInt(), 0xFFEF3B2D.toInt(), 0xFF8E2A9E.toInt(), 0xFF1E9CE6.toInt())

    /** Relleno de los paneles de vidrio (menú lateral y menús): más opaco donde no se puede difuminar lo de atrás. */
    fun rellenoPanel(hayDifuminado: Boolean) = when {
        vice && hayDifuminado -> 0x8C1A1D45.toInt()
        vice -> 0xEB12163A.toInt()
        hayDifuminado -> 0x8C3A3A40.toInt()
        else -> 0xF52C2C2E.toInt()
    }
}

/**
 * Vidrio igual al de la app del celular: relleno translúcido, un brillo que baja desde arriba,
 * borde finito blanco y una línea de luz arriba de todo.
 */
class Vidrio(
    private val radio: Float,
    private val relleno: Int,
    private val redondo: Boolean = false,
    private val grosorBorde: Float = 1.5f,
) : Drawable() {
    private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = relleno }
    private val pBrillo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = grosorBorde
        color = 0x24FFFFFF
    }
    private val pLuz = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        color = 0x2EFFFFFF
    }
    private val caja = RectF()

    override fun draw(canvas: Canvas) {
        caja.set(bounds)
        val r = if (redondo) caja.height() / 2 else radio
        pBrillo.shader = LinearGradient(0f, caja.top, 0f, caja.bottom, 0x1CFFFFFF, 0x06FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(caja, r, r, pRelleno)
        canvas.drawRoundRect(caja, r, r, pBrillo)
        caja.inset(grosorBorde / 2, grosorBorde / 2)
        canvas.drawRoundRect(caja, r, r, pBorde)
        // Línea de luz arriba, sin tocar las esquinas redondeadas.
        val margen = maxOf(r, caja.width() * 0.06f)
        canvas.drawLine(caja.left + margen, caja.top + 1.5f, caja.right - margen, caja.top + 1.5f, pLuz)
    }

    override fun setAlpha(alpha: Int) {
        pRelleno.alpha = Math.round(android.graphics.Color.alpha(relleno) * alpha / 255f)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        pRelleno.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
