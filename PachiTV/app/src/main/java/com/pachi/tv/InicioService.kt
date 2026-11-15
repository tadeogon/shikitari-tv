package com.pachi.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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
        startActivity(intento.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    override fun onInterrupt() {}

    private companion object {
        // Google TV, Android TV viejo y Fire TV (Amazon)
        val LAUNCHERS = setOf("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.amazon.tv.launcher")
    }
}
