package com.shikitari.instalador

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/**
 * Editor de encuadre: muestra la foto con un recuadro de la misma forma que la portada de la TV.
 * Se arrastra con un dedo y se acerca o aleja pellizcando; al confirmar, se recorta exactamente lo del recuadro.
 */
class RecorteActivity : Activity() {

    private lateinit var editor: EditorRecorte

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        val foto = try {
            leerFoto(this, uri)
        } catch (e: Exception) {
            null
        } ?: run {
            setResult(RESULT_CANCELED)
            return finish()
        }

        editor = EditorRecorte(this, foto)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
        }
        col.addView(TextView(this).apply {
            text = "Mové la foto con un dedo y pellizcá para acercar.\nLo que queda adentro del recuadro es lo que se ve en la TV."
            setTextColor(0x8CEBEBF5.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(8))
        })
        col.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val botones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        botones.addView(boton("Cancelar", false) {
            setResult(RESULT_CANCELED)
            finish()
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
        botones.addView(boton("Mandar a la TV", true) {
            val recorte = editor.recortar()
            val archivo = File(cacheDir, "portada_recortada.jpg")
            archivo.outputStream().use { recorte.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            setResult(RESULT_OK, Intent().putExtra(EXTRA_RUTA, archivo.path))
            finish()
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(8) })
        col.addView(botones)
        setContentView(col)
    }

    /** Mismo estilo que el resto de la app: el principal blanco, el otro de vidrio. */
    private fun boton(texto: String, principal: Boolean, accion: () -> Unit) = Button(this).apply {
        text = texto
        isAllCaps = false
        stateListAnimator = null
        setTextColor(if (principal) Color.BLACK else Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = GradientDrawable().apply {
            if (principal) {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(0xF7FFFFFF.toInt(), 0xD6FFFFFF.toInt())
            } else {
                setColor(0x1FFFFFFF)
                setStroke(dp(1), 0x1FFFFFFF)
            }
            cornerRadius = dp(14).toFloat()
        }
        setOnClickListener { accion() }
    }

    private fun dp(valor: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), resources.displayMetrics).toInt()

    companion object {
        const val EXTRA_RUTA = "ruta"

        /** Forma de la portada en la TV: ancho dividido alto (1792 × 580 píxeles). */
        const val PROPORCION = 1792f / 580f

        /** Lee la foto achicada (máximo ~2500 px) y enderezada según la cámara (EXIF). */
        fun leerFoto(contexto: Context, uri: Uri): Bitmap? {
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
            var muestra = 1
            while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= 2500) muestra *= 2
            val foto = contexto.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
            } ?: return null
            val giro = contexto.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (giro == 0f) return foto
            return Bitmap.createBitmap(foto, 0, 0, foto.width, foto.height, Matrix().apply { postRotate(giro) }, true)
        }
    }
}

/** Vista que muestra la foto debajo de un recuadro fijo; la foto se mueve y se escala con los dedos. */
class EditorRecorte(contexto: Context, private val foto: Bitmap) : View(contexto) {

    private val marco = RectF()
    private var escala = 1f
    private var escalaMinima = 1f
    private var x0 = 0f
    private var y0 = 0f
    private var ultimoX = 0f
    private var ultimoY = 0f

    private val pintura = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sombra = Paint().apply { color = 0xAA000000.toInt() }
    private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2
    }
    private val guias = Paint().apply {
        color = 0x66FFFFFF
        strokeWidth = resources.displayMetrics.density
    }

    private val pellizco = ScaleGestureDetector(contexto, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val nueva = (escala * d.scaleFactor).coerceIn(escalaMinima, escalaMinima * 6)
            val f = nueva / escala
            // Acercar alrededor del punto entre los dedos.
            x0 = d.focusX - (d.focusX - x0) * f
            y0 = d.focusY - (d.focusY - y0) * f
            escala = nueva
            limitar()
            invalidate()
            return true
        }
    })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val margen = 16 * resources.displayMetrics.density
        var ancho = w - 2 * margen
        var alto = ancho / RecorteActivity.PROPORCION
        if (alto > h - 2 * margen) {
            alto = h - 2 * margen
            ancho = alto * RecorteActivity.PROPORCION
        }
        marco.set((w - ancho) / 2, (h - alto) / 2, (w + ancho) / 2, (h + alto) / 2)
        // Al empezar, la foto cubre justo el recuadro y queda centrada.
        escalaMinima = maxOf(marco.width() / foto.width, marco.height() / foto.height)
        escala = escalaMinima
        x0 = marco.centerX() - foto.width * escala / 2
        y0 = marco.centerY() - foto.height * escala / 2
    }

    /** La foto nunca deja huecos adentro del recuadro. */
    private fun limitar() {
        x0 = x0.coerceIn(marco.right - foto.width * escala, marco.left)
        y0 = y0.coerceIn(marco.bottom - foto.height * escala, marco.top)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        pellizco.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                val i = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP && e.actionIndex == 0) 1 else 0
                ultimoX = e.getX(i)
                ultimoY = e.getY(i)
            }
            MotionEvent.ACTION_MOVE -> if (e.pointerCount == 1 && !pellizco.isInProgress) {
                x0 += e.x - ultimoX
                y0 += e.y - ultimoY
                ultimoX = e.x
                ultimoY = e.y
                limitar()
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(foto, Matrix().apply { setScale(escala, escala); postTranslate(x0, y0) }, pintura)
        // Oscurece lo que queda afuera del recuadro.
        canvas.drawRect(0f, 0f, width.toFloat(), marco.top, sombra)
        canvas.drawRect(0f, marco.bottom, width.toFloat(), height.toFloat(), sombra)
        canvas.drawRect(0f, marco.top, marco.left, marco.bottom, sombra)
        canvas.drawRect(marco.right, marco.top, width.toFloat(), marco.bottom, sombra)
        // Guías en tercios para centrar mejor.
        for (k in 1..2) {
            val gx = marco.left + marco.width() * k / 3
            val gy = marco.top + marco.height() * k / 3
            canvas.drawLine(gx, marco.top, gx, marco.bottom, guias)
            canvas.drawLine(marco.left, gy, marco.right, gy, guias)
        }
        canvas.drawRoundRect(marco, 12f, 12f, borde)
    }

    /** Devuelve lo que está adentro del recuadro, a 1920 px de ancho. */
    fun recortar(): Bitmap {
        val origen = Rect(
            ((marco.left - x0) / escala).toInt(),
            ((marco.top - y0) / escala).toInt(),
            ((marco.right - x0) / escala).toInt(),
            ((marco.bottom - y0) / escala).toInt(),
        )
        val ancho = 1920
        val alto = (ancho / RecorteActivity.PROPORCION).toInt()
        val salida = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        Canvas(salida).drawBitmap(foto, origen, Rect(0, 0, ancho, alto), pintura)
        return salida
    }
}
