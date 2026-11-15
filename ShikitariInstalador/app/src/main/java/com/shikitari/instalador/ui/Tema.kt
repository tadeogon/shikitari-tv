package com.shikitari.instalador.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colores de la interfaz «F»: negro, grises de vidrio y blanco. Sin azul ni colores vivos. */
object Colores {
    val fondo = Color(0xFF000000)
    val superficie1 = Color(0xFF0E0E10)   // fondo de lista baja
    val superficie2 = Color(0xFF1C1C1E)   // hoja / tarjeta alta
    val superficie3 = Color(0xFF2A2A2E)
    val texto = Color(0xFFF5F5F7)
    val textoDim = Color(0x8CEBEBF5)      // 55 %
    val textoDim2 = Color(0x57EBEBF5)     // 34 %
    val linea = Color(0x1AFFFFFF)         // 10 %
    val lineaFina = Color(0x12FFFFFF)     // 7 %
    val vidrioArriba = Color(0x16FFFFFF)  // 8,5 %
    val vidrioAbajo = Color(0x0BFFFFFF)   // 4,5 %
    val acento = Color(0xFFF2F2F7)        // el «acento» es blanco, no azul
    val apagado = Color(0xFFC7C7CC)
    val sobreAcento = Color(0xFF000000)
    val pastillaApagada = Color(0xFFA1A1A6)
    val error = Color(0xFFFF6961)
}

object Formas {
    val hoja = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val tarjeta = RoundedCornerShape(16.dp)
    val campo = RoundedCornerShape(14.dp)
    val boton = RoundedCornerShape(14.dp)
    val pastilla = RoundedCornerShape(50)
}

object Espacio {
    val borde = 16.dp          // margen lateral
    val hueco = 14.dp          // entre bloques
    val filaAlto = 54.dp
    val filaPadH = 14.dp
    val toque = 48.dp          // mínimo tocable
    val botonAlto = 50.dp
}

object Tiempos {
    const val hojaMs = 440
    const val pantallaMs = 260
    const val filaMs = 180
    const val cuentaMs = 700
    const val pulsoMs = 90
    const val reducidoMs = 150
}

/** Estilos de texto. Fuente del sistema. */
object Letras {
    val titulo = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 32.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.9).sp, lineHeight = 36.sp, color = Colores.texto,
    )
    val numero = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 56.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-2).sp, lineHeight = 60.sp, fontFeatureSettings = "tnum", color = Colores.texto,
    )
    val frase = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Colores.texto)
    val cuerpo = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = Colores.texto)
    val paquete = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = Colores.textoDim2)
    // Sobre la imagen de fondo el 34 % no se lee: los textos chicos sueltos usan el 55 %.
    val detalle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, color = Colores.textoDim)
    val seccion = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.72.sp, color = Color.White.copy(alpha = 0.42f),
    )
    val nota = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.5.sp, lineHeight = 18.75.sp, color = Colores.textoDim)
    val tabular = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontFeatureSettings = "tnum", color = Colores.textoDim)
}

private val tipografia = Typography(
    displayLarge = Letras.numero,
    headlineLarge = Letras.titulo,
    titleMedium = Letras.frase,
    bodyLarge = Letras.cuerpo,
    bodySmall = Letras.nota,
    labelMedium = Letras.seccion,
    labelSmall = Letras.paquete,
)

/** true cuando el sistema tiene las animaciones apagadas: entonces solo hay fundidos cortos. */
val LocalReducido = staticCompositionLocalOf { false }

@Composable
fun TemaShikitari(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Colores.fondo,
            onBackground = Colores.texto,
            surface = Colores.superficie1,
            onSurface = Colores.texto,
            primary = Colores.acento,
            onPrimary = Colores.sobreAcento,
        ),
        shapes = Shapes(small = Formas.campo, medium = Formas.tarjeta, large = Formas.hoja),
        typography = tipografia,
        content = content,
    )
}
