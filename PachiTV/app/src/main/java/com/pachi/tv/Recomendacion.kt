package com.pachi.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * La recomendación de hoy: una serie por día, elegida por género, nota mínima y años. Sale de TVmaze (gratis, sin
 * clave; solo series). Se busca una vez por día y se guarda. La idea viene de Aura TV (github.com/cookedzera/aura-tv,
 * licencia MIT); el código es propio de Ziota.
 */
object Recomendacion {
    class Serie(val titulo: String, val anio: String, val nota: Double, val generos: String, val resumen: String, val canal: String, val poster: Bitmap?)

    /** Géneros de TVmaze con su nombre en castellano. */
    val GENEROS = listOf("" to "Todos", "Drama" to "Drama", "Comedy" to "Comedia", "Horror" to "Terror", "Science-Fiction" to "Ciencia ficción",
        "Crime" to "Policial", "Thriller" to "Suspenso", "Action" to "Acción", "Fantasy" to "Fantasía", "Romance" to "Romance")
    val NOTAS = listOf(6.0, 7.0, 7.5, 8.0)
    val ANIOS = listOf(0, 2010, 2015, 2020)

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun activa(c: Context) = prefs(c).getBoolean("reco_activa", true)
    fun genero(c: Context) = prefs(c).getString("reco_genero", "") ?: ""
    fun nota(c: Context) = prefs(c).getFloat("reco_nota", 7f).toDouble()
    fun desde(c: Context) = prefs(c).getInt("reco_desde", 0)

    fun nombreGenero(c: Context) = GENEROS.firstOrNull { it.first == genero(c) }?.second ?: "Todos"

    /** Cambia un ajuste y olvida la de hoy, para buscar otra con los límites nuevos. */
    fun cambiar(c: Context, cambio: (android.content.SharedPreferences.Editor) -> Unit) {
        val e = prefs(c).edit()
        cambio(e)
        e.remove("reco_dia").remove("reco_json").apply()
    }

    private fun hoy() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** La de hoy si ya está guardada; null si todavía no se buscó. */
    fun guardada(c: Context): Serie? {
        val p = prefs(c)
        if (p.getString("reco_dia", null) != hoy()) return null
        val j = JSONObject(p.getString("reco_json", null) ?: return null)
        val archivo = File(c.cacheDir, "reco.jpg")
        val poster = if (archivo.exists()) BitmapFactory.decodeFile(archivo.path) else null
        return Serie(j.getString("t"), j.getString("a"), j.getDouble("n"), j.getString("g"), j.getString("r"), j.getString("c"), poster)
    }

    /** Último resultado de la búsqueda: "Nada encaja…" o vacío. */
    fun estado(c: Context) = prefs(c).getString("reco_estado", "") ?: ""

    private var buscando = false

    /** Busca la de hoy en otro hilo y avisa en el hilo principal cuando termina (encontrada o no). */
    fun buscar(c: Context, listo: () -> Unit) {
        if (buscando || guardada(c) != null) return
        buscando = true
        val app = c.applicationContext
        Thread {
            val p = prefs(app)
            try {
                val semilla = hoy().toInt() + p.getInt("reco_salto", 0) * 7919
                var elegida: JSONObject? = null
                // Prueba algunas páginas de la lista de TVmaze (250 series cada una) hasta encontrar una que encaje.
                for (intento in 0 until 4) {
                    val pagina = (semilla + intento * 37) % 120
                    val lista = JSONArray(leer("https://api.tvmaze.com/shows?page=$pagina"))
                    val candidatas = (0 until lista.length()).map { lista.getJSONObject(it) }.filter { encaja(app, it) }
                    if (candidatas.isNotEmpty()) {
                        elegida = candidatas[semilla % candidatas.size]
                        break
                    }
                }
                if (elegida == null) {
                    p.edit().putString("reco_estado", "Nada encaja · probá con menos límites").apply()
                } else {
                    val poster = elegida.optJSONObject("image")?.optString("medium")
                    if (!poster.isNullOrBlank()) bajar(poster, File(app.cacheDir, "reco.jpg"))
                    val j = JSONObject()
                        .put("t", elegida.getString("name"))
                        .put("a", elegida.optString("premiered").take(4))
                        .put("n", elegida.optJSONObject("rating")?.optDouble("average") ?: 0.0)
                        .put("g", (0 until (elegida.optJSONArray("genres")?.length() ?: 0)).joinToString(" · ") { i ->
                            val g = elegida.getJSONArray("genres").getString(i)
                            GENEROS.firstOrNull { it.first == g }?.second ?: g
                        })
                        .put("r", elegida.optString("summary").replace(Regex("<[^>]*>"), "").trim())
                        .put("c", elegida.optJSONObject("webChannel")?.optString("name") ?: elegida.optJSONObject("network")?.optString("name") ?: "")
                    p.edit().putString("reco_dia", hoy()).putString("reco_json", j.toString()).putString("reco_estado", "").apply()
                }
            } catch (e: Exception) {
                // Sin internet: se vuelve a intentar la próxima vez.
            }
            buscando = false
            Handler(Looper.getMainLooper()).post(listo)
        }.start()
    }

    private fun encaja(c: Context, s: JSONObject): Boolean {
        val nota = s.optJSONObject("rating")?.optDouble("average") ?: return false
        if (nota.isNaN() || nota < nota(c)) return false
        val anio = s.optString("premiered").take(4).toIntOrNull() ?: return false
        if (anio < desde(c)) return false
        val g = genero(c)
        if (g.isNotEmpty()) {
            val generos = s.optJSONArray("genres") ?: return false
            if ((0 until generos.length()).none { generos.getString(it) == g }) return false
        }
        return s.optJSONObject("image") != null && s.optString("summary").isNotBlank()
    }

    private fun leer(direccion: String): String {
        val conexion = URL(direccion).openConnection() as HttpURLConnection
        conexion.connectTimeout = 8000
        conexion.readTimeout = 10000
        return try {
            conexion.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexion.disconnect()
        }
    }

    private fun bajar(direccion: String, destino: File) {
        val conexion = URL(direccion).openConnection() as HttpURLConnection
        conexion.connectTimeout = 8000
        conexion.readTimeout = 10000
        try {
            conexion.inputStream.use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
        } finally {
            conexion.disconnect()
        }
    }
}
