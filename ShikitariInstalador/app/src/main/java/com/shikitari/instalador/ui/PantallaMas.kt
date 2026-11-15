package com.shikitari.instalador.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shikitari.instalador.AccionesTv
import com.shikitari.instalador.Bitacora
import com.shikitari.instalador.Modelo

// ---------- Pestaña Optimizar (la de verdad) ----------

@Composable
fun PestanaOptimizar(app: EstadoApp, irAPestana: (Pestana) -> Unit) {
    val contexto = LocalContext.current
    Pagina(titulo = "Optimizar", subtitulo = app.conectada?.nombre) {
        if (app.conectada == null) {
            SinTv(irAPestana)
            return@Pagina
        }
        Tarjeta(Modifier.padding(top = Espacio.hueco)) {
            NotaTarjeta(
                "La app se da cuenta sola de qué TV es. En Google TV quita ${AccionesTv.LIMPIEZA_SEGURA.size} apps de Google que no hacen falta; " +
                    "en Fire TV apaga ${AccionesTv.LIMPIEZA_FIRE.size} apps de Amazon de rastreo, publicidad y promos. " +
                    "Alexa, Prime Video, la tienda y el control no se tocan. Todo se puede deshacer.",
            )
        }
        Hueco()
        BotonPrincipal("Optimizar", onClick = { app.ejecutar("Optimizar") { app.acciones.optimizar() } })
        Hueco(10)
        BotonSecundario("Cerrar apps abiertas", onClick = { app.ejecutar("Cerrar apps abiertas") { app.acciones.cerrarApps() } })

        EtiquetaSeccion("Más opciones")
        Tarjeta {
            FilaIr("Deshacer optimización", "Vuelve a prender todo lo que se apagó", Iconos.volver, primera = true) {
                app.dialogos.confirmacion = ConfirmacionPedida(
                    "¿Deshacer la optimización?",
                    "Se vuelven a prender las apps que se apagaron y los ajustes quedan como estaban.",
                    "Deshacer",
                ) { si -> if (si) app.ejecutar("Deshacer optimización") { app.acciones.deshacer() } }
            }
            FilaIr("Si algo sale mal", "Solo Fire TV: el inicio de Amazon quedó en negro", Iconos.escudo) {
                app.dialogos.confirmacion = ConfirmacionPedida(
                    "El inicio de Amazon quedó en negro",
                    "Vuelve a prender las tres apps que dibujan el inicio de Amazon y lo abre de nuevo.",
                    "Arreglarlo",
                ) { si -> if (si) app.ejecutar("El inicio de Amazon quedó en negro") { app.acciones.emergenciaFire() } }
            }
            FilaIr("Diseño nuevo de Optimizar", "Vista de prueba: todavía no cambia nada en la TV", Iconos.chispa) {
                contexto.startActivity(Intent(contexto, OptimizarActivity::class.java))
            }
        }
    }
}

// ---------- Pestaña Más ----------

@Composable
fun PestanaMas(app: EstadoApp, irA: (Ruta) -> Unit) {
    Pagina(titulo = "Más") {
        EtiquetaSeccion("En la TV")
        Tarjeta {
            FilaIr("Archivos", "Ver, borrar y pasar archivos", Iconos.carpeta, primera = true) { if (app.hayTv()) irA(Ruta.Archivos()) }
            FilaIr("Instalar un APK", "Desde tu celular a la TV", Iconos.paquete) { if (app.hayTv()) irA(Ruta.Archivos(instalar = true)) }
            FilaIr("Botones del control", "Cambiar qué app abre cada botón", Iconos.control) { if (app.hayTv()) irA(Ruta.Botones) }
        }
        EtiquetaSeccion("Launchers")
        Tarjeta {
            FilaIr("Shikitari TV", "Launcher con tus gatos", Iconos.tv, primera = true) { irA(Ruta.Launcher(Launcher.SHIKITARI)) }
            FilaIr("Ziota", "Estilo Apple TV, con isla dinámica", Iconos.tv) { irA(Ruta.Launcher(Launcher.ZIOTA)) }
            FilaIr("Solari", "Launcher del trabajo", Iconos.tv) { irA(Ruta.Launcher(Launcher.SOLARI)) }
        }
        EtiquetaSeccion("Ayuda")
        Tarjeta {
            FilaIr("Ayuda con Claude", "Contale el problema y revisa la TV", Iconos.chispa, primera = true) { irA(Ruta.Claude) }
            FilaIr("Historial", "Todo lo que se hizo en las TVs", Iconos.reloj) { irA(Ruta.Historial) }
        }
    }
}

