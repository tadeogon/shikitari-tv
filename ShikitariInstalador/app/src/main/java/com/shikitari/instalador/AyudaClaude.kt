package com.shikitari.instalador

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam

/**
 * Ayuda con Claude: le contás el problema y Claude puede consultar la TV por ADB.
 * Los comandos de solo lectura se ejecutan directo; cualquier otro necesita tu confirmación,
 * y los peligrosos o que tocan paquetes protegidos se bloquean siempre.
 */
class AyudaClaude(
    claveApi: String,
    private val tv: ConexionTv,
    /** Muestra un mensaje en el chat. */
    private val mostrar: (String) -> Unit,
    /** Pregunta al usuario si ejecuta el comando; bloquea hasta que responde. */
    private val confirmar: (comando: String, motivo: String) -> Boolean,
) {
    private val cliente: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(claveApi).build()
    private val historial = mutableListOf<MessageParam>()

    private val herramienta: Tool = Tool.builder()
        .name("ejecutar_comando_adb")
        .description(
            "Ejecuta un comando de shell en la TV Android conectada por ADB y devuelve lo que imprime. " +
                "Escribí solo el comando, sin 'adb shell' adelante. Usalo para diagnosticar (getprop, pm list packages, " +
                "dumpsys, settings get, df, logcat -d) o para aplicar un arreglo que el usuario confirma.",
        )
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(
                    Tool.InputSchema.Properties.builder()
                        .putAdditionalProperty("comando", JsonValue.from(mapOf("type" to "string", "description" to "Comando de shell, por ejemplo: pm list packages -3")))
                        .putAdditionalProperty("motivo", JsonValue.from(mapOf("type" to "string", "description" to "En español simple: qué hace y por qué lo necesitás")))
                        .build(),
                )
                .required(listOf("comando", "motivo"))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build(),
        )
        .strict(true)
        .build()

    /** Envía una pregunta y sigue el ciclo de herramientas hasta que Claude termina de responder. */
    fun preguntar(texto: String) {
        historial.add(MessageParam.builder().role(MessageParam.Role.USER).content(texto).build())
        while (true) {
            val respuesta = cliente.messages().create(
                MessageCreateParams.builder()
                    .model("claude-opus-5-5")
                    .maxTokens(16000L)
                    .system(SISTEMA)
                    .addTool(herramienta)
                    .messages(historial)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                    // Si Claude rechaza la consulta por precaución, la API reintenta con otro modelo.
                    .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    .build(),
            )
            historial.add(respuesta.toParam())

            for (bloque in respuesta.content()) {
                bloque.text().ifPresent { if (it.text().isNotBlank()) mostrar(it.text()) }
            }

            when (respuesta.stopReason().orElse(null)) {
                StopReason.TOOL_USE -> {
                    val resultados = respuesta.content().mapNotNull { bloque -> bloque.toolUse().orElse(null) }.map { uso ->
                        val entrada = uso._input().asObject().orElse(emptyMap())
                        val comando = entrada["comando"]?.asString()?.orElse(null).orEmpty().trim()
                        val motivo = entrada["motivo"]?.asString()?.orElse(null).orEmpty()
                        val (salida, esError) = ejecutarConCuidado(comando, motivo)
                        ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                .toolUseId(uso.id())
                                .content(salida.take(8000))
                                .isError(esError)
                                .build(),
                        )
                    }
                    historial.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(resultados).build())
                }
                StopReason.REFUSAL -> {
                    mostrar("Claude no puede ayudar con esto. Probá contarlo de otra forma.")
                    return
                }
                StopReason.MAX_TOKENS -> {
                    mostrar("(La respuesta quedó cortada por ser muy larga.)")
                    return
                }
                else -> return
            }
        }
    }

    /** Devuelve la salida del comando y si fue un error. */
    private fun ejecutarConCuidado(comando: String, motivo: String): Pair<String, Boolean> {
        if (comando.isBlank()) return "Comando vacío" to true
        motivoDeBloqueo(comando)?.let {
            mostrar("🚫 Bloqueado: $comando\n($it)")
            return "Bloqueado por la app: $it. Proponé otra solución." to true
        }
        if (!esSoloLectura(comando)) {
            if (!confirmar(comando, motivo)) {
                mostrar("✋ No ejecutado: $comando")
                return "El usuario no aprobó este comando." to true
            }
        }
        mostrar("▶ $comando")
        return try {
            tv.ejecutar(comando).ifBlank { "(sin salida)" } to false
        } catch (e: Exception) {
            "Error al ejecutar: ${e.message}" to true
        }
    }

    private fun esSoloLectura(comando: String): Boolean {
        if (Regex("[;&|><`]|\\$\\(").containsMatchIn(comando)) return false
        return LECTURA.any { comando == it || comando.startsWith("$it ") }
    }

    private fun motivoDeBloqueo(comando: String): String? {
        val c = comando.lowercase()
        PELIGROSOS.firstOrNull { c.contains(it) }?.let { return "puede borrar datos o dejar la TV inutilizable" }
        val cambiaPaquete = Regex("\\b(uninstall|disable|disable-user|clear|hide|suspend)\\b").containsMatchIn(c)
        if (cambiaPaquete && AccionesTv.PROTEGIDOS.any { c.contains(it.lowercase()) }) {
            return "toca un paquete protegido del sistema"
        }
        return null
    }

    companion object {
        private val LECTURA = listOf(
            "getprop", "pm list packages", "pm list users", "pm path", "dumpsys meminfo", "dumpsys diskstats",
            "dumpsys package", "dumpsys activity activities", "dumpsys battery", "dumpsys window", "df", "settings get",
            "settings list", "cmd package resolve-activity", "cmd package query-activities", "logcat -d", "cat /proc/meminfo",
            "uptime", "wm size", "wm density", "ps -A", "appops get", "dumpsys media_session",
        )

        private val PELIGROSOS = listOf(
            "rm -", "rm /", "recovery", "bootloader", "wipe", "factory", "mkfs", "dd if", "format",
            "pm clear com.google.android.gms", "svc power shutdown",
        )

        private val SISTEMA = """
            Sos el asistente de "Shikitari Instalador", una app de celular que configura y optimiza TVs con Android TV / Google TV
            por ADB inalámbrico. El usuario es principiante: respondé en español rioplatense, corto y simple, explicando qué hace cada paso.

            Podés usar la herramienta ejecutar_comando_adb para diagnosticar y arreglar. Reglas:
            - Diagnosticá primero con comandos de solo lectura (getprop, pm list packages, dumpsys, settings get, df, logcat -d); esos se ejecutan directo.
            - Cualquier comando que cambie algo se lo muestra la app al usuario para que lo apruebe. Proponé de a uno y explicá el motivo.
            - Apps del sistema: quitarlas SOLO con "pm uninstall -k --user 0 PAQUETE" o "pm disable-user --user 0 PAQUETE" (son reversibles con
              "cmd package install-existing PAQUETE" o "pm enable PAQUETE"). Nunca otra forma.
            - Nunca toques paquetes críticos: android, systemui, ajustes de la TV, launchers de Google, Google Play Services (gms, gsf), Play Store,
              instalador de paquetes, permisos, webview, teclado, control remoto y Bluetooth, servicios de Cast (mediashell), proveedores (providers),
              networkstack, shell, overlays, resources, ni módulos del sistema. La app bloquea esos comandos igual.
            - Ojo: com.google.android.apps.tv.dreamx contiene el panel de Google Home (luces de la casa); no lo quites.
            - El launcher propio del usuario es Shikitari TV (paquete com.milauncher.tv). Su servicio del botón Inicio es
              com.milauncher.tv/com.milauncher.tv.InicioService y su protector es com.milauncher.tv/com.milauncher.tv.ProtectorService.
            - El otro launcher es Pachi TV (paquete com.pachi.tv), con servicio de Inicio com.pachi.tv/com.pachi.tv.InicioService y protector
              com.pachi.tv/com.pachi.tv.ProtectorService. Hay una tercera versión, Solari (paquete com.pachi.tv.trabajo), el mismo launcher con la marca del trabajo: sus componentes son com.pachi.tv.trabajo/com.pachi.tv.InicioService, .ProtectorService y .MusicaListener. Solo un launcher puede tener activo el servicio de Inicio a la vez.
            - La isla dinámica de Pachi TV necesita el acceso a la música: "cmd notification allow_listener com.pachi.tv/com.pachi.tv.MusicaListener".
              Si no aparece, comprobalo en "settings get secure enabled_notification_listeners". Solo se ve si la app de música sigue sonando
              en segundo plano: Spotify sí; YouTube Music para TV se corta al salir; SmartTube sigue si tiene "Reproducción de fondo" activada.
            - Pachi TV también usa WRITE_SECURE_SETTINGS y BLUETOOTH_CONNECT (este último desde Android 12), otorgados con "pm grant".
            - Si algo puede salir mal, avisalo antes y decí cómo se deshace.
        """.trimIndent()
    }
}
