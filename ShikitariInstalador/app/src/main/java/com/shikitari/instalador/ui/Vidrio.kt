package com.shikitari.instalador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * El vidrio son tres capas: relleno translúcido, borde de 1 dp y una luz interior de 1 px arriba.
 * Abajo de todo va un velo negro suave: el fondo es una imagen con colores fuertes y sin él el texto no se lee.
 */
fun Modifier.vidrio(
    forma: Shape = Formas.tarjeta,
    alto: Boolean = false,
): Modifier = this
    .clip(forma)
    .background(Color.Black.copy(alpha = if (alto) 0.38f else 0.32f))
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (alto) 0.085f else 0.06f),
                Color.White.copy(alpha = if (alto) 0.045f else 0.03f),
            ),
        ),
    )
    .border(1.dp, Color.White.copy(alpha = 0.10f), forma)
    .drawWithContent {
        drawContent()
        // línea de luz interior arriba
        drawLine(
            color = Color.White.copy(alpha = 0.07f),
            start = Offset(size.width * 0.04f, 1f),
            end = Offset(size.width * 0.96f, 1f),
            strokeWidth = 2f,
        )
    }
