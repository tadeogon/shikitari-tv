package com.shikitari.instalador.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shikitari.instalador.Bitacora
import com.shikitari.instalador.ConexionTv
import java.util.concurrent.Executors

/**
 * Los archivos de la TV (su almacenamiento interno, la carpeta /sdcard): entrar a las carpetas, borrar,
 * traer archivos al celular, pasar archivos del celular a la TV e instalar APKs. Misma lógica que la pantalla vieja.
 */
class EstadoArchivos(private val app: EstadoApp) {

    data class Archivo(val nombre: String, val carpeta: Boolean, val tamano: Long)

    private val contexto: Context get() = app.contexto
    private val hilo = Executors.newSingleThreadExecutor()

    var ruta by mutableStateOf(RAIZ)
        private set
    var cosas by mutableStateOf<List<Archivo>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var espacio by mutableStateOf("")
        private set

    // Lo que se está pasando
    var ocupado by mutableStateOf(false)
        private set
    var textoProgreso by mutableStateOf("")
        private set
    var detalleProgreso by mutableStateOf("")
        private set
    var fraccion by mutableStateOf<Float?>(0f)
        private set
    var resultado by mutableStateOf<Boolean?>(null) // null = todavía pasando
        private set

    /** Para que la pantalla no se apague mientras pasa algo. */
    var alCambiarOcupado: (Boolean) -> Unit = {}

    fun cerrar() = hilo.shutdownNow()

    val enRaiz get() = ruta == RAIZ

    fun titulo(): String = if (enRaiz) "Almacenamiento" else ruta.substringAfterLast('/')

    fun camino(): String = "Almacenamiento" + ruta.removePrefix(RAIZ).replace("/", " › ")

    fun subir() {
        if (!enRaiz) abrir(ruta.substringBeforeLast('/'))
    }

    fun abrir(nueva: String) {
        ruta = nueva
        cosas = null
        error = null
        hilo.execute {
            try {
                // Una línea por cosa: "directory|4096|/sdcard/Movies"
                val salida = app.tv.ejecutar("stat -c '%F|%s|%n' ${ConexionTv.comillas(nueva)}/* 2>/dev/null", segundos = 30)
                val lista = salida.lines().mapNotNull { linea ->
                    val partes = linea.trim().split('|', limit = 3)
                    if (partes.size < 3) return@mapNotNull null
                    Archivo(partes[2].substringAfterLast('/'), partes[0] == "directory", partes[1].toLongOrNull() ?: 0)
                }.sortedWith(compareBy({ !it.carpeta }, { it.nombre.lowercase() }))
                val libre = app.tv.ejecutar("df -h $RAIZ | tail -1", segundos = 10).trim().split(Regex("\\s+"))
                app.enUi {
                    if (libre.size >= 4) espacio = "Libre: ${libre[3]} de ${libre[1]}"
                    if (ruta == nueva) cosas = lista
                }
            } catch (e: Exception) {
                app.enUi { if (ruta == nueva) error = "No se pudo leer: ${e.message ?: "sin conexión"}" }
            }
        }
    }

    // ---------- Opciones de un archivo ----------

    fun opciones(a: Archivo, guardar: (Archivo) -> Unit) {
        if (ocupado) {
            app.cartel.avisar(false, "Esperá a que termine lo que se está pasando.")
            return
        }
        val esApk = a.nombre.endsWith(".apk", ignoreCase = true)
        val items = mutableListOf<Opcion>()
        if (a.carpeta) {
            items += Opcion("Abrir", Iconos.carpeta) { abrir("$ruta/${a.nombre}") }
        } else {
            items += Opcion("Guardar en el celular", Iconos.bajar) { guardar(a) }
            if (esApk) items += Opcion("Instalar en la TV", Iconos.paquete) { instalarApk(a) }
        }
        val protegida = a.carpeta && enRaiz && a.nombre == "Android"
        if (!protegida) items += Opcion("Borrar", Iconos.basura, peligro = true) { confirmarBorrar(a) }
        app.dialogos.hoja = HojaPedida(a.nombre, if (a.carpeta) "Carpeta" else tamano(a.tamano), items)
    }

