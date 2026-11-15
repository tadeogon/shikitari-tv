package com.pachi.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

/**
 * Qué app abre cada botón remapeado del control. Se guarda como "KEYCODE_APP_3=com.amazon.firetv.youtube;...".
 * Los nombres de las teclas se traducen en la TV misma: así funcionan también las teclas propias de Amazon
 * (APP_1..APP_4 son los botones de apps del control del Fire TV).
 */
object Botones {

    private const val CLAVE = "botones"

    @Volatile
    private var cache: Map<Int, String>? = null

    fun destino(contexto: Context, codigo: Int): String? = mapa(contexto)[codigo]

    /**
     * En Fire TV, Amazon se queda con los botones de apps antes de que lleguen a Pachi. Lo que sí se ve es el
     * pedido que hace el sistema al apretarlo (queda en el registro del sistema), por ejemplo:
     *   START u0 {act=...VIEW dat=amzns://apps/android?asin=B07Y8SJGCV&clickStreamReftag=r_btn_app_12 ...} from uid 1000
     * Cada botón pide algo distinto; esta tabla dice qué buscar en esa línea para cada botón.
     */
    private val SEÑAL_FIRE = mapOf(
        "KEYCODE_APP_1" to "com.amazon.firebat",  // Prime Video
        "KEYCODE_APP_2" to "netflix",             // Netflix
        "KEYCODE_APP_3" to "B07Y8SJGCV",          // Disney+
        "KEYCODE_APP_4" to "B08BG9MPT9",          // Peacock
        "KEYCODE_LIVE_TV" to "showGuideButtonContent", // botón TV (guía de canales)
        // Canal + y Canal − no pasan por Amazon: llegan directo a onKeyEvent.
    )

    /** Si la línea del registro es un botón remapeado del Fire TV, devuelve la app que hay que abrir. */
    fun destinoFire(contexto: Context, linea: String): String? {
        if (!linea.contains("START u0") || !linea.contains("from uid 1000")) return null
        val texto = contexto.getSharedPreferences("botones", Context.MODE_PRIVATE).getString(CLAVE, "") ?: ""
        texto.split(';').forEach { par ->
            val partes = par.split('=', limit = 2)
            if (partes.size != 2) return@forEach
            val señal = SEÑAL_FIRE[partes[0].trim()] ?: return@forEach
            val destino = partes[1].trim()
            // Si lo que se abre ya es la app elegida (por ejemplo, Netflix asignado a Netflix), no hay nada que hacer.
            if (linea.contains(señal, ignoreCase = true) && !linea.contains(destino)) return destino
        }
        return null
    }

    fun hayAlgoRemapeado(contexto: Context) =
        !contexto.getSharedPreferences("botones", Context.MODE_PRIVATE).getString(CLAVE, "").isNullOrBlank()

    private fun mapa(contexto: Context): Map<Int, String> = cache ?: run {
        val texto = contexto.getSharedPreferences("botones", Context.MODE_PRIVATE).getString(CLAVE, "") ?: ""
        leer(texto).also { cache = it }
    }

    fun texto(contexto: Context): String =
        contexto.getSharedPreferences("botones", Context.MODE_PRIVATE).getString(CLAVE, "") ?: ""

    fun guardar(contexto: Context, texto: String) {
        contexto.getSharedPreferences("botones", Context.MODE_PRIVATE).edit().putString(CLAVE, texto).apply()
        cache = leer(texto)
    }

    private fun leer(texto: String): Map<Int, String> =
        texto.split(';').mapNotNull { par ->
            val (tecla, paquete) = par.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val codigo = KeyEvent.keyCodeFromString(tecla.trim())
            val p = paquete.trim()
            if (codigo == KeyEvent.KEYCODE_UNKNOWN || p.isEmpty() || codigo in INTOCABLES) null else codigo to p
        }.toMap()

    /** Estas nunca se remapean: sin ellas no se puede volver atrás ni salir de nada. */
    private val INTOCABLES = setOf(
        KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
    )
}

/**
 * Recibe la configuración desde el instalador del celular:
 *   am broadcast -n <paquete>/com.pachi.tv.ReceptorBotones --es mapa "KEYCODE_APP_3=com.x;KEYCODE_APP_4=com.y"
 * Un mapa vacío deja todos los botones como vienen de fábrica.
 */
class ReceptorBotones : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val mapa = intent.getStringExtra("mapa")
        if (mapa == null) {
            // Sin mapa: el instalador pregunta cómo están configurados ahora.
            resultData = Botones.texto(contexto)
            return
        }
        Botones.guardar(contexto, mapa)
        resultData = "ok"
    }
}
