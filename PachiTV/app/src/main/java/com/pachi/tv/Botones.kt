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

    private fun mapa(contexto: Context): Map<Int, String> = cache ?: run {
        val texto = contexto.getSharedPreferences("botones", Context.MODE_PRIVATE).getString(CLAVE, "") ?: ""
        leer(texto).also { cache = it }
    }

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
        val mapa = intent.getStringExtra("mapa") ?: return
        Botones.guardar(contexto, mapa)
        resultData = "ok"
    }
}
