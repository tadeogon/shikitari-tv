package com.shikitari.instalador.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Las cuatro pestañas de abajo. Lo que se usa menos queda guardado adentro de «Más». */
enum class Pestana(val titulo: String, val icono: ImageVector) {
    TV("TV", Iconos.tv),
    CONTROL("Control", Iconos.control),
    OPTIMIZAR("Optimizar", Iconos.rayo),
    MAS("Más", Iconos.mas),
}

/** Pantallas que se abren encima de una pestaña (con «Atrás» para volver). */
sealed interface Ruta {
    data object AgregarTv : Ruta
    data object Apps : Ruta
    data class Archivos(val instalar: Boolean = false) : Ruta
    data object Botones : Ruta
    data class Launcher(val cual: com.shikitari.instalador.ui.Launcher) : Ruta
    data object Claude : Ruta
    data object Historial : Ruta
}

@Composable
fun AppShikitari(app: EstadoApp, control: EstadoControl, archivos: EstadoArchivos, elegirPortada: () -> Unit) {
    val reducido = LocalReducido.current
    var pestana by rememberSaveable { mutableStateOf(Pestana.TV) }
    val pila = remember { mutableStateListOf<Ruta>() }
    val irA: (Ruta) -> Unit = { pila.add(it) }
    val volver: () -> Unit = { if (pila.isNotEmpty()) pila.removeAt(pila.lastIndex) }
    val irAPestana: (Pestana) -> Unit = {
        pila.clear()
        pestana = it
    }

    BackHandler(enabled = pila.isNotEmpty()) { volver() }
    BackHandler(enabled = pila.isEmpty() && pestana != Pestana.TV) { pestana = Pestana.TV }

    Box(Modifier.fillMaxSize().background(Colores.fondo)) {
        FondoOptimizar()
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = pila.lastOrNull() to pestana,
                    transitionSpec = {
                        if (reducido) {
                            fadeIn(tween(Tiempos.reducidoMs)) togetherWith fadeOut(tween(Tiempos.reducidoMs))
                        } else {
                            (fadeIn(tween(Tiempos.pantallaMs)) + slideInVertically(tween(Tiempos.pantallaMs)) { it / 90 }) togetherWith
                                fadeOut(tween(140))
                        }
                    },
                    label = "pantalla",
                ) { (ruta, p) ->
                    if (ruta != null) {
                        when (ruta) {
                            Ruta.AgregarTv -> PantallaAgregarTv(app, onAtras = volver)
                            Ruta.Apps -> PantallaApps(app, control, onAtras = volver)
                            is Ruta.Archivos -> PantallaArchivos(app, archivos, ruta.instalar, onAtras = volver)
                            Ruta.Botones -> PantallaBotones(app, onAtras = volver)
                            is Ruta.Launcher -> PantallaLauncher(app, ruta.cual, onAtras = volver, elegirPortada = elegirPortada)
                            Ruta.Claude -> PantallaClaude(app, onAtras = volver)
                            Ruta.Historial -> PantallaHistorial(app, onAtras = volver)
                        }
                    } else {
                        when (p) {
                            Pestana.TV -> PestanaTv(app, irA, irAPestana)
                            Pestana.CONTROL -> PestanaControl(app, control, irA, irAPestana)
                            Pestana.OPTIMIZAR -> PestanaOptimizar(app, irAPestana)
                            Pestana.MAS -> PestanaMas(app, irA)
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = pila.isEmpty(),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                BarraPestanas(pestana, onElegir = { pestana = it })
            }
        }
        Cartel(
            app.cartel,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = Espacio.borde, end = Espacio.borde, bottom = if (pila.isEmpty()) 76.dp else 92.dp),
        )
        CapaDialogos(app.dialogos)
    }
}

@Composable
private fun BarraPestanas(actual: Pestana, onElegir: (Pestana) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xD90E0E10)),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Colores.linea))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp),
        ) {
            Pestana.entries.forEach { p ->
                val elegida = p == actual
                val color by animateColorAsState(if (elegida) Colores.texto else Colores.textoDim2, tween(Tiempos.filaMs), label = "pestaña")
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 58.dp)
                        .clickable(role = Role.Tab) { onElegir(p) }
                        .padding(top = 8.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(p.icono, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Text(
                        p.titulo,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = if (elegida) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
