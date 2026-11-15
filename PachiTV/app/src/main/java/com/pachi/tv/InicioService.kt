package com.pachi.tv

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Dos trabajos:
 *  1. Cuando aparece la pantalla de inicio de Google o de Amazon (al apretar Home en el control),
 *     abre Pachi TV encima. No reemplaza el launcher del sistema.
 *  2. Botones remapeados: si un botón del control (por ejemplo el de Disney+) está asignado a otra app,
 *     se atrapa antes de que llegue al sistema y se abre esa app. Lo configura el instalador del celular.
 */
class InicioService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName !in LAUNCHERS) return
        val clase = event.className ?: return
        if (!clase.contains("Home") && !clase.contains("MainActivity")) return
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainActivity.EXTRA_DESDE_HOME, true),
        )
    }

    private val principal = Handler(Looper.getMainLooper())
    @Volatile
    private var vivo = false
    private var lector: Process? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        vivo = true
        // Solo en Fire TV: ahí los botones de apps no pasan por onKeyEvent (ver Botones.destinoFire).
        if (Build.MANUFACTURER.equals("Amazon", ignoreCase = true)) Thread({ vigilarBotonesFire() }, "botones-fire").start()
    }

    override fun onDestroy() {
        vivo = false
        lector?.destroy()
        super.onDestroy()
    }

    /** Lee el registro del sistema y, cuando un botón remapeado pide abrir su app, abre la elegida encima. */
    private fun vigilarBotonesFire() {
        while (vivo) {
            // El permiso lo da el instalador del celular al guardar los botones; hasta entonces, se espera.
            if (checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED || !Botones.hayAlgoRemapeado(this)) {
                Thread.sleep(15_000)
                continue
            }
            try {
                val p = ProcessBuilder("logcat", "-v", "brief", "-T", "1", "ActivityTaskManager:I", "*:S").redirectErrorStream(true).start()
                lector = p
                p.inputStream.bufferedReader().useLines { lineas ->
                    for (linea in lineas) {
                        if (!vivo) break
                        val destino = Botones.destinoFire(this, linea) ?: continue
                        // Amazon abre su pantalla un instante después; se abre la elegida al rato y otra vez por las dudas.
                        principal.postDelayed({ abrir(destino) }, 450)
                        principal.postDelayed({ abrir(destino) }, 1400)
                    }
                }
            } catch (e: Exception) {
                Thread.sleep(5_000)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val destino = Botones.destino(this, event.keyCode) ?: return false
        // Se atrapan el "apretar" y el "soltar"; la app se abre al soltar, una sola vez.
        if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) abrir(destino)
        return true
    }

    private fun abrir(paquete: String) {
        val pm = packageManager
        val intento = pm.getLeanbackLaunchIntentForPackage(paquete) ?: pm.getLaunchIntentForPackage(paquete)
        if (intento == null) {
            Toast.makeText(this, "La app de este botón no está instalada", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(intento.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    override fun onInterrupt() {}

    private companion object {
        // Google TV, Android TV viejo y Fire TV (Amazon)
        val LAUNCHERS = setOf("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.amazon.tv.launcher")
    }
}
