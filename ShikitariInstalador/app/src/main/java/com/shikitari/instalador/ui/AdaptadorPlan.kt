package com.shikitari.instalador.ui

import android.content.Context
import com.shikitari.instalador.AccionesTv
import com.shikitari.instalador.Bitacora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * El motor de verdad de la pantalla Optimizar: lee qué apps tiene la TV conectada, arma los grupos
 * con lo que conviene apagar y apaga SOLO lo que la persona dejó elegido.
 *
 * - Las listas salen de AccionesTv (LIMPIEZA_FIRE, LIMPIEZA_SEGURA, PROTEGIDOS); AccionesTv no se modifica.
 * - Se apaga con «pm disable-user --user 0» (si la TV no lo deja, «pm uninstall -k --user 0»). Nada se borra.
 * - Lo apagado se anota por TV en el mismo lugar que usa AccionesTv («apagados_<serie>» / «quitados_<serie>»),
 *   así «Deshacer optimización» de la pestaña también lo vuelve atrás.
 */
class AdaptadorPlan(private val app: EstadoApp) : ProveedorPlan {

    private val tv get() = app.tv
    private val prefs = app.contexto.getSharedPreferences("tvs", Context.MODE_PRIVATE)

    private var fire = false
    private var id = "desconocida"
    /** paquete → (grupo, nombre), en el orden en que se muestran. Solo esto se puede apagar. */
    private var permitidos: Map<String, Pair<String, String>> = emptyMap()
    private var ultimoPlan: PlanUi? = null

    private fun paquetes(comando: String): Set<String> =
        tv.ejecutar(comando).lines().map { it.trim().removePrefix("package:") }.filter { it.isNotBlank() }.toSet()

    private fun protegido(p: String) = AccionesTv.PROTEGIDOS.any { p == it || p.startsWith("$it.") }

    override suspend fun analizar(): Resultado<PlanUi> = withContext(Dispatchers.IO) {
        if (app.conectada == null) return@withContext Resultado.Error("Primero conectá una TV en la pestaña TV.")
        try {
            id = tv.ejecutar("getprop ro.serialno").trim().ifBlank { "desconocida" }
            fire = tv.ejecutar("getprop ro.product.manufacturer").trim().equals("Amazon", ignoreCase = true)
            val modelo = tv.ejecutar("getprop ro.product.model").trim()
            val sistema = if (fire) {
                tv.ejecutar("getprop ro.build.version.name").trim().substringBefore(" (").ifBlank { "Fire OS" }
            } else {
                "Android " + tv.ejecutar("getprop ro.build.version.release").trim()
            }
            val todas = paquetes("pm list packages")
            val prendidas = paquetes("pm list packages -e")

            val candidatos = if (fire) AccionesTv.LIMPIEZA_FIRE else AccionesTv.LIMPIEZA_SEGURA
            val definicion = if (fire) GRUPOS_FIRE else GRUPOS_GOOGLE
            val usados = mutableSetOf<String>()
            val grupos = definicion.mapNotNull { g ->
                val entradas = g.paquetes
                    .filter { it in candidatos && it in prendidas && !protegido(it) && usados.add(it) }
                    .map { EntradaUi(nombreDe(it), it) }
                if (entradas.isEmpty()) null else GrupoUi(g.titulo, entradas, g.recomendado)
            }.toMutableList()
            // Por si la lista de AccionesTv suma paquetes que acá no tienen grupo.
            val sueltas = candidatos.filter { it in prendidas && !protegido(it) && it !in usados }
            if (sueltas.isNotEmpty()) grupos += GrupoUi("Otras", sueltas.map { EntradaUi(nombreDe(it), it) }, recomendado = true)

            val seQuedan = (if (fire) QUEDAN_FIRE else QUEDAN_GOOGLE)
                .filter { (p, _, _) -> p in todas && (p != PAQUETE_VOZ_ALEXA || p in prendidas) }
                .map { (p, nombre, motivo) -> EntradaUi(nombre, p, motivo) }

            permitidos = grupos.flatMap { g -> g.entradas.map { it.paquete to (g.titulo to it.nombre) } }.toMap() +
                seQuedan.filter { it.paquete == PAQUETE_VOZ_ALEXA }.associate { it.paquete to ("Voz" to it.nombre) }

            // Lo que ya está apagado (de esta app o de antes), para verlo y poder prender una sola.
            val grupoDe = definicion.flatMap { g -> g.paquetes.map { it to g.titulo } }.toMap()
            val yaApagadas = (candidatos + PAQUETE_VOZ_ALEXA).distinct()
                .filter { (it in todas && it !in prendidas) || (it !in todas && it in leer("quitados_$id")) }
                .map { EntradaUi(nombreDe(it), it, grupoDe[it] ?: if (it == PAQUETE_VOZ_ALEXA) "Voz" else "Otras") }

            val plan = PlanUi(
                modelo = "$modelo · $sistema",
                totalApps = todas.size,
                grupos = grupos,
                seQuedan = seQuedan,
                perfilConocido = true,
                esEjemplo = false,
                yaApagadas = yaApagadas,
            )
            ultimoPlan = plan
            Resultado.Ok(plan)
        } catch (e: Exception) {
            Resultado.Error(app.explicarError(e))
        }
    }