// ---------- Launchers ----------

enum class Launcher { SHIKITARI, ZIOTA, SOLARI }

@Composable
fun PantallaLauncher(app: EstadoApp, cual: Launcher, onAtras: () -> Unit, elegirPortada: () -> Unit) {
    when (cual) {
        Launcher.SHIKITARI -> Pagina(
            titulo = "Shikitari TV",
            onAtras = onAtras,
            pie = { BotonPrincipal("Todo en uno", onClick = { app.ejecutar("Todo en uno") { app.acciones.todoEnUno() } }) },
        ) {
            Tarjeta(Modifier.padding(top = Espacio.hueco)) {
                NotaTarjeta("El launcher con tus gatos: «Todo en uno» lo instala, le da permisos y activa el botón Inicio y el protector de pantalla.")
            }
            EtiquetaSeccion("Paso por paso")
            Tarjeta {
                FilaIr("Solo instalar", null, Iconos.bajar, primera = true) { app.ejecutar("Solo instalar") { app.acciones.instalarShikitari() } }
                FilaIr("Solo dar permisos", null, Iconos.escudo) { app.ejecutar("Solo dar permisos") { app.acciones.darPermisos() } }
                FilaIr("Solo activar Inicio y protector", null, Iconos.casa) { app.ejecutar("Activar Inicio y protector") { app.acciones.activarInicioYProtector() } }
            }
            EtiquetaSeccion("Portada")
            Tarjeta {
                FilaIr("Elegir foto", "La imagen grande del Inicio y del protector", Iconos.archivo, primera = true) { if (app.hayTv()) elegirPortada() }
                FilaIr("Volver a la portada original", null, Iconos.volver) { app.ejecutar("Portada original") { app.acciones.portadaOriginal() } }
            }
        }
        Launcher.ZIOTA -> PaginaModelo(
            app, AccionesTv.PACHI, "Ziota",
            "El launcher estilo Apple TV, con isla dinámica para la música, menú de vidrio y protector con el logo. " +
                "Se puede tener junto a Shikitari TV; el botón Inicio abre uno solo de los dos.",
            onAtras,
        )
        Launcher.SOLARI -> PaginaModelo(
            app, AccionesTv.SOLARI, "Solari",
            "La versión formal para las TVs del trabajo: vitrina de propiedades con el video y QR, logo y tipografía de Solari " +
                "y protector sobrio. Se puede instalar junto a Ziota.",
            onAtras,
        )
    }
}

@Composable
private fun PaginaModelo(app: EstadoApp, m: Modelo, titulo: String, descripcion: String, onAtras: () -> Unit) {
    Pagina(
        titulo = titulo,
        onAtras = onAtras,
        pie = { BotonPrincipal("Todo en uno", onClick = { app.ejecutar("Todo en uno: $titulo") { app.acciones.todoEnUnoModelo(m) } }) },
    ) {
        Tarjeta(Modifier.padding(top = Espacio.hueco)) {
            NotaTarjeta("$descripcion\n\n«Todo en uno» lo instala, lo prepara y también cambia el botón Inicio.")
        }
        EtiquetaSeccion("Paso por paso")
        Tarjeta {
            FilaIr("Instalar y preparar la isla dinámica", null, Iconos.bajar, primera = true) {
                app.ejecutar("Instalar $titulo") { listOf(app.acciones.instalarModelo(m), app.acciones.prepararModelo(m)).joinToString("\n") }
            }
            FilaIr("Usar $titulo con el botón Inicio", null, Iconos.casa) {
                app.ejecutar("Usar $titulo con Inicio") { app.acciones.usarModeloConInicio(m) }
            }
        }
    }
}

// ---------- Botones del control ----------

