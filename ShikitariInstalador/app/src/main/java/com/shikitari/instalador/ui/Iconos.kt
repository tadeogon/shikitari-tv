package com.shikitari.instalador.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ocho iconos dibujados a mano: grilla de 24, trazo 1,8, puntas redondas, sin relleno. */
object Iconos {

    private fun icono(nombre: String, trazo: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = nombre,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = trazo,
        ).build()

    val silencio = icono("silencio") {
        moveTo(4f, 9.5f); lineTo(4f, 14.5f); lineTo(7.5f, 14.5f); lineTo(12f, 18.5f); lineTo(12f, 5.5f); lineTo(7.5f, 9.5f); close()
        moveTo(16f, 9.5f); lineTo(21f, 14.5f)
        moveTo(21f, 9.5f); lineTo(16f, 14.5f)
    }
    val encendido = icono("encendido") {
        moveTo(12f, 3.5f); lineTo(12f, 11.5f)
        moveTo(7.2f, 6.6f); arcTo(7.3f, 7.3f, 0f, true, false, 16.8f, 6.6f)
    }
    val retroceder = icono("retroceder") {
        moveTo(20f, 6f); lineTo(13f, 12f); lineTo(20f, 18f); close()
        moveTo(11f, 6f); lineTo(4f, 12f); lineTo(11f, 18f); close()
    }
    val adelantar = icono("adelantar") {
        moveTo(4f, 6f); lineTo(11f, 12f); lineTo(4f, 18f); close()
        moveTo(13f, 6f); lineTo(20f, 12f); lineTo(13f, 18f); close()
    }

