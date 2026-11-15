package com.shikitari.instalador.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Una línea de «lo último que se hizo» mientras limpia. */
data class Linea(val id: Int, val texto: String)

sealed interface Estado {
    data class Analizando(val perfil: String? = null) : Estado
    data class Mostrando(val plan: PlanUi) : Estado
    data class Limpiando(
        val plan: PlanUi,
        val progreso: Float,
        val hechos: Int,
        val total: Int,
        val narracion: String,
        val ultimas: List<Linea>,
    ) : Estado
    data class Listo(val plan: PlanUi, val resumen: Resumen, val segundos: Int, val ejecutados: List<String>) : Estado
    data class Fallo(val plan: PlanUi?, val mensaje: String) : Estado
}

private const val NOTA_EJEMPLO = "Vista de prueba con datos de ejemplo: todavía no cambia nada en tu TV."

private val GuardarSeleccion = Saver<MutableState<Set<String>?>, ArrayList<String>>(
    save = { estado -> estado.value?.let { ArrayList(it) } },
    restore = { mutableStateOf(it.toSet()) },
)

/** Los paquetes a apagar, en el orden del plan. */
private fun ordenados(plan: PlanUi, apagar: Set<String>): List<String> =
    (plan.todos() + plan.seQuedan).map { it.paquete }.filter { it in apagar }.distinct()

