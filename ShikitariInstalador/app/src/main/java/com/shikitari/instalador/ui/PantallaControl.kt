package com.shikitari.instalador.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as ColorAndroid
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikitari.instalador.Bitacora
import com.shikitari.instalador.ControlActivity
import com.shikitari.instalador.SamsungTv
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Control de la TV: la pantalla casi en vivo (una captura por segundo), el control remoto
 * y las apps instaladas. Es la lógica de la pantalla vieja de Control, pasada en limpio.
 */
class EstadoControl(private val app: EstadoApp) {

    private val contexto: Context get() = app.contexto
    private val prefs = contexto.getSharedPreferences("app", Context.MODE_PRIVATE)

    // Cada cosa en su propio hilo: la captura no frena las teclas, y las teclas salen en orden.
    private val hiloCaptura = Executors.newSingleThreadExecutor()
    private val hiloTeclas = Executors.newSingleThreadExecutor()
    private val hiloApps = Executors.newSingleThreadExecutor()
    private val capturando = AtomicBoolean(false)

    var imagen by mutableStateOf<ImageBitmap?>(null)
        private set
    var aviso by mutableStateOf<String?>("Conectando con la pantalla…")
        private set
    var pausa by mutableStateOf(false)
        private set

    // Video en vivo: si la TV no puede, se vuelve a las fotos (una por segundo).
    var videoFallo by mutableStateOf(false)
    var enVivo by mutableStateOf(false)
        private set

    fun alVideo(e: VideoTv.Estado) {
        when (e) {
            VideoTv.Estado.CONECTANDO -> enVivo = false
            VideoTv.Estado.EN_VIVO -> enVivo = true
            VideoTv.Estado.FALLO -> {
                enVivo = false
                videoFallo = true
                Bitacora.anotar(contexto, "▸ Vista en vivo: la TV no mandó video, se usan fotos")
            }
        }
    }

    var apps by mutableStateOf<List<Pair<String, String>>>(emptyList()) // paquete, nombre
        private set
    var estadoApps by mutableStateOf("Buscando las apps de la TV…")
        private set
    var favoritas by mutableStateOf(leerFavoritas())
        private set
    private var instaladasPorVos: Set<String> = emptySet() // las de "-3"; el resto vino de fábrica
    private var appsDe: String? = null

    fun cerrar() {
        hiloCaptura.shutdownNow()
        hiloTeclas.shutdownNow()
        hiloVolumen.shutdownNow()
        hiloApps.shutdownNow()
    }

    // ---------- Vista de la TV ----------

    fun alternarPausa() {
        pausa = !pausa
        if (pausa) aviso = "Vista en pausa"
    }

    /** Pide una captura; si la anterior todavía no llegó, saltea esta vuelta para no amontonar pedidos. */
    fun pedirCaptura() {
        if (!capturando.compareAndSet(false, true)) return
        hiloCaptura.execute {
            try {
                val datos = app.tv.leerBinario("screencap -p")
                val opciones = BitmapFactory.Options().apply { inSampleSize = 2 } // la mitad alcanza para el celular
                val foto = BitmapFactory.decodeByteArray(datos, 0, datos.size, opciones)
                app.enUi {
                    if (pausa) return@enUi
                    when {
                        foto == null -> aviso = "No llegó la imagen de la TV. Reintentando…"
                        esNegra(foto) -> {
                            imagen = foto.asImageBitmap()
                            aviso = "Contenido protegido, no se puede mostrar"
                        }
                        else -> {
                            imagen = foto.asImageBitmap()
                            aviso = null
                        }
                    }
                }
            } catch (e: Exception) {
                app.enUi { aviso = "No se pudo ver la pantalla: ${e.message ?: "sin conexión"}" }
            } finally {
                capturando.set(false)
            }
        }
    }

