package com.shikitari.instalador.ui

import com.shikitari.instalador.ConexionTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Memoria de la TV, en MB. «Libre» es lo que Android puede darle a una app sin trabarse (MemAvailable). */
data class Memoria(val total: Int, val libre: Int) {
    val usada: Int get() = (total - libre).coerceAtLeast(0)
}

/** Lee /proc/meminfo de la TV: no cambia nada. Devuelve null si no se pudo. */
suspend fun leerMemoria(tv: ConexionTv): Memoria? = withContext(Dispatchers.IO) {
    runCatching {
        val texto = tv.leerSuave("cat /proc/meminfo") ?: return@runCatching null
        fun mb(clave: String) = Regex("$clave:\\s+(\\d+)").find(texto)!!.groupValues[1].toLong().div(1024).toInt()
        Memoria(mb("MemTotal"), mb("MemAvailable"))
    }.getOrNull()
}

/** «405 MB» o «1,2 GB». */
fun enTexto(mb: Int): String =
    if (mb < 1000) "$mb MB" else String.format(java.util.Locale("es", "AR"), "%.1f GB", mb / 1024f)