private fun scriptDe(plan: PlanUi, paquetes: List<String>): String = buildString {
    appendLine("# Shikitari · Optimizar · ${plan.modelo}")
    appendLine("# Lo que se apagó, para repetirlo o deshacerlo desde una PC con adb.")
    if (plan.esEjemplo) appendLine("# Generado con datos de ejemplo (vista de prueba).")
    appendLine()
    appendLine("# Apagar")
    paquetes.forEach { appendLine("adb shell pm disable-user --user 0 $it") }
    appendLine()
    appendLine("# Deshacer")
    paquetes.forEach { appendLine("adb shell pm enable $it") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaOptimizar(
    proveedor: ProveedorPlan,
    desenfoque: Boolean,
    alSalir: () -> Unit,
    alGuardarScript: (String) -> Unit,
) {
    val reducido = LocalReducido.current
    val alcance = rememberCoroutineScope()
    val aviso = remember { EstadoAviso() }
    var estado by remember { mutableStateOf<Estado>(Estado.Analizando()) }
    var seleccion by rememberSaveable(saver = GuardarSeleccion) { mutableStateOf<Set<String>?>(null) }
    var filtroTodo by rememberSaveable { mutableStateOf(false) }
    var hojaAbierta by rememberSaveable { mutableStateOf(false) }
    // Lo de la hoja es local: se aplica recién al tocar «Dejarla limpia».
    var apagarAlexa by rememberSaveable { mutableStateOf(false) }
    var animRapidas by rememberSaveable { mutableStateOf(false) }
    var trabajo by remember { mutableStateOf<Job?>(null) }
    var deshaciendo by remember { mutableStateOf(false) }
    var ultimoPlan by remember { mutableStateOf<PlanUi?>(null) }
    var ultimaLista by remember { mutableStateOf(emptyList<String>()) }
    var inicioLimpieza by remember { mutableLongStateOf(0L) }

    fun analizar() {
        estado = Estado.Analizando()
        alcance.launch {
            val inicio = SystemClock.elapsedRealtime()
            when (val r = proveedor.analizar()) {
                is Resultado.Ok -> {
                    val plan = r.valor
                    ultimoPlan = plan
                    estado = Estado.Analizando(listOfNotNull(plan.modelo, plan.totalApps?.let { "$it apps" }).joinToString(" · "))
                    // Al menos 0,6 s en «Analizando», aunque responda antes.
                    delay(maxOf(600 - (SystemClock.elapsedRealtime() - inicio), 250))
                    if (seleccion == null) seleccion = plan.todos().map { it.paquete }.toSet()
                    estado = Estado.Mostrando(plan)
                }
                is Resultado.Error -> estado = Estado.Fallo(ultimoPlan, r.mensaje)
            }
        }
    }

    fun limpiar(plan: PlanUi) {
        val apagar = (seleccion ?: emptySet()) + if (apagarAlexa) setOf(PAQUETE_VOZ_ALEXA) else emptySet()
        if (apagar.isEmpty() && !animRapidas) {
            aviso.mostrar("No hay nada seleccionado")
            return
        }
        ultimaLista = ordenados(plan, apagar)
        inicioLimpieza = SystemClock.elapsedRealtime()
        var lineas = emptyList<Linea>()
        var hechos = 0
        estado = Estado.Limpiando(plan, 0f, 0, apagar.size, "Preparando…", emptyList())
        trabajo = alcance.launch {
            val r = proveedor.ejecutar(apagar, OpcionesUi(animacionesRapidas = animRapidas)) { h, t, n ->
                if (h > hechos) lineas = (lineas + Linea(h, n.removePrefix("Apagando "))).takeLast(6)
                hechos = h
                estado = Estado.Limpiando(plan, if (t == 0) 1f else h.toFloat() / t, h, t, n, lineas)
            }
            val segundos = ((SystemClock.elapsedRealtime() - inicioLimpieza) / 1000).toInt().coerceAtLeast(1)
            when (r) {
                is Resultado.Ok -> {
                    (estado as? Estado.Limpiando)?.let { estado = it.copy(progreso = 1f, narracion = "Listo.") }
                    delay(260)
                    estado = Estado.Listo(plan, r.valor, segundos, ultimaLista)
                }
                is Resultado.Error -> estado = Estado.Fallo(plan, r.mensaje)
            }
        }
    }

    fun parar() {
        val actual = estado as? Estado.Limpiando ?: return
        trabajo?.cancel()
        val segundos = ((SystemClock.elapsedRealtime() - inicioLimpieza) / 1000).toInt().coerceAtLeast(1)
        val hechos = ultimaLista.take(actual.hechos)
        estado = Estado.Listo(
            actual.plan,
            Resumen(apagadas = hechos.size, protegidas = 0, seQuedan = actual.plan.seQuedan.count { it.paquete !in hechos }),
            segundos,
            hechos,
        )
        aviso.mostrar("Paraste. Se puede deshacer")
    }

    fun deshacer(plan: PlanUi?) {
        deshaciendo = true
        alcance.launch {
            val r = proveedor.deshacerTodo()
            deshaciendo = false
            when (r) {
                is Resultado.Ok -> {
                    val p = plan ?: ultimoPlan
                    seleccion = p?.todos()?.map { it.paquete }?.toSet()
                    apagarAlexa = false
                    animRapidas = false
                    if (p != null) estado = Estado.Mostrando(p) else analizar()
                    aviso.mostrar("Todo volvió a como estaba")
                }
                is Resultado.Error -> estado = Estado.Fallo(plan, r.mensaje)
            }
        }
    }

    LaunchedEffect(Unit) { analizar() }
    BackHandler(enabled = estado is Estado.Limpiando) { aviso.mostrar("Para salir, primero tocá Parar") }

    val radio by animateDpAsState(if (desenfoque && hojaAbierta) 18.dp else 0.dp, tween(Tiempos.hojaMs), label = "desenfoque")

    Box(Modifier.fillMaxSize().background(Colores.fondo)) {
        Box(Modifier.fillMaxSize().then(if (desenfoque && radio > 0.dp) Modifier.blur(radio) else Modifier)) {
            FondoOptimizar()
            Column(Modifier.fillMaxSize()) {
                BarraSuperior(onAtras = {
                    if (estado is Estado.Limpiando) aviso.mostrar("Para salir, primero tocá Parar") else alSalir()
                })
                AnimatedContent(
                    targetState = estado,
                    contentKey = { it::class },
                    transitionSpec = {
                        if (reducido) {
                            fadeIn(tween(Tiempos.reducidoMs)) togetherWith fadeOut(tween(Tiempos.reducidoMs))
                        } else {
                            (fadeIn(tween(Tiempos.pantallaMs)) + slideInVertically(tween(Tiempos.pantallaMs)) { it / 90 }) togetherWith
                                fadeOut(tween(160))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = "pantalla",
                ) { e ->
                    when (e) {
                        is Estado.Analizando -> VistaAnalizando(e)
                        is Estado.Mostrando -> {
                            val sel = seleccion ?: emptySet()
                            VistaPlan(
                                plan = e.plan,
                                seleccion = sel,
                                apagarAlexa = apagarAlexa,
                                filtroTodo = filtroTodo,
                                onFiltro = { filtroTodo = it },
                                onAlternar = { p -> seleccion = if (p in sel) sel - p else sel + p },
                                onLimpiar = { limpiar(e.plan) },
                                onPersonalizar = { hojaAbierta = true },
                            )
                        }
                        is Estado.Limpiando -> VistaLimpiando(e, onParar = { parar() })
                        is Estado.Listo -> VistaListo(
                            e,
                            deshaciendo = deshaciendo,
                            onDeshacer = { deshacer(e.plan) },
                            onScript = { alGuardarScript(scriptDe(e.plan, e.ejecutados)) },
                        )
                        is Estado.Fallo -> VistaFallo(
                            e,
                            deshaciendo = deshaciendo,
                            onReconectar = { analizar() },
                            onDeshacer = { deshacer(e.plan) },
                        )
                    }
                }
            }
        }
        Aviso(
            aviso,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = Espacio.borde, end = Espacio.borde, bottom = 140.dp),
        )
    }

    if (hojaAbierta) {
        val plan = (estado as? Estado.Mostrando)?.plan ?: ultimoPlan
        val casting = plan?.grupos?.firstOrNull { it.titulo == "Casting" }?.entradas?.map { it.paquete }.orEmpty()
        val sel = seleccion ?: emptySet()
        HojaPersonalizar(
            alexa = apagarAlexa,
            onAlexa = { apagarAlexa = it },
            casting = casting.any { it in sel },
            onCasting = { prender -> seleccion = if (prender) sel + casting else sel - casting.toSet() },
            mostrarCasting = casting.isNotEmpty(),
            animaciones = animRapidas,
            onAnimaciones = { animRapidas = it },
            onCerrar = { hojaAbierta = false },
        )
    }
}

// ---------- Piezas comunes de las pantallas ----------

@Composable
private fun BarraSuperior(onAtras: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 50.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(Formas.boton)
                .clickable(role = Role.Button, onClick = onAtras)
                .heightIn(min = Espacio.toque)
                .padding(start = 4.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Iconos.atras, contentDescription = null, tint = Colores.texto, modifier = Modifier.size(22.dp))
            Text("Atrás", fontSize = 16.sp, color = Colores.texto)
        }
    }
}

@Composable
private fun Pie(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .navigationBarsPadding()
            .padding(start = Espacio.borde, end = Espacio.borde, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun LineaHecha(texto: String, animar: Boolean = true) {
    val reducido = LocalReducido.current
    val visible = remember { MutableTransitionState(!animar || reducido).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(Tiempos.filaMs)) + slideInVertically(tween(Tiempos.filaMs)) { it / 2 },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Iconos.check, contentDescription = null, tint = Colores.texto, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(texto, fontSize = 13.sp, color = Colores.textoDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- 1 · Analizando ----------

@Composable
private fun VistaAnalizando(e: Estado.Analizando) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espacio.borde),
    ) {
        Text("Optimizar", style = Letras.titulo)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Colores.texto, strokeWidth = 1.5.dp)
            Spacer(Modifier.width(10.dp))
            Text("Leyendo tu Fire TV…", fontSize = 17.sp, color = Colores.textoDim)
        }
        AnimatedVisibility(visible = e.perfil != null, enter = fadeIn(tween(Tiempos.filaMs)) + expandVertically()) {
            Tarjeta(Modifier.padding(top = Espacio.hueco)) {
                Fila(e.perfil ?: "", "Perfil reconocido", primera = true, subtituloMono = false)
            }
        }
    }
}

