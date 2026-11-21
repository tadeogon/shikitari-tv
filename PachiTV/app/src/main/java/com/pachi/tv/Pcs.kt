package com.pachi.tv

import android.content.Context
import org.json.JSONArray
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Prender las PC de la casa desde la tele (Wake on LAN), igual que hace WolPuente.
 * La lista se guarda con el mismo formato que WolPuente: [{"name":..,"mac":..,"ip":..}].
 */
object Pcs {
    data class Pc(val nombre: String, val mac: String, val ip: String)

    private const val CLAVE = "pcs"
    // Puertos que una PC con Windows suele tener abiertos aunque no conteste el ping.
    private val PUERTOS = intArrayOf(445, 5357, 3389, 135, 139, 22)

    fun lista(c: Context): List<Pc> {
        val texto = c.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString(CLAVE, "") ?: ""
        if (texto.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(texto)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val mac = o.optString("mac")
                if (mac.replace(Regex("[^0-9A-Fa-f]"), "").length != 12) null
                else Pc(o.optString("name").ifBlank { "PC ${i + 1}" }, mac, o.optString("ip"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Guarda la lista que llega desde la PC con ADB (una sola vez). */
    fun guardar(c: Context, json: String): Boolean {
        return try {
            JSONArray(json)
            c.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putString(CLAVE, json).commit()
        } catch (e: Exception) {
            false
        }
    }

    /** Manda el "paquete mágico" varias veces, a toda la red y directo a la IP de la PC. Llamar fuera del hilo principal. */
    fun prender(pc: Pc): Boolean {
        val hex = pc.mac.replace(Regex("[^0-9A-Fa-f]"), "")
        val mac = ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val paquete = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) paquete[i] = 0xFF.toByte()
        for (i in 6 until paquete.size step 6) System.arraycopy(mac, 0, paquete, i, 6)

        val destinos = LinkedHashSet<InetAddress>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) if (ia.address is Inet4Address) ia.broadcast?.let { destinos += it }
            }
        } catch (e: Exception) {
        }
        try { destinos += InetAddress.getByName("255.255.255.255") } catch (e: Exception) {}
        if (pc.ip.isNotBlank()) try { destinos += InetAddress.getByName(pc.ip.trim()) } catch (e: Exception) {}

        var bien = 0
        DatagramSocket().use { s ->
            s.broadcast = true
            repeat(3) { vuelta ->
                for (d in destinos) for (puerto in intArrayOf(9, 7)) {
                    try {
                        s.send(DatagramPacket(paquete, paquete.size, d, puerto))
                        bien++
                    } catch (e: Exception) {
                    }
                }
                if (vuelta < 2) Thread.sleep(300)
            }
        }
        return bien > 0
    }

    /**
     * ¿La PC está prendida? Solo cuenta si un puerto típico de Windows acepta la conexión.
     * Antes también contaban "conexión rechazada" y el ping de Android (que en realidad prueba el puerto 7
     * y da "sí" con un rechazo): así, otro aparato que quedó con esa IP, o la placa de red de una PC
     * apagada, la hacían aparecer "prendida" sin estarlo. Llamar fuera del hilo principal.
     */
    fun prendida(pc: Pc, espera: Int = 600): Boolean {
        val ip = pc.ip.trim()
        if (ip.isBlank()) return false
        // Todas las pruebas a la vez: alcanza con que una conteste.
        val tareas = PUERTOS.map { puerto ->
            Callable {
                Socket().use { it.connect(InetSocketAddress(ip, puerto), espera) }
                true
            }
        }
        val hilos = Executors.newFixedThreadPool(tareas.size)
        return try {
            hilos.invokeAny(tareas, espera + 400L, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            false
        } finally {
            hilos.shutdownNow()
        }
    }
}
