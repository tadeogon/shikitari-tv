package com.shikitari.instalador

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Una TV guardada: el nombre lo elegís vos; la IP y el puerto se actualizan solos al conectar. */
data class TvGuardada(val nombre: String, val ip: String, val puerto: Int, val modelo: String)

/** La lista de TVs guardadas en el celular. */
object MisTvs {

    private const val CLAVE = "mis_tvs"

    fun cargar(contexto: Context): List<TvGuardada> {
        val texto = prefs(contexto).getString(CLAVE, null) ?: return emptyList()
        return try {
            val arr = JSONArray(texto)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TvGuardada(o.getString("nombre"), o.getString("ip"), o.optInt("puerto", 5555), o.optString("modelo"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun guardar(contexto: Context, lista: List<TvGuardada>) {
        val arr = JSONArray()
        lista.forEach {
            arr.put(JSONObject().put("nombre", it.nombre).put("ip", it.ip).put("puerto", it.puerto).put("modelo", it.modelo))
        }
        prefs(contexto).edit().putString(CLAVE, arr.toString()).apply()
    }

    /** Después de conectar: si la TV ya estaba, actualiza su puerto; si no, la agrega con el nombre del modelo. */
    fun recordar(contexto: Context, ip: String, puerto: Int, modelo: String): TvGuardada {
        val lista = cargar(contexto).toMutableList()
        val i = lista.indexOfFirst { it.ip == ip }
        val tv = if (i >= 0) {
            lista[i].copy(puerto = puerto, modelo = modelo.ifBlank { lista[i].modelo })
        } else {
            val base = modelo.ifBlank { "TV" }
            val repetidos = lista.count { it.nombre.startsWith(base) }
            TvGuardada(if (repetidos == 0) base else "$base ${repetidos + 1}", ip, puerto, modelo)
        }
        if (i >= 0) lista[i] = tv else lista += tv
        guardar(contexto, lista)
        return tv
    }

    fun renombrar(contexto: Context, ip: String, nombre: String) =
        guardar(contexto, cargar(contexto).map { if (it.ip == ip) it.copy(nombre = nombre) else it })

    /** La TV es la misma pero el router le dio otra IP: se mueve el guardado (con su nombre) a la IP nueva. */
    fun mudar(contexto: Context, ipVieja: String, ipNueva: String) =
        guardar(
            contexto,
            cargar(contexto).filter { it.ip != ipNueva }.map { if (it.ip == ipVieja) it.copy(ip = ipNueva) else it },
        )

    fun borrar(contexto: Context, ip: String) =
        guardar(contexto, cargar(contexto).filter { it.ip != ip })

    private fun prefs(contexto: Context) = contexto.getSharedPreferences("app", Context.MODE_PRIVATE)
}

/**
 * Busca TVs en el Wi-Fi: las que tienen la Depuración inalámbrica prendida se anuncian solas en la red
 * (igual que lo que hace "adb mdns services" en la PC). Así se sabe la IP y el puerto sin mirar la TV,
 * aunque el puerto haya cambiado al reiniciarla.
 */
object BuscadorTvs {

    const val CONEXION = "_adb-tls-connect._tcp"
    const val VINCULACION = "_adb-tls-pairing._tcp"

    /** Devuelve IP → puerto de lo que encuentre en [segundos]. Se llama desde un hilo de fondo. */
    fun buscar(contexto: Context, tipo: String = CONEXION, segundos: Long = 4): Map<String, Int> {
        val nsd = contexto.getSystemService(Context.NSD_SERVICE) as NsdManager
        val encontrados = java.util.Collections.synchronizedList(mutableListOf<NsdServiceInfo>())
        val oyente = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                encontrados += info
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStarted(tipo: String) {}
            override fun onDiscoveryStopped(tipo: String) {}
            override fun onStartDiscoveryFailed(tipo: String, error: Int) {}
            override fun onStopDiscoveryFailed(tipo: String, error: Int) {}
        }
        try {
            nsd.discoverServices(tipo, NsdManager.PROTOCOL_DNS_SD, oyente)
            Thread.sleep(segundos * 1000)
        } finally {
            try {
                nsd.stopServiceDiscovery(oyente)
            } catch (_: Exception) {
            }
        }
        // Se averigua la IP de a uno: los Android más viejos no dejan resolver dos a la vez.
        val resultado = LinkedHashMap<String, Int>()
        for (info in encontrados.toList().distinctBy { it.serviceName }) {
            val listo = CountDownLatch(1)
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, error: Int) = listo.countDown()
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val ip = i.host?.hostAddress
                    if (ip != null && ip.contains('.')) resultado[ip] = i.port
                    listo.countDown()
                }
            })
            listo.await(3, TimeUnit.SECONDS)
        }
        return resultado
    }

    /**
     * Fire TV y TVs viejas usan «Depuración ADB» en el puerto 5555 y no se anuncian en el Wi-Fi.
     * Si el router les cambia la IP, la única forma de encontrarlas es probar cada dirección
     * de la red (192.168.x.1 a 254) a ver cuál tiene ese puerto abierto. Tarda 1 o 2 segundos.
     */
    fun buscarViejas(contexto: Context): Map<String, Int> {
        val cm = contexto.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val propia = cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }?.firstOrNull { it is java.net.Inet4Address }?.hostAddress ?: return emptyMap()
        val base = propia.substringBeforeLast('.')
        val pool = java.util.concurrent.Executors.newFixedThreadPool(48)
        try {
            val pedidos = (1..254).map { "$base.$it" }.filter { it != propia }.map { ip ->
                pool.submit<String?> {
                    try {
                        java.net.Socket().use { it.connect(java.net.InetSocketAddress(ip, 5555), 400) }
                        ip
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            return pedidos.mapNotNull { try { it.get(5, TimeUnit.SECONDS) } catch (e: Exception) { null } }
                .associateWith { 5555 }
        } finally {
            pool.shutdownNow()
        }
    }
}
