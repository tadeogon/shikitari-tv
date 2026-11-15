package com.shikitari.instalador

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * Los archivos de la TV (su almacenamiento interno, la carpeta /sdcard):
 * entrar a las carpetas, borrar, traer archivos al celular y pasar archivos del celular a la TV.
 */
class ArchivosActivity : Activity() {

    private data class Archivo(val nombre: String, val carpeta: Boolean, val tamano: Long)

    private val tinta = Color.WHITE
    private val tintaSuave = 0xA6FFFFFF.toInt()
    private val crema = 0xFFF3E9D2.toInt()

    private val tv by lazy { ConexionTv.obtener(this) }
    private val hilo = Executors.newSingleThreadExecutor()

    private var ruta = RAIZ
    private var ocupado = false
    private var paraGuardar: Archivo? = null // el archivo que se está por traer al celular

    private lateinit var lugar: TextView
    private lateinit var espacio: TextView
    private lateinit var lista: LinearLayout
    private lateinit var cajaProgreso: LinearLayout
    private lateinit var textoProgreso: TextView
    private lateinit var barra: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(armar())
        abrir(RAIZ)
        // Desde el mosaico "Instalar APK" del Inicio se abre directo el selector.
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_INSTALAR, false)) elegirApk()
    }

    override fun onDestroy() {
        hilo.shutdownNow()
        super.onDestroy()
    }

    @Deprecated("Atrás sube una carpeta")
    override fun onBackPressed() {
        if (ruta != RAIZ) abrir(ruta.substringBeforeLast('/')) else super.onBackPressed()
    }

    // ---------- Armado ----------

    private fun armar(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(32))
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@ArchivosActivity).apply {
                text = "‹"
                setTextColor(tinta)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
                setPadding(dp(4), 0, dp(14), dp(4))
                setOnClickListener { finish() }
            })
            addView(TextView(this@ArchivosActivity).apply {
                text = "Archivos de la TV"
                setTextColor(tinta)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
        })
        espacio = TextView(this).apply {
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(4), dp(2), 0, 0)
        }
        col.addView(espacio)

        col.addView(boton("⬆  Pasar archivos del celular a la TV", true) { elegirParaSubir() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(14) })
        col.addView(boton("📦  Instalar un APK del celular en la TV", false) { elegirApk() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(10) })

        // Progreso de lo que se está pasando
        cajaProgreso = tarjeta().apply { visibility = View.GONE }
        textoProgreso = TextView(this).apply {
            setTextColor(tinta)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        barra = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(crema)
        }
        cajaProgreso.addView(textoProgreso)
        cajaProgreso.addView(barra, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)).apply { topMargin = dp(8) })
        col.addView(cajaProgreso, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        val caja = tarjeta()
        lugar = TextView(this).apply {
            setTextColor(crema)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(6))
        }
        caja.addView(lugar)
        caja.addView(TextView(this).apply {
            text = "Tocá una carpeta para entrar. Tocá un archivo (o dejá apretada una carpeta) para ver qué hacer."
            setTextColor(tintaSuave)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(6))
        })
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        caja.addView(lista)
        col.addView(caja, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        return FrameLayout(this).apply {
            addView(FondoVidrio(this@ArchivosActivity))
            addView(ScrollView(this@ArchivosActivity).apply { addView(col) })
        }
    }

    private fun boton(texto: String, principal: Boolean, accion: () -> Unit) = Button(this).apply {
        text = texto
        isAllCaps = false
        stateListAnimator = null
        setTextColor(if (principal) 0xFF111114.toInt() else tinta)
        background = GradientDrawable().apply {
            setColor(if (principal) crema else 0x26FFFFFF)
            cornerRadius = dp(24).toFloat()
        }
        setOnClickListener { accion() }
    }

    private fun tarjeta() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(0x1FFFFFFF)
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), 0x33FFFFFF)
        }
    }

    private fun fila(icono: String, nombre: String, detalle: String, alTocar: () -> Unit, alMantener: (() -> Unit)?) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
            addView(TextView(this@ArchivosActivity).apply {
                text = icono
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setPadding(0, 0, dp(12), 0)
            })
            addView(LinearLayout(this@ArchivosActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@ArchivosActivity).apply {
                    text = nombre
                    setTextColor(tinta)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                })
                if (detalle.isNotEmpty()) addView(TextView(this@ArchivosActivity).apply {
                    text = detalle
                    setTextColor(tintaSuave)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { alTocar() }
            if (alMantener != null) setOnLongClickListener { alMantener(); true }
        }

    // ---------- Navegar ----------

    private fun abrir(nueva: String) {
        ruta = nueva
        lugar.text = "📂  " + (if (ruta == RAIZ) "Almacenamiento" else "Almacenamiento" + ruta.removePrefix(RAIZ).replace("/", " › "))
        lista.removeAllViews()
        lista.addView(TextView(this).apply {
            text = "Leyendo…"
            setTextColor(tintaSuave)
            setPadding(dp(4), dp(8), 0, dp(8))
        })
        hilo.execute {
            try {
                // Una línea por cosa: "directory|4096|/sdcard/Movies"
                val salida = tv.ejecutar("stat -c '%F|%s|%n' ${ConexionTv.comillas(ruta)}/* 2>/dev/null", segundos = 30)
                val cosas = salida.lines().mapNotNull { linea ->
                    val partes = linea.trim().split('|', limit = 3)
                    if (partes.size < 3) return@mapNotNull null
                    Archivo(partes[2].substringAfterLast('/'), partes[0] == "directory", partes[1].toLongOrNull() ?: 0)
                }.sortedWith(compareBy({ !it.carpeta }, { it.nombre.lowercase() }))
                val libre = tv.ejecutar("df -h $RAIZ | tail -1", segundos = 10).trim().split(Regex("\\s+"))
                runOnUiThread {
                    if (libre.size >= 4) espacio.text = "Libre: ${libre[3]} de ${libre[1]}"
                    if (ruta == nueva) pintar(cosas)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    lista.removeAllViews()
                    lista.addView(TextView(this).apply {
                        text = "No se pudo leer: ${e.message ?: "sin conexión"}"
                        setTextColor(tintaSuave)
                        setPadding(dp(4), dp(8), 0, dp(8))
                    })
                }
            }
        }
    }

    private fun pintar(cosas: List<Archivo>) {
        lista.removeAllViews()
        if (ruta != RAIZ) lista.addView(fila("↩", "Volver a la carpeta anterior", "", { abrir(ruta.substringBeforeLast('/')) }, null))
        if (cosas.isEmpty()) {
            lista.addView(TextView(this).apply {
                text = "Esta carpeta está vacía."
                setTextColor(tintaSuave)
                setPadding(dp(4), dp(8), 0, dp(8))
            })
            return
        }
        cosas.forEach { a ->
            val v = if (a.carpeta)
                fila("📁", a.nombre, "", { abrir("$ruta/${a.nombre}") }, { opciones(a) })
            else
                fila(icono(a.nombre), a.nombre, tamano(a.tamano), { opciones(a) }, { opciones(a) })
            lista.addView(v)
        }
    }

    // ---------- Acciones ----------

    private fun opciones(a: Archivo) {
        if (ocupado) {
            Toast.makeText(this, "Esperá a que termine lo que se está pasando.", Toast.LENGTH_SHORT).show()
            return
        }
        val esApk = a.nombre.endsWith(".apk", ignoreCase = true)
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (a.carpeta) {
            items += "📂  Abrir" to { abrir("$ruta/${a.nombre}") }
        } else {
            items += "⬇  Guardar en el celular" to { elegirDondeGuardar(a) }
            if (esApk) items += "📦  Instalar en la TV" to { instalarApk(a) }
        }
        val protegida = a.carpeta && ruta == RAIZ && a.nombre == "Android"
        if (!protegida) items += "🗑  Borrar" to { confirmarBorrar(a) }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(a.nombre)
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .show()
    }

    private fun confirmarBorrar(a: Archivo) {
        val mensaje = if (a.carpeta) "Se borra la carpeta con todo lo que tiene adentro. No se puede deshacer."
        else "Se borra de la TV (${tamano(a.tamano)}). No se puede deshacer."
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("¿Borrar ${a.nombre}?")
            .setMessage(mensaje)
            .setPositiveButton("Borrar") { _, _ -> borrar(a) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun borrar(a: Archivo) {
        val completa = "$ruta/${a.nombre}"
        hilo.execute {
            val comando = "rm -rf ${ConexionTv.comillas(completa)}"
            val salida = try {
                tv.ejecutar("$comando 2>&1; echo FIN:\$?", segundos = 120)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            val ok = salida.contains("FIN:0")
            Bitacora.anotar(this, "▸ Borrar archivo de la TV\n$ $comando\n${salida.replace(Regex("FIN:\\d+"), "").trim().ifEmpty { "OK" }}")
            runOnUiThread {
                Toast.makeText(this, if (ok) "✓ ${a.nombre} borrado" else "No se pudo borrar: $salida", if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                abrir(ruta)
            }
        }
    }

    private fun instalarApk(a: Archivo) {
        Toast.makeText(this, "Instalando ${a.nombre}…", Toast.LENGTH_SHORT).show()
        hilo.execute {
            val comando = "pm install -r ${ConexionTv.comillas("$ruta/${a.nombre}")}"
            val salida = try {
                tv.ejecutar("$comando 2>&1", segundos = 180)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            Bitacora.anotar(this, "▸ Instalar APK desde Archivos\n$ $comando\n$salida")
            runOnUiThread {
                val ok = salida.contains("Success")
                Toast.makeText(this, if (ok) "✓ Instalada en la TV" else "No se pudo instalar: $salida", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Traer de la TV al celular ---

    private fun elegirDondeGuardar(a: Archivo) {
        paraGuardar = a
        val tipo = MimeTypeMap.getSingleton().getMimeTypeFromExtension(a.nombre.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = tipo
            putExtra(Intent.EXTRA_TITLE, a.nombre)
        }, PEDIDO_GUARDAR)
    }

    private fun traer(a: Archivo, destino: Uri) {
        val origen = "$ruta/${a.nombre}"
        empezarProgreso("Trayendo ${a.nombre}…")
        hilo.execute {
            try {
                val salida = contentResolver.openOutputStream(destino) ?: throw Exception("no se pudo crear el archivo en el celular")
                salida.use { s -> tv.recibirArchivo(origen, s) { hecho -> progreso(hecho, a.tamano) } }
                terminarProgreso(true, "✓ ${a.nombre} quedó guardado en el celular")
            } catch (e: Exception) {
                terminarProgreso(false, "No se pudo traer ${a.nombre}: ${e.message}")
            }
        }
    }

    // --- Pasar del celular a la TV ---

    private fun elegirParaSubir() {
        if (ocupado) {
            Toast.makeText(this, "Esperá a que termine lo que se está pasando.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, PEDIDO_SUBIR)
    }

    private fun subir(elegidos: List<Uri>) {
        val destino = ruta
        empezarProgreso("Preparando…")
        hilo.execute {
            var bien = 0
            val errores = mutableListOf<String>()
            elegidos.forEachIndexed { i, uri ->
                val (nombre, tam) = datosDe(uri)
                val donde = "$destino/$nombre"
                val cual = if (elegidos.size > 1) " (${i + 1} de ${elegidos.size})" else ""
                runOnUiThread { textoProgreso.text = "Pasando $nombre$cual…" }
                try {
                    val entrada = contentResolver.openInputStream(uri) ?: throw Exception("no se pudo abrir")
                    entrada.use { e -> tv.enviarArchivo(donde, e) { hecho -> progreso(hecho, tam) } }
                    // Se confirma que llegó entero. La TV termina de escribir unos segundos después de recibir,
                    // así que se espera mientras el archivo siga creciendo.
                    if (tam > 0) {
                        runOnUiThread { textoProgreso.text = "Terminando de guardar $nombre en la TV…" }
                        var enTv = -1L
                        var anterior = -2L
                        var quietos = 0
                        while (enTv != tam && quietos < 6) {
                            Thread.sleep(500)
                            enTv = tv.ejecutar("stat -c %s ${ConexionTv.comillas(donde)}", segundos = 15).trim().toLongOrNull() ?: -1
                            if (enTv == anterior) quietos++ else quietos = 0
                            anterior = enTv
                        }
                        if (enTv != tam) throw Exception("llegó incompleto (${tamano(enTv)} de ${tamano(tam)})")
                    }
                    bien++
                    Bitacora.anotar(this, "▸ Pasar archivo a la TV\n$nombre → $donde (${tamano(tam)})")
                } catch (ex: Exception) {
                    errores += "$nombre: ${ex.message}"
                    Bitacora.anotar(this, "▸ Pasar archivo a la TV — falló\n$nombre: ${ex.message}")
                }
            }
            val ok = errores.isEmpty()
            val texto = if (ok) "✓ ${if (bien == 1) "Archivo pasado" else "$bien archivos pasados"} a la TV"
            else "Pasaron $bien de ${elegidos.size}.\n" + errores.joinToString("\n")
            terminarProgreso(ok, texto)
            runOnUiThread { if (ruta == destino) abrir(destino) }
        }
    }

    // --- Instalar un APK que está en el celular ---

    private fun elegirApk() {
        if (ocupado) {
            Toast.makeText(this, "Esperá a que termine lo que se está pasando.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Algunos celulares no marcan los APK con su tipo, así que se aceptan todos y se revisa después.
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, PEDIDO_APK)
    }

    private fun instalarDelCelular(elegidos: List<Uri>) {
        empezarProgreso("Preparando…")
        hilo.execute {
            var bien = 0
            val errores = mutableListOf<String>()
            elegidos.forEachIndexed { i, uri ->
                val (nombre, tam) = datosDe(uri)
                val cual = if (elegidos.size > 1) " (${i + 1} de ${elegidos.size})" else ""
                try {
                    if (!nombre.endsWith(".apk", ignoreCase = true)) {
                        throw Exception(
                            if (nombre.endsWith(".apks", true) || nombre.endsWith(".xapk", true) || nombre.endsWith(".apkm", true))
                                "es un paquete dividido (.apks/.xapk); hace falta el .apk común"
                            else "no es un APK"
                        )
                    }
                    if (tam <= 0) throw Exception("no se pudo saber cuánto pesa")
                    runOnUiThread { textoProgreso.text = "Instalando $nombre$cual…" }
                    val salida = contentResolver.openInputStream(uri)?.use { e ->
                        tv.instalarApk(e, tam) { hecho ->
                            progreso(hecho, tam)
                            if (hecho >= tam) runOnUiThread { textoProgreso.text = "La TV está instalando $nombre…" }
                        }
                    } ?: throw Exception("no se pudo abrir")
                    Bitacora.anotar(this, "▸ Instalar APK del celular en la TV\n$nombre (${tamano(tam)})\n$salida")
                    if (!salida.contains("Success")) throw Exception(motivoError(salida))
                    bien++
                } catch (ex: Exception) {
                    errores += "$nombre: ${ex.message}"
                }
            }
            val ok = errores.isEmpty()
            val texto = if (ok) "✓ ${if (bien == 1) "App instalada" else "$bien apps instaladas"} en la TV"
            else "Se instalaron $bien de ${elegidos.size}.\n" + errores.joinToString("\n")
            terminarProgreso(ok, texto)
        }
    }

    /** Traduce los errores más comunes de Android a algo entendible. */
    private fun motivoError(salida: String): String = when {
        "INSTALL_FAILED_NO_MATCHING_ABIS" in salida -> "esa versión no es para el procesador de esta TV (buscá la de 32 bits / armeabi-v7a)"
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in salida -> "ya hay otra versión instalada con distinta firma: desinstalala primero"
        "INSTALL_FAILED_VERSION_DOWNGRADE" in salida -> "la TV ya tiene una versión más nueva"
        "INSTALL_FAILED_OLDER_SDK" in salida -> "la app pide un Android más nuevo que el de la TV"
        "INSTALL_FAILED_INSUFFICIENT_STORAGE" in salida -> "no hay espacio en la TV"
        "INSTALL_PARSE_FAILED" in salida -> "el archivo está dañado o incompleto"
        else -> salida.lines().lastOrNull { it.isNotBlank() } ?: "error desconocido"
    }

    private fun datosDe(uri: Uri): Pair<String, Long> {
        var nombre = "archivo"
        var tam = -1L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { nombre = it }
                if (!c.isNull(1)) tam = c.getLong(1)
            }
        }
        return nombre.replace('/', '_') to tam
    }

    @Deprecated("Selector de archivos del sistema")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            PEDIDO_GUARDAR -> {
                val a = paraGuardar ?: return
                data.data?.let { traer(a, it) }
            }
            PEDIDO_SUBIR, PEDIDO_APK -> {
                val uris = mutableListOf<Uri>()
                val clip = data.clipData
                if (clip != null) for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
                else data.data?.let { uris += it }
                if (uris.isEmpty()) return
                if (requestCode == PEDIDO_APK) instalarDelCelular(uris) else subir(uris)
            }
        }
    }

    // ---------- Progreso ----------

    private fun empezarProgreso(texto: String) {
        ocupado = true
        textoProgreso.text = texto
        barra.isIndeterminate = false
        barra.progress = 0
        cajaProgreso.visibility = View.VISIBLE
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private var ultimoAviso = 0L

    private fun progreso(hecho: Long, total: Long) {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoAviso < 150) return // no hace falta redibujar en cada pedacito
        ultimoAviso = ahora
        runOnUiThread {
            if (total > 0) {
                barra.isIndeterminate = false
                barra.progress = (hecho * 1000 / total).toInt().coerceIn(0, 1000)
            } else {
                barra.isIndeterminate = true
            }
            val base = textoProgreso.text.toString().substringBefore("\n")
            textoProgreso.text = base + "\n" + tamano(hecho) + (if (total > 0) " de " + tamano(total) else "")
        }
    }

    private fun terminarProgreso(ok: Boolean, texto: String) = runOnUiThread {
        ocupado = false
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        barra.isIndeterminate = false
        barra.progress = if (ok) 1000 else barra.progress
        textoProgreso.text = texto
        textoProgreso.setTextColor(if (ok) 0xFF7CE38B.toInt() else 0xFFFF8A80.toInt())
        cajaProgreso.postDelayed({
            if (!ocupado) {
                cajaProgreso.visibility = View.GONE
                textoProgreso.setTextColor(tinta)
            }
        }, if (ok) 4000 else 9000)
    }

    // ---------- Ayudas ----------

    private fun icono(nombre: String): String = when (nombre.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> "🖼"
        "mp4", "mkv", "avi", "mov", "webm", "ts", "m4v" -> "🎬"
        "mp3", "m4a", "wav", "flac", "ogg", "aac" -> "🎵"
        "apk", "apks", "xapk" -> "📦"
        "zip", "rar", "7z", "tar", "gz" -> "🗜"
        "pdf" -> "📕"
        else -> "📄"
    }

    private fun tamano(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.0f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
        else -> String.format("%.2f GB", bytes / 1073741824.0)
    }

    private fun dp(valor: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), resources.displayMetrics).toInt()

    companion object {
        const val EXTRA_INSTALAR = "instalar_apk"
        private const val RAIZ = "/sdcard"
        private const val PEDIDO_GUARDAR = 41
        private const val PEDIDO_SUBIR = 42
        private const val PEDIDO_APK = 43
    }
}
