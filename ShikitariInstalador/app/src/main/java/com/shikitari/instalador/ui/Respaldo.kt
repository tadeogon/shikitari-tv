package com.shikitari.instalador.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.shikitari.instalador.ConexionTv
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copia de seguridad de una app de la TV: el instalador (APK) queda en Descargas/Shikitari APKs del celular.
 * Solo lee de la TV. No incluye los datos de la app (cuentas, partidas, ajustes): Android no deja leerlos.
 */
object Respaldo {

    const val CARPETA = "Shikitari APKs"

    private const val TIPO_APK = "application/vnd.android.package-archive"

    /** Algunas apps vienen en varios pedazos: se guardan juntos en un solo archivo .apks. */
    private const val TIPO_APKS = "application/octet-stream"

    class Guardado(val archivo: String, val bytes: Long)

    /** [progreso] recibe lo que ya llegó y el total, en bytes. */
    fun guardar(contexto: Context, tv: ConexionTv, paquete: String, nombre: String, progreso: (Long, Long) -> Unit): Guardado {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw Exception("hace falta Android 10 o más en el celular")
        val rutas = tv.ejecutar("pm path $paquete", segundos = 20).lines()
            .map { it.trim() }.filter { it.startsWith("package:") }.map { it.removePrefix("package:") }
        if (rutas.isEmpty()) throw Exception("la TV no encontró la app")
        val tamanos = rutas.map { ruta ->
            tv.ejecutar("stat -c %s ${ConexionTv.comillas(ruta)}", segundos = 20).trim().toLongOrNull()
                ?: throw Exception("la TV no deja leer esta app")
        }
        val total = tamanos.sum()
        val version = Regex("versionName=(\\S+)")
            .find(tv.ejecutar("dumpsys package $paquete | grep -m1 versionName", segundos = 20))?.groupValues?.get(1)
        val partida = rutas.size > 1
        val archivo = listOfNotNull(nombre, version).joinToString(" ").replace(Regex("[\\\\/:*?\"<>|]"), "_") +
            if (partida) ".apks" else ".apk"

        val resolver = contexto.contentResolver
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, archivo)
                put(MediaStore.Downloads.MIME_TYPE, if (partida) TIPO_APKS else TIPO_APK)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/$CARPETA")
                put(MediaStore.Downloads.IS_PENDING, 1)
            },
        ) ?: throw Exception("no se pudo crear el archivo en el celular")
        try {
            escribir(contexto, tv, uri, rutas, tamanos, total, progreso)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // no dejar una copia a medias
            throw e
        }
        return Guardado(archivo, total)
    }

    private fun escribir(
        contexto: Context, tv: ConexionTv, uri: Uri,
        rutas: List<String>, tamanos: List<Long>, total: Long, progreso: (Long, Long) -> Unit,
    ) {
        val salida = contexto.contentResolver.openOutputStream(uri) ?: throw Exception("no se pudo escribir en el celular")
        salida.use { s ->
            if (rutas.size == 1) {
                traer(tv, rutas[0], s, total) { progreso(it, total) }
                return
            }
            val zip = ZipOutputStream(s)
            zip.setLevel(Deflater.NO_COMPRESSION) // los APK ya vienen comprimidos
            var antes = 0L
            rutas.forEachIndexed { i, ruta ->
                zip.putNextEntry(ZipEntry(ruta.substringAfterLast('/')))
                traer(tv, ruta, zip, tamanos[i]) { progreso(antes + it, total) }
                zip.closeEntry()
                antes += tamanos[i]
            }
            zip.finish()
        }
    }

    private fun traer(tv: ConexionTv, ruta: String, salida: OutputStream, tamano: Long, progreso: (Long) -> Unit) {
        var llego = 0L
        tv.recibirArchivo(ruta, salida) { llego = it; progreso(it) }
        // Si se cortó la conexión, el archivo queda corto: mejor avisar que guardar una copia rota.
        if (llego != tamano) throw Exception("llegó incompleta (${EstadoArchivos.tamano(llego)} de ${EstadoArchivos.tamano(tamano)})")
    }

    /** Instala en la TV una copia .apks: manda todos los pedazos juntos. Devuelve lo que contestó la TV. */
    fun instalarPartida(contexto: Context, tv: ConexionTv, entrada: InputStream, progreso: (Long, Long) -> Unit): String {
        val carpeta = File(contexto.cacheDir, "apks").apply { deleteRecursively(); mkdirs() }
        try {
            val pedazos = mutableListOf<File>()
            ZipInputStream(entrada).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory || !e.name.endsWith(".apk", true)) continue
                    val f = File(carpeta, "${pedazos.size}.apk")
                    f.outputStream().use { zip.copyTo(it) }
                    pedazos += f
                }
            }
            if (pedazos.isEmpty()) throw Exception("adentro no hay ningún APK")
            val total = pedazos.sumOf { it.length() }
            val creada = tv.ejecutar("cmd package install-create -r -S $total 2>&1", segundos = 30)
            val sesion = Regex("\\[(\\d+)]").find(creada)?.groupValues?.get(1) ?: return creada
            var antes = 0L
            pedazos.forEach { f ->
                val r = f.inputStream().use { tv.mandarPedazo(sesion, f.name, it, f.length()) { hecho -> progreso(antes + hecho, total) } }
                if (!r.contains("Success")) {
                    runCatching { tv.ejecutar("cmd package install-abandon $sesion", segundos = 20) }
                    return r
                }
                antes += f.length()
            }
            return tv.ejecutar("cmd package install-commit $sesion 2>&1", segundos = 180)
        } finally {
            carpeta.deleteRecursively()
        }
    }
}