@Composable
fun PantallaBotones(app: EstadoApp, onAtras: () -> Unit) {
    val mapa = remember { mutableStateMapOf<String, String>().apply { putAll(app.mapaBotones()) } }
    LaunchedEffect(app.conectada?.ip) {
        app.leerBotonesDeTv { enTv ->
            mapa.clear()
            mapa.putAll(enTv)
        }
    }
    Pagina(
        titulo = "Botones del control",
        onAtras = onAtras,
        pie = { BotonPrincipal("Guardar en la TV", onClick = { app.guardarBotones(mapa.toMap()) }) },
    ) {
        Tarjeta(Modifier.padding(top = Espacio.hueco)) {
            NotaTarjeta(
                "Elegí qué app abre cada botón. Funciona con Ziota o Solari instalada y con su botón Inicio activado. " +
                    "Los que dejes «como viene» hacen lo de siempre. Nada cambia hasta que toques Guardar.",
            )
        }
        Hueco()
        Tarjeta {
            app.botonesControl.forEachIndexed { i, (tecla, nombre) ->
                val destino = mapa[tecla]
                FilaIr(
                    nombre,
                    null,
                    Iconos.control,
                    primera = i == 0,
                    derecha = {
                        Pastilla(destino?.let { app.nombreDeApp(it) } ?: "como viene", activa = destino != null)
                    },
                ) {
                    app.elegirAppPara(nombre) { paquete ->
                        if (paquete == null) mapa.remove(tecla) else mapa[tecla] = paquete
                    }
                }
            }
        }
    }
}

// ---------- Ayuda con Claude ----------

@Composable
fun PantallaClaude(app: EstadoApp, onAtras: () -> Unit) {
    var clave by rememberSaveable { mutableStateOf(app.claveGuardada()) }
    var cambiarClave by rememberSaveable { mutableStateOf(app.claveGuardada().isBlank()) }
    var pregunta by rememberSaveable { mutableStateOf("") }
    Pagina(
        titulo = "Ayuda con Claude",
        onAtras = onAtras,
        margenAbajo = false,
        pie = {
            CampoTexto(pregunta, { pregunta = it }, "Ej.: el perfil de YouTube no carga", lineas = 3)
            BotonPrincipal(
                "Preguntar a Claude",
                cargando = app.pensando,
                onClick = {
                    app.preguntarAClaude(pregunta.trim(), clave.trim())
                    if (app.conectada != null && clave.isNotBlank() && pregunta.isNotBlank()) {
                        pregunta = ""
                        cambiarClave = false
                    }
                },
            )
        },
    ) {
        Tarjeta(Modifier.padding(top = Espacio.hueco)) {
            NotaTarjeta(
                "Contale el problema y Claude revisa la TV. Lo que solo lee lo hace solo; cualquier cambio te lo pregunta antes. " +
                    "Lo que consulta de la TV se envía a Anthropic.",
            )
            if (cambiarClave) {
                Column(Modifier.padding(start = Espacio.filaPadH, end = Espacio.filaPadH, bottom = 14.dp)) {
                    CampoTexto(clave, { clave = it }, "Clave de API (sk-ant-…)", teclado = KeyboardType.Password, oculto = true)
                    Text("Se consigue en console.anthropic.com (se paga por uso).", style = Letras.nota, modifier = Modifier.padding(top = 6.dp))
                }
            } else {
                FilaIr("Clave de API guardada", "Tocá para cambiarla", Iconos.escudo) { cambiarClave = true }
            }
        }
        Hueco(18)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            app.chat.forEach { m -> Burbuja(m) }
            if (app.pensando) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Colores.texto, strokeWidth = 1.5.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Claude está revisando…", style = Letras.detalle)
                }
            }
        }
    }
}

@Composable
private fun Burbuja(m: EstadoApp.Mensaje) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.deClaude) Arrangement.Start else Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (m.deClaude) Modifier.vidrio(Formas.tarjeta)
                    else Modifier
                        .background(Color.White.copy(alpha = 0.9f), Formas.tarjeta)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), Formas.tarjeta),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            SelectionContainer {
                Text(m.texto, style = Letras.cuerpo.copy(fontSize = 15.sp, color = if (m.deClaude) Colores.texto else Colores.sobreAcento))
            }
        }
    }
}

// ---------- Historial ----------

@Composable
fun PantallaHistorial(app: EstadoApp, onAtras: () -> Unit) {
    val texto = remember { Bitacora.leer(app.contexto).ifEmpty { "Todavía no se hizo nada." } }
    Pagina(titulo = "Historial", subtitulo = "Todo lo que se hizo en las TVs, con el detalle completo", onAtras = onAtras) {
        Tarjeta(Modifier.padding(top = Espacio.hueco)) {
            SelectionContainer {
                Text(
                    texto,
                    style = Letras.detalle.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp),
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}
