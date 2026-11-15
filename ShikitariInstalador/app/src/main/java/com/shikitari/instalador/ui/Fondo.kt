package com.shikitari.instalador.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.shikitari.instalador.R

/**
 * Fondo de la pantalla Optimizar: la imagen AMOLED que eligió el usuario
 * («Amoled Exclusive Backgrounds», de su autor; uso personal en esta app), con un velo negro
 * más fuerte abajo para que la lista y los botones se lean bien.
 */
@Composable
fun FondoOptimizar(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Colores.fondo)) {
        Image(
            painter = painterResource(R.drawable.fondo_optimizar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    // Arriba la imagen tiene lo más brillante y ahí van los textos chicos: velo más fuerte.
                    0f to Color.Black.copy(alpha = 0.55f),
                    0.35f to Color.Black.copy(alpha = 0.50f),
                    1f to Color.Black.copy(alpha = 0.72f),
                ),
            ),
        )
    }
}
