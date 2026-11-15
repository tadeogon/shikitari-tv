package com.shikitari.instalador.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- Cartel de abajo: "trabajando…" con ruedita, o el resultado ----------

class EstadoCartel {
    enum class Tipo { TRABAJANDO, OK, ERROR }

    var tipo by mutableStateOf(Tipo.OK)
        private set
    var texto by mutableStateOf<String?>(null)
        private set
    var vez by mutableIntStateOf(0)
        private set

    fun trabajando(mensaje: String) {
        tipo = Tipo.TRABAJANDO
        texto = "$mensaje…"
        vez++
    }

    fun avisar(ok: Boolean, mensaje: String) {
        tipo = if (ok) Tipo.OK else Tipo.ERROR
        texto = mensaje
        vez++
    }

    fun cerrar() {
        texto = null
    }
}

@Composable
fun Cartel(estado: EstadoCartel, modifier: Modifier = Modifier) {
    val desplazamiento = with(LocalDensity.current) { 16.dp.roundToPx() }
    var ultimo by remember { mutableStateOf("") }
    var ultimoTipo by remember { mutableStateOf(EstadoCartel.Tipo.OK) }
    estado.texto?.let {
        ultimo = it
        ultimoTipo = estado.tipo
    }
    LaunchedEffect(estado.vez) {
        when (estado.tipo) {
            EstadoCartel.Tipo.TRABAJANDO -> Unit
            EstadoCartel.Tipo.OK -> { delay(3500); estado.cerrar() }
            EstadoCartel.Tipo.ERROR -> { delay(7000); estado.cerrar() }
        }
    }
    AnimatedVisibility(
        visible = estado.texto != null,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { desplazamiento },
        exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { desplazamiento },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Formas.tarjeta)
                .background(Color(0xF0222226))
                .border(1.dp, Colores.linea, Formas.tarjeta)
                .clickable { estado.cerrar() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (ultimoTipo) {
                EstadoCartel.Tipo.TRABAJANDO ->
                    CircularProgressIndicator(Modifier.size(20.dp), color = Colores.texto, strokeWidth = 2.dp)
                EstadoCartel.Tipo.OK ->
                    Icon(Iconos.check, null, tint = Colores.texto, modifier = Modifier.size(20.dp))
                EstadoCartel.Tipo.ERROR ->
                    Text("!", color = Colores.error, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.width(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(ultimo, style = Letras.cuerpo.copy(fontSize = 14.sp), maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- Página: barra con Atrás, título grande, contenido con scroll y pie fijo ----------

@Composable
fun Pagina(
    titulo: String,
    onAtras: (() -> Unit)? = null,
    subtitulo: String? = null,
    accion: (@Composable () -> Unit)? = null,
    pie: (@Composable ColumnScope.() -> Unit)? = null,
    margenAbajo: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 50.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onAtras != null) {
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
            Spacer(Modifier.weight(1f))
            if (accion != null) accion()
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.borde),
        ) {
            Text(titulo, style = Letras.titulo)
            if (subtitulo != null) Text(subtitulo, style = Letras.detalle, modifier = Modifier.padding(top = 4.dp))
            content()
            Spacer(Modifier.heightIn(min = if (margenAbajo) 96.dp else 24.dp))
        }
        if (pie != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = Espacio.borde, end = Espacio.borde, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = pie,
            )
        }
    }
}

/** Botón chico de la barra de arriba, a la derecha (por ejemplo «+»). */
@Composable
fun BotonBarra(icono: ImageVector, descripcion: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Espacio.toque)
            .clip(Formas.pastilla)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = descripcion, tint = Colores.texto, modifier = Modifier.size(24.dp))
    }
}

/** Texto explicativo dentro de una tarjeta. */
@Composable
fun NotaTarjeta(texto: String) {
    Text(
        texto,
        style = Letras.nota.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
        modifier = Modifier.padding(horizontal = Espacio.filaPadH, vertical = 12.dp),
    )
}

/** Fila que lleva a otra pantalla: icono, título, detalle y la flechita. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilaIr(
    titulo: String,
    detalle: String? = null,
    icono: ImageVector? = null,
    primera: Boolean = false,
    activa: Boolean = true,
    derecha: (@Composable () -> Unit)? = null,
    alMantener: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (!primera) Separador()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Espacio.filaAlto)
                .combinedClickable(onClick = onClick, onLongClick = alMantener)
                .padding(horizontal = Espacio.filaPadH, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icono != null) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(Formas.campo)
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icono, null, tint = Colores.texto.copy(alpha = if (activa) 1f else 0.5f), modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = Letras.cuerpo.copy(color = Colores.texto.copy(alpha = if (activa) 1f else 0.55f)))
                if (detalle != null) Text(detalle, style = Letras.detalle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            if (derecha != null) derecha() else Icon(Iconos.chevronDerecha, null, tint = Colores.textoDim2, modifier = Modifier.size(18.dp))
        }
    }
}

// ---------- Campo de texto de vidrio ----------

@Composable
fun CampoTexto(
    valor: String,
    onCambio: (String) -> Unit,
    pista: String,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    oculto: Boolean = false,
    lineas: Int = 1,
) {
    BasicTextField(
        value = valor,
        onValueChange = onCambio,
        singleLine = lineas == 1,
        maxLines = lineas.coerceAtLeast(1) * 4,
        textStyle = Letras.cuerpo,
        cursorBrush = SolidColor(Colores.texto),
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        visualTransformation = if (oculto) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { campo ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Espacio.toque)
                    .vidrio(Formas.campo)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (valor.isEmpty()) Text(pista, style = Letras.cuerpo.copy(color = Colores.textoDim2))
                campo()
            }
        },
    )
}

// ---------- Hojas de opciones y confirmaciones (las pide la lógica, las dibuja la raíz) ----------

data class Opcion(val texto: String, val icono: ImageVector? = null, val peligro: Boolean = false, val accion: () -> Unit)

data class HojaPedida(val titulo: String, val detalle: String? = null, val opciones: List<Opcion>)

data class ConfirmacionPedida(
    val titulo: String,
    val mensaje: String,
    val si: String,
    val peligro: Boolean = false,
    val no: String = "Cancelar",
    val alResponder: (Boolean) -> Unit,
)

data class TextoPedido(val titulo: String, val valor: String, val pista: String, val alGuardar: (String) -> Unit)

/** Lo que está abierto encima de todo. Se puede pedir desde cualquier hilo. */
class Dialogos {
    var hoja by mutableStateOf<HojaPedida?>(null)
    var confirmacion by mutableStateOf<ConfirmacionPedida?>(null)
    var texto by mutableStateOf<TextoPedido?>(null)
}

