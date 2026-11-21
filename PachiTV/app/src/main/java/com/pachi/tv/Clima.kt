package com.pachi.tv

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Pronóstico del lugar. La ubicación sale de la IP de la conexión (ipwho.is), porque la TV no tiene GPS,
 * y el tiempo de Open-Meteo. Ninguno pide clave. Se guarda 30 minutos para no consultar a cada rato.
 */
object Clima {

    private const val VIGENCIA_MS = 30 * 60 * 1000L
    private val enCurso = java.util.concurrent.atomic.AtomicBoolean(false)

    fun guardado(contexto: Context): Pair<String, String>? {
        val p = contexto.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val ahora = p.getString("clima_ahora", null) ?: return null
        return ahora to (p.getString("clima_detalle", "") ?: "")
    }

    /** Actualiza en otro hilo si hace falta; avisa solo si consiguió datos nuevos. */
    fun actualizar(contexto: Context, listo: (Pair<String, String>) -> Unit) {
        val p = contexto.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("clima_hora", 0) < VIGENCIA_MS) return
        // Si ya hay una consulta en camino (salir y volver rápido), no se larga otra.
        if (!enCurso.compareAndSet(false, true)) return
        Thread {
          try {
            try {
                val lugar = JSONObject(leer("https://ipwho.is/?fields=success,city,latitude,longitude"))
                if (!lugar.optBoolean("success")) return@Thread
                val datos = JSONObject(
                    leer(
                        "https://api.open-meteo.com/v1/forecast?latitude=${lugar.getDouble("latitude")}" +
                            "&longitude=${lugar.getDouble("longitude")}&current=temperature_2m,weather_code" +
                            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto",
                    ),
                )
                val actual = datos.getJSONObject("current")
                val dia = datos.getJSONObject("daily")
                val (icono, nombre) = describir(actual.getInt("weather_code"))
                val ahora = "$icono ${actual.getDouble("temperature_2m").roundToInt()}° $nombre"
                val maxima = dia.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
                val minima = dia.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
                val detalle = "${lugar.optString("city")} · Máx $maxima° Mín $minima°"
                p.edit()
                    .putString("clima_ahora", ahora)
                    .putString("clima_detalle", detalle)
                    .putLong("clima_hora", System.currentTimeMillis())
                    .apply()
                listo(ahora to detalle)
            } catch (e: Exception) {
                // Sin internet o servicio caído: queda lo último guardado y se reintenta la próxima vez.
            }
          } finally {
            enCurso.set(false)
          }
        }.start()
    }

    private fun leer(direccion: String): String {
        val conexion = URL(direccion).openConnection() as HttpURLConnection
        conexion.connectTimeout = 8000
        conexion.readTimeout = 8000
        return try {
            conexion.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexion.disconnect()
        }
    }

    /** Códigos de tiempo de la OMM que usa Open-Meteo. */
    private fun describir(codigo: Int): Pair<String, String> = when (codigo) {
        0 -> "☀️" to "Despejado"
        1 -> "🌤️" to "Mayormente despejado"
        2 -> "⛅" to "Parcialmente nublado"
        3 -> "☁️" to "Nublado"
        45, 48 -> "🌫️" to "Niebla"
        51, 53, 55, 56, 57 -> "🌦️" to "Llovizna"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️" to "Lluvia"
        71, 73, 75, 77, 85, 86 -> "🌨️" to "Nieve"
        95, 96, 99 -> "⛈️" to "Tormenta"
        else -> "🌡️" to ""
    }
}
