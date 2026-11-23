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

/** Colores (4.0, diseño de Sleek): negro puro, línea fina gris, texto claro y un solo acento, el ámbar. */
object Colores {
    val fondo = Color(0xFF000000)
    val superficie1 = Color(0xFF0B0C0D)   // fondo de lista baja
    val superficie2 = Color(0xFF121314)   // hoja / tarjeta alta
    val superficie3 = Color(0xFF1D1F21)
    val texto = Color(0xFFF5F5F5)
    val textoDim = Color(0xFF9A9DA1)
    val textoDim2 = Color(0xFF7C8086)
    val linea = Color(0xFF2B2D30)
    val lineaFina = Color(0xFF2B2D30)
    val acento = Color(0xFFFFB020)        // ámbar: solo lo elegido, lo prendido y el botón principal
    val apagado = Color(0xFFC7C7CC)
    val sobreAcento = Color(0xFF000000)
    val pastillaApagada = Color(0xFFA1A1A6)
    val error = Color(0xFFFF6961)
}

object Formas {
    val hoja = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val tarjeta = RoundedCornerShape(16.dp)
    val barra = RoundedCornerShape(20.dp)
    val campo = RoundedCornerShape(14.dp)
    val boton = RoundedCornerShape(50)
    val pastilla = RoundedCornerShape(50)
}

object Espacio {
    val borde = 20.dp          // margen lateral
    val hueco = 16.dp          // entre bloques
    val filaAlto = 60.dp
    val filaPadH = 0.dp           // las filas van sueltas sobre el negro, sin caja
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
        fontFamily = FontFamily.SansSerif, fontSize = 34.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.7).sp, lineHeight = 40.sp, color = Colores.texto,
    )
    val numero = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 72.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-2.2).sp, lineHeight = 76.sp, fontFeatureSettings = "tnum", color = Colores.texto,
    )
    val frase = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Colores.texto)
    val cuerpo = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = Colores.texto)
    val paquete = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = Colores.textoDim2)
    // Sobre la imagen de fondo el 34 % no se lee: los textos chicos sueltos usan el 55 %.
    val detalle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, color = Colores.textoDim)
    val seccion = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.72.sp, color = Colores.textoDim,
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