@Composable
fun CapaDialogos(d: Dialogos) {
    d.hoja?.let { h -> HojaOpciones(h, onCerrar = { d.hoja = null }) }
    d.confirmacion?.let { c ->
        Confirmar(c, onCerrar = { si ->
            d.confirmacion = null
            c.alResponder(si)
        })
    }
    d.texto?.let { t ->
        PedirTexto(t, onCerrar = { d.texto = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDeVidrio(onCerrar: () -> Unit, content: @Composable ColumnScope.(cerrar: () -> Unit) -> Unit) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val alcance = rememberCoroutineScope()
    val cerrar: () -> Unit = { alcance.launch { estadoHoja.hide() }.invokeOnCompletion { onCerrar() } }
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = estadoHoja,
        shape = Formas.hoja,
        containerColor = Color(0xE61E1E20),
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
            content(cerrar)
        }
    }
}

@Composable
private fun HojaOpciones(h: HojaPedida, onCerrar: () -> Unit) {
    HojaDeVidrio(onCerrar) { cerrar ->
        Text(h.titulo, style = Letras.frase, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (h.detalle != null) Text(h.detalle, style = Letras.nota, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.heightIn(min = Espacio.hueco))
        Tarjeta(alto = true) {
            h.opciones.forEachIndexed { i, o ->
                Column {
                    if (i > 0) Separador()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Espacio.filaAlto)
                            .clickable {
                                cerrar()
                                o.accion()
                            }
                            .padding(horizontal = Espacio.filaPadH),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val color = if (o.peligro) Colores.error else Colores.texto
                        if (o.icono != null) {
                            Icon(o.icono, null, tint = color, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(o.texto, style = Letras.cuerpo.copy(color = color))
                    }
                }
            }
        }
        Spacer(Modifier.heightIn(min = 10.dp))
        BotonTexto("Cancelar", onClick = cerrar)
    }
}

@Composable
private fun Confirmar(c: ConfirmacionPedida, onCerrar: (Boolean) -> Unit) {
    Dialog(onDismissRequest = { onCerrar(false) }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Formas.tarjeta)
                .background(Color(0xF2202024))
                .border(1.dp, Colores.linea, Formas.tarjeta)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(c.titulo, style = Letras.frase)
            Text(c.mensaje, style = Letras.cuerpo.copy(color = Colores.textoDim, fontSize = 15.sp))
            Spacer(Modifier.heightIn(min = 6.dp))
            if (c.peligro) {
                BotonBase(
                    texto = c.si,
                    onClick = { onCerrar(true) },
                    relleno = Brush.verticalGradient(listOf(Colores.error.copy(alpha = 0.9f), Colores.error.copy(alpha = 0.75f))),
                    colorTexto = Color.White,
                    borde = Colores.error.copy(alpha = 0.5f),
                    sombra = false,
                )
            } else {
                BotonPrincipal(c.si, onClick = { onCerrar(true) })
            }
            BotonSecundario(c.no, onClick = { onCerrar(false) })
        }
    }
}

@Composable
private fun PedirTexto(t: TextoPedido, onCerrar: () -> Unit) {
    var valor by remember { mutableStateOf(t.valor) }
    Dialog(onDismissRequest = onCerrar) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Formas.tarjeta)
                .background(Color(0xF2202024))
                .border(1.dp, Colores.linea, Formas.tarjeta)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(t.titulo, style = Letras.frase)
            CampoTexto(valor, { valor = it }, t.pista)
            BotonPrincipal("Guardar", onClick = {
                onCerrar()
                t.alGuardar(valor.trim())
            })
            BotonSecundario("Cancelar", onClick = onCerrar)
        }
    }
}

/** Fila de una tarjeta con dos botones lado a lado. */
@Composable
fun DosBotones(izquierda: @Composable (Modifier) -> Unit, derecha: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        izquierda(Modifier.weight(1f))
        derecha(Modifier.weight(1f))
    }
}

@Composable
fun Hueco(alto: Int = 14) = Spacer(Modifier.heightIn(min = alto.dp))

@Composable
fun BarraNavegacionVacia() = Spacer(Modifier.navigationBarsPadding())
