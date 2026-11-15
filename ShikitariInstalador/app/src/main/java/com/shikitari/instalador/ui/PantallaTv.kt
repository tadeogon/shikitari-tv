package com.shikitari.instalador.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pestaña TV: a cuál estás conectado, Mis TVs y atajos a lo que más se usa. */
@Composable
fun PestanaTv(app: EstadoApp, irA: (Ruta) -> Unit, irAPestana: (Pestana) -> Unit) {
    val conectada = app.conectada
    Pagina(
        titulo = "Mis TVs",
        accion = { BotonBarra(Iconos.agregar, "Agregar TV") { irA(Ruta.AgregarTv) } },
    ) {
        // Estado de la conexión
        Tarjeta(Modifier.padding(top = Espacio.hueco), alto = true) {
            Row(
                Modifier.padding(horizontal = Espacio.filaPadH, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(if (conectada != null) Colores.texto else Color.Transparent, CircleShape)
                        .border(1.5.dp, Colores.texto.copy(alpha = if (conectada != null) 1f else 0.4f), CircleShape),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(conectada?.let { "Conectado a ${it.nombre}" } ?: "Sin conectar", style = Letras.frase.copy(fontSize = 17.sp))
                    Text(
                        if (conectada != null) app.infoConectada else "Tocá una TV de la lista para conectarte.",
                        style = Letras.detalle,
                    )
                }
            }
        }

        // Atajos (solo con una TV conectada)
        AnimatedVisibility(visible = conectada != null) {
            Column {
                EtiquetaSeccion("Atajos")
                Tarjeta {
                    FilaIr("Control remoto", "Ver la pantalla y manejarla", Iconos.control, primera = true) { irAPestana(Pestana.CONTROL) }
                    FilaIr("Apps de la TV", "Abrir, fijar o desinstalar", Iconos.grilla) { irAPestana(Pestana.CONTROL); irA(Ruta.Apps) }
                    FilaIr("Instalar un APK", "Desde tu celular a la TV", Iconos.paquete) { irA(Ruta.Archivos(instalar = true)) }
                }
            }
        }

        // Mis TVs
        EtiquetaSeccion("Guardadas")
        Tarjeta {
            if (app.tvs.isEmpty() && app.nuevas.isEmpty()) {
                NotaTarjeta("Todavía no hay TVs guardadas. Buscalas en el Wi-Fi o agregá una con el código.")
            }
            app.tvs.forEachIndexed { i, t ->
                val activa = conectada?.ip == t.ip
                FilaIr(
                    titulo = t.nombre,
                    detalle = "${t.modelo.ifBlank { "TV" }} · ${t.ip}",
                    icono = Iconos.tv,
                    primera = i == 0,
                    derecha = { if (activa) Pastilla("Conectada", activa = true) else Icon(Iconos.chevronDerecha, null, tint = Colores.textoDim2, modifier = Modifier.size(18.dp)) },
                    alMantener = { app.opcionesTv(t) },
                ) { app.conectar(t) }
            }
        }
        if (app.tvs.isNotEmpty()) {
            Text(
                "Tocá una TV para conectarte. Dejala apretada para cambiarle el nombre o sacarla.",
                style = Letras.nota,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }

        if (app.nuevas.isNotEmpty()) {
            EtiquetaSeccion("Encontradas en el Wi-Fi")
            Tarjeta {
                app.nuevas.entries.forEachIndexed { i, (ip, p) ->
                    FilaIr("TV nueva en $ip", "Tocá para conectar y guardarla", Iconos.agregar, primera = i == 0) { app.conectarNueva(ip, p) }
                }
            }
        }

        Hueco(16)
        BotonSecundario("Buscar en el Wi-Fi", onClick = { app.buscarTvs() })
    }
}

/** Agregar una TV nueva: con el código de vinculación, o por IP para las viejas. */
@Composable
fun PantallaAgregarTv(app: EstadoApp, onAtras: () -> Unit) {
    var ip by rememberSaveable { mutableStateOf(app.ipGuardada()) }
    var codigo by rememberSaveable { mutableStateOf("") }
    var avanzado by rememberSaveable { mutableStateOf(false) }
    var ip2 by rememberSaveable { mutableStateOf("") }
    var puerto by rememberSaveable { mutableStateOf("") }
    Pagina(
        titulo = "Agregar una TV",
        onAtras = onAtras,
        pie = { BotonPrincipal("Vincular y conectar", onClick = { app.vincular(ip, codigo, onAtras) }) },
    ) {
        Tarjeta(Modifier.padding(top = Espacio.hueco)) {
            Paso(1, "En la TV: Ajustes → Sistema → Opciones para desarrolladores → Depuración inalámbrica (activada).", primera = true)
            Paso(2, "Tocá «Vincular dispositivo con código»: la TV muestra una IP y un código de 6 números.")
            Paso(3, "Escribilos acá abajo. El puerto lo busco solo.")
        }
        Hueco()
        CampoTexto(ip, { ip = it }, "IP de la TV (ej. 192.168.100.2)", teclado = KeyboardType.Phone)
        Hueco(10)
        CampoTexto(codigo, { codigo = it }, "Código de vinculación (6 números)", teclado = KeyboardType.Number)

        Hueco(22)
        Tarjeta {
            FilaIr(
                "TVs viejas o sin código",
                "Si la TV tiene «Depuración por red» (puerto 5555)",
                Iconos.tv,
                primera = true,
                derecha = { Icon(if (avanzado) Iconos.chevronArriba else Iconos.chevronAbajo, null, tint = Colores.textoDim, modifier = Modifier.size(18.dp)) },
            ) { avanzado = !avanzado }
        }
        AnimatedVisibility(visible = avanzado) {
            Column(Modifier.padding(top = 10.dp)) {
                CampoTexto(ip2, { ip2 = it }, "IP de la TV", teclado = KeyboardType.Phone)
                Hueco(10)
                CampoTexto(puerto, { puerto = it }, "Puerto (vacío = 5555 o lo busco solo)", teclado = KeyboardType.Number)
                Hueco(10)
                Text("En la TV aceptá el aviso que aparece.", style = Letras.nota, modifier = Modifier.padding(start = 4.dp))
                Hueco(10)
                BotonSecundario("Conectar", onClick = { app.conectarManual(ip2, puerto.trim().toIntOrNull() ?: 0, onAtras) })
            }
        }
    }
}

@Composable
private fun Paso(numero: Int, texto: String, primera: Boolean = false) {
    Column {
        if (!primera) Separador()
        Row(Modifier.padding(horizontal = Espacio.filaPadH, vertical = 12.dp)) {
            Box(
                Modifier
                    .size(24.dp)
                    .border(1.dp, Colores.texto.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$numero", style = Letras.detalle.copy(color = Colores.texto))
            }
            Spacer(Modifier.width(12.dp))
            Text(texto, style = Letras.cuerpo.copy(fontSize = 15.sp), modifier = Modifier.fillMaxWidth())
        }
    }
}
