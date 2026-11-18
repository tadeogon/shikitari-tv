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
 * Temas de Ziota. Los tres "neón" (Vice City, Neon Sunset y Digital Pulse) comparten el mismo dibujo:
 * cielo, sol con rayas, cuadrícula en el piso, letras altas y un degradado como sello.
 * "Clásico" es el de siempre (casi negro con selección crema). Se cambia en Ajustes.
 */
enum class Estilo(
    val nombre: String,
    val fondo: Int,
    val cielo: Int,
    val degradado: IntArray,
    val sol: IntArray,
    val piso: Int,
    val clima: Int,
    val panel: Int,
    val tarjeta: Int,
) {
    VICE(
        "Vice City", 0xFF0B0F2B.toInt(), 0xFF3A1258.toInt(),
        intArrayOf(0xFF17A9E5.toInt(), 0xFF6263D9.toInt(), 0xFFE53D92.toInt(), 0xFFFD7742.toInt()),
        intArrayOf(0xFFFFB86B.toInt(), 0xFFE53D92.toInt()),
        0xFFE53D92.toInt(), 0xFFFFB86B.toInt(), 0xF512163A.toInt(), 0xE61C1F4D.toInt(),
    ),
    SUNSET(
        "Neon Sunset", 0xFF0A0A12.toInt(), 0xFF4A0C2E.toInt(),
        intArrayOf(0xFFFF0F7B.toInt(), 0xFFFF5C00.toInt(), 0xFFFFD000.toInt(), 0xFFA8E000.toInt()),
        intArrayOf(0xFFFFD000.toInt(), 0xFFFF0F7B.toInt()),
        0xFFFF0F7B.toInt(), 0xFFFFD000.toInt(), 0xF51A0F1E.toInt(), 0xE6221426.toInt(),
    ),
    PULSE(
        "Digital Pulse", 0xFF0F172A.toInt(), 0xFF2E1065.toInt(),
        intArrayOf(0xFFDDD6FE.toInt(), 0xFFF472B6.toInt(), 0xFFC084FC.toInt()),
        intArrayOf(0xFFF472B6.toInt(), 0xFF7C3AED.toInt()),
        0xFFF472B6.toInt(), 0xFFDDD6FE.toInt(), 0xF51A1640.toInt(), 0xE6221B4A.toInt(),
    ),
    CLASICO(
        "Clásico", 0xFF0B0B0F.toInt(), 0xFF0B0B0F.toInt(),
        intArrayOf(0xFFF3E9D2.toInt(), 0xFFF3E9D2.toInt()),
        intArrayOf(0, 0), 0, 0xFFFFFFFF.toInt(), 0xF52C2C2E.toInt(), 0xFF26262C.toInt(),
    ),
}

object Tema {
    const val CREMA_CLASICA = 0xFFF3E9D2.toInt()

    var estilo = Estilo.VICE
        private set

    /** Cualquiera de los temas neón (no el clásico). */
    val neon get() = estilo != Estilo.CLASICO

    /** [porDefecto]: Ziota arranca en Vice City; Solari (versión formal) se queda en el clásico. */
    fun cargar(c: Context, porDefecto: Estilo) {
        val p = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val guardado = p.getString("tema", null)
        estilo = when {
            guardado != null -> Estilo.values().firstOrNull { it.name == guardado } ?: porDefecto
            // La versión 1.30 guardaba solo sí/no.
            p.contains("tema_vice") -> if (p.getBoolean("tema_vice", true)) Estilo.VICE else Estilo.CLASICO
            else -> porDefecto
        }
    }

    /** Pasa al siguiente tema: Vice City → Neon Sunset → Digital Pulse → Clásico → Vice City. */
    fun siguiente(c: Context) {
        val todos = Estilo.values()
        estilo = todos[(estilo.ordinal + 1) % todos.size]
        c.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putString("tema", estilo.name).commit()
    }

    val degradado get() = estilo.degradado
    val fondo get() = estilo.fondo

    /** Color de selección sólido (fila del menú en el clásico, destellos de la isla). */
    val acento get() = if (neon) estilo.degradado[0] else CREMA_CLASICA

    /** Rayita del costado que avisa que hay un menú. */
    val rayita get() = if (neon) estilo.degradado
    else intArrayOf(0xFF6CBE45.toInt(), 0xFFFFE600.toInt(), 0xFFEF3B2D.toInt(), 0xFF8E2A9E.toInt(), 0xFF1E9CE6.toInt())

    /** Relleno de los paneles de vidrio: más transparente donde el sistema puede difuminar lo de atrás. */
    fun rellenoPanel(hayDifuminado: Boolean) = when {
        hayDifuminado && neon -> (estilo.panel and 0x00FFFFFF) or 0x8C000000.toInt()
        hayDifuminado -> 0x8C3A3A40.toInt()
        else -> estilo.panel
    }

    /** Relleno de la isla de música: el fondo del tema casi opaco. */
    val rellenoIsla get() = (estilo.fondo and 0x00FFFFFF) or 0xF0000000.toInt()
}

/**
 * Vidrio igual al de la app del celular: relleno translúcido, un brillo que baja desde arriba,
 * borde finito blanco y una línea de luz arriba de todo. Con [anillo] el borde pasa a ser
 * el degradado del tema (así se marca lo enfocado en los temas neón).
 */
class Vidrio(
    private val radio: Float,
    private val relleno: Int,
    private val redondo: Boolean = false,
    private val grosorBorde: Float = 1.5f,
    private val grosorAnillo: Float = 5f,
    anillo: IntArray? = null,
) : Drawable() {
    var anillo: IntArray? = anillo
        set(valor) {
            field = valor
            invalidateSelf()
        }
    private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = relleno }
    private val pBrillo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pLuz = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        color = 0x2EFFFFFF
    }
    private val caja = RectF()

    override fun draw(canvas: Canvas) {
        caja.set(bounds)
        val r = if (redondo) caja.height() / 2 else radio
        pBrillo.shader = LinearGradient(0f, caja.top, 0f, caja.bottom, 0x1CFFFFFF, 0x06FFFFFF, Shader.TileMode.CLAMP)
        if (relleno != 0) canvas.drawRoundRect(caja, r, r, pRelleno)
        canvas.drawRoundRect(caja, r, r, pBrillo)
        val colores = anillo
        val grosor = if (colores != null) grosorAnillo else grosorBorde
        pBorde.strokeWidth = grosor
        if (colores != null) {
            pBorde.color = android.graphics.Color.WHITE // opaco: si no, el degradado sale apagado
            pBorde.shader = LinearGradient(caja.left, 0f, caja.right, 0f, colores, null, Shader.TileMode.CLAMP)
        } else {
            pBorde.shader = null
            pBorde.color = 0x24FFFFFF
        }
        caja.inset(grosor / 2, grosor / 2)
        val ri = maxOf(0f, r - grosor / 2)
        canvas.drawRoundRect(caja, ri, ri, pBorde)
        if (colores == null) {
            // Línea de luz arriba, sin tocar las esquinas redondeadas.
            val margen = maxOf(r, caja.width() * 0.06f)
            canvas.drawLine(caja.left + margen, caja.top + 1.5f, caja.right - margen, caja.top + 1.5f, pLuz)
        }
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