    private fun confirmarBorrar(a: Archivo) {
        val mensaje = if (a.carpeta) "Se borra la carpeta con todo lo que tiene adentro. No se puede deshacer."
        else "Se borra de la TV (${tamano(a.tamano)}). No se puede deshacer."
        app.dialogos.confirmacion = ConfirmacionPedida("¿Borrar ${a.nombre}?", mensaje, "Borrar", peligro = true) { si -> if (si) borrar(a) }
    }

    private fun borrar(a: Archivo) {
        val completa = "$ruta/${a.nombre}"
        app.cartel.trabajando("Borrando ${a.nombre}")
        hilo.execute {
            val comando = "rm -rf ${ConexionTv.comillas(completa)}"
            val salida = try {
                app.tv.ejecutar("$comando 2>&1; echo FIN:\$?", segundos = 120)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            val ok = salida.contains("FIN:0")
            Bitacora.anotar(contexto, "▸ Borrar archivo de la TV\n$ $comando\n${salida.replace(Regex("FIN:\\d+"), "").trim().ifEmpty { "OK" }}")
            app.enUi {
                app.cartel.avisar(ok, if (ok) "${a.nombre} borrado" else "No se pudo borrar: $salida")
                abrir(ruta)
            }
        }
    }

    private fun instalarApk(a: Archivo) {
        app.cartel.trabajando("Instalando ${a.nombre}")
        hilo.execute {
            val comando = "pm install -r ${ConexionTv.comillas("$ruta/${a.nombre}")}"
            val salida = try {
                app.tv.ejecutar("$comando 2>&1", segundos = 180)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            Bitacora.anotar(contexto, "▸ Instalar APK desde Archivos\n$ $comando\n$salida")
            val ok = salida.contains("Success")
            app.enUi { app.cartel.avisar(ok, if (ok) "Instalada en la TV" else "No se pudo instalar: ${motivoError(salida)}") }
        }
    }

    // ---------- Traer de la TV al celular ----------

    fun traer(a: Archivo, destino: Uri) {
        val origen = "$ruta/${a.nombre}"
        empezar("Trayendo ${a.nombre}")
        hilo.execute {
            try {
                val salida = contexto.contentResolver.openOutputStream(destino) ?: throw Exception("no se pudo crear el archivo en el celular")
                salida.use { s -> app.tv.recibirArchivo(origen, s) { hecho -> progreso(hecho, a.tamano) } }
                terminar(true, "${a.nombre} quedó guardado en el celular")
            } catch (e: Exception) {
                terminar(false, "No se pudo traer ${a.nombre}: ${e.message}")
            }
        }
    }

    // ---------- Pasar del celular a la TV ----------

    fun subirArchivos(elegidos: List<Uri>) {
        if (elegidos.isEmpty()) return
        val destino = ruta
        empezar("Preparando")
        hilo.execute {
            var bien = 0
            val errores = mutableListOf<String>()
            elegidos.forEachIndexed { i, uri ->
                val (nombre, tam) = datosDe(uri)
                val donde = "$destino/$nombre"
                val cual = if (elegidos.size > 1) " (${i + 1} de ${elegidos.size})" else ""
                app.enUi { textoProgreso = "Pasando $nombre$cual" }
                try {
                    val entrada = contexto.contentResolver.openInputStream(uri) ?: throw Exception("no se pudo abrir")
                    entrada.use { e -> app.tv.enviarArchivo(donde, e) { hecho -> progreso(hecho, tam) } }
                    // Se confirma que llegó entero. La TV termina de escribir unos segundos después de recibir,
                    // así que se espera mientras el archivo siga creciendo.
                    if (tam > 0) {
                        app.enUi { textoProgreso = "Terminando de guardar $nombre en la TV" }
                        var enTv = -1L
                        var anterior = -2L
                        var quietos = 0
                        while (enTv != tam && quietos < 6) {
                            Thread.sleep(500)
                            enTv = app.tv.ejecutar("stat -c %s ${ConexionTv.comillas(donde)}", segundos = 15).trim().toLongOrNull() ?: -1
                            if (enTv == anterior) quietos++ else quietos = 0
                            anterior = enTv
                        }
                        if (enTv != tam) throw Exception("llegó incompleto (${tamano(enTv)} de ${tamano(tam)})")
                    }
                    bien++
                    Bitacora.anotar(contexto, "▸ Pasar archivo a la TV\n$nombre → $donde (${tamano(tam)})")
                } catch (ex: Exception) {
                    errores += "$nombre: ${ex.message}"
                    Bitacora.anotar(contexto, "▸ Pasar archivo a la TV — falló\n$nombre: ${ex.message}")
                }
            }
            val ok = errores.isEmpty()
            terminar(
                ok,
                if (ok) "${if (bien == 1) "Archivo pasado" else "$bien archivos pasados"} a la TV"
                else "Pasaron $bien de ${elegidos.size}.\n" + errores.joinToString("\n"),
            )
            app.enUi { if (ruta == destino) abrir(destino) }
        }
    }

    // ---------- Instalar un APK que está en el celular ----------

    fun instalarDelCelular(elegidos: List<Uri>) {
        if (elegidos.isEmpty()) return
        empezar("Preparando")
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
                            else "no es un APK",
                        )
                    }
                    if (tam <= 0) throw Exception("no se pudo saber cuánto pesa")
                    app.enUi { textoProgreso = "Instalando $nombre$cual" }
                    val salida = contexto.contentResolver.openInputStream(uri)?.use { e ->
                        app.tv.instalarApk(e, tam) { hecho ->
                            progreso(hecho, tam)
                            if (hecho >= tam) app.enUi { textoProgreso = "La TV está instalando $nombre" }
                        }
                    } ?: throw Exception("no se pudo abrir")
                    Bitacora.anotar(contexto, "▸ Instalar APK del celular en la TV\n$nombre (${tamano(tam)})\n$salida")
                    if (!salida.contains("Success")) throw Exception(motivoError(salida))
                    bien++
                } catch (ex: Exception) {
                    errores += "$nombre: ${ex.message}"
                }
            }
            val ok = errores.isEmpty()
            terminar(
                ok,
                if (ok) "${if (bien == 1) "App instalada" else "$bien apps instaladas"} en la TV"
                else "Se instalaron $bien de ${elegidos.size}.\n" + errores.joinToString("\n"),
            )
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
        contexto.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { nombre = it }
                if (!c.isNull(1)) tam = c.getLong(1)
            }
        }
        return nombre.replace('/', '_') to tam
    }

    // ---------- Progreso ----------

    private fun empezar(texto: String) {
        ocupado = true
        resultado = null
        textoProgreso = texto
        detalleProgreso = ""
        fraccion = 0f
        alCambiarOcupado(true)
    }

    private var ultimoAviso = 0L

    private fun progreso(hecho: Long, total: Long) {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoAviso < 150) return // no hace falta redibujar en cada pedacito
        ultimoAviso = ahora
        app.enUi {
            fraccion = if (total > 0) (hecho.toFloat() / total).coerceIn(0f, 1f) else null
            detalleProgreso = tamano(hecho) + (if (total > 0) " de " + tamano(total) else "")
        }
    }

    private fun terminar(ok: Boolean, texto: String) = app.enUi {
        ocupado = false
        resultado = ok
        if (ok) fraccion = 1f
        textoProgreso = texto
        detalleProgreso = ""
        alCambiarOcupado(false)
    }

    fun olvidarResultado() {
        if (!ocupado) resultado = null
    }

    companion object {
        const val RAIZ = "/sdcard"

        fun tamano(bytes: Long): String = when {
            bytes < 0 -> ""
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.0f KB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
            else -> String.format("%.2f GB", bytes / 1073741824.0)
        }
    }
}