    /** Netflix y otras apps con DRM salen totalmente negras en la captura. */
    private fun esNegra(b: Bitmap): Boolean {
        val pasosX = 16
        val pasosY = 9
        for (i in 0 until pasosX) for (j in 0 until pasosY) {
            val p = b.getPixel(b.width * (2 * i + 1) / (2 * pasosX), b.height * (2 * j + 1) / (2 * pasosY))
            if (ColorAndroid.red(p) + ColorAndroid.green(p) + ColorAndroid.blue(p) > 36) return false
        }
        return true
    }

    // ---------- Control remoto ----------

    fun mandarTecla(codigo: String) {
        hiloTeclas.execute {
            try {
                app.tv.ejecutar("input keyevent $codigo", segundos = 10)
                // Que la vista muestre enseguida el cambio, sin esperar al próximo segundo.
                app.enUi { if (!pausa && videoFallo) pedirCaptura() }
            } catch (e: Exception) {
                app.enUi { app.cartel.avisar(false, "No llegó a la TV: ${e.message}") }
            }
        }
    }

    // ---------- Volumen ----------
    // Por HDMI, el Fire Stick ignora el volumen («fixed volume device»): hay que pedírselo a la tele.
    // Con parlante o auriculares Bluetooth sí lo maneja él.

    private val samsung = SamsungTv(contexto)
    private val hiloVolumen = Executors.newSingleThreadExecutor()
    private var volumenFijo = false
    private var volumenMirado = 0L
    private var volumenDe = ""
    private var teleSamsung: String? = null

    fun volumen(codigo: String, teclaSamsung: String) {
        val ip = app.conectada?.ip ?: return
        hiloVolumen.execute {
            val ahora = System.currentTimeMillis()
            if (volumenDe != ip || ahora - volumenMirado > 20_000) {
                if (volumenDe != ip) {
                    volumenDe = ip
                    volumenFijo = false
                    teleSamsung = null
                }
                val audio = app.tv.leerSuave("dumpsys audio | grep -E 'mFixedVolumeDevices|STREAM_MUSIC:|Devices:'")
                if (audio != null) {
                    volumenMirado = ahora
                    volumenFijo = sinVolumenPropio(audio)
                }
            }
            if (!volumenFijo) {
                mandarTecla(codigo)
                return@execute
            }
            try {
                val tele = teleSamsung ?: run {
                    app.enUi { app.cartel.trabajando("Buscando la tele") }
                    samsung.buscar(ip).also { teleSamsung = it }
                }
                if (tele == null) {
                    app.enUi { app.cartel.avisar(false, "Por HDMI el volumen lo maneja la tele, y no encontré una Samsung en el Wi-Fi.") }
                    return@execute
                }
                val primera = !samsung.conPermiso(tele)
                if (primera) app.enUi { app.cartel.trabajando("Mirá la tele: aceptá «Shikitari Control» (elegí Permitir)") }
                samsung.tecla(tele, teclaSamsung)
                app.enUi { if (primera) app.cartel.avisar(true, "Listo: el volumen ahora va directo a la tele") else app.cartel.cerrar() }
            } catch (e: SamsungTv.SinPermiso) {
                app.enUi { app.cartel.avisar(false, "La tele no dio permiso. Tocá de nuevo y elegí Permitir en la pantalla.") }
            } catch (e: Exception) {
                teleSamsung = null
                Bitacora.anotar(contexto, "▸ Volumen de la tele — falló\n${e.message}")
                app.enUi { app.cartel.avisar(false, "La tele no contestó al volumen: ${e.message}") }
            }
        }
    }

    /** ¿La música sale por HDMI y la TV marca ese camino como de volumen fijo? */
    private fun sinVolumenPropio(audio: String): Boolean {
        val lineas = audio.lines().map { it.trim() }
        val fijos = lineas.firstOrNull { it.startsWith("mFixedVolumeDevices") }?.substringAfter('=')?.split(',')?.map { it.trim() }.orEmpty()
        val i = lineas.indexOfFirst { it.contains("STREAM_MUSIC:") }
        val sale = if (i < 0) "" else lineas.drop(i).firstOrNull { it.startsWith("Devices:") }.orEmpty()
        return "0x400" in fijos && sale.substringAfter(':').trim() == "hdmi"
    }