    override suspend fun ejecutar(
        apagar: Set<String>,
        opciones: OpcionesUi,
        alPaso: (hechos: Int, total: Int, narracion: String) -> Unit,
    ): Resultado<Resumen> = withContext(Dispatchers.IO) {
        try {
            // Solo lo que vino en el plan (nunca un paquete suelto) y nunca uno protegido.
            val lista = permitidos.keys.filter { it in apagar }
            val protegidas = apagar.count { protegido(it) }
            val extras = (if (fire) 1 else 0) + (if (opciones.animacionesRapidas) 1 else 0) + 1
            val total = lista.size + extras
            val avisos = mutableListOf<String>()
            val log = StringBuilder()
            val apagados = leer("apagados_$id")
            val quitados = leer("quitados_$id")
            var hechos = 0
            var ok = 0

            alPaso(0, total, "Revisando la TV…")
            for (p in lista) {
                coroutineContext.ensureActive() // «Parar» corta acá, entre una app y la otra
                val (grupo, nombre) = permitidos.getValue(p)
                var r = tv.ejecutar("pm disable-user --user 0 $p 2>&1", segundos = 20)
                if (r.contains("disabled-user")) {
                    apagados += p
                    guardar("apagados_$id", apagados) // se anota en el momento, por si se corta
                    ok++
                    log.append("Apagada: $nombre ($p)\n")
                } else {
                    r = tv.ejecutar("pm uninstall -k --user 0 $p 2>&1", segundos = 20)
                    if (r.contains("Success")) {
                        quitados += p
                        guardar("quitados_$id", quitados)
                        ok++
                        log.append("Quitada para tu usuario: $nombre ($p)\n")
                    } else {
                        avisos += "No se pudo apagar $nombre"
                        log.append("No se pudo: $nombre ($p): ${r.lines().lastOrNull { it.isNotBlank() } ?: r}\n")
                    }
                }
                hechos++
                alPaso(hechos, total, "Apagando ${grupo.lowercase()}: $nombre")
            }

            coroutineContext.ensureActive()
            if (fire) {
                // Privacidad: se guarda cómo estaba una sola vez, para poder volver atrás.
                if (!prefs.contains("privacidad_$id")) {
                    val originales = AccionesTv.AJUSTES_FIRE.joinToString("\n") { (ns, clave, _) ->
                        "$ns $clave ${tv.ejecutar("settings get $ns $clave").trim()}"
                    }
                    prefs.edit().putString("privacidad_$id", originales).commit()
                }
                AccionesTv.AJUSTES_FIRE.forEach { (ns, clave, valor) -> tv.ejecutar("settings put $ns $clave $valor") }
                hechos++
                alPaso(hechos, total, "Apagando el rastreo publicitario")
                avisos += "Rastreo publicitario y videos automáticos apagados"
            }
            if (opciones.animacionesRapidas) {
                if (!prefs.contains("anim_$id")) {
                    val originales = AccionesTv.ESCALAS.map {
                        tv.ejecutar("settings get global $it").trim().let { v -> if (v == "null" || v.isBlank()) "1.0" else v }
                    }
                    prefs.edit().putString("anim_$id", originales.joinToString(",")).apply()
                }
                // En Fire TV la del menú queda en 1.0: en 0.5 el menú lateral de Amazon se cierra solo.
                AccionesTv.ESCALAS.forEach { clave ->
                    tv.ejecutar("settings put global $clave ${if (fire && clave == "animator_duration_scale") "1.0" else "0.5"}")
                }
                hechos++
                alPaso(hechos, total, "Animaciones a 0,5")
                avisos += "Animaciones más rápidas"
            }
            tv.ejecutar("pm trim-caches 999G", segundos = 120)
            hechos++
            alPaso(hechos, total, "Liberando caché")

            // Verificación: el inicio tiene que seguir prendido.
            val prendidas = paquetes("pm list packages -e")
            val inicio = if (fire) listOf("com.amazon.tv.launcher", "com.amazon.imp") else emptyList()
            if (inicio.all { it in prendidas }) {
                avisos.add(0, "Guardado para deshacer cuando quieras")
                if (fire) avisos += "Inicio de Amazon verificado: sigue prendido"
            } else {
                avisos += "Ojo: el inicio de Amazon quedó apagado. Usá «Si algo sale mal»."
            }

            Bitacora.anotar(app.contexto, "▸ Optimizar (elegido a mano)\n$log")
            val seQuedan = ultimoPlan?.seQuedan?.count { it.paquete !in apagar } ?: 0
            Resultado.Ok(Resumen(apagadas = ok, protegidas = protegidas, seQuedan = seQuedan, avisos = avisos))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Resultado.Error(app.explicarError(e))
        }
    }

