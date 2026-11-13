package com.pachi.tv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Html
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vitrina de Solari: el video de la página de fondo y, encima, las propiedades destacadas
 * de solaribienesraices.com pasando una por una, cada una con su QR para verla en el celular.
 * Todo sale de la página: si cargan o sacan una propiedad allá, la TV se entera sola.
 */
@SuppressLint("ViewConstructor")
class Vitrina(context: Context) : FrameLayout(context) {

    class Propiedad(
        val id: String,
        val foto: String,
        val titulo: String,
        val lugar: String,
        val precio: String,
        val detalles: List<String>,
    ) {
        val enlace get() = "$SITIO/propiedad.php?propiedad=$id"
    }

    private val principal = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private var propiedades: List<Propiedad> = emptyList()
    private val fotos = HashMap<String, Bitmap>()
    private var actual = -1

    private val video = WebView(context)
    private val foto = ImageView(context)
    private val textos = LinearLayout(context)
    private val titulo = TextView(context)
    private val lugar = TextView(context)
    private val precio = TextView(context)
    private val detalles = TextView(context)
    private val puntos = LinearLayout(context)
    private val qr = ImageView(context)
    private val kicker = TextView(context)
    private val leyendaQr = TextView(context)

    private val pasar = object : Runnable {
        override fun run() {
            siguiente()
            principal.postDelayed(this, CADA_MS)
        }
    }