    // ---------- Apps ----------

    /** Lee las apps de la TV conectada (una vez por TV; [forzar] para releer). */
    fun cargarApps(forzar: Boolean = false) {
        val ip = app.conectada?.ip ?: return
        if (!forzar && appsDe == ip && apps.isNotEmpty()) return
        appsDe = ip
        estadoApps = "Buscando las apps de la TV…"
        hiloApps.execute {
            try {
                val terceros = paquetes(app.tv.ejecutar("pm list packages -3"))
                // YouTube, Netflix y otras vienen de fábrica en el Chromecast: "-3" no las lista, así que se suman aparte.
                val todas = paquetes(app.tv.ejecutar("pm list packages")).toSet()
                val deFabrica = ControlActivity.NOMBRES.keys.filter { it in todas && it !in ControlActivity.OCULTAR }
                val lista = (terceros + deFabrica).distinct()
                    .filter { it !in ControlActivity.OCULTAR }
                    .map { it to nombreLegible(it) }
                    .sortedBy { it.second.lowercase() }
                app.enUi {
                    instaladasPorVos = terceros.toSet()
                    // La primera vez, las favoritas son las conocidas que estén instaladas.
                    if (!prefs.contains(ControlActivity.CLAVE_FAVORITAS)) {
                        guardarFavoritas(ControlActivity.FAVORITAS_INICIALES.filter { p -> lista.any { it.first == p } })
                    }
                    apps = lista
                    estadoApps = if (lista.isEmpty()) "No se encontraron apps en la TV." else "${lista.size} apps · dejá apretada una para guardarla en el celular, fijarla o desinstalarla"
                }
            } catch (e: Exception) {
                app.enUi { estadoApps = "No se pudieron leer las apps: ${e.message}" }
            }
        }
    }

    private fun paquetes(salida: String) =
        salida.lines().map { it.trim().removePrefix("package:") }.filter { it.isNotBlank() && !it.contains(" ") }

    fun abrirApp(paquete: String, nombre: String) {
        app.cartel.trabajando("Abriendo $nombre")
        hiloTeclas.execute {
            try {
                var salida = app.tv.ejecutar("monkey -p $paquete 1 2>&1", segundos = 15)
                if (!abrio(salida)) {
                    // Algunas apps de TV solo tienen la entrada de Android TV.
                    salida = app.tv.ejecutar("monkey -p $paquete -c android.intent.category.LEANBACK_LAUNCHER 1 2>&1", segundos = 15)
                }
                val ok = abrio(salida)
                app.enUi { app.cartel.avisar(ok, if (ok) "$nombre abierta en la TV" else "$nombre no se pudo abrir (no tiene pantalla de inicio).") }
            } catch (e: Exception) {
                app.enUi { app.cartel.avisar(false, "No llegó a la TV: ${e.message}") }
            }
        }
    }

    /** monkey siempre avisa "no activities found for category MONKEY"; lo que importa es que haya mandado el evento. */
    private fun abrio(salida: String) =
        salida.contains("Events injected", true) && !salida.contains("monkey aborted", true)

    private fun leerFavoritas(): List<String> =
        (prefs.getString(ControlActivity.CLAVE_FAVORITAS, "") ?: "").split(',').filter { it.isNotBlank() }

    private fun guardarFavoritas(lista: List<String>) {
        prefs.edit().putString(ControlActivity.CLAVE_FAVORITAS, lista.distinct().joinToString(",")).apply()
        favoritas = leerFavoritas()
    }

