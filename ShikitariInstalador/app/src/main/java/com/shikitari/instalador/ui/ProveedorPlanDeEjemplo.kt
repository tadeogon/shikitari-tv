package com.shikitari.instalador.ui

import kotlinx.coroutines.delay

/**
 * Datos de muestra para ver y probar la pantalla sin tocar ninguna TV.
 * Los 35 paquetes y los 8 que se quedan son los del prototipo (Prototipo-Shikitari.html).
 *
 * TODO(motor): cuando exista un motor que arme el plan leyendo la TV y apague paquete por paquete,
 * se enchufa en OptimizarActivity con un ui/AdaptadorPlan.kt que implemente ProveedorPlan.
 * Hoy AccionesTv.optimizar() apaga su lista entera de una vez y no recibe una selección, así que no encaja.
 */
class ProveedorPlanDeEjemplo : ProveedorPlan {

    private val apagadas = linkedSetOf<String>()

    override suspend fun analizar(): Resultado<PlanUi> {
        delay(250)
        return Resultado.Ok(PLAN)
    }

    override suspend fun ejecutar(
        apagar: Set<String>,
        opciones: OpcionesUi,
        alPaso: (hechos: Int, total: Int, narracion: String) -> Unit,
    ): Resultado<Resumen> {
        // En el orden del plan, no en el del conjunto.
        val conGrupo = PLAN.grupos.flatMap { g -> g.entradas.map { g.titulo to it } } +
            PLAN.seQuedan.map { "Voz" to it }
        val lista = conGrupo.filter { it.second.paquete in apagar }.distinctBy { it.second.paquete }
        val total = lista.size + if (opciones.animacionesRapidas) 1 else 0
        alPaso(0, total, "Guardando el punto de restauración…")
        delay(650)
        lista.forEachIndexed { i, (grupo, e) ->
            delay(85)
            apagadas += e.paquete
            alPaso(i + 1, total, "Apagando ${grupo.lowercase()}: ${e.nombre}")
        }
        if (opciones.animacionesRapidas) {
            delay(85)
            alPaso(total, total, "Animaciones a 0,5")
        }
        return Resultado.Ok(
            Resumen(
                apagadas = lista.size,
                protegidas = PLAN.seQuedan.count { it.motivo?.contains("firmware") == true },
                seQuedan = PLAN.seQuedan.count { it.paquete !in apagar },
                avisos = listOf("Punto de restauración guardado", "Home verificado: sigue respondiendo"),
            ),
        )
    }

    override suspend fun deshacerTodo(): Resultado<Unit> {
        delay(500)
        apagadas.clear()
        return Resultado.Ok(Unit)
    }

    override suspend fun prender(paquete: String): Resultado<Unit> {
        delay(85)
        apagadas -= paquete
        return Resultado.Ok(Unit)
    }

    companion object {
        private fun g(titulo: String, recomendado: Boolean, vararg e: Pair<String, String>) =
            GrupoUi(titulo, e.map { EntradaUi(it.first, it.second) }, recomendado)

        val PLAN = PlanUi(
            modelo = "AFTKRT · Fire OS 8",
            totalApps = 91,
            perfilConocido = true,
            esEjemplo = true,
            grupos = listOf(
                g(
                    "Publicidad", true,
                    "Sneak Peek" to "com.amazon.sneakpeek",
                    "Anuncios del inicio" to "com.amazon.kso.blackbird",
                    "Shopping" to "com.amazon.shoptv.client",
                    "Identificadores de anuncio" to "com.amazon.hybridadidservice",
                    "Recomendaciones" to "com.amazon.media.recommendations",
                    "Fila de contenido patrocinado" to "com.amazon.providers.contentsupport",
                ),
                g(
                    "Telemetría", true,
                    "Seguimiento del uso" to "com.amazon.tv.acr",
                    "Métricas del cliente" to "com.amazon.client.metrics.api",
                    "Logs del sistema" to "com.amazon.dp.logger",
                    "Ceviche" to "com.amazon.ceviche",
                    "Recolección de rendimiento" to "com.amazon.perfcollection",
                    "Emisor de telemetría" to "com.amazon.device.telemetry.emitter",
                    "Métricas del firmware" to "com.amazon.tv.fw.metrics",
                ),
                g(
                    "Voz", true,
                    "Alertas de Alexa" to "com.amazon.tv.alexaalerts",
                    "Notificaciones de Alexa" to "com.amazon.tv.alexanotifications",
                    "Datastore de Alexa" to "com.amazon.alexa.datastore.app",
                    "Brokers de directivas" to "com.amazon.alexadirectivebrokerservice",
                ),
                g(
                    "Casting", true,
                    "Enviar video del celular" to "com.amazon.cast.sink",
                    "DIAL" to "com.amazon.dialservice",
                    "Receptor de fling" to "com.amazon.awvflingreceiver",
                    "WhisperPlay" to "com.amazon.whisperplay.service.install",
                ),
                g(
                    "Apps preinstaladas", false,
                    "Fotos" to "com.amazon.bueller.photos",
                    "Música" to "com.amazon.bueller.music",
                    "Tahoe" to "com.amazon.tahoe",
                    "Mini TV" to "com.amazon.minitv.android.app",
                    "Navegador Silk" to "com.amazon.cloud9",
                ),
                g(
                    "Casa inteligente", false,
                    "Matter" to "com.amazon.tv.matter",
                    "Matter co-host" to "com.amazon.tv.mattercohost",
                    "Vista de casa" to "com.amazon.smarthomemapviewapp",
                ),
                g(
                    "Restos de Android", false,
                    "Tutoriales del sistema" to "com.android.protips",
                    "Impresión" to "com.android.printservice.recommendation",
                    "Protectores de pantalla" to "com.android.dreams.basic",
                    "Traceur" to "com.android.traceur",
                    "Avisos legales" to "com.amazon.tv.legal.notices",
                    "Tutorial de tormenta" to "com.amazon.storm.lightning.tutorial",
                ),
            ),
            seQuedan = listOf(
                EntradaUi("Pantalla de inicio", "com.amazon.tv.launcher", "es el Home de Fire OS"),
                EntradaUi("Ajustes de la TV", "com.amazon.tv.settings", "sin esto no se configura nada"),
                EntradaUi("Voz de Alexa", PAQUETE_VOZ_ALEXA, "la usás desde el control"),
                EntradaUi("Appstore", "com.amazon.venezia", "sin esto no se instala nada"),
                EntradaUi("Teclado", "com.amazon.tv.ime", "sin esto no se escribe"),
                EntradaUi("Amazon Imp", "com.amazon.imp", "apagarlo deja el Home en negro"),
                EntradaUi("Métricas del dispositivo", "com.amazon.device.metrics", "protegido por el firmware"),
                EntradaUi("Deeplinking de anuncios", "com.amazon.ftvads.deeplinking", "protegido por el firmware"),
            ),
        )
    }
}