    init {
        val radio = dp(20).toFloat()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radio)
        }
        clipToOutline = true
        setBackgroundColor(0xFF0B0B0F.toInt())
        // Se puede enfocar con el control: es lo primero que se ve al entrar.
        isFocusable = true
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS

        // 1. El video de fondo: el mismo de YouTube que tiene la página, sin sonido y en bucle
        video.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // Reproductor de computadora: con controls=0 no muestra botones encima del video.
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
            webChromeClient = WebChromeClient()
        }
        addView(video, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // 2. Oscurecido de izquierda a derecha para que se lean los textos
        addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xF2050507.toInt(), 0xCC050507.toInt(), 0x80050507.toInt()),
            )
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 3. Foto, datos y QR
        val fila = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(24), dp(20))
        }
        foto.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
            }
            clipToOutline = true
            setBackgroundColor(0x22FFFFFF)
        }
        fila.addView(foto, LinearLayout.LayoutParams(dp(270), ViewGroup.LayoutParams.MATCH_PARENT))

        textos.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(26), 0, dp(16), 0)
        }
        textos.addView(kicker.apply {
            text = "PROPIEDADES DESTACADAS"
            setTextColor(CREMA)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.28f
            typeface = Typeface.DEFAULT_BOLD
        })
        titulo.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, dp(2))
        }
        textos.addView(titulo)
        lugar.apply {
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textos.addView(lugar)
        precio.apply {
            setTextColor(CREMA)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        }
        textos.addView(precio)
        detalles.apply {
            setTextColor(0xB3FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textos.addView(detalles)
        puntos.apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        textos.addView(puntos)

        // Abajo de los datos: el QR con su leyenda al costado
        val filaQr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        qr.apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        filaQr.addView(qr, LinearLayout.LayoutParams(dp(118), dp(118)))
        filaQr.addView(leyendaQr.apply {
            text = "Escaneá el código\ny mirala en tu celular"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(14), 0, 0, 0)
        })
        textos.addView(filaQr)
        fila.addView(textos, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(fila, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Mientras carga: el QR de la página y el título de la marca
        titulo.text = "Solari Bienes Raíces"
        lugar.text = "solaribienesraices.com"
        qr.setImageBitmap(hacerQr(SITIO))

        prefs.getString(CLAVE_PAGINA, null)?.let { propiedades = leerPagina(it) }
        if (propiedades.isNotEmpty()) siguiente()
        actualizar()
    }

    /** Cuanto más alta es la vitrina, más grandes la foto, los textos y el QR. */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h == 0 || h == oldh) return
        val f = (h / dp(214).toFloat()).coerceIn(1f, 1.5f)
        tamanos.forEach { (vista, sp) -> vista.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * f) }
        val lado = dp(104)
        post {
            foto.layoutParams = foto.layoutParams.apply { width = minOf(((h - dp(40)) * 1.25f).toInt(), w / 2) }
            qr.layoutParams = qr.layoutParams.apply {
                width = lado
                height = lado
            }
        }
    }

    private val tamanos by lazy {
        listOf(kicker to 9f, titulo to 21f, lugar to 12f, precio to 28f, detalles to 11f, leyendaQr to 9f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        arrancarVideo()
        principal.removeCallbacks(pasar)
        principal.postDelayed(pasar, CADA_MS)
    }

    override fun onDetachedFromWindow() {
        principal.removeCallbacks(pasar)
        pararVideo()
        super.onDetachedFromWindow()
    }

    // ---------- Propiedades ----------

    /** Baja la página de inicio de Solari (cada 30 minutos como mucho) y el video (una vez por semana). */
    private fun actualizar() {
        Thread {
            try {
                var lista = propiedades
                if (System.currentTimeMillis() - prefs.getLong("vitrina_hora", 0) > 30 * 60 * 1000L || lista.isEmpty()) {
                    val html = leer("$SITIO/index.php")
                    val nuevas = leerPagina(html)
                    if (nuevas.isNotEmpty()) {
                        lista = nuevas
                        prefs.edit().putString(CLAVE_PAGINA, html).putLong("vitrina_hora", System.currentTimeMillis()).apply()
                        principal.post {
                            val primera = propiedades.isEmpty()
                            propiedades = nuevas
                            if (primera) siguiente()
                        }
                    }
                }
                for (p in lista) bajarFoto(p)
            } catch (e: Exception) {
                // Sin internet: queda lo último guardado.
            }
        }.start()
    }

    private fun leerPagina(html: String): List<Propiedad> {
        val vistas = HashSet<String>()
        return html.split("class=\"property-card\"").drop(1).mapNotNull { bloque ->
            val id = Regex("propiedad=(\\d+)").find(bloque)?.groupValues?.get(1) ?: return@mapNotNull null
            if (!vistas.add(id)) return@mapNotNull null
            val foto = Regex("class=\"property-img\"\\s+src=\"([^\"]+)\"").find(bloque)?.groupValues?.get(1) ?: return@mapNotNull null
            fun texto(patron: String) = Regex(patron, RegexOption.DOT_MATCHES_ALL).find(bloque)?.groupValues?.get(1)?.let { limpiar(it) } ?: ""
            val amenities = bloque.substringAfter("amenities", "").substringBefore("</ul>")
            Propiedad(
                id = id,
                foto = if (foto.startsWith("http")) foto else "$SITIO/${foto.replace(" ", "%20")}",
                titulo = texto("class=\"title[^\"]*\">(.*?)</div>"),
                lugar = texto("class=\"location[^\"]*\">.*?<span>(.*?)</span>"),
                precio = texto("class=\"price[^\"]*\">(.*?)</h6>"),
                detalles = Regex("<span>(.*?)</span>").findAll(amenities).map { limpiar(it.groupValues[1]) }.filter { it.isNotBlank() }.toList(),
            )
        }
    }

    private fun limpiar(s: String): String =
        Html.fromHtml(s.replace(Regex("<[^>]+>"), " ")).toString().replace(Regex("\\s+"), " ").trim()

    private fun bajarFoto(p: Propiedad) {
        if (fotos.containsKey(p.id)) return
        try {
            // Guardadas en la TV: la próxima vez salen al instante, aunque no haya internet.
            val guardada = java.io.File(context.cacheDir, "vitrina_${p.id}.jpg")
            val datos = if (guardada.exists()) guardada.readBytes() else leerBytes(p.foto).also { guardada.writeBytes(it) }
            val tamano = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(datos, 0, datos.size, tamano)
            var muestra = 1
            while (tamano.outWidth / (muestra * 2) >= 800) muestra *= 2
            val imagen = BitmapFactory.decodeByteArray(datos, 0, datos.size, BitmapFactory.Options().apply { inSampleSize = muestra })
                ?: return
            principal.post {
                fotos[p.id] = imagen
                if (propiedades.getOrNull(actual)?.id == p.id) foto.setImageBitmap(imagen)
            }
        } catch (e: Exception) {
        }
    }

    /** Pasa a la próxima propiedad con un fundido suave. */
    fun siguiente() {
        if (propiedades.isEmpty()) return
        val nueva = (actual + 1) % propiedades.size
        fundido(1f, 0f, 260) {
            actual = nueva
            pintar(propiedades[nueva])
            fundido(0f, 1f, 420) {}
        }
    }

    private fun pintar(p: Propiedad) {
        titulo.text = p.titulo.lowercase().replaceFirstChar { it.uppercase() }
        lugar.text = "📍 ${p.lugar}"
        precio.text = p.precio
        detalles.text = p.detalles.joinToString("  ·  ")
        val imagen = fotos[p.id]
        if (imagen != null) foto.setImageBitmap(imagen) else {
            foto.setImageDrawable(null)
            Thread { bajarFoto(p) }.start()
        }
        qr.setImageBitmap(hacerQr(p.enlace))
        puntos.removeAllViews()
        propiedades.indices.forEach { i ->
            puntos.addView(View(context).apply {
                background = GradientDrawable().apply {
                    setColor(if (i == actual) CREMA else 0x55FFFFFF)
                    cornerRadius = dp(3).toFloat()
                }
            }, LinearLayout.LayoutParams(if (i == actual) dp(18) else dp(6), dp(6)).apply { marginEnd = dp(5) })
        }
    }

    /** Animación a mano: en estas TVs las animaciones del sistema están apagadas. */
    private fun fundido(desde: Float, hasta: Float, ms: Int, listo: () -> Unit) {
        val inicio = SystemClock.uptimeMillis()
        val paso = object : Runnable {
            override fun run() {
                val c = ((SystemClock.uptimeMillis() - inicio) / ms.toFloat()).coerceIn(0f, 1f)
                val a = desde + (hasta - desde) * c
                foto.alpha = a
                textos.alpha = a
                qr.alpha = a
                if (c < 1f) postOnAnimation(this) else listo()
            }
        }
        postOnAnimation(paso)
    }

    private fun hacerQr(texto: String): Bitmap {
        val matriz = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        val modulos = matriz.width
        val escala = 8
        val bmp = Bitmap.createBitmap(modulos * escala, modulos * escala, Bitmap.Config.ARGB_8888)
        val pixeles = IntArray(bmp.width * bmp.height)
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            pixeles[y * bmp.width + x] = if (matriz[x / escala, y / escala]) 0xFF0B0B0F.toInt() else Color.WHITE
        }
        bmp.setPixels(pixeles, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return bmp
    }

    // ---------- Video ----------

    /** Video a pantalla llena dentro del recuadro (recortado, sin bordes negros) y sin controles. */
    fun arrancarVideo() {
        if (cargado) return
        cargado = true
        video.onResume()
        // YouTube muestra sus controles unos segundos al arrancar: el video aparece recién después.
        video.alpha = 0f
        principal.postDelayed({
            if (!cargado) return@postDelayed
            val inicio = SystemClock.uptimeMillis()
            val paso = object : Runnable {
                override fun run() {
                    val c = ((SystemClock.uptimeMillis() - inicio) / 1200f).coerceIn(0f, 1f)
                    video.alpha = c
                    if (c < 1f) postOnAnimation(this)
                }
            }
            postOnAnimation(paso)
        }, 9000)
        val html = """
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>
            html,body{margin:0;height:100%;overflow:hidden;background:#000}
            iframe{position:absolute;top:50%;left:50%;border:0;pointer-events:none;
              width:max(100vw,177.78vh);height:max(56.25vw,100vh);transform:translate(-50%,-50%) scale(1.25)}
            </style></head><body>
            <iframe src="https://www.youtube.com/embed/$VIDEO_YT?autoplay=1&mute=1&loop=1&playlist=$VIDEO_YT&controls=0&rel=0&modestbranding=1&playsinline=1&iv_load_policy=3&disablekb=1"
              allow="autoplay; encrypted-media"></iframe></body></html>
        """.trimIndent()
        video.loadDataWithBaseURL("$SITIO/", html, "text/html", "utf-8", null)
    }

    /** Al irse a otra app se descarga del todo, así no gasta internet ni procesador. */
    fun pararVideo() {
        cargado = false
        video.loadUrl("about:blank")
        video.onPause()
    }

    private var cargado = false
    private fun leer(direccion: String) = String(leerBytes(direccion))

    private fun leerBytes(direccion: String): ByteArray {
        val c = URL(direccion).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        return try {
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val SITIO = "https://www.solaribienesraices.com"
        const val VIDEO_YT = "uGnx2AmXzTg"
        const val CLAVE_PAGINA = "vitrina_pagina"
        const val CADA_MS = 9000L
        val CREMA = 0xFFF3E9D2.toInt()
    }
}
