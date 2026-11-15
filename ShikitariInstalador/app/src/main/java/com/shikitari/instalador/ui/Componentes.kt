package com.shikitari.instalador.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---------- Botones ----------

@Composable
private fun BotonBase(
    texto: String,
    onClick: () -> Unit,
    relleno: Brush,
    colorTexto: Color,
    borde: Color,
    sombra: Boolean,
    modifier: Modifier = Modifier,
    cargando: Boolean = false,
    habilitado: Boolean = true,
) {
    val reducido = LocalReducido.current
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionado && !reducido) 0.97f else 1f,
        animationSpec = if (presionado) tween(Tiempos.pulsoMs) else spring(stiffness = 800f),
        label = "escala",
    )
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = Espacio.botonAlto)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
                alpha = if (habilitado) 1f else 0.5f
            }
            .then(if (sombra) Modifier.shadow(10.dp, Formas.boton, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .clip(Formas.boton)
            .background(relleno)
            .border(1.dp, borde, Formas.boton)
            .clickable(
                interactionSource = fuente,
                indication = null,
                enabled = habilitado && !cargando,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(18.dp), color = colorTexto, strokeWidth = 2.dp)
        } else {
            Text(texto, color = colorTexto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cargando: Boolean = false,
    habilitado: Boolean = true,
) = BotonBase(
    texto = texto,
    onClick = onClick,
    relleno = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.97f), Color.White.copy(alpha = 0.84f))),
    colorTexto = Colores.sobreAcento,
    borde = Color.White.copy(alpha = 0.5f),
    sombra = true,
    modifier = modifier,
    cargando = cargando,
    habilitado = habilitado,
)

@Composable
fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cargando: Boolean = false,
) = BotonBase(
    texto = texto,
    onClick = onClick,
    relleno = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.08f))),
    colorTexto = Colores.texto,
    borde = Color.White.copy(alpha = 0.12f),
    sombra = false,
    modifier = modifier,
    cargando = cargando,
)

@Composable
fun BotonTexto(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(Formas.boton)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = Colores.textoDim, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

// ---------- Tarjetas y filas ----------

@Composable
fun Tarjeta(modifier: Modifier = Modifier, alto: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().vidrio(alto = alto), content = content)
}

@Composable
fun Separador(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(start = Espacio.filaPadH)
            .fillMaxWidth()
            .height(1.dp)
            .background(Colores.lineaFina),
    )
}

/**
 * Fila de una tarjeta: título, subtítulo chico y algo a la derecha (pastilla o interruptor).
 * Solo se toca si trae onClick.
 */
@Composable
fun Fila(
    titulo: String,
    subtitulo: String?,
    modifier: Modifier = Modifier,
    primera: Boolean = false,
    subtituloMono: Boolean = true,
    accion: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (!primera) Separador()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Espacio.filaAlto)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Espacio.filaPadH, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = Letras.cuerpo)
                if (subtitulo != null) {
                    Text(
                        subtitulo,
                        style = if (subtituloMono) Letras.paquete else Letras.detalle,
                        maxLines = if (subtituloMono) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            accion()
        }
    }
}

/** «Apagar» (activa) o «Se queda» (inactiva, con ✓ adelante). */
@Composable
fun Pastilla(texto: String, activa: Boolean, modifier: Modifier = Modifier, maxLineas: Int = 1) {
    val reducido = LocalReducido.current
    val ms = if (reducido) 0 else Tiempos.filaMs
    val fondo by animateColorAsState(if (activa) Color.White.copy(alpha = 0.14f) else Color.Transparent, tween(ms), label = "fondo")
    val borde by animateColorAsState(if (activa) Color.Transparent else Color.White.copy(alpha = 0.24f), tween(ms), label = "borde")
    val color by animateColorAsState(if (activa) Color.White else Colores.pastillaApagada, tween(ms), label = "color")
    Row(
        modifier
            .widthIn(max = 168.dp)
            .heightIn(min = 28.dp)
            .clip(Formas.pastilla)
            .background(fondo)
            .border(1.dp, borde, Formas.pastilla)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!activa) Text("✓ ", fontSize = 10.sp, color = color)
        Text(texto, fontSize = 13.sp, color = color, maxLines = maxLineas, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
    }
}

/** Etiqueta de sección en mayúsculas chicas, con un agregado opcional («6 de 6»). */
@Composable
fun EtiquetaSeccion(texto: String, modifier: Modifier = Modifier, extra: String? = null) {
    val completo = if (extra != null) "$texto · $extra" else texto
    Text(
        completo.uppercase(),
        style = Letras.seccion,
        modifier = modifier.padding(start = 4.dp, top = Espacio.hueco + 6.dp, bottom = 8.dp),
    )
}

/** Número grande que cuenta hasta el objetivo; si el objetivo cambia, sigue desde donde está. */
@Composable
fun NumeroQueCuenta(hasta: Int, modifier: Modifier = Modifier) {
    val reducido = LocalReducido.current
    val valor = remember { Animatable(0f) }
    LaunchedEffect(hasta) {
        if (reducido) valor.snapTo(hasta.toFloat())
        else valor.animateTo(hasta.toFloat(), tween(Tiempos.cuentaMs, easing = FastOutSlowInEasing))
    }
    Text(valor.value.roundToInt().toString(), style = Letras.numero, modifier = modifier.animateContentSize())
}

/** Interruptor estilo iOS, pero blanco: pista gris apagada, casi blanca prendida. */
@Composable
fun Interruptor(prendido: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val pista by animateColorAsState(if (prendido) Color(0xFFE9E9EE) else Color(0xFF39393D), tween(Tiempos.filaMs), label = "pista")
    val x by animateDpAsState(if (prendido) 22.dp else 2.dp, spring(dampingRatio = 0.8f), label = "perilla")
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(Formas.pastilla)
            .background(pista)
            .toggleable(value = prendido, role = Role.Switch, onValueChange = onChange),
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

// ---------- Aviso (cartelito abajo) ----------

class EstadoAviso {
    var texto by mutableStateOf<String?>(null)
        internal set
    var vez by mutableIntStateOf(0)
        private set

    fun mostrar(mensaje: String) {
        texto = mensaje
        vez++
    }

    internal fun ocultar() {
        texto = null
    }
}

@Composable
fun Aviso(estado: EstadoAviso, modifier: Modifier = Modifier) {
    val desplazamiento = with(LocalDensity.current) { 12.dp.roundToPx() }
    var ultimo by remember { mutableStateOf("") }
    estado.texto?.let { ultimo = it }
    LaunchedEffect(estado.vez) {
        if (estado.texto != null) {
            delay(1900)
            estado.ocultar()
        }
    }
    AnimatedVisibility(
        visible = estado.texto != null,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { desplazamiento },
        exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { desplazamiento },
    ) {
        Text(
            ultimo,
            color = Colores.texto,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(Formas.pastilla)
                .background(Color(0xDB28282C))
                .border(1.dp, Colores.linea, Formas.pastilla)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
