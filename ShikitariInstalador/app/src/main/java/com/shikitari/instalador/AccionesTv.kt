package com.shikitari.instalador

import android.content.Context

/**
 * Lo que la app hace en la TV. Cada función devuelve un texto para mostrar en el registro.
 * Las apps del sistema solo se quitan con "pm uninstall -k --user 0" (reversible) y se anotan por TV.
 */
class AccionesTv(private val contexto: Context, private val tv: ConexionTv) {

    private val prefs = contexto.getSharedPreferences("tvs", Context.MODE_PRIVATE)

    /** Identificador de la TV para guardar qué se le quitó (número de serie). */
    private fun idTv(): String = tv.ejecutar("getprop ro.serialno").ifBlank { "desconocida" }

    private fun instalado(paquete: String): Boolean =
        tv.ejecutar("pm list packages $paquete").lines().any { it.trim() == "package:$paquete" }

    private fun espacioLibre(): String =
        tv.ejecutar("df -h /data").lines().lastOrNull()?.trim()?.split(Regex("\\s+"))?.getOrNull(3) ?: "?"

    fun infoTv(): String {
        val modelo = tv.ejecutar("getprop ro.product.model")
        val android = tv.ejecutar("getprop ro.build.version.release")
        return "Conectado a $modelo (Android $android). Espacio libre: ${espacioLibre()}"
    }

    // ---------- Shikitari TV ----------

    fun instalarShikitari(): String {
        val apk = contexto.assets.open("shikitari-tv.apk").use { it.readBytes() }
        val resultado = tv.instalarApk(apk)
        return if (resultado.contains("Success")) "Shikitari TV instalado ✓" else "No se pudo instalar: $resultado"
    }

    fun darPermisos(): String {
        if (!instalado(PAQUETE_SHIKITARI)) return "Primero instalá Shikitari TV"
        val r1 = tv.ejecutar("pm grant $PAQUETE_SHIKITARI android.permission.WRITE_SECURE_SETTINGS")
        val r2 = tv.ejecutar("appops set $PAQUETE_SHIKITARI WRITE_SETTINGS allow")
        return if (r1.isBlank() && r2.isBlank()) "Permisos de ajustes otorgados ✓" else "Permisos: $r1 $r2".trim()
    }

    /** Prende el servicio del botón Inicio y deja el protector de los gatos a los 10 minutos. */
    fun activarInicioYProtector(): String {
        if (!instalado(PAQUETE_SHIKITARI)) return "Primero instalá Shikitari TV"
        val actuales = tv.ejecutar("settings get secure enabled_accessibility_services")
            .split(':').filter { it.isNotBlank() && it != "null" && it !in SERVICIOS_DE_LAUNCHERS }
        tv.ejecutar("settings put secure enabled_accessibility_services ${(actuales + SERVICIO_INICIO).joinToString(":")}")
        tv.ejecutar("settings put secure accessibility_enabled 1")
        tv.ejecutar("settings put secure screensaver_components $PROTECTOR")
        tv.ejecutar("settings put secure screensaver_enabled 1")
        tv.ejecutar("settings put secure screensaver_activate_on_sleep 1")
        tv.ejecutar("settings put system screen_off_timeout 600000")
        return "Botón Inicio y protector de los gatos activados ✓ (el botón Inicio funciona en Google TV)"
    }

    /** Manda una foto como portada (y protector) de Shikitari TV y la abre para que se vea al instante. */
    fun cambiarPortada(foto: ByteArray): String {
        if (!instalado(PAQUETE_SHIKITARI)) return "Primero instalá Shikitari TV"
        tv.ejecutar("mkdir -p $CARPETA_SHIKITARI")
        tv.escribirArchivo("$CARPETA_SHIKITARI/portada.jpg", foto)
        Thread.sleep(800)
        val tam = tv.ejecutar("stat -c %s $CARPETA_SHIKITARI/portada.jpg").trim().toLongOrNull() ?: 0
        if (tam != foto.size.toLong()) return "La foto no llegó completa ($tam de ${foto.size} bytes). Probá de nuevo."
        tv.ejecutar("am start -S -n $PAQUETE_SHIKITARI/.MainActivity")
        return "Portada cambiada ✓ (${foto.size / 1024} KB)"
    }

    fun portadaOriginal(): String {
        tv.ejecutar("rm -f $CARPETA_SHIKITARI/portada.jpg")
        tv.ejecutar("am start -S -n $PAQUETE_SHIKITARI/.MainActivity")
        return "Portada original (los gatos) ✓"
    }

