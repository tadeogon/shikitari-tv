package com.shikitari.instalador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Fondo de toda la app: negro puro (AMOLED). La imagen de la versión anterior sigue
 * guardada en res/drawable-nodpi/fondo_optimizar.jpg por si se quiere volver.
 */
@Composable
fun FondoOptimizar(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Colores.fondo))
}
