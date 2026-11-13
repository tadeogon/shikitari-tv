package com.milauncher.tv

import android.app.Notification
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Solo para Fire TV, donde el sistema no deja recibir el botón Home ni usar el servicio de accesibilidad:
 * mira cada poco qué app pasó a primer plano y, si es la pantalla de Amazon, abre Shikitari TV encima.
 * Necesita el permiso de uso de apps (appops GET_USAGE_STATS, otorgado por ADB). No reemplaza el launcher del sistema.
 * Se apaga desde Ajustes > "Abrir Shikitari TV con Inicio".
 */
class BotonInicioService : Service() {

    private val espera = Handler(Looper.getMainLooper())
    private var ultimaRevision = 0L

    private val revisar = object : Runnable {
        override fun run() {
            if (ultimaAppEnPantalla() == LAUNCHER_AMAZON) abrirLauncher(this@BotonInicioService)
            espera.postDelayed(this, INTERVALO_MS)
        }
    }

    /** Última app que pasó a primer plano desde la revisión anterior, o null si no cambió nada. */
    private fun ultimaAppEnPantalla(): String? {
        val uso = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ahora = System.currentTimeMillis()
        val eventos = uso.queryEvents(ultimaRevision, ahora)
        ultimaRevision = ahora
        val evento = UsageEvents.Event()
        var ultima: String? = null
        while (eventos.hasNextEvent()) {
            eventos.getNextEvent(evento)
            if (evento.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) ultima = evento.packageName
        }
        return ultima
    }

    override fun onCreate() {
        super.onCreate()
        ultimaRevision = System.currentTimeMillis()
        // En primer plano, para que Fire OS no lo cierre cuando falta memoria.
        @Suppress("DEPRECATION")
        startForeground(1, Notification.Builder(this)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.app_name))
            .build())
        espera.post(revisar)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        espera.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val LAUNCHER_AMAZON = "com.amazon.tv.launcher"
        private const val INTERVALO_MS = 500L

        fun esFireTv(contexto: Context) = try {
            contexto.packageManager.getPackageInfo(LAUNCHER_AMAZON, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        fun activado(contexto: Context) =
            contexto.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean("boton_inicio", true)

        fun abrirLauncher(contexto: Context) {
            contexto.startActivity(
                Intent(contexto, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(MainActivity.EXTRA_DESDE_HOME, true),
            )
        }

        /** Arranca o apaga el servicio según el ajuste; devuelve true si quedó corriendo en un Fire TV. */
        fun iniciarSiCorresponde(contexto: Context): Boolean {
            val intent = Intent(contexto, BotonInicioService::class.java)
            if (!esFireTv(contexto)) return false
            if (!activado(contexto)) {
                contexto.stopService(intent)
                return false
            }
            contexto.startService(intent)
            return true
        }
    }
}

/** Al prender el Fire TV, arranca el servicio y abre Shikitari TV. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (BotonInicioService.iniciarSiCorresponde(contexto)) BotonInicioService.abrirLauncher(contexto)
    }
}
