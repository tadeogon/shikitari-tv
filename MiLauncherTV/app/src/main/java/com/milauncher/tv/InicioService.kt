package com.milauncher.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Cuando aparece la pantalla de inicio de Google (al apretar Home en el control),
 * abre Shikitari TV encima. Es el mismo método que usa Projectivy; no reemplaza el launcher del sistema.
 */
class InicioService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName !in LAUNCHERS) return
        if (event.className?.contains("Home") != true) return
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainActivity.EXTRA_DESDE_HOME, true),
        )
    }

    override fun onInterrupt() {}

    private companion object {
        val LAUNCHERS = setOf("com.google.android.apps.tv.launcherx", "com.amazon.tv.launcher")
    }
}