    fun todoEnUno(): String = listOf(instalarShikitari(), darPermisos(), activarInicioYProtector()).joinToString("\n")

    // ---------- Pachi TV y Solari (el mismo launcher, con otro nombre y logo) ----------

    fun instalarModelo(m: Modelo): String {
        val apk = contexto.assets.open(m.apk).use { it.readBytes() }
        val resultado = tv.instalarApk(apk)
        return if (resultado.contains("Success")) "${m.nombre} instalado ✓" else "No se pudo instalar: $resultado"
    }

    /** Todo lo que el launcher necesita: ajustes, Bluetooth y el acceso a la música para la isla dinámica. */
    fun prepararModelo(m: Modelo): String {
        if (!instalado(m.paquete)) return "Primero instalá ${m.nombre}"
        val log = StringBuilder()
        val ajustes = tv.ejecutar("pm grant ${m.paquete} android.permission.WRITE_SECURE_SETTINGS")
        log.append(if (ajustes.isBlank()) "Permiso de ajustes ✓\n" else "Permiso de ajustes: $ajustes\n")
        // El permiso de Bluetooth solo existe desde Android 12: en TVs más viejas no hace falta.
        val versionSdk = tv.ejecutar("getprop ro.build.version.sdk").trim().toIntOrNull() ?: 0
        if (versionSdk >= 31) {
            val bt = tv.ejecutar("pm grant ${m.paquete} android.permission.BLUETOOTH_CONNECT")
            log.append(if (bt.isBlank()) "Permiso de Bluetooth ✓\n" else "Permiso de Bluetooth: $bt\n")
        }
        tv.ejecutar("cmd notification allow_listener ${m.listener}")
        val habilitado = tv.ejecutar("settings get secure enabled_notification_listeners").contains(m.listener)
        log.append(if (habilitado) "Isla dinámica lista ✓" else "No se pudo activar la isla dinámica en esta TV")
        return log.toString()
    }

    /** Hace que el botón Inicio abra este launcher (y no los otros), y usa su protector de pantalla. */
    fun usarModeloConInicio(m: Modelo): String {
        if (!instalado(m.paquete)) return "Primero instalá ${m.nombre}"
        val actuales = tv.ejecutar("settings get secure enabled_accessibility_services")
            .split(':').filter { it.isNotBlank() && it != "null" && it !in SERVICIOS_DE_LAUNCHERS }
        tv.ejecutar("settings put secure enabled_accessibility_services ${(actuales + m.servicioInicio).joinToString(":")}")
        tv.ejecutar("settings put secure accessibility_enabled 1")
        tv.ejecutar("settings put secure screensaver_components ${m.protector}")
        tv.ejecutar("settings put secure screensaver_enabled 1")
        tv.ejecutar("settings put secure screensaver_activate_on_sleep 1")
        return "Botón Inicio y protector de ${m.nombre} activados ✓ (el botón Inicio funciona en Google TV)"
    }

    fun todoEnUnoModelo(m: Modelo): String =
        listOf(instalarModelo(m), prepararModelo(m), usarModeloConInicio(m)).joinToString("\n")

    // ---------- Optimizar ----------

    fun optimizar(): String {
        val id = idTv()
        val log = StringBuilder("Espacio libre antes: ${espacioLibre()}\n")
        val quitados = leerQuitados(id)
        for (paquete in LIMPIEZA_SEGURA) {
            if (!instalado(paquete)) continue
            val r = tv.ejecutar("pm uninstall -k --user 0 $paquete")
            if (r.contains("Success")) {
                quitados.add(paquete)
                guardarQuitados(id, quitados) // se anota en el momento
                log.append("Quitado: $paquete\n")
            } else {
                log.append("No se pudo quitar $paquete: $r\n")
            }
        }
        // Guardar las animaciones originales una sola vez, antes de cambiarlas.
        if (!prefs.contains("anim_$id")) {
            val originales = ESCALAS.map { tv.ejecutar("settings get global $it").let { v -> if (v == "null" || v.isBlank()) "1.0" else v } }
            prefs.edit().putString("anim_$id", originales.joinToString(",")).apply()
        }
        ESCALAS.forEach { tv.ejecutar("settings put global $it 0.5") }
        tv.ejecutar("pm trim-caches 999G", segundos = 120)
        log.append("Caché liberada y animaciones en 0.5\n")
        log.append("Espacio libre después: ${espacioLibre()}")
        return log.toString()
    }

