package com.pachi.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Cuando aparece la pantalla de inicio de Google (al apretar Home en el control),
 * abre Pachi TV encima. No reemplaza el launcher del sistema.
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

    override fun onInterrupt() {}

    private companion object {
        val LAUNCHERS = setOf("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher")
    }
}