    override suspend fun deshacerTodo(): Resultado<Unit> = withContext(Dispatchers.IO) {
        try {
            val r = app.acciones.deshacer()
            Bitacora.anotar(app.contexto, "▸ Deshacer optimización\n$r")
            Resultado.Ok(Unit)
        } catch (e: Exception) {
            Resultado.Error(app.explicarError(e))
        }
    }

    override suspend fun prender(paquete: String): Resultado<Unit> = withContext(Dispatchers.IO) {
        try {
            val r = tv.ejecutar("pm enable $paquete 2>&1")
            if (!r.contains("enabled")) tv.ejecutar("cmd package install-existing $paquete 2>&1")
            guardar("apagados_$id", leer("apagados_$id") - paquete)
            guardar("quitados_$id", leer("quitados_$id") - paquete)
            Resultado.Ok(Unit)
        } catch (e: Exception) {
            Resultado.Error(app.explicarError(e))
        }
    }

    private fun leer(clave: String): MutableList<String> =
        (prefs.getString(clave, "") ?: "").split(',').filter { it.isNotBlank() }.toMutableList()

    private fun guardar(clave: String, lista: List<String>) {
        prefs.edit().putString(clave, lista.distinct().joinToString(",")).commit()
    }

    private class Grupo(val titulo: String, val recomendado: Boolean, val paquetes: List<String>)