    fun deshacer(): String {
        val id = idTv()
        val quitados = leerQuitados(id)
        val log = StringBuilder()
        for (paquete in quitados.toList()) {
            val r = tv.ejecutar("cmd package install-existing $paquete")
            log.append("$paquete: ${if (r.contains("installed")) "restaurado" else r}\n")
            quitados.remove(paquete)
            guardarQuitados(id, quitados)
        }
        prefs.getString("anim_$id", null)?.split(',')?.let { valores ->
            ESCALAS.zip(valores).forEach { (clave, valor) -> tv.ejecutar("settings put global $clave $valor") }
            prefs.edit().remove("anim_$id").apply()
            log.append("Animaciones restauradas\n")
        }
        return log.toString().ifBlank { "No hay nada para deshacer en esta TV" }.trimEnd()
    }

    private fun leerQuitados(id: String): MutableList<String> =
        (prefs.getString("quitados_$id", "") ?: "").split(',').filter { it.isNotBlank() }.toMutableList()

    private fun guardarQuitados(id: String, lista: List<String>) {
        prefs.edit().putString("quitados_$id", lista.distinct().joinToString(",")).commit()
    }

    companion object {
        const val PAQUETE_SHIKITARI = "com.milauncher.tv"
        const val CARPETA_SHIKITARI = "/sdcard/Android/data/com.milauncher.tv/files"
        const val SERVICIO_INICIO = "com.milauncher.tv/com.milauncher.tv.InicioService"
        const val PROTECTOR = "com.milauncher.tv/com.milauncher.tv.ProtectorService"
        val PACHI = Modelo("Pachi TV", "com.pachi.tv", "pachi-tv.apk")
        val SOLARI = Modelo("Solari", "com.pachi.tv.trabajo", "solari-tv.apk")

        /** Solo un launcher puede tener el botón Inicio a la vez. */
        val SERVICIOS_DE_LAUNCHERS = listOf(SERVICIO_INICIO, PACHI.servicioInicio, SOLARI.servicioInicio)

        val ESCALAS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

        /** La limpieza segura de la Fase 1: apps de Google que no hacen falta para usar la TV. */
        val LIMPIEZA_SEGURA = listOf(
            "com.google.android.play.games",
            "com.google.android.feedback",
            "com.android.tv.feedbackconsent",
            "com.google.android.tv.remotecontrol.logging",
            "com.google.android.apps.education.cast2class",
            "com.google.android.apps.tv.netoscope",
            "com.android.printspooler",
            "com.android.dreams.basic",
            "com.google.android.marvin.talkback",
            "com.google.android.syncadapters.calendar",
            "com.android.bluetoothmidiservice",
            "com.android.cameraextensions",
            "com.google.android.hotspot2.osulogin",
        )

        /** Nunca se tocan: sin ellos la TV puede quedar inutilizable o sin control remoto. */
        val PROTEGIDOS = listOf(
            "android", "android.chromecast", "com.android.systemui", "com.android.tv.settings",
            "com.google.tv.settings.chromecast", "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher",
            "com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller", "com.google.android.webview", "com.google.android.inputmethod.latin",
            "com.google.android.tungsten.setupwraith", "com.droidlogic", "com.google.android.tv.remote.service",
            "com.google.android.tv.dfuservice", "com.google.android.bluetooth", "com.android.bluetooth",
            "com.google.android.apps.mediashell", "com.google.android.chromecast.chromecastservice",
            "com.netflix.tokenmanager", "com.android.providers", "com.google.android.providers",
            "com.google.android.networkstack", "com.android.networkstack", "com.android.shell",
            "com.google.android.ext.services", "com.google.android.ext.shared", "com.google.android.apps.tv.dreamx",
            "overlay", "resources", "adservices", "ondevicepersonalization", "federatedcompute",
            "healthconnect", "sdksandbox", "rkpdapp", "configupdater",
        )
    }
}

/** Un modelo del launcher estilo Apple TV: mismo código (clases com.pachi.tv), distinto paquete. */
class Modelo(val nombre: String, val paquete: String, val apk: String) {
    val servicioInicio get() = "$paquete/com.pachi.tv.InicioService"
    val protector get() = "$paquete/com.pachi.tv.ProtectorService"
    val listener get() = "$paquete/com.pachi.tv.MusicaListener"
}