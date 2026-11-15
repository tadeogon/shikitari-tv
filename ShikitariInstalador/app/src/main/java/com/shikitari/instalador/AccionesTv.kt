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

    private fun esFire(): Boolean =
        tv.ejecutar("getprop ro.product.manufacturer").trim().equals("Amazon", ignoreCase = true)

    /** Memoria libre de la TV en MB (lo que Android puede usar sin cerrar nada). */
    private fun memoriaLibreMb(): Long =
        tv.ejecutar("grep MemAvailable /proc/meminfo").trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull()?.div(1024) ?: -1

    private fun paquetes(comando: String): Set<String> =
        tv.ejecutar(comando).lines().map { it.trim().removePrefix("package:") }.filter { it.isNotBlank() }.toSet()

    fun optimizar(): String {
        if (esFire()) return optimizarFire()
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
        // En Fire TV, animator_duration_scale en 0.5 hace que el menú lateral se cierre sin abrir nada
        // (no se llega a Ajustes con el control), así que ahí esa queda en 1.0.
        val esFire = tv.ejecutar("getprop ro.product.manufacturer").trim().equals("Amazon", ignoreCase = true)
        ESCALAS.forEach { clave ->
            val valor = if (esFire && clave == "animator_duration_scale") "1.0" else "0.5"
            tv.ejecutar("settings put global $clave $valor")
        }
        tv.ejecutar("pm trim-caches 999G", segundos = 120)
        log.append(if (esFire) "Caché liberada y animaciones en 0.5 (la del menú queda en 1.0 por ser Fire TV)\n" else "Caché liberada y animaciones en 0.5\n")
        log.append("Espacio libre después: ${espacioLibre()}")
        return log.toString()
    }

    /**
     * Fire TV: apaga (no borra) las apps de Amazon de telemetría, publicidad, compras, promos, tutoriales y
     * restos de Android, y apaga el rastreo publicitario. Lista probada en el Fire TV Stick 4K Max con dos
     * reinicios (guías cleanyourtv y dumbtv). Alexa, Prime Video, la tienda y el control no se tocan.
     */
    private fun optimizarFire(): String {
        val id = idTv()
        val antes = memoriaLibreMb()
        val log = StringBuilder()
        val instalados = paquetes("pm list packages")
        val yaApagados = paquetes("pm list packages -d")
        val apagados = leerLista("apagados_$id")
        var nuevos = 0
        val fallas = mutableListOf<String>()
        for (paquete in LIMPIEZA_FIRE) {
            if (paquete !in instalados || paquete in yaApagados) continue
            val r = tv.ejecutar("pm disable-user --user 0 $paquete")
            if (r.contains("disabled-user")) {
                apagados.add(paquete)
                guardarLista("apagados_$id", apagados) // se anota en el momento
                nuevos++
            } else {
                fallas += "$paquete: ${r.lines().lastOrNull { it.isNotBlank() } ?: r}"
            }
        }
        log.append("Apps de Amazon apagadas ahora: $nuevos (en total: ${paquetes("pm list packages -d").size})\n")
        fallas.forEach { log.append("No se pudo apagar $it\n") }

        // Privacidad: se guarda el valor original una sola vez, para poder volver atrás.
        if (!prefs.contains("privacidad_$id")) {
            val originales = AJUSTES_FIRE.joinToString("\n") { (ns, clave, _) -> "$ns $clave ${tv.ejecutar("settings get $ns $clave").trim()}" }
            prefs.edit().putString("privacidad_$id", originales).commit()
        }
        AJUSTES_FIRE.forEach { (ns, clave, valor) -> tv.ejecutar("settings put $ns $clave $valor") }
        log.append("Rastreo publicitario y videos automáticos del inicio apagados ✓\n")

        if (!prefs.contains("anim_$id")) {
            val originales = ESCALAS.map { tv.ejecutar("settings get global $it").let { v -> if (v == "null" || v.isBlank()) "1.0" else v } }
            prefs.edit().putString("anim_$id", originales.joinToString(",")).apply()
        }
        // animator_duration_scale queda en 1.0: en 0.5 el menú lateral de Amazon se cierra sin abrir nada.
        ESCALAS.forEach { clave -> tv.ejecutar("settings put global $clave ${if (clave == "animator_duration_scale") "1.0" else "0.5"}") }
        tv.ejecutar("pm trim-caches 999G", segundos = 120)
        log.append("Animaciones más rápidas (la del menú queda normal) y caché liberada ✓\n")
        log.append("Memoria libre: $antes MB → ${memoriaLibreMb()} MB\n")
        log.append("Conviene reiniciar el Fire TV una vez para que se note del todo.")
        return log.toString()
    }

    /** Cierra las apps que quedaron abiertas en segundo plano (no los launchers ni el teclado). */
    fun cerrarApps(): String {
        val antes = memoriaLibreMb()
        val mantener = setOf(PAQUETE_SHIKITARI, PACHI.paquete, SOLARI.paquete, contexto.packageName)
        val cerradas = mutableListOf<String>()
        for (paquete in paquetes("pm list packages -3")) {
            if (paquete in mantener) continue
            if (tv.ejecutar("pidof $paquete").isBlank()) continue // no estaba abierta
            tv.ejecutar("am force-stop $paquete")
            cerradas += ControlActivity.NOMBRES[paquete] ?: paquete.substringAfterLast('.')
        }
        tv.ejecutar("am kill-all") // procesos de fondo que el sistema puede volver a abrir solo si hacen falta
        Thread.sleep(1500)
        val texto = if (cerradas.isEmpty()) "No había apps tuyas abiertas" else "Cerradas: ${cerradas.joinToString(", ")}"
        return "$texto ✓\nMemoria libre: $antes MB → ${memoriaLibreMb()} MB"
    }

    /** Si el inicio de Amazon quedó en negro después de una limpieza, prende lo que lo dibuja (guía de la comunidad). */
    fun emergenciaFire(): String {
        if (!esFire()) return "Esto es solo para Fire TV."
        val id = idTv()
        val apagados = leerLista("apagados_$id")
        val log = StringBuilder()
        for (paquete in EMERGENCIA_FIRE) {
            val r = tv.ejecutar("pm enable $paquete")
            log.append("$paquete: ${if (r.contains("enabled")) "prendida" else r}\n")
            apagados.remove(paquete)
        }
        guardarLista("apagados_$id", apagados)
        tv.ejecutar("am start -n com.amazon.tv.launcher/.ui.HomeActivity_vNext")
        log.append("Listo: el inicio de Amazon debería volver a verse. Si no, usá Deshacer optimización.")
        return log.toString()
    }

    fun deshacer(): String {
        val id = idTv()
        val quitados = leerQuitados(id)
        val log = StringBuilder()
        // Fire TV: volver a prender lo apagado y dejar la privacidad como estaba.
        val apagados = leerLista("apagados_$id")
        if (apagados.isNotEmpty()) {
            var prendidas = 0
            for (paquete in apagados.toList()) {
                if (tv.ejecutar("pm enable $paquete").contains("enabled")) prendidas++
                apagados.remove(paquete)
                guardarLista("apagados_$id", apagados)
            }
            log.append("Apps de Amazon prendidas de nuevo: $prendidas\n")
        }
        prefs.getString("privacidad_$id", null)?.let { texto ->
            texto.lines().forEach { linea ->
                val partes = linea.split(' ', limit = 3)
                if (partes.size < 3) return@forEach
                val (ns, clave, valor) = partes
                if (valor == "null" || valor.isBlank()) tv.ejecutar("settings delete $ns $clave") else tv.ejecutar("settings put $ns $clave $valor")
            }
            prefs.edit().remove("privacidad_$id").apply()
            log.append("Ajustes de privacidad como estaban\n")
        }
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

    private fun leerLista(clave: String): MutableList<String> =
        (prefs.getString(clave, "") ?: "").split(',').filter { it.isNotBlank() }.toMutableList()

    private fun guardarLista(clave: String, lista: List<String>) {
        prefs.edit().putString(clave, lista.distinct().joinToString(",")).commit()
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

        /**
         * Fire TV: se APAGAN (pm disable-user), nunca se borran. Probada en el Fire TV Stick 4K Max (AFTKRT,
         * Fire OS 8) con dos reinicios. Fuera a propósito: Alexa, Prime Video, la tienda, el teclado, el control,
         * las actualizaciones, com.amazon.imp (sin él el inicio de Amazon queda en negro), dialservice ("Enviar a
         * la TV" de YouTube), y franktvinput/tifobserver (en los televisores con Fire TV manejan las entradas HDMI).
         */
        val LIMPIEZA_FIRE = listOf(
            // Telemetría, métricas y rastreo
            "com.amazon.client.metrics.api", "com.amazon.device.telemetry.emitter", "com.amazon.wirelessmetrics.service",
            "com.amazon.perfcollection", "com.amazon.perfc", "com.amazon.dp.logger", "com.amazon.ceviche",
            "com.amazon.tv.developer.dataservice", "com.amazon.livedeviceservice", "com.amazon.privacypassservice",
            "com.amazon.wifilocker", "com.amazon.tv.acr", "com.amazon.ftvspatial", "com.amazon.neo.minerva",
            "com.amazon.neo.minerva.logcat",
            // Publicidad, recomendaciones, promos y compras
            "com.amazon.kso.blackbird", "com.amazon.sneakpeek", "com.amazon.hybridadidservice", "com.amazon.media.recommendations",
            "com.amazon.providers.contentsupport", "com.amazon.tv.easyupgrade", "com.amazon.shoptv.client",
            "com.amazon.shoptv.firetv.client", "com.amazon.hedwig", "com.amazon.systemnotices", "com.amazon.uxnotification",
            "com.amazon.tv.releasenotes", "com.amazon.tv.earlyaccess", "com.amazon.gamehub",
            // Transmitir con apps de Amazon (YouTube sigue andando)
            "com.amazon.awvflingreceiver", "com.amazon.cast.sink", "com.amazon.whisperplay.service.install",
            "com.amazon.whisperjoin.middleware.v2.np",
            // Casa inteligente / Matter
            "com.amazon.tv.matter", "com.amazon.tv.mattercohost", "com.amazon.smarthomemapviewapp",
            // Soporte y servicios de fondo
            "com.amazon.tv.support", "com.amazon.firetv.troubleshooting", "com.amazon.tv.ottssocompanionapp",
            "com.amazon.device_remote_config_sync_service", "com.amazon.ods.kindleconnect", "com.amazon.prism.android.service",
            "com.amazon.sharingservice.android.client.proxy", "com.amazon.tv.ffsprovisioneeclient", "com.amazon.minitv.android.app",
            "com.amazon.tv.website_launcher",
            // Tutoriales, avisos legales y apps vacías
            "com.amazon.tv.legal.notices", "com.amazon.tv.legalresources", "com.amazon.storm.lightning.tutorial",
            "com.amazon.tmm.tutorial", "com.amazon.dummy.alarmclock", "com.amazon.dummy.calendar", "com.amazon.dummy.contacts",
            "com.amazon.dummy.gallery", "com.amazon.dummy.music", "com.amazon.dummy.settings", "com.amazon.dummy.soundpicker",
            // Restos de Android
            "com.android.cts.ctsshim", "com.android.cts.priv.ctsshim", "com.android.protips", "com.android.dreams.basic",
            "com.android.printservice.recommendation", "com.android.bookmarkprovider", "com.android.calllogbackup",
            "com.android.smspush", "com.android.mms.service", "com.android.hotspot2.osulogin", "com.android.traceur",
            "com.android.onetimeinitializer", "com.android.carrierconfig", "com.android.safetyregulatoryinfo",
            "com.android.htmlviewer",
        )

        /** Privacidad en Fire TV: rastreo publicitario, métricas de uso, avisos emergentes y videos automáticos del inicio. */
        val AJUSTES_FIRE = listOf(
            Triple("secure", "amz_limit_ad_tracking", "1"),
            Triple("secure", "limit_ad_tracking", "1"),
            Triple("secure", "non_amz_limit_ad_tracking", "1"),
            Triple("secure", "USAGE_METRICS_UPLOAD_ENABLED", "0"),
            Triple("secure", "usage_metrics_marketing_enabled", "0"),
            Triple("global", "heads_up_notifications_enabled", "0"),
            Triple("global", "com.amazon.tv.settings.featured_item_video_enabled", "0"),
            Triple("global", "com.amazon.tv.settings.featured_item_video_volume", "0.0"),
        )

        /** Lo que hay que prender si el inicio de Amazon queda en negro (según la guía de la comunidad). */
        val EMERGENCIA_FIRE = listOf("com.amazon.imp", "com.amazon.shoptv.client", "com.amazon.media.recommendations")

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