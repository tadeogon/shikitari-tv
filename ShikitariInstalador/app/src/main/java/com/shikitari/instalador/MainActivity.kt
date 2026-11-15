package com.shikitari.instalador

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import com.shikitari.instalador.ui.AppShikitari
import com.shikitari.instalador.ui.EstadoApp
import com.shikitari.instalador.ui.EstadoArchivos
import com.shikitari.instalador.ui.EstadoControl
import com.shikitari.instalador.ui.LocalReducido
import com.shikitari.instalador.ui.TemaShikitari
import org.conscrypt.Conscrypt
import java.io.File
import java.security.Security

/**
 * Shikitari Instalador: desde el celular, conecta con una TV por depuración inalámbrica,
 * instala Shikitari TV, Ziota o Solari, optimiza la TV, la controla y ofrece ayuda con Claude.
 *
 * Toda la interfaz está en el paquete ui/ (estilo «F»: negro, vidrio y blanco).
 * Abajo hay cuatro pestañas: TV, Control, Optimizar y Más.
 */
class MainActivity : ComponentActivity() {

    private lateinit var app: EstadoApp
    private lateinit var control: EstadoControl
    private lateinit var archivos: EstadoArchivos

    // Portada de Shikitari TV: 1) elegir la foto, 2) encuadrarla, 3) mandarla a la TV.
    private val recortar = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != RESULT_OK) return@registerForActivityResult
        val ruta = r.data?.getStringExtra(RecorteActivity.EXTRA_RUTA) ?: return@registerForActivityResult
        app.ejecutar("Mandando la foto a la TV") { app.acciones.cambiarPortada(File(ruta).readBytes()) }
    }
    private val elegirFoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) recortar.launch(Intent(this, RecorteActivity::class.java).setData(uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Conscrypt hace posible el emparejamiento seguro (TLS) con la TV.
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        app = EstadoApp(applicationContext)
        control = EstadoControl(app)
        archivos = EstadoArchivos(app)

        val reducido = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent {
            CompositionLocalProvider(LocalReducido provides reducido) {
                TemaShikitari {
                    AppShikitari(app, control, archivos, elegirPortada = { elegirFoto.launch("image/*") })
                }
            }
        }
    }

    override fun onDestroy() {
        if (::app.isInitialized) {
            app.cerrar()
            control.cerrar()
            archivos.cerrar()
        }
        super.onDestroy()
    }

    @Suppress("unused")
    private fun gamaBaja() = getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
}