// ---------- 2 · Mostrando (el plan) ----------

@Composable
private fun VistaPlan(
    plan: PlanUi,
    seleccion: Set<String>,
    apagarAlexa: Boolean,
    filtroTodo: Boolean,
    onFiltro: (Boolean) -> Unit,
    onAlternar: (String) -> Unit,
    onLimpiar: () -> Unit,
    onPersonalizar: () -> Unit,
) {
    var pista by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(4500)
        pista = false
    }
    // Filtrar no cambia la selección: el número cuenta también lo que está oculto.
    val cuenta = seleccion.size + if (apagarAlexa) 1 else 0
    val visibles = if (filtroTodo) plan.grupos else plan.grupos.filter { it.recomendado }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Espacio.borde, end = Espacio.borde, bottom = 28.dp),
            ) {
                item(key = "cabecera") {
                    Column {
                        Text(plan.modelo.uppercase(), style = Letras.seccion, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                        NumeroQueCuenta(cuenta)
                        Text("cosas para apagar", style = Letras.frase)
                        Text(
                            "${plan.seQuedan.size} se quedan · ${plan.grupos.size} grupos",
                            style = Letras.detalle,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        if (plan.esEjemplo) Text(NOTA_EJEMPLO, style = Letras.nota, modifier = Modifier.padding(top = 6.dp))
                        Segmentado(todo = filtroTodo, onCambio = onFiltro)
                    }
                }
                visibles.forEach { g ->
                    item(key = g.titulo) {
                        Column(Modifier.animateItem()) {
                            val activos = g.entradas.count { it.paquete in seleccion }
                            EtiquetaSeccion(g.titulo, extra = "$activos de ${g.entradas.size}")
                            Tarjeta {
                                g.entradas.forEachIndexed { i, en ->
                                    val apagar = en.paquete in seleccion
                                    Fila(
                                        titulo = en.nombre,
                                        subtitulo = en.paquete,
                                        primera = i == 0,
                                        accion = { Pastilla(if (apagar) "Apagar" else "Se queda", activa = apagar) },
                                        onClick = { onAlternar(en.paquete) },
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "quedan") {
                    Column(Modifier.animateItem()) {
                        EtiquetaSeccion("Se quedan")
                        Tarjeta {
                            plan.seQuedan.forEachIndexed { i, en ->
                                val seApaga = en.paquete == PAQUETE_VOZ_ALEXA && apagarAlexa
                                Fila(
                                    titulo = en.nombre,
                                    subtitulo = en.paquete,
                                    primera = i == 0,
                                    accion = {
                                        Pastilla(
                                            if (seApaga) "se apaga (Personalizar)" else en.motivo.orEmpty(),
                                            activa = seApaga,
                                            maxLineas = 2,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // Con el nombre completo: adentro de una Column, Kotlin elegiría la versión de ColumnScope.
            androidx.compose.animation.AnimatedVisibility(
                visible = pista,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                enter = fadeIn(),
                exit = fadeOut(tween(600)),
            ) {
                Text(
                    "Tocá una fila para cambiarla",
                    fontSize = 13.sp,
                    color = Colores.texto,
                    modifier = Modifier
                        .clip(Formas.pastilla)
                        .background(Color(0xDB28282C))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Pie {
            BotonPrincipal("Dejarla limpia", onLimpiar)
            BotonSecundario("Personalizar", onPersonalizar)
        }
    }
}

@Composable
private fun Segmentado(todo: Boolean, onCambio: (Boolean) -> Unit) {
    val forma = RoundedCornerShape(10.dp)
    val formaOpcion = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Espacio.hueco)
            .clip(forma)
            .background(Color.White.copy(alpha = 0.10f))
            .padding(2.dp),
    ) {
        listOf(false to "Recomendado", true to "Todo").forEach { (valor, texto) ->
            val activo = todo == valor
            val fondo by animateColorAsState(
                if (activo) Color.White.copy(alpha = 0.22f) else Color.Transparent,
                tween(Tiempos.filaMs),
                label = "segmento",
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .then(if (activo) Modifier.shadow(1.dp, formaOpcion) else Modifier)
                    .clip(formaOpcion)
                    .background(fondo)
                    .selectable(selected = activo, role = Role.Tab, onClick = { onCambio(valor) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    texto,
                    fontSize = 14.sp,
                    fontWeight = if (activo) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (activo) Colores.texto else Colores.textoDim,
                )
            }
        }
    }
}

// ---------- 3 · Personalizar (hoja desde abajo) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaPersonalizar(
    alexa: Boolean,
    onAlexa: (Boolean) -> Unit,
    casting: Boolean,
    onCasting: (Boolean) -> Unit,
    mostrarCasting: Boolean,
    animaciones: Boolean,
    onAnimaciones: (Boolean) -> Unit,
    onCerrar: () -> Unit,
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val alcance = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = estadoHoja,
        shape = Formas.hoja,
        containerColor = Color(0x9E1E1E20),
        contentColor = Colores.texto,
        scrimColor = Color(0x80000000),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 10.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(Formas.pastilla)
                    .background(Color.White.copy(alpha = 0.34f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Espacio.borde, end = Espacio.borde, bottom = 16.dp),
        ) {
            Text("Personalizar", style = Letras.titulo.copy(fontSize = 26.sp, lineHeight = 30.sp))
            Text(
                "Nada se aplica hasta que toques Dejarla limpia.",
                style = Letras.nota,
                modifier = Modifier.padding(top = 4.dp, bottom = Espacio.hueco),
            )
            Tarjeta(alto = true) {
                Fila(
                    "Voz de Alexa", "deja de responder al control", primera = true, subtituloMono = false,
                    accion = { Interruptor(alexa, onAlexa) }, onClick = { onAlexa(!alexa) },
                )
                if (mostrarCasting) {
                    Fila(
                        "Casting", "no podés tirar video del celular", subtituloMono = false,
                        accion = { Interruptor(casting, onCasting) }, onClick = { onCasting(!casting) },
                    )
                }
                Fila(
                    "Animaciones a 0,5", "más rápido, menos suave", subtituloMono = false,
                    accion = { Interruptor(animaciones, onAnimaciones) }, onClick = { onAnimaciones(!animaciones) },
                )
            }
            Spacer(Modifier.height(Espacio.hueco))
            BotonPrincipal("Listo", onClick = {
                alcance.launch { estadoHoja.hide() }.invokeOnCompletion { onCerrar() }
            })
        }
    }
}

// ---------- 4 · Limpiando ----------

@Composable
private fun VistaLimpiando(e: Estado.Limpiando, onParar: () -> Unit) {
    val reducido = LocalReducido.current
    val vista = LocalView.current
    DisposableEffect(Unit) {
        vista.keepScreenOn = true
        onDispose { vista.keepScreenOn = false }
    }
    // Lineal, sin resorte: tiene que leerse como avance.
    val progreso by animateFloatAsState(
        e.progreso,
        if (reducido) snap() else tween(180, easing = LinearEasing),
        label = "progreso",
    )
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.borde),
        ) {
            Text("Limpiando", style = Letras.titulo)
            Text("${e.hechos} de ${e.total}", style = Letras.tabular, modifier = Modifier.padding(top = 6.dp))
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(Formas.pastilla)
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progreso.coerceIn(0f, 1f))
                        .clip(Formas.pastilla)
                        .background(Color.White),
                )
            }
            Text(
                e.narracion,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Colores.textoDim,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (e.ultimas.isNotEmpty()) {
                Tarjeta(Modifier.padding(top = Espacio.hueco)) {
                    Column(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        e.ultimas.forEach { l -> key(l.id) { LineaHecha(l.texto) } }
                    }
                }
            }
            Text(
                "Si algo sale mal, esto se puede deshacer entero.",
                style = Letras.nota,
                modifier = Modifier.padding(top = Espacio.hueco),
            )
        }
        Pie { BotonSecundario("Parar", onParar) }
    }
}

// ---------- 5 · Listo ----------

@Composable
private fun VistaListo(e: Estado.Listo, deshaciendo: Boolean, onDeshacer: () -> Unit, onScript: () -> Unit) {
    val reducido = LocalReducido.current
    val escala = remember { Animatable(if (reducido) 1f else 0f) }
    LaunchedEffect(Unit) {
        escala.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.borde),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = escala.value
                        scaleY = escala.value
                    }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Iconos.check, contentDescription = null, tint = Colores.texto, modifier = Modifier.size(30.dp))
            }
            Text(
                "Tu Fire TV\nestá más liviana",
                style = Letras.titulo,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Estadistica(e.resumen.apagadas.toString(), "apagadas", Modifier.weight(1f))
                Estadistica(e.resumen.seQuedan.toString(), "se quedaron", Modifier.weight(1f))
                Estadistica("${e.segundos} s", "tardó", Modifier.weight(1f))
            }
            if (e.resumen.avisos.isNotEmpty()) {
                Tarjeta(Modifier.padding(top = Espacio.hueco)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        e.resumen.avisos.forEach { LineaHecha(it, animar = false) }
                    }
                }
            }
            if (e.plan.esEjemplo) {
                Text(NOTA_EJEMPLO, style = Letras.nota, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Espacio.hueco))
            }
        }
        Pie {
            BotonPrincipal("Deshacer todo", onDeshacer, cargando = deshaciendo)
            BotonSecundario("Guardar el script", onScript)
        }
    }
}

@Composable
private fun Estadistica(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Tarjeta(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(valor, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Colores.texto, style = Letras.tabular)
            Text(etiqueta, fontSize = 12.5.sp, color = Colores.textoDim, textAlign = TextAlign.Center)
        }
    }
}

// ---------- 6 · Fallo ----------

@Composable
private fun VistaFallo(e: Estado.Fallo, deshaciendo: Boolean, onReconectar: () -> Unit, onDeshacer: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.borde),
        ) {
            Text("Optimizar", style = Letras.titulo)
            Column(
                Modifier
                    .padding(top = Espacio.hueco)
                    .fillMaxWidth()
                    .vidrio()
                    .border(1.dp, Colores.error.copy(alpha = 0.4f), Formas.tarjeta)
                    .padding(16.dp),
            ) {
                Text("Se cortó la conexión", style = Letras.frase)
                Text(
                    "Lo que ya se apagó quedó guardado. Podés deshacerlo cuando vuelvas a conectar.",
                    style = Letras.cuerpo.copy(color = Colores.textoDim),
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (e.mensaje.isNotBlank()) Text(e.mensaje, style = Letras.nota, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Pie {
            BotonPrincipal("Reconectar", onReconectar)
            BotonSecundario("Deshacer", onDeshacer, cargando = deshaciendo)
        }
    }
}