    fun opcionesApp(paquete: String, nombre: String) {
        val esFavorita = paquete in favoritas
        app.dialogos.hoja = HojaPedida(
            nombre, paquete,
            listOf(
                Opcion("Abrir en la TV", Iconos.play) { abrirApp(paquete, nombre) },
                Opcion(if (esFavorita) "Quitar de favoritas" else "Fijar arriba", Iconos.estrella) { alternarFavorita(paquete, nombre) },
                Opcion("Guardar APK en el celular", Iconos.bajar) { guardarEnCelular(paquete, nombre) },
                Opcion("Desinstalar de la TV", Iconos.basura, peligro = true) { confirmarDesinstalar(paquete, nombre) },
            ),
        )
    }

    /** Cuántas copias se están guardando (mientras tanto, la pantalla del celular no se apaga). */
    var guardando by mutableStateOf(0)
        private set

    private fun guardarEnCelular(paquete: String, nombre: String) {
        app.cartel.trabajando("Guardando $nombre")
        guardando++
        hiloApps.execute {
            var ultimo = 0L
            try {
                val g = Respaldo.guardar(contexto, app.tv, paquete, nombre) { hecho, total ->
                    val ahora = System.currentTimeMillis()
                    if (ahora - ultimo > 500 && total > 0) {
                        ultimo = ahora
                        val parte = (hecho * 100 / total).toInt()
                        app.enUi { app.cartel.trabajando("Guardando $nombre: $parte % de ${EstadoArchivos.tamano(total)}") }
                    }
                }
                Bitacora.anotar(contexto, "▸ Guardar APK en el celular\n$nombre ($paquete) → ${g.archivo} (${EstadoArchivos.tamano(g.bytes)})")
                app.enUi { app.cartel.avisar(true, "Guardada en Descargas › ${Respaldo.CARPETA}: ${g.archivo}") }
            } catch (e: Exception) {
                Bitacora.anotar(contexto, "▸ Guardar APK en el celular — falló\n$nombre ($paquete): ${e.message}")
                app.enUi { app.cartel.avisar(false, "No se pudo guardar $nombre: ${e.message}. No salgas de la app mientras se guarda.") }
            } finally {
                app.enUi { guardando-- }
            }
        }
    }

    private fun confirmarDesinstalar(paquete: String, nombre: String) {
        if (paquete in ControlActivity.PROTEGIDAS) {
            app.cartel.avisar(false, "$nombre es parte del sistema de la TV: no conviene sacarla.")
            return
        }
        val deFabrica = paquete !in instaladasPorVos
        val detalle = if (deFabrica)
            "Vino con la TV, así que se quita solo para tu usuario (no se rompe nada y se puede recuperar)."
        else
            "Se borra de la TV junto con sus datos. Si la querés de nuevo, hay que volver a instalarla."
        app.dialogos.confirmacion = ConfirmacionPedida("¿Desinstalar $nombre?", detalle, "Desinstalar", peligro = true) { si ->
            if (si) desinstalar(paquete, nombre, deFabrica)
        }
    }

