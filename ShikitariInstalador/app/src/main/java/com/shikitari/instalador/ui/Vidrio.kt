package com.shikitari.instalador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Superficie de la app: gris casi negro con una línea fina alrededor. Sin degradados. */
fun Modifier.vidrio(
    forma: Shape = Formas.tarjeta,
    alto: Boolean = false,
): Modifier = this
    .clip(forma)
    .background(if (alto) Colores.superficie2 else Colores.superficie1)
    .border(1.dp, Colores.linea, forma)
