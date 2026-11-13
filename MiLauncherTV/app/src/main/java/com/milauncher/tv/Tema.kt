package com.milauncher.tv

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface

/** Colores del launcher según el modo (claro u oscuro) y el color de acento elegido en Ajustes. */
class Tema(
    val oscuro: Boolean,
    val acento: Int,
    val nombreAcento: String,
    val nombreLetra: String,
    /** Letra de los títulos de sección y diálogos. */
    val fuenteTitulo: Typeface,
    /** Letra del menú, las filas de ajustes y los textos. */
    val fuenteTexto: Typeface,
) {
    val fuenteTextoNegrita: Typeface = Typeface.create(fuenteTexto, Typeface.BOLD)

    /** Logo de la barra: texto oscuro sobre el papel claro, o claro sobre el fondo oscuro. */
    val logoBarra = if (oscuro) R.drawable.logo_barra_oscuro else R.drawable.logo_barra_claro

    val fondo = if (oscuro) 0xFF17161B.toInt() else 0xFFF3EBDD.toInt()
    val tinta = if (oscuro) 0xFFEEE7DC.toInt() else 0xFF2B2622.toInt()
    val tintaSuave = if (oscuro) 0xFFA79E92.toInt() else 0xFF6B5B4B.toInt()
    /** Fondo de las filas de Ajustes y de las tarjetas de apps sin banner. */
    val reposo = if (oscuro) 0xFF2A2830.toInt() else 0xFFEADFCB.toInt()
    val tarjeta = if (oscuro) 0xFF2A2830.toInt() else 0xFFFFFFFF.toInt()

    companion object {
        /** Paletas de acento: el color de la pincelada, los bordes y los íconos. */
        val ACENTOS = listOf(
            "Rojo" to 0xFFC8372D.toInt(),
            "Índigo" to 0xFF2E4A7D.toInt(),
            "Matcha" to 0xFF5B8C3A.toInt(),
            "Sakura" to 0xFFD9668A.toInt(),
            "Violeta" to 0xFF7E57C2.toInt(),
            "Naranja" to 0xFFE07B24.toInt(),
        )

        /** Estilos de letra: Pincel (Kaushan Script + Varela Round), Redonda (Varela Round) y Clásica (serif del sistema). */
        val LETRAS = listOf("Pincel", "Redonda", "Clásica")

        fun de(contexto: Context): Tema {
            val prefs = contexto.getSharedPreferences("launcher", Context.MODE_PRIVATE)
            val (nombre, color) = ACENTOS.getOrElse(prefs.getInt("acento", 0)) { ACENTOS[0] }
            val letra = prefs.getInt("letra", 0).coerceIn(0, LETRAS.size - 1)
            val pincel = Typeface.createFromAsset(contexto.assets, "fonts/kaushan.ttf")
            val redonda = Typeface.createFromAsset(contexto.assets, "fonts/varela.ttf")
            val (titulo, texto) = when (letra) {
                0 -> pincel to redonda
                1 -> Typeface.create(redonda, Typeface.BOLD) to redonda
                else -> Typeface.create(Typeface.SERIF, Typeface.BOLD) to Typeface.DEFAULT
            }
            return Tema(prefs.getBoolean("oscuro", false), color, nombre, LETRAS[letra], titulo, texto)
        }

        /** Filtro "noche" para la portada en modo oscuro: más oscura, azulada y con menos saturación. */
        fun filtroNoche(): ColorMatrixColorFilter {
            val noche = ColorMatrix().apply { setSaturation(0.65f) }
            noche.postConcat(ColorMatrix(floatArrayOf(
                0.38f, 0f, 0f, 0f, 0f,
                0f, 0.42f, 0f, 0f, 2f,
                0f, 0f, 0.58f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f,
            )))
            return ColorMatrixColorFilter(noche)
        }
    }
}