/** Crear un archivo en el celular con nombre y tipo (para «Guardar en el celular»). */
private class CrearDocumento : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>) =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
fun PantallaArchivos(app: EstadoApp, archivos: EstadoArchivos, instalarAlEntrar: Boolean, onAtras: () -> Unit) {
    val actividad = LocalContext.current as? Activity
    var paraGuardar by remember { mutableStateOf<EstadoArchivos.Archivo?>(null) }

    val guardar = rememberLauncherForActivityResult(CrearDocumento()) { uri ->
        val a = paraGuardar
        paraGuardar = null
        if (uri != null && a != null) archivos.traer(a, uri)
    }
    val subir = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        archivos.subirArchivos(uris)
    }
    val apk = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        archivos.instalarDelCelular(uris)
    }
    // Algunos celulares no marcan los APK con su tipo, así que se aceptan todos y se revisa después.
    val elegirApk = { apk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) }
    val elegirParaSubir = { subir.launch(arrayOf("*/*")) }

    archivos.alCambiarOcupado = { si ->
        if (si) actividad?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else actividad?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    LaunchedEffect(Unit) {
        archivos.abrir(archivos.ruta)
        if (instalarAlEntrar) elegirApk()
    }
    LaunchedEffect(archivos.resultado) {
        if (archivos.resultado != null) {
            kotlinx.coroutines.delay(if (archivos.resultado == true) 4000 else 9000)
            archivos.olvidarResultado()
        }
    }
    // Atrás sube una carpeta; en la raíz, vuelve.
    BackHandler(enabled = !archivos.enRaiz) { archivos.subir() }

    val pedirGuardar: (EstadoArchivos.Archivo) -> Unit = { a ->
        paraGuardar = a
        val tipo = MimeTypeMap.getSingleton().getMimeTypeFromExtension(a.nombre.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        guardar.launch(a.nombre to tipo)
    }

    Pagina(
        titulo = archivos.titulo(),
        subtitulo = listOf(archivos.espacio, if (!archivos.enRaiz) archivos.camino() else "").filter { it.isNotBlank() }.joinToString(" · "),
        onAtras = { if (archivos.enRaiz) onAtras() else archivos.subir() },
        accion = {
            BotonBarra(Iconos.agregar, "Pasar a la TV") {
                if (archivos.ocupado) {
                    app.cartel.avisar(false, "Esperá a que termine lo que se está pasando.")
                } else {
                    app.dialogos.hoja = HojaPedida(
                        "Pasar a la TV", "Se guarda en: ${archivos.camino()}",
                        listOf(
                            Opcion("Archivos del celular", Iconos.subir) { elegirParaSubir() },
                            Opcion("Instalar un APK del celular", Iconos.paquete) { elegirApk() },
                        ),
                    )
                }
            }
        },
    ) {
        // Lo que se está pasando
        AnimatedVisibility(visible = archivos.ocupado || archivos.resultado != null) {
            Tarjeta(Modifier.padding(top = Espacio.hueco), alto = true) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        archivos.textoProgreso + if (archivos.ocupado) "…" else "",
                        style = Letras.cuerpo.copy(color = if (archivos.resultado == false) Colores.error else Colores.texto),
                    )
                    if (archivos.detalleProgreso.isNotBlank()) Text(archivos.detalleProgreso, style = Letras.tabular)
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(Formas.pastilla)
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(archivos.fraccion ?: 0.3f)
                                .clip(Formas.pastilla)
                                .background(Color.White),
                        )
                    }
                }
            }
        }

        Hueco()
        val lista = archivos.cosas
        Tarjeta {
            when {
                archivos.error != null -> NotaTarjeta(archivos.error ?: "")
                lista == null -> NotaTarjeta("Leyendo…")
                lista.isEmpty() -> NotaTarjeta("Esta carpeta está vacía.")
                else -> lista.forEachIndexed { i, a ->
                    if (a.carpeta) {
                        FilaIr(a.nombre, null, Iconos.carpeta, primera = i == 0, alMantener = { archivos.opciones(a, pedirGuardar) }) {
                            archivos.abrir("${archivos.ruta}/${a.nombre}")
                        }
                    } else {
                        val esApk = a.nombre.endsWith(".apk", true)
                        FilaIr(
                            a.nombre,
                            EstadoArchivos.tamano(a.tamano),
                            if (esApk) Iconos.paquete else Iconos.archivo,
                            primera = i == 0,
                            derecha = { IconoMas() },
                        ) { archivos.opciones(a, pedirGuardar) }
                    }
                }
            }
        }
        Text(
            "Tocá una carpeta para entrar. Tocá un archivo (o dejá apretada una carpeta) para ver qué hacer. Con + pasás cosas del celular.",
            style = Letras.nota,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
}

@Composable
private fun IconoMas() = androidx.compose.material3.Icon(
    Iconos.mas, null, tint = Colores.textoDim, modifier = Modifier.padding(start = 4.dp),
)
