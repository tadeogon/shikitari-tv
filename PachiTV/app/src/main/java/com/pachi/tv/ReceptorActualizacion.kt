package com.pachi.tv

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Cuando Ziota se actualiza, el Fire Stick deja el servicio del botón Inicio "conectado" pero sin avisarle nada:
 * Inicio te deja en la pantalla de Amazon hasta que alguien lo apague y lo vuelva a prender.
 * Esto lo hace solo apenas termina la actualización (si el instalador del celular ya le dio el permiso).
 */
class ReceptorActualizacion : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (contexto.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        val r = contexto.contentResolver
        val servicio = "${contexto.packageName}/${InicioService::class.java.name}"
        val actuales = (Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(':').filter { it.isNotBlank() }
        // Si el sistema lo sacó de la lista al actualizar, se vuelve a poner, pero solo si estaba prendido antes
        // (lo anota el servicio al conectarse; si lo apagaste desde Ajustes, queda apagado).
        val estaba = contexto.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean("servicio_antes", false)
        if (servicio !in actuales) {
            if (!estaba) return
            try {
                Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (actuales + servicio).joinToString(":"))
                Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            } catch (_: Exception) {
            }
            return
        }
        val pendiente = goAsync()
        try {
            Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (actuales - servicio).joinToString(":"))
        } catch (e: Exception) {
            pendiente.finish()
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, actuales.joinToString(":"))
                Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            } catch (_: Exception) {
            }
            pendiente.finish()
        }, 800)
    }
}