    val atras = icono("atras") {
        moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
    }
    val check = icono("check") {
        moveTo(4.5f, 12.5f); lineTo(9.7f, 17.7f); lineTo(19.5f, 6.8f)
    }
    val menos = icono("menos") {
        moveTo(6f, 12f); lineTo(18f, 12f)
    }
    val chevronAbajo = icono("chevronAbajo") {
        moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
    }
    val chevronArriba = icono("chevronArriba") {
        moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f)
    }
    val escudo = icono("escudo") {
        moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 11f)
        curveTo(19f, 15.5f, 16f, 19f, 12f, 21f)
        curveTo(8f, 19f, 5f, 15.5f, 5f, 11f)
        lineTo(5f, 6f); close()
    }
    val detener = icono("detener") {
        moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 17f); lineTo(7f, 17f); close()
    }
    val flecha = icono("flecha") {
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }

    // ---------- Los del resto de la app ----------

    val chevronDerecha = icono("chevronDerecha") {
        moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
    }
    val chevronIzquierda = icono("chevronIzquierda") {
        moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f)
    }
    val tv = icono("tv") {
        moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 16f); lineTo(4f, 16f); close()
        moveTo(9f, 20f); lineTo(15f, 20f)
    }
    val control = icono("control") {
        moveTo(9f, 3f); lineTo(15f, 3f)
        curveTo(16.1f, 3f, 17f, 3.9f, 17f, 5f); lineTo(17f, 19f)
        curveTo(17f, 20.1f, 16.1f, 21f, 15f, 21f); lineTo(9f, 21f)
        curveTo(7.9f, 21f, 7f, 20.1f, 7f, 19f); lineTo(7f, 5f)
        curveTo(7f, 3.9f, 7.9f, 3f, 9f, 3f); close()
        moveTo(12f, 7f); lineTo(12f, 7.01f)
        moveTo(10f, 11f); lineTo(14f, 11f)
        moveTo(12f, 9f); lineTo(12f, 13f)
        moveTo(10f, 17f); lineTo(14f, 17f)
    }
    val rayo = icono("rayo") {
        moveTo(13f, 3f); lineTo(5f, 13.5f); lineTo(11.5f, 13.5f); lineTo(10.5f, 21f); lineTo(19f, 10f); lineTo(12.5f, 10f); close()
    }
    val mas = icono("mas") {
        moveTo(6f, 12f); lineTo(6.01f, 12f)
        moveTo(12f, 12f); lineTo(12.01f, 12f)
        moveTo(18f, 12f); lineTo(18.01f, 12f)
    }
    val agregar = icono("agregar") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }
    val lupa = icono("lupa") {
        moveTo(17f, 10.5f)
        curveTo(17f, 14.09f, 14.09f, 17f, 10.5f, 17f)
        curveTo(6.91f, 17f, 4f, 14.09f, 4f, 10.5f)
        curveTo(4f, 6.91f, 6.91f, 4f, 10.5f, 4f)
        curveTo(14.09f, 4f, 17f, 6.91f, 17f, 10.5f); close()
        moveTo(15.5f, 15.5f); lineTo(20f, 20f)
    }
    val carpeta = icono("carpeta") {
        moveTo(3.5f, 7f); lineTo(3.5f, 18f); lineTo(20.5f, 18f); lineTo(20.5f, 8.5f); lineTo(11.5f, 8.5f)
        lineTo(9.5f, 6f); lineTo(4.5f, 6f); close()
    }
    val archivo = icono("archivo") {
        moveTo(6f, 3f); lineTo(14f, 3f); lineTo(18f, 7f); lineTo(18f, 21f); lineTo(6f, 21f); close()
        moveTo(14f, 3f); lineTo(14f, 7f); lineTo(18f, 7f)
    }
    val paquete = icono("paquete") {
        moveTo(12f, 3f); lineTo(20f, 7.5f); lineTo(20f, 16.5f); lineTo(12f, 21f); lineTo(4f, 16.5f); lineTo(4f, 7.5f); close()
        moveTo(4f, 7.5f); lineTo(12f, 12f); lineTo(20f, 7.5f)
        moveTo(12f, 12f); lineTo(12f, 21f)
    }
    val estrella = icono("estrella") {
        moveTo(12f, 3.5f); lineTo(14.6f, 8.9f); lineTo(20.5f, 9.6f); lineTo(16.2f, 13.7f); lineTo(17.3f, 19.5f)
        lineTo(12f, 16.7f); lineTo(6.7f, 19.5f); lineTo(7.8f, 13.7f); lineTo(3.5f, 9.6f); lineTo(9.4f, 8.9f); close()
    }
    val reloj = icono("reloj") {
        moveTo(20f, 12f)
        curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
        curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
        curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
        curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f); close()
        moveTo(12f, 8f); lineTo(12f, 12f); lineTo(15f, 14f)
    }
    val chispa = icono("chispa") {
        moveTo(12f, 3f); lineTo(13.8f, 10.2f); lineTo(21f, 12f); lineTo(13.8f, 13.8f); lineTo(12f, 21f)
        lineTo(10.2f, 13.8f); lineTo(3f, 12f); lineTo(10.2f, 10.2f); close()
    }
    val casa = icono("casa") {
        moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f)
        moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f)
    }
    val volver = icono("volver") {
        moveTo(9f, 7f); lineTo(4f, 12f); lineTo(9f, 17f)
        moveTo(4f, 12f); lineTo(15f, 12f)
        curveTo(17.8f, 12f, 20f, 14.2f, 20f, 17f)
    }
    val volumenMenos = icono("volumenMenos") {
        moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f); lineTo(12f, 18.5f); lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close()
        moveTo(15.5f, 12f); lineTo(20f, 12f)
    }
    val volumenMas = icono("volumenMas") {
        moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f); lineTo(12f, 18.5f); lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close()
        moveTo(15.5f, 12f); lineTo(20.5f, 12f)
        moveTo(18f, 9.5f); lineTo(18f, 14.5f)
    }
    val pausa = icono("pausa") {
        moveTo(9f, 6f); lineTo(9f, 18f)
        moveTo(15f, 6f); lineTo(15f, 18f)
    }
    val play = icono("play") {
        moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close()
    }
    val basura = icono("basura") {
        moveTo(4.5f, 7f); lineTo(19.5f, 7f)
        moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f)
        moveTo(6.5f, 7f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 7f)
    }
    val bajar = icono("bajar") {
        moveTo(12f, 4f); lineTo(12f, 15f)
        moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        moveTo(5f, 20f); lineTo(19f, 20f)
    }
    val subir = icono("subir") {
        moveTo(12f, 15f); lineTo(12f, 4f)
        moveTo(7f, 9f); lineTo(12f, 4f); lineTo(17f, 9f)
        moveTo(5f, 20f); lineTo(19f, 20f)
    }
    val grilla = icono("grilla") {
        moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
        moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
        moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
        moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
    }
    val lapiz = icono("lapiz") {
        moveTo(4f, 20f); lineTo(8f, 19f); lineTo(19f, 8f); lineTo(16f, 5f); lineTo(5f, 16f); close()
        moveTo(14f, 7f); lineTo(17f, 10f)
    }
    val enviar = icono("enviar") {
        moveTo(12f, 19f); lineTo(12f, 5f)
        moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
    }
}
