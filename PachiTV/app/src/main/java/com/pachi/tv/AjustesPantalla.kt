package com.pachi.tv

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Ajustes que explican cada cosa, ordenados en menús: primero la lista de secciones (Ziota, Apps, Esta tele…);
 * OK entra a una y Atrás vuelve. A la izquierda, el ícono, el título y la explicación de lo elegido (cambia al
 * moverse); a la derecha, las opciones con iconito de color y el valor o un interruptor.
 * La idea viene de Aura TV (github.com/cookedzera/aura-tv, licencia MIT); el código es propio de Ziota.
 */
class AjustesPantalla(
    context: Context,
    fondo: Bitmap?,
    private val secciones: List<Seccion>,
) : FrameLayout(context) {

    /** [padre]: si está adentro de otra sección, Atrás vuelve a esa. [elegida]: cuál queda marcada al entrar. */
    class Seccion(
        val icono: Int, val color: Int, val titulo: String, val explicacion: String, val opciones: List<Opcion>,
        val padre: (() -> Seccion)? = null, val elegida: () -> Int = { 0 },
    )

    class Opcion(
        val icono: Int,
        val color: Int,
        val nombre: String,
        val explicacion: String,
        val valor: () -> String = { "" },
        val interruptor: (() -> Boolean)? = null,
        val accion: () -> Unit,
    )

    private class Fila(val opcion: Opcion, val vista: LinearLayout, val nombre: TextView, val valor: TextView?, val llave: FrameLayout?)

    private val filas = mutableListOf<Fila>()
    private val iconoGrande = ImageView(context)
    private val cajaIcono = FrameLayout(context)
    private val explicacion = texto(18f, Color.WHITE).apply { setLineSpacing(0f, 1.3f) }
    private val titulo = texto(44f, Color.WHITE, true).apply { text = "Ajustes"; letterSpacing = -0.02f }
    private val ayuda = texto(15f, CentroDeControl.GRIS)
    private val lista = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(48), dp(8), dp(48))
    }
    private val desplazar = ScrollView(context)
    private var primera: View? = null
    /** La sección abierta (null = la lista de secciones). */
    private var abierta: Seccion? = null

    init {
        if (fondo != null) addView(ImageView(context).apply {
            setImageBitmap(fondo)
            scaleType = ImageView.ScaleType.FIT_XY
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(View(context).apply { setBackgroundColor(if (fondo != null) 0xD90A0A0C.toInt() else 0xB30A0A0C.toInt()) },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val izquierda = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            cajaIcono.addView(iconoGrande, LayoutParams(dp(36), dp(36), Gravity.CENTER))
            addView(cajaIcono, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(titulo,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })
            addView(ayuda,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(explicacion, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(32) })
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(96), 0, dp(64), 0)
            addView(izquierda, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(64) })
            addView(desplazar.apply {
                isVerticalScrollBarEnabled = false
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(32))
                clipToPadding = false
                addView(lista)
            }, LinearLayout.LayoutParams(dp(480), LinearLayout.LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        mostrarMenu(null)
    }

    fun enfocar() {
        primera?.requestFocus()
    }

    /** Atrás: desde una sección vuelve a la lista. Devuelve false si ya estaba en la lista (hay que cerrar). */
    fun atras(): Boolean {
        val s = abierta ?: return false
        val padre = s.padre
        if (padre != null) mostrarSeccion(padre()) else mostrarMenu(s)
        return true
    }

    /** La lista de secciones; [volviendoDe] queda elegida al volver de esa sección. */
    private fun mostrarMenu(volviendoDe: Seccion?) {
        abierta = null
        empezarLista("Ajustes", "Elegí una sección con OK.")
        var elegir: View? = null
        for (s in secciones) {
            val entrada = Opcion(s.icono, s.color, s.titulo, s.explicacion, { "›" }) { mostrarSeccion(s) }
            val v = fila(entrada, abre = true)
            lista.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)).apply { bottomMargin = dp(6) })
            if (primera == null) primera = v
            if (s === volviendoDe) elegir = v
        }
        terminarLista(elegir)
    }

    fun mostrarSeccion(s: Seccion) {
        abierta = s
        empezarLista(s.titulo, "Atrás vuelve a Ajustes. Los cambios se aplican al momento.")
        val marcada = s.elegida()
        s.opciones.forEachIndexed { i, o ->
            val v = fila(o)
            lista.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(4) })
            if (primera == null || i == marcada) primera = v
        }
        terminarLista(null)
    }

    private fun empezarLista(nombre: String, texto: String) {
        lista.removeAllViews()
        filas.clear()
        primera = null
        titulo.text = nombre
        ayuda.text = texto
    }

    private fun terminarLista(elegir: View?) {
        refrescar()
        desplazar.scrollTo(0, 0)
        val destino = elegir ?: primera
        // La letra de la marca y el foco, cuando la lista nueva ya está puesta.
        post {
            (context as? MainActivity)?.ponerFuente(lista)
            destino?.requestFocus()
        }
    }

    /** Vuelve a leer los valores (después de cambiar algo). */
    fun refrescar() {
        for (f in filas) pintar(f, f.vista.isFocused)
    }

    private fun fila(o: Opcion, abre: Boolean = false): View {
        val nombre = texto(if (abre) 18f else 16f, Color.WHITE, true).apply { text = o.nombre }
        val valor = if (o.interruptor == null) texto(if (abre) 22f else 14f, CentroDeControl.GRIS).apply { gravity = Gravity.END } else null
        val llave = o.interruptor?.let { FrameLayout(context).apply { addView(View(context), LayoutParams(dp(16), dp(16), Gravity.CENTER_VERTICAL)) } }
        val vista = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(16), 0)
            isFocusable = true
            isClickable = true
            addView(cuadrito(o, if (abre) 40 else 32, if (abre) 22 else 18))
            addView(nombre, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16); marginEnd = dp(12) })
            if (valor != null) addView(valor, LinearLayout.LayoutParams(if (abre) dp(40) else dp(180), LinearLayout.LayoutParams.WRAP_CONTENT))
            if (llave != null) addView(llave, LinearLayout.LayoutParams(dp(40), dp(24)))
            setOnClickListener {
                o.accion()
                if (!abre) postDelayed({ refrescar() }, 300)
            }
        }
        val f = Fila(o, vista, nombre, valor, llave)
        filas += f
        vista.setOnFocusChangeListener { _, foco ->
            pintar(f, foco)
            if (foco) mostrarExplicacion(o)
        }
        return vista
    }

    /** Lo elegido se pone blanco con letras oscuras; el interruptor prendido lleva el color del tema. */
    private fun pintar(f: Fila, foco: Boolean) {
        f.vista.background = if (foco) redondo(Color.WHITE, 16) else Vidrio(dp(16).toFloat(), 0x12FFFFFF)
        f.nombre.setTextColor(if (foco) 0xFF111114.toInt() else Color.WHITE)
        f.valor?.let { v ->
            v.text = f.opcion.valor()
            v.setTextColor(if (foco) 0xFF55575E.toInt() else CentroDeControl.GRIS)
        }
        f.llave?.let { l ->
            val prendido = f.opcion.interruptor?.invoke() == true
            l.background = redondo(if (prendido) Tema.color else if (foco) 0xFFD5D6DA.toInt() else 0x40FFFFFF, 12)
            val bolita = l.getChildAt(0)
            bolita.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            bolita.layoutParams = (bolita.layoutParams as LayoutParams).apply {
                gravity = Gravity.CENTER_VERTICAL or (if (prendido) Gravity.END else Gravity.START)
                setMargins(dp(4), 0, dp(4), 0)
            }
        }
    }

    private fun mostrarExplicacion(o: Opcion) {
        explicacion.text = o.explicacion
        cajaIcono.background = redondo(o.color, 20)
        iconoGrande.setImageResource(o.icono)
        iconoGrande.imageTintList = ColorStateList.valueOf(Color.WHITE)
    }

    private fun cuadrito(o: Opcion, lado: Int, icono: Int) = FrameLayout(context).apply {
        background = redondo(o.color, if (lado > 40) 14 else 9)
        addView(ImageView(context).apply {
            setImageResource(o.icono)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
        }, LayoutParams(dp(icono), dp(icono), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(lado), dp(lado))
    }

    private fun redondo(color: Int, radio: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radio).toFloat()
    }

    private fun texto(sp: Float, color: Int, medio: Boolean = false) = TextView(context).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (medio) typeface = Typeface.DEFAULT_BOLD
        maxLines = 6
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}

