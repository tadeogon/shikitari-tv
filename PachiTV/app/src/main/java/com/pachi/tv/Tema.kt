package com.pachi.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View

/**
 * Temas de Ziota. Los tres "neón" (Vice City, Neon Sunset y Digital Pulse) comparten el mismo dibujo:
 * cielo, un brillo suave en el horizonte y una cuadrícula tenue en el piso. El degradado del tema es el único
 * color fuerte de la pantalla y solo marca lo enfocado. "Clásico" es el de siempre (casi negro con selección crema).
 */
enum class Estilo(
    val nombre: String,
    val fondo: Int,
    val cielo: Int,
    val degradado: IntArray,
    val piso: Int,
    val tarjeta: Int,
    /** Temas AMOLED (1.45): foto de fondo líquida sobre negro puro; las tarjetas son vidrio transparente. */
    val imagen: Int = 0,
) {
    PRISMA(
        "Prisma", 0xFF000000.toInt(), 0xFF000000.toInt(),
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        0, 0x24FFFFFF, R.drawable.fondo_prisma,
    ),
    SEDA(
        "Seda negra", 0xFF000000.toInt(), 0xFF000000.toInt(),
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        0, 0x24FFFFFF, R.drawable.fondo_seda,
    ),
    VINO(
        "Vino líquido", 0xFF000000.toInt(), 0xFF000000.toInt(),
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        0, 0x24FFFFFF, R.drawable.fondo_vino,
    ),
    VICE(
        "Vice City", 0xFF0B0F2B.toInt(), 0xFF3A1258.toInt(),
        intArrayOf(0xFF17A9E5.toInt(), 0xFF6263D9.toInt(), 0xFFE53D92.toInt(), 0xFFFD7742.toInt()),
        0xFFE53D92.toInt(), 0xE61C1F4D.toInt(),
    ),
    SUNSET(
        "Neon Sunset", 0xFF0A0A12.toInt(), 0xFF4A0C2E.toInt(),
        intArrayOf(0xFFFF0F7B.toInt(), 0xFFFF5C00.toInt(), 0xFFFFD000.toInt(), 0xFFA8E000.toInt()),
        0xFFFF0F7B.toInt(), 0xE6221426.toInt(),
    ),
    PULSE(
        "Digital Pulse", 0xFF0F172A.toInt(), 0xFF2E1065.toInt(),
        intArrayOf(0xFFDDD6FE.toInt(), 0xFFF472B6.toInt(), 0xFFC084FC.toInt()),
        0xFFF472B6.toInt(), 0xE6221B4A.toInt(),
    ),
    CLASICO(
        "Clásico", 0xFF0B0B0F.toInt(), 0xFF0B0B0F.toInt(),
        intArrayOf(0xFFF3E9D2.toInt(), 0xFFF3E9D2.toInt()),
        0, 0xFF26262C.toInt(),
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
        // 1.45: quien venía de un tema neón viejo pasa una sola vez a Prisma, el nuevo de Ziota.
        if (porDefecto != Estilo.CLASICO && !p.getBoolean("tema_amoled_visto", false)) {
            if (estilo != Estilo.CLASICO) estilo = Estilo.PRISMA
            p.edit().putBoolean("tema_amoled_visto", true).putString("tema", estilo.name).apply()
        }
    }

    /** Temas con foto de fondo (Prisma, Seda negra, Vino líquido). */
    val amoled get() = estilo.imagen != 0

    /** Pasa al siguiente tema: Vice City → Neon Sunset → Digital Pulse → Clásico → Vice City. */
    fun siguiente(c: Context) {
        val todos = Estilo.values()
        estilo = todos[(estilo.ordinal + 1) % todos.size]
        c.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putString("tema", estilo.name).commit()
    }

    /** El único color de acento de cada tema: solo para lo encendido y el puntito de lo elegido. */
    val color: Int get() = when (estilo) {
        Estilo.PRISMA -> 0xFF7FD8FF.toInt()
        Estilo.SEDA -> 0xFFE9E9EE.toInt()
        Estilo.VINO -> 0xFFFF6FAE.toInt()
        Estilo.VICE -> 0xFF4CC2FF.toInt()
        Estilo.SUNSET -> 0xFFFF7A2E.toInt()
        Estilo.PULSE -> 0xFFC084FC.toInt()
        Estilo.CLASICO -> CREMA_CLASICA
    }

    /** Borde de lo enfocado: en neón, blanco liso (el color del tema va en el puntito). */
    val degradado get() = if (neon) intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()) else estilo.degradado
    val fondo get() = estilo.fondo

    /** Color de selección sólido (fila del menú en el clásico, destellos y barritas de la isla). En neón, blanco: el color queda para el foco. */
    val acento get() = if (neon) 0xFFFFFFFF.toInt() else CREMA_CLASICA

    /** Rayita del costado que avisa que hay un menú. En neón, blanca y lisa. */
    val rayita get() = if (neon) intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
    else intArrayOf(0xFF6CBE45.toInt(), 0xFFFFE600.toInt(), 0xFFEF3B2D.toInt(), 0xFF8E2A9E.toInt(), 0xFF1E9CE6.toInt())

    /**
     * Relleno de los paneles (menú lateral y menús): en neón es vidrio oscuro sin color, como el de la app
     * del celular, encima de lo de atrás difuminado (por el sistema o con [fotoBorrosa]).
     */
    fun rellenoPanel(hayDifuminado: Boolean) = when {
        hayDifuminado && neon -> 0x52000000
        neon -> 0x8C000000.toInt()
        hayDifuminado -> 0x8C3A3A40.toInt()
        else -> 0xF52C2C2E.toInt()
    }

    /** Si no se pudo sacar la foto borrosa: vidrio oscuro casi tapado, para que no se lea lo de atrás. */
    const val PANEL_SIN_FOTO = 0xF2101016.toInt()

    /** Relleno de la isla de música: vidrio oscuro sin color. */
    val rellenoIsla get() = 0x73000000

    /** Cuánto se achica la foto de lo de atrás: más chica, más borrosa y más liviana. */
    const val ESCALA_FOTO = 10

    /**
     * Vidrio esmerilado para equipos que no difuminan (Fire Stick): una foto muy chiquita de [v],
     * borroneada, que el [Vidrio] estira detrás de su relleno. Pesa menos de 100 KB.
     */
    fun fotoBorrosa(v: View): Bitmap? = try {
        if (v.width == 0 || v.height == 0) null
        else Bitmap.createBitmap(maxOf(1, v.width / ESCALA_FOTO), maxOf(1, v.height / ESCALA_FOTO), Bitmap.Config.ARGB_8888).also { bmp ->
            val lienzo = Canvas(bmp)
            lienzo.scale(1f / ESCALA_FOTO, 1f / ESCALA_FOTO)
            v.draw(lienzo)
            desenfocar(bmp)
        }
    } catch (e: Throwable) {
        null // una imagen que no se puede dibujar así, o falta de memoria: queda el vidrio tapado
    }

    /** Tres pasadas de promedio con los vecinos, a lo ancho y a lo alto. */
    private fun desenfocar(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        bmp.getPixels(a, 0, w, 0, 0, w, h)
        repeat(3) {
            promediar(a, b, w, h, 1, w, 2)
            promediar(b, a, h, w, w, 1, 2)
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h)
    }

    /** Promedia cada punto con [r] vecinos para cada lado sobre las [lineas] de largo [largo]. */
    private fun promediar(de: IntArray, a: IntArray, largo: Int, lineas: Int, paso: Int, salto: Int, r: Int) {
        for (l in 0 until lineas) {
            val base = l * salto
            for (i in 0 until largo) {
                var rr = 0; var gg = 0; var bb = 0; var n = 0
                for (k in maxOf(0, i - r)..minOf(largo - 1, i + r)) {
                    val p = de[base + k * paso]
                    rr += (p shr 16) and 0xFF; gg += (p shr 8) and 0xFF; bb += p and 0xFF; n++
                }
                a[base + i * paso] = (0xFF shl 24) or ((rr / n) shl 16) or ((gg / n) shl 8) or (bb / n)
            }
        }
    }
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
    private val punto: Int = 0,
) : Drawable() {
    var anillo: IntArray? = anillo
        set(valor) {
            field = valor
            invalidateSelf()
        }
    /**
     * Vidrio esmerilado (ver [Tema.fotoBorrosa]): la foto borrosa de lo de atrás y dónde está esta pieza en la
     * pantalla, sin contar las animaciones. Sin foto se usa [sinFoto] como relleno.
     */
    var foto: Bitmap? = null
    var posicion: (() -> Point)? = null
    var sinFoto: Int = relleno
    private val pFoto = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matriz = Matrix()
    private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = relleno }
    private val pBrillo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pLuz = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        color = 0x2EFFFFFF
    }
    private val caja = RectF()
    private val pPunto = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        caja.set(bounds)
        val r = if (redondo) caja.height() / 2 else radio
        pBrillo.shader = LinearGradient(0f, caja.top, 0f, caja.bottom, 0x1CFFFFFF, 0x06FFFFFF, Shader.TileMode.CLAMP)
        val f = foto
        val donde = posicion?.invoke()
        if (f != null && donde != null) {
            // La foto chiquita se estira al tamaño real y se corre para que coincida con lo que hay detrás.
            matriz.setScale(Tema.ESCALA_FOTO.toFloat(), Tema.ESCALA_FOTO.toFloat())
            matriz.postTranslate(-donde.x.toFloat(), -donde.y.toFloat())
            pFoto.shader = BitmapShader(f, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matriz) }
            canvas.drawRoundRect(caja, r, r, pFoto)
            pRelleno.color = relleno
        } else {
            pRelleno.color = sinFoto
        }
        pRelleno.alpha = Math.round(pRelleno.alpha * alfa / 255f)
        if (pRelleno.alpha != 0) canvas.drawRoundRect(caja, r, r, pRelleno)
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
        // Lo enfocado lleva un puntito del color del tema arriba a la derecha.
        if (colores != null && punto != 0) {
            pPunto.color = punto
            canvas.drawCircle(caja.right - grosor * 4, caja.top + grosor * 4, grosor * 1.6f, pPunto)
        }
        if (colores == null) {
            // Línea de luz arriba, sin tocar las esquinas redondeadas.
            val margen = maxOf(r, caja.width() * 0.06f)
            canvas.drawLine(caja.left + margen, caja.top + 1.5f, caja.right - margen, caja.top + 1.5f, pLuz)
        }
    }

    private var alfa = 255

    override fun setAlpha(alpha: Int) {
        alfa = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        pRelleno.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
