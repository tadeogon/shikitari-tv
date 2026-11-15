package com.shikitari.instalador.ui

import android.app.ActivityManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider

/** Pantalla Optimizar con la interfaz «F». Se entra desde Optimizar del menú principal y se sale con Atrás. */
class OptimizarActivity : ComponentActivity() {

    private var scriptPendiente: String? = null

    private val guardarScript = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val texto = scriptPendiente
        scriptPendiente = null
        if (uri == null || texto == null) return@registerForActivityResult
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(texto.toByteArray()) } ?: error("sin archivo")
        }.isSuccess
        Toast.makeText(this, if (ok) "Script guardado" else "No se pudo guardar el script", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // TODO(motor): hoy no hay un motor que arme el plan leyendo la TV y apague paquete por paquete
        // (AccionesTv.optimizar() apaga su lista entera de una vez). Cuando exista, se reemplaza esta línea
        // por un ui/AdaptadorPlan.kt que implemente ProveedorPlan.
        val proveedor: ProveedorPlan = ProveedorPlanDeEjemplo()

        val reducido = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val gamaBaja = getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        // Desenfoque real de lo de atrás solo cuando la hoja está abierta, y solo si el teléfono lo banca.
        val desenfoque = Build.VERSION.SDK_INT >= 31 && !gamaBaja

        setContent {
            CompositionLocalProvider(LocalReducido provides reducido) {
                TemaShikitari {
                    PantallaOptimizar(
                        proveedor = proveedor,
                        desenfoque = desenfoque,
                        alSalir = { finish() },
                        alGuardarScript = { texto ->
                            scriptPendiente = texto
                            guardarScript.launch("shikitari-optimizar.txt")
                        },
                    )
                }
            }
        }
    }
}