/**
 * Informes de problemas: si Ziota se cierra por un error, lo anota en un archivo que la app del celular puede leer
 * por ADB (Android/data/com.pachi.tv/files/informes.txt). Se guardan los últimos.
 */
object Informes {
    private const val SEPARADOR = "\n----\n"

    fun instalar(c: Context) {
        val app = c.applicationContext
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        if (anterior is Anotador) return
        Thread.setDefaultUncaughtExceptionHandler(Anotador(app, anterior))
    }

    private class Anotador(val c: Context, val anterior: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try {
                val version = c.packageManager.getPackageInfo(c.packageName, 0).versionName
                val nuevo = "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())} · Ziota $version\n${android.util.Log.getStackTraceString(e).take(3000)}"
                val viejos = lista(c).takeLast(9)
                archivo(c)?.writeText((viejos + nuevo).joinToString(SEPARADOR))
            } catch (x: Exception) {
            }
            anterior?.uncaughtException(t, e)
        }
    }

    private fun archivo(c: Context): File? = c.getExternalFilesDir(null)?.let { File(it, "informes.txt") }

    fun lista(c: Context): List<String> = try {
        archivo(c)?.takeIf { it.exists() }?.readText()?.split(SEPARADOR)?.filter { it.isNotBlank() } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun borrar(c: Context) {
        archivo(c)?.delete()
    }
}

/**
 * Tu configuración: el orden de las apps, categorías, ocultas, tema y PCs, en un archivo que la app del celular copia
 * por ADB (Android/data/com.pachi.tv/files/configuracion.json) y le pasa a otra tele con el extra "configuracion".
 */
object Configuracion {
    fun guardar(c: Context): Boolean = try {
        val json = JSONObject()
        for ((clave, valor) in c.getSharedPreferences("launcher", Context.MODE_PRIVATE).all) {
            if (clave.startsWith("clima") || clave.startsWith("isla_")) continue // cosas de esta tele
            json.put(clave, if (valor is Set<*>) JSONArray(valor.toList()) else valor)
        }
        val dir = c.getExternalFilesDir(null) ?: throw IllegalStateException()
        File(dir, "configuracion.json").writeText(json.toString(2))
        true
    } catch (e: Exception) {
        false
    }

    fun cargar(c: Context, texto: String): Boolean = try {
        val json = JSONObject(texto)
        val editor = c.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit()
        for (clave in json.keys()) {
            when (val v = json.get(clave)) {
                is Boolean -> editor.putBoolean(clave, v)
                is Int -> editor.putInt(clave, v)
                is Long -> editor.putLong(clave, v)
                is Double -> editor.putFloat(clave, v.toFloat())
                is JSONArray -> editor.putStringSet(clave, (0 until v.length()).map { v.getString(it) }.toSet())
                else -> editor.putString(clave, v.toString())
            }
        }
        editor.commit()
    } catch (e: Exception) {
        false
    }
}
