package com.shikitari.instalador

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Volumen de una tele Samsung por el Wi-Fi de casa. Hace falta porque un Fire Stick conectado por HDMI no puede
 * cambiar el volumen de la tele («fixed volume device»): su control físico lo hace por infrarrojo.
 * La primera vez la tele pregunta si deja manejarla; después se acuerda (guardamos el permiso que da).
 */
class SamsungTv(contexto: Context) {

    private val prefs = contexto.getSharedPreferences("samsung", Context.MODE_PRIVATE)
    private var socket: SSLSocket? = null
    private var conectadaA = ""
    private var ultimoUso = 0L

    class SinPermiso : IOException("la tele no dio permiso")

    /** ¿Ya aceptó antes? Si no, hay que avisar que va a preguntar en la pantalla. */
    fun conPermiso(ip: String) = !prefs.getString("permiso_$ip", null).isNullOrBlank()

    /** La Samsung de la misma red que [ipVecina] (la del Fire Stick), o null. Se acuerda de la última. */
    fun buscar(ipVecina: String): String? {
        val red = ipVecina.substringBeforeLast('.', "")
        if (red.isBlank()) return null
        prefs.getString("tele_$red", null)?.let { if (esSamsung(it)) return it }
        val hilos = Executors.newFixedThreadPool(64)
        try {
            val encontrada = hilos.invokeAll((1..254).map { n -> Callable { "$red.$n".takeIf { esSamsung(it) } } }, 12, TimeUnit.SECONDS)
                .firstNotNullOfOrNull { runCatching { it.get() }.getOrNull() } ?: return null
            prefs.edit().putString("tele_$red", encontrada).apply()
            return encontrada
        } finally {
            hilos.shutdownNow()
        }
    }

    private fun esSamsung(ip: String): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, 8001), 1500)
            s.soTimeout = 2000
            s.getOutputStream().write("GET /api/v2/ HTTP/1.1\r\nHost: $ip:8001\r\nConnection: close\r\n\r\n".toByteArray())
            val b = ByteArray(4096)
            var texto = ""
            while (texto.length < 4096 && "TokenAuthSupport" !in texto) {
                val n = s.getInputStream().read(b)
                if (n < 0) break
                texto += String(b, 0, n)
            }
            "TokenAuthSupport" in texto
        }
    } catch (e: Exception) {
        false
    }

    /** Aprieta una tecla de la tele (por ejemplo KEY_VOLUP). */
    @Synchronized
    fun tecla(ip: String, tecla: String) {
        val viva = socket != null && conectadaA == ip && System.currentTimeMillis() - ultimoUso < 15_000
        if (!viva) conectar(ip)
        val orden = """{"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":"$tecla","Option":"false","TypeOfRemote":"SendRemoteKey"}}"""
        try {
            mandar(orden)
        } catch (e: IOException) {
            conectar(ip)
            mandar(orden)
        }
        ultimoUso = System.currentTimeMillis()
    }

    @Synchronized
    fun cerrar() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun conectar(ip: String) {
        cerrar()
        val base = Socket()
        base.connect(InetSocketAddress(ip, 8002), 3000)
        val s = tlsDeLaTele().createSocket(base, ip, 8002, true) as SSLSocket
        try {
            s.soTimeout = 35_000 // el tiempo para aceptar el cartel en la tele
            s.startHandshake()
            val nombre = Base64.encodeToString("Shikitari Control".toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
            val permiso = prefs.getString("permiso_$ip", null)
            val camino = "/api/v2/channels/samsung.remote.control?name=$nombre" + if (permiso.isNullOrBlank()) "" else "&token=$permiso"
            val llave = Base64.encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
            s.outputStream.write(
                ("GET $camino HTTP/1.1\r\nHost: $ip:8002\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $llave\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray(),
            )
            val entrada = s.inputStream
            if (" 101 " !in leerCabecera(entrada).lineSequence().first()) throw IOException("la tele no abrió el control")
            while (true) {
                val json = JSONObject(leerMensaje(entrada))
                when (json.optString("event")) {
                    "ms.channel.connect" -> {
                        val nuevo = json.optJSONObject("data")?.optString("token").orEmpty()
                        if (nuevo.isNotBlank()) prefs.edit().putString("permiso_$ip", nuevo).apply()
                        break
                    }
                    "ms.channel.unauthorized", "ms.channel.timeOut" -> {
                        prefs.edit().remove("permiso_$ip").apply()
                        throw SinPermiso()
                    }
                }
            }
            socket = s
            conectadaA = ip
        } catch (e: Exception) {
            try { s.close() } catch (_: Exception) {}
            throw e
        }
    }

    private fun leerCabecera(entrada: InputStream): String {
        val sb = StringBuilder()
        while (!sb.endsWith("\r\n\r\n")) {
            val c = entrada.read()
            if (c < 0 || sb.length > 16_384) throw IOException("la tele cortó la conexión")
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    /** Lee un mensaje de texto de la tele (salteando los de control). */
    private fun leerMensaje(entrada: InputStream): String {
        fun byte(): Int = entrada.read().also { if (it < 0) throw IOException("la tele cortó la conexión") }
        val texto = ByteArrayOutputStream()
        while (true) {
            val b0 = byte()
            val b1 = byte()
            var largo = (b1 and 0x7f).toLong()
            if (largo == 126L) largo = ((byte() shl 8) or byte()).toLong()
            else if (largo == 127L) { largo = 0; repeat(8) { largo = (largo shl 8) or byte().toLong() } }
            if (largo < 0 || largo > 1_000_000) throw IOException("mensaje inválido")
            val datos = ByteArray(largo.toInt())
            var i = 0
            while (i < datos.size) {
                val n = entrada.read(datos, i, datos.size - i)
                if (n < 0) throw IOException("la tele cortó la conexión")
                i += n
            }
            when (b0 and 0x0f) {
                0x8 -> throw IOException("la tele cortó la conexión")
                0x0, 0x1 -> {
                    texto.write(datos)
                    if (b0 and 0x80 != 0) return texto.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

    private fun mandar(texto: String) {
        val s = socket ?: throw IOException("sin conexión")
        val datos = texto.toByteArray()
        val mascara = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val salida = ByteArrayOutputStream()
        salida.write(0x81)
        if (datos.size < 126) {
            salida.write(0x80 or datos.size)
        } else {
            salida.write(0x80 or 126)
            salida.write(datos.size shr 8)
            salida.write(datos.size and 0xff)
        }
        salida.write(mascara)
        datos.forEachIndexed { i, b -> salida.write(b.toInt() xor mascara[i % 4].toInt()) }
        s.outputStream.write(salida.toByteArray())
        s.outputStream.flush()
    }

    /** Las Samsung usan un certificado propio que nadie firma; se acepta solo en esta conexión, dentro de casa. */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun tlsDeLaTele() = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, tipo: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, tipo: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }), SecureRandom())
    }.socketFactory
}
