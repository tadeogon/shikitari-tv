package com.shikitari.instalador

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro de todo lo que se cambió en las TVs. Queda guardado en el celular aunque cierres la app. */
object Bitacora {

    private const val MAXIMO = 60_000

    private fun archivo(contexto: Context) = File(contexto.filesDir, "historial.txt")

    @Synchronized
    fun anotar(contexto: Context, texto: String) {
        val f = archivo(contexto)
        val hora = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        f.appendText("[$hora] $texto\n\n")
        // Que no crezca para siempre: se queda con lo más nuevo.
        if (f.length() > MAXIMO * 2) f.writeText(f.readText().takeLast(MAXIMO))
    }

    @Synchronized
    fun leer(contexto: Context): String {
        val f = archivo(contexto)
        return if (f.exists()) f.readText().takeLast(MAXIMO).trimEnd() else ""
    }
}
