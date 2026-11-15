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
}