    private companion object {
        // Los mismos grupos que tiene la lista de AccionesTv en sus comentarios.
        val GRUPOS_FIRE = listOf(
            Grupo("Rastreo", true, listOf(
                "com.amazon.client.metrics.api", "com.amazon.device.telemetry.emitter", "com.amazon.wirelessmetrics.service",
                "com.amazon.perfcollection", "com.amazon.perfc", "com.amazon.dp.logger", "com.amazon.ceviche",
                "com.amazon.tv.developer.dataservice", "com.amazon.livedeviceservice", "com.amazon.privacypassservice",
                "com.amazon.wifilocker", "com.amazon.tv.acr", "com.amazon.ftvspatial", "com.amazon.neo.minerva",
                "com.amazon.neo.minerva.logcat",
            )),
            Grupo("Publicidad y promos", true, listOf(
                "com.amazon.kso.blackbird", "com.amazon.sneakpeek", "com.amazon.hybridadidservice", "com.amazon.media.recommendations",
                "com.amazon.providers.contentsupport", "com.amazon.tv.easyupgrade", "com.amazon.shoptv.client",
                "com.amazon.shoptv.firetv.client", "com.amazon.hedwig", "com.amazon.systemnotices", "com.amazon.uxnotification",
                "com.amazon.tv.releasenotes", "com.amazon.tv.earlyaccess", "com.amazon.gamehub",
            )),
            Grupo("Casting", true, listOf(
                "com.amazon.awvflingreceiver", "com.amazon.cast.sink", "com.amazon.whisperplay.service.install",
                "com.amazon.whisperjoin.middleware.v2.np",
            )),
            Grupo("Casa inteligente", false, listOf(
                "com.amazon.tv.matter", "com.amazon.tv.mattercohost", "com.amazon.smarthomemapviewapp",
            )),
            Grupo("Servicios de fondo", true, listOf(
                "com.amazon.tv.support", "com.amazon.firetv.troubleshooting", "com.amazon.tv.ottssocompanionapp",
                "com.amazon.device_remote_config_sync_service", "com.amazon.ods.kindleconnect", "com.amazon.prism.android.service",
                "com.amazon.sharingservice.android.client.proxy", "com.amazon.tv.ffsprovisioneeclient", "com.amazon.minitv.android.app",
                "com.amazon.tv.website_launcher",
            )),
            Grupo("Tutoriales y apps vacías", true, listOf(
                "com.amazon.tv.legal.notices", "com.amazon.tv.legalresources", "com.amazon.storm.lightning.tutorial",
                "com.amazon.tmm.tutorial", "com.amazon.dummy.alarmclock", "com.amazon.dummy.calendar", "com.amazon.dummy.contacts",
                "com.amazon.dummy.gallery", "com.amazon.dummy.music", "com.amazon.dummy.settings", "com.amazon.dummy.soundpicker",
            )),
            Grupo("Restos de Android", true, listOf(
                "com.android.cts.ctsshim", "com.android.cts.priv.ctsshim", "com.android.protips", "com.android.dreams.basic",
                "com.android.printservice.recommendation", "com.android.bookmarkprovider", "com.android.calllogbackup",
                "com.android.smspush", "com.android.mms.service", "com.android.hotspot2.osulogin", "com.android.traceur",
                "com.android.onetimeinitializer", "com.android.carrierconfig", "com.android.safetyregulatoryinfo",
                "com.android.htmlviewer",
            )),
        )

        val GRUPOS_GOOGLE = listOf(
            Grupo("Apps de Google que no hacen falta", true, listOf(
                "com.google.android.play.games", "com.google.android.feedback", "com.android.tv.feedbackconsent",
                "com.google.android.tv.remotecontrol.logging", "com.google.android.apps.education.cast2class",
                "com.google.android.apps.tv.netoscope",
            )),
            Grupo("Servicios que una TV no usa", true, listOf(
                "com.android.printspooler", "com.android.dreams.basic", "com.google.android.syncadapters.calendar",
                "com.android.bluetoothmidiservice", "com.android.cameraextensions", "com.google.android.hotspot2.osulogin",
            )),
            Grupo("Accesibilidad", false, listOf("com.google.android.marvin.talkback")),
        )

        val QUEDAN_FIRE = listOf(
            Triple(PAQUETE_VOZ_ALEXA, "Alexa", "La voz del control (se puede apagar en Personalizar)"),
            Triple("com.amazon.tv.launcher", "Inicio de Amazon", "Sin esto la TV no tiene pantalla de inicio"),
            Triple("com.amazon.imp", "Dibujo del inicio", "Sin esto el inicio de Amazon queda en negro"),
            Triple("com.amazon.avod", "Prime Video", "La usás para ver"),
            Triple("com.amazon.venezia", "Appstore", "Para instalar y actualizar apps"),
            Triple("com.amazon.dialservice", "Enviar a la TV", "YouTube lo usa para mandar videos desde el celular"),
            Triple("com.amazon.franktvinput", "Entradas HDMI", "Las TVs con Fire TV lo usan para el HDMI"),
            Triple("com.amazon.device.software.ota", "Actualizaciones", "Mantiene la TV al día"),
        )

        val QUEDAN_GOOGLE = listOf(
            Triple("com.google.android.apps.tv.launcherx", "Inicio de Google TV", "Sin esto la TV no tiene pantalla de inicio"),
            Triple("com.google.android.tvlauncher", "Inicio de Android TV", "Sin esto la TV no tiene pantalla de inicio"),
            Triple("com.google.android.gms", "Servicios de Google", "Lo usan casi todas las apps"),
            Triple("com.android.vending", "Play Store", "Para instalar y actualizar apps"),
            Triple("com.google.android.apps.mediashell", "Chromecast incorporado", "Para mandar videos desde el celular"),
            Triple("com.google.android.tv.remote.service", "Control remoto", "Sin esto el control no anda"),
        )

        /** Nombres entendibles para los paquetes más raros; el resto se arma a partir del paquete. */
        val NOMBRES = mapOf(
            "com.amazon.client.metrics.api" to "Métricas de Amazon",
            "com.amazon.device.telemetry.emitter" to "Telemetría",
            "com.amazon.wirelessmetrics.service" to "Métricas del Wi-Fi",
            "com.amazon.perfcollection" to "Medición de rendimiento",
            "com.amazon.perfc" to "Contador de rendimiento",
            "com.amazon.dp.logger" to "Registro de uso",
            "com.amazon.ceviche" to "Seguimiento de apps",
            "com.amazon.tv.developer.dataservice" to "Datos para desarrolladores",
            "com.amazon.livedeviceservice" to "Estado en vivo del equipo",
            "com.amazon.privacypassservice" to "Privacy Pass",
            "com.amazon.wifilocker" to "Copia de claves Wi-Fi",
            "com.amazon.tv.acr" to "Reconocimiento de lo que mirás",
            "com.amazon.ftvspatial" to "Datos de ubicación",
            "com.amazon.neo.minerva" to "Minerva (datos)",
            "com.amazon.neo.minerva.logcat" to "Minerva (registros)",
            "com.amazon.kso.blackbird" to "Anuncios del inicio",
            "com.amazon.sneakpeek" to "Avances automáticos",
            "com.amazon.hybridadidservice" to "ID de publicidad",
            "com.amazon.media.recommendations" to "Recomendaciones",
            "com.amazon.providers.contentsupport" to "Contenido sugerido",
            "com.amazon.tv.easyupgrade" to "Ofertas de mejora",
            "com.amazon.shoptv.client" to "Compras en la TV",
            "com.amazon.shoptv.firetv.client" to "Tienda de Amazon",
            "com.amazon.hedwig" to "Notificaciones push",
            "com.amazon.systemnotices" to "Avisos del sistema",
            "com.amazon.uxnotification" to "Avisos emergentes",
            "com.amazon.tv.releasenotes" to "Novedades",
            "com.amazon.tv.earlyaccess" to "Acceso anticipado",
            "com.amazon.gamehub" to "Juegos de Amazon",
            "com.amazon.awvflingreceiver" to "Recibir de apps de Amazon",
            "com.amazon.cast.sink" to "Duplicar pantalla",
            "com.amazon.whisperplay.service.install" to "Whisperplay",
            "com.amazon.whisperjoin.middleware.v2.np" to "Configuración rápida",
            "com.amazon.tv.matter" to "Matter",
            "com.amazon.tv.mattercohost" to "Matter (puente)",
            "com.amazon.smarthomemapviewapp" to "Mapa de la casa",
            "com.amazon.tv.support" to "Ayuda de Amazon",
            "com.amazon.firetv.troubleshooting" to "Solución de problemas",
            "com.amazon.tv.ottssocompanionapp" to "Inicio de sesión de apps",
            "com.amazon.device_remote_config_sync_service" to "Configuración remota",
            "com.amazon.ods.kindleconnect" to "Kindle",
            "com.amazon.prism.android.service" to "Prism",
            "com.amazon.sharingservice.android.client.proxy" to "Compartir",
            "com.amazon.tv.ffsprovisioneeclient" to "Configurar otros equipos",
            "com.amazon.minitv.android.app" to "miniTV",
            "com.amazon.tv.website_launcher" to "Abrir sitios web",
            "com.amazon.tv.legal.notices" to "Avisos legales",
            "com.amazon.tv.legalresources" to "Textos legales",
            "com.amazon.storm.lightning.tutorial" to "Tutorial del control",
            "com.amazon.tmm.tutorial" to "Tutorial de inicio",
            "com.android.cts.ctsshim" to "CTS (pruebas)",
            "com.android.cts.priv.ctsshim" to "CTS privado (pruebas)",
            "com.android.protips" to "Consejos",
            "com.android.dreams.basic" to "Protector básico",
            "com.android.printservice.recommendation" to "Impresoras sugeridas",
            "com.android.printspooler" to "Impresión",
            "com.android.bookmarkprovider" to "Favoritos del navegador",
            "com.android.calllogbackup" to "Copia de llamadas",
            "com.android.smspush" to "Mensajes SMS",
            "com.android.mms.service" to "Mensajes MMS",
            "com.android.hotspot2.osulogin" to "Login de Wi-Fi público",
            "com.google.android.hotspot2.osulogin" to "Login de Wi-Fi público",
            "com.android.traceur" to "Rastreo del sistema",
            "com.android.onetimeinitializer" to "Arranque inicial",
            "com.android.carrierconfig" to "Ajustes de operador",
            "com.android.safetyregulatoryinfo" to "Info regulatoria",
            "com.android.htmlviewer" to "Visor HTML",
            "com.google.android.play.games" to "Play Juegos",
            "com.google.android.feedback" to "Enviar comentarios",
            "com.android.tv.feedbackconsent" to "Permiso de comentarios",
            "com.google.android.tv.remotecontrol.logging" to "Registro del control",
            "com.google.android.apps.education.cast2class" to "Cast para clases",
            "com.google.android.apps.tv.netoscope" to "Diagnóstico de red",
            "com.google.android.marvin.talkback" to "TalkBack (lector de pantalla)",
            "com.google.android.syncadapters.calendar" to "Sincronizar calendario",
            "com.android.bluetoothmidiservice" to "MIDI por Bluetooth",
            "com.android.cameraextensions" to "Extensiones de cámara",
        )

        fun nombreDe(p: String): String = NOMBRES[p] ?: when {
            p.startsWith("com.amazon.dummy.") -> "App vacía (${p.substringAfterLast('.')})"
            else -> p.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }
}