    private fun desinstalar(paquete: String, nombre: String, deFabrica: Boolean) {
        app.cartel.trabajando("Desinstalando $nombre")
        hiloApps.execute {
            val comando = if (deFabrica) "pm uninstall -k --user 0 $paquete" else "pm uninstall $paquete"
            val salida = try {
                app.tv.ejecutar("$comando 2>&1", segundos = 60)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            val ok = salida.contains("Success")
            Bitacora.anotar(contexto, "▸ Desinstalar $nombre ($paquete)\n$ $comando\n$salida")
            app.enUi {
                if (ok) {
                    app.cartel.avisar(true, "$nombre desinstalada")
                    apps = apps.filter { it.first != paquete }
                    guardarFavoritas(favoritas - paquete)
                } else {
                    app.cartel.avisar(false, "No se pudo desinstalar $nombre: $salida")
                }
            }
        }
    }

    private fun alternarFavorita(paquete: String, nombre: String) {
        if (paquete in favoritas) {
            guardarFavoritas(favoritas - paquete)
            app.cartel.avisar(true, "$nombre ya no está en favoritas")
        } else {
            guardarFavoritas(favoritas + paquete)
            app.cartel.avisar(true, "$nombre fijada arriba")
        }
    }

    /** Nombre para mostrar: los conocidos con su nombre real; el resto, armado a partir del paquete. */
    private fun nombreLegible(paquete: String): String {
        ControlActivity.NOMBRES[paquete]?.let { return it }
        val ignorar = setOf("com", "org", "net", "tv", "android", "app", "apps", "mobile", "google", "atv", "androidtv", "leanback", "ui", "client", "launcher", "release", "player")
        val parte = paquete.split('.').lastOrNull { it.lowercase() !in ignorar && it.length > 1 }
            ?: paquete.substringAfterLast('.')
        return parte.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { it.uppercase() }
    }
}

// ---------- Vista en vivo ----------

/** Un TextureView donde VideoTv dibuja el video de la TV. Al salir de la pantalla, el video se corta solo. */
@Composable
private fun VistaVideo(app: EstadoApp, control: EstadoControl) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { control.alVideo(VideoTv.Estado.CONECTANDO) }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.view.TextureView(ctx).apply {
                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    private var video: VideoTv? = null
                    private var superficie: android.view.Surface? = null

                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        val s = android.view.Surface(st)
                        superficie = s
                        video = VideoTv(app.tv, s) { e -> app.enUi { control.alVideo(e) } }.also { it.empezar() }
                    }

                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}

                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                        video?.parar()
                        video = null
                        superficie?.release()
                        superficie = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// ---------- Pestaña Control ----------

@Composable
fun PestanaControl(app: EstadoApp, control: EstadoControl, irA: (Ruta) -> Unit, irAPestana: (Pestana) -> Unit) {
    val conectada = app.conectada
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val estadoCiclo by ciclo.currentStateFlow.collectAsStateWithLifecycle()
    // Si la conexión se cortó, deja de pedir capturas hasta que vuelva (el aviso de arriba lo explica).
    val mirando = estadoCiclo.isAtLeast(Lifecycle.State.RESUMED) && conectada != null && !control.pausa &&
        app.enlace == EstadoApp.Enlace.BIEN
    val conVideo = !control.videoFallo
    LaunchedEffect(mirando, conVideo) {
        while (mirando && !conVideo) {
            control.pedirCaptura()
            delay(1000)
        }
    }
    LaunchedEffect(conectada?.ip) {
        control.videoFallo = false // con otra TV se vuelve a probar el video
        control.cargarApps()
    }

    if (conectada == null) {
        Pagina(titulo = "Control", antetitulo = "Sin conexión") { SinTv(irAPestana) }
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Arriba: en qué está la vista y el botón de pausa
        Row(
            Modifier.fillMaxWidth().padding(start = Espacio.borde, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val vivo = !control.pausa && (if (conVideo) control.enVivo else control.imagen != null)
            Box(Modifier.size(8.dp).background(if (vivo) Colores.acento else Colores.textoDim2, CircleShape))
            Text(
                when {
                    control.pausa -> "EN PAUSA"
                    vivo -> "EN VIVO"
                    else -> "CONECTANDO"
                },
                style = Letras.seccion.copy(color = Colores.texto),
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                conectada.nombre.uppercase(),
                style = Letras.seccion,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            BotonBarra(if (control.pausa) Iconos.play else Iconos.pausa, if (control.pausa) "Reanudar la vista" else "Pausar la vista") { control.alternarPausa() }
        }
        // Pantalla de la TV, de borde a borde (16:9)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Colores.superficie1),
            contentAlignment = Alignment.Center,
        ) {
            if (conVideo) {
                // key: si cambia la TV o se reconecta, el video arranca de nuevo.
                if (mirando) androidx.compose.runtime.key(conectada.ip, conectada.puerto) { VistaVideo(app, control) }
            } else {
                control.imagen?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
            val texto = when {
                control.pausa -> "Vista en pausa"
                conVideo && !control.enVivo -> "Conectando con la pantalla…"
                conVideo -> null
                else -> control.aviso
            }
            texto?.let {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Text(it, style = Letras.detalle, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                }
            }
        }
        // Control remoto: centrado en lo que queda; si no entra, se desliza.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.borde, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Cruceta(control)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeclaTexto("Atrás", Modifier.weight(1f)) { control.mandarTecla("KEYCODE_BACK") }
                TeclaTexto("Inicio", Modifier.weight(1f)) { control.mandarTecla("KEYCODE_HOME") }
                TeclaTexto("Menú", Modifier.weight(1f)) { control.mandarTecla("KEYCODE_MENU") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeclaRedonda(Iconos.silencio, "Silenciar", color = Colores.textoDim, borde = false) { control.volumen("KEYCODE_VOLUME_MUTE", "KEY_MUTE") }
                TeclaRedonda(Iconos.menos, "Bajar el volumen") { control.volumen("KEYCODE_VOLUME_DOWN", "KEY_VOLDOWN") }
                Text("Volumen", style = Letras.detalle.copy(fontSize = 15.sp), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TeclaRedonda(Iconos.agregar, "Subir el volumen") { control.volumen("KEYCODE_VOLUME_UP", "KEY_VOLUP") }
                TeclaRedonda(Iconos.encendido, "Prender o apagar la TV", color = Colores.acento) { control.mandarTecla("KEYCODE_POWER") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TeclaIcono(Iconos.retroceder, "Retroceder", Modifier.weight(1f)) { control.mandarTecla("KEYCODE_MEDIA_REWIND") }
                TeclaRedonda(Iconos.pausa, "Pausar o seguir", tamano = 56, color = Colores.sobreAcento, relleno = Colores.texto) { control.mandarTecla("KEYCODE_MEDIA_PLAY_PAUSE") }
                TeclaIcono(Iconos.adelantar, "Adelantar", Modifier.weight(1f)) { control.mandarTecla("KEYCODE_MEDIA_FAST_FORWARD") }
            }
            // Las apps tienen su propia pantalla, para que esta no quede cargada.
            FilaIr("Apps de la TV", if (control.apps.isEmpty()) control.estadoApps else "${control.apps.size} apps · favoritas arriba", Iconos.grilla, primera = true) {
                irA(Ruta.Apps)
            }
        }
    }
}

@Composable
fun SinTv(irAPestana: (Pestana) -> Unit) {
    Tarjeta(Modifier.padding(top = Espacio.hueco)) {
        NotaTarjeta("Primero conectá una TV: elegila en Mis TVs.")
    }
    Hueco()
    BotonPrincipal("Ir a Mis TVs", onClick = { irAPestana(Pestana.TV) })
}

/** Las cuatro flechas y el OK, en un círculo de línea fina. */
@Composable
private fun Cruceta(control: EstadoControl) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(212.dp).vidrio(CircleShape, alto = true)) {
            Flecha(Iconos.chevronArriba, "Arriba", Modifier.align(Alignment.TopCenter)) { control.mandarTecla("KEYCODE_DPAD_UP") }
            Flecha(Iconos.chevronAbajo, "Abajo", Modifier.align(Alignment.BottomCenter)) { control.mandarTecla("KEYCODE_DPAD_DOWN") }
            Flecha(Iconos.chevronIzquierda, "Izquierda", Modifier.align(Alignment.CenterStart)) { control.mandarTecla("KEYCODE_DPAD_LEFT") }
            Flecha(Iconos.chevronDerecha, "Derecha", Modifier.align(Alignment.CenterEnd)) { control.mandarTecla("KEYCODE_DPAD_RIGHT") }
            val vibrar = LocalHapticFeedback.current
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(84.dp)
                    .clip(CircleShape)
                    .border(1.dp, Colores.linea, CircleShape)
                    .clickable(role = Role.Button) {
                        vibrar.performHapticFeedback(HapticFeedbackType.LongPress)
                        control.mandarTecla("KEYCODE_DPAD_CENTER")
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("OK", color = Colores.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Flecha(icono: ImageVector, nombre: String, modifier: Modifier, onClick: () -> Unit) {
    val vibrar = LocalHapticFeedback.current
    Box(
        modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) {
                vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = nombre, tint = Colores.texto, modifier = Modifier.size(24.dp))
    }
}

/** Tecla redonda con ícono; por defecto, solo el borde. */
@Composable
private fun TeclaRedonda(
    icono: ImageVector,
    nombre: String,
    tamano: Int = 48,
    color: Color = Colores.texto,
    relleno: Color = Color.Transparent,
    borde: Boolean = true,
    onClick: () -> Unit,
) {
    val vibrar = LocalHapticFeedback.current
    Box(
        Modifier
            .size(tamano.dp)
            .clip(CircleShape)
            .background(relleno)
            .border(1.dp, if (borde) Colores.linea else Color.Transparent, CircleShape)
            .clickable(role = Role.Button) {
                vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = nombre, tint = color, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TeclaTexto(texto: String, modifier: Modifier, onClick: () -> Unit) {
    val vibrar = LocalHapticFeedback.current
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(Formas.pastilla)
            .border(1.dp, Colores.linea, Formas.pastilla)
            .clickable(role = Role.Button) {
                vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, style = Letras.cuerpo.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun TeclaIcono(icono: ImageVector, nombre: String, modifier: Modifier, onClick: () -> Unit) {
    val vibrar = LocalHapticFeedback.current
    Box(
        modifier
            .heightIn(min = 56.dp)
            .clip(Formas.tarjeta)
            .border(1.dp, Colores.linea, Formas.tarjeta)
            .clickable(role = Role.Button) {
                vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = nombre, tint = Colores.texto, modifier = Modifier.size(22.dp))
    }
}

// ---------- Apps de la TV ----------

@Composable
fun PantallaApps(app: EstadoApp, control: EstadoControl, onAtras: () -> Unit) {
    LaunchedEffect(app.conectada?.ip) { control.cargarApps() }
    val vista = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(control.guardando > 0) {
        vista.keepScreenOn = control.guardando > 0
        onDispose { vista.keepScreenOn = false }
    }
    val favs = control.favoritas
    val fijadas = favs.mapNotNull { p -> control.apps.find { it.first == p } }
    val resto = control.apps.filter { it.first !in favs }
    Pagina(
        titulo = "Apps",
        subtitulo = control.estadoApps,
        onAtras = onAtras,
        accion = { BotonBarra(Iconos.volver, "Volver a leer") { control.cargarApps(forzar = true) } },
    ) {
        if (fijadas.isNotEmpty()) {
            EtiquetaSeccion("Favoritas")
            GrillaApps(fijadas, favorita = true, control)
        }
        if (resto.isNotEmpty()) {
            EtiquetaSeccion("Todas")
            GrillaApps(resto, favorita = false, control)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GrillaApps(lista: List<Pair<String, String>>, favorita: Boolean, control: EstadoControl) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lista.chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { (paquete, nombre) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .vidrio(Formas.campo, alto = favorita)
                            .combinedClickable(
                                onClick = { control.abrirApp(paquete, nombre) },
                                onLongClick = { control.opcionesApp(paquete, nombre) },
                            )
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            nombre,
                            style = Letras.cuerpo.copy(fontSize = 13.5.sp, fontWeight = if (favorita) FontWeight.SemiBold else FontWeight.Normal),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Completa la última fila para que no queden estirados.
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
