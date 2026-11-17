package com.shikitari.instalador

import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Conexión ADB inalámbrica con la TV. La primera vez crea una llave propia y la guarda,
 * así la TV la reconoce después de emparejar y no hay que repetir el código.
 */
class ConexionTv private constructor(contexto: Context) : AbsAdbConnectionManager() {

    private val llave: PrivateKey
    private val certificado: Certificate

    init {
        api = 30 // Depuración inalámbrica con emparejamiento existe desde Android 11 (API 30)
        // Sin esto, si la TV no contesta el saludo, la librería espera PARA SIEMPRE
        // (era el «Conectando…» que no terminaba nunca). 15 s da tiempo a aceptar el cartel en la TV.
        setTimeout(15, TimeUnit.SECONDS)
        val archivoLlave = File(contexto.filesDir, "adb.key")
        val archivoCert = File(contexto.filesDir, "adb.crt")
        if (archivoLlave.exists() && archivoCert.exists()) {
            llave = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(archivoLlave.readBytes()))
            certificado = archivoCert.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val generador = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            val par = generador.generateKeyPair()
            val nombre = X500Name("CN=Shikitari Instalador")
            val desde = Date()
            val hasta = Date(desde.time + 3650L * 24 * 3600 * 1000)
            val constructor = JcaX509v3CertificateBuilder(nombre, BigInteger.valueOf(desde.time), desde, hasta, nombre, par.public)
            val firmado = constructor.build(JcaContentSignerBuilder("SHA256withRSA").build(par.private))
            llave = par.private
            certificado = JcaX509CertificateConverter().getCertificate(firmado)
            archivoLlave.writeBytes(llave.encoded)
            archivoCert.writeBytes(certificado.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = llave
    override fun getCertificate(): Certificate = certificado
    override fun getDeviceName(): String = "Shikitari Instalador"

    /**
     * Se llama (en un hilo de fondo) cuando un pedido a la TV falla o se cuelga, para que la app
     * revise si la conexión sigue viva y, si no, reconecte o avise.
     */
    @Volatile
    var alFallar: (() -> Unit)? = null

    /** Corre [bloque] y, si falla de verdad (no el "Stream closed" normal), avisa con [alFallar]. */
    private fun <T> vigilado(bloque: () -> T): T =
        try {
            bloque()
        } catch (e: Exception) {
            alFallar?.invoke()
            throw e
        }

    /** Prueba rápida: ¿la TV contesta? Manda un "echo" y espera como mucho [segundos]. No reconecta ni avisa. */
    fun responde(segundos: Long = 5): Boolean {
        if (!isConnected) return false
        return try {
            correr("echo ok", segundos).trim() == "ok"
        } catch (e: Exception) {
            false
        }
    }

    /** Suelta la conexión (por ejemplo, cuando quedó colgada) sin tirar error. */
    fun soltar() {
        try {
            disconnect()
        } catch (_: Exception) {
        }
    }

    /** Ejecuta un comando en la TV y devuelve todo lo que imprime. Corta a los [segundos] por si se cuelga. */
    fun ejecutar(comando: String, segundos: Long = 60): String = vigilado {
        asegurarConexion()
        correr(comando, segundos)
    }

    private fun correr(comando: String, segundos: Long): String {
        val tarea = Executors.newSingleThreadExecutor()
        try {
            return tarea.submit<String> {
                val stream = openStream("shell:$comando")
                try {
                    leerTodo(stream.openInputStream())
                } finally {
                    cerrarSinError(stream)
                }
            }.get(segundos, TimeUnit.SECONDS).trimEnd()
        } finally {
            tarea.shutdownNow()
        }
    }

    /** Trae los bytes que devuelve un comando, sin convertirlos a texto (igual que "adb exec-out"). Sirve para las capturas. */
    fun leerBinario(comando: String, segundos: Long = 10): ByteArray = vigilado { leerBinarioSinVigilar(comando, segundos) }

    private fun leerBinarioSinVigilar(comando: String, segundos: Long): ByteArray {
        asegurarConexion()
        val tarea = Executors.newSingleThreadExecutor()
        try {
            return tarea.submit<ByteArray> {
                val stream = openStream("exec:$comando")
                try {
                    val salida = ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    val entrada = stream.openInputStream()
                    try {
                        while (true) {
                            val n = entrada.read(buffer)
                            if (n < 0) break
                            salida.write(buffer, 0, n)
                        }
                    } catch (e: IOException) {
                        if (e.message?.contains("Stream closed", ignoreCase = true) != true) throw e
                    }
                    salida.toByteArray()
                } finally {
                    cerrarSinError(stream)
                }
            }.get(segundos, TimeUnit.SECONDS)
        } finally {
            tarea.shutdownNow()
        }
    }

    /** Instala un APK enviándolo por la conexión (igual que "adb install"). */
    fun instalarApk(apk: ByteArray): String {
        asegurarConexion()
        val stream = openStream("exec:cmd package install -r -S ${apk.size}")
        try {
            val salida = stream.openOutputStream()
            var i = 0
            while (i < apk.size) {
                val n = minOf(64 * 1024, apk.size - i)
                salida.write(apk, i, n)
                i += n
            }
            salida.flush()
            return leerTodo(stream.openInputStream()).trim()
        } finally {
            cerrarSinError(stream)
        }
    }

    private fun cerrarSinError(stream: java.io.Closeable) {
        try {
            stream.close()
        } catch (_: IOException) {
        }
    }

    /**
     * Lee todo lo que imprime un comando. Si la TV cierra el canal enseguida (comandos que no imprimen nada,
     * como "pm grant" o "mkdir"), la librería avisa "Stream closed": eso es el final normal, no un error.
     */
    private fun leerTodo(entrada: InputStream): String {
        val salida = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val n = entrada.read(buffer)
                if (n < 0) break
                salida.write(buffer, 0, n)
            }
        } catch (e: IOException) {
            if (e.message?.contains("Stream closed", ignoreCase = true) != true) throw e
        }
        return salida.toString(Charsets.UTF_8.name())
    }

    private var ultimoHost: String? = null
    private var ultimoPuerto = 0

    /** Conecta de nuevo aunque haya quedado abierta una conexión vieja, y recuerda a dónde para reconectar sola. */
    @Synchronized
    fun reconectar(host: String, puerto: Int): Boolean {
        try {
            disconnect()
        } catch (_: Exception) {
        }
        val ok = connect(host, puerto)
        if (ok) {
            ultimoHost = host
            ultimoPuerto = puerto
        }
        return ok
    }

    /** Si la conexión se cortó (la TV entró en reposo, se cambió de app, etc.), vuelve a conectar sola. */
    @Synchronized
    private fun asegurarConexion() {
        if (isConnected) return
        val host = ultimoHost ?: throw IOException("Primero tocá Conectar.")
        if (!reconectar(host, ultimoPuerto)) {
            throw IOException("Se perdió la conexión con la TV.")
        }
    }

    /** Escribe un archivo en la TV (por ejemplo, la foto de portada), enviándolo por la conexión. */
    fun escribirArchivo(ruta: String, datos: ByteArray) {
        asegurarConexion()
        val stream = openStream("exec:sh -c 'cat > $ruta'")
        val salida = stream.openOutputStream()
        var i = 0
        while (i < datos.size) {
            val n = minOf(64 * 1024, datos.size - i)
            salida.write(datos, i, n)
            i += n
        }
        salida.flush()
        cerrarSinError(stream) // al cerrar, "cat" termina de escribir el archivo
    }

    /** Pasa un archivo del celular a la TV de a pedazos (sirve para videos grandes sin llenar la memoria). */
    fun enviarArchivo(ruta: String, entrada: InputStream, progreso: (Long) -> Unit) {
        asegurarConexion()
        val stream = openStream("exec:cat > ${comillas(ruta)}")
        try {
            val salida = stream.openOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = entrada.read(buffer)
                if (n < 0) break
                salida.write(buffer, 0, n)
                total += n
                progreso(total)
            }
            salida.flush()
        } finally {
            cerrarSinError(stream)
        }
    }

    /** Instala un APK que está en el celular, mandándolo de a pedazos (sirve aunque pese cientos de MB). */
    fun instalarApk(entrada: InputStream, tamano: Long, progreso: (Long) -> Unit): String {
        asegurarConexion()
        val stream = openStream("exec:cmd package install -r -S $tamano")
        try {
            val salida = stream.openOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = entrada.read(buffer)
                if (n < 0) break
                salida.write(buffer, 0, n)
                total += n
                progreso(total)
            }
            salida.flush()
            return leerTodo(stream.openInputStream()).trim()
        } finally {
            cerrarSinError(stream)
        }
    }

    /** Trae un archivo de la TV al celular, escribiéndolo en [salida] a medida que llega. */
    fun recibirArchivo(ruta: String, salida: OutputStream, progreso: (Long) -> Unit) {
        asegurarConexion()
        val stream = openStream("exec:cat ${comillas(ruta)}")
        try {
            val entrada = stream.openInputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            try {
                while (true) {
                    val n = entrada.read(buffer)
                    if (n < 0) break
                    salida.write(buffer, 0, n)
                    total += n
                    progreso(total)
                }
            } catch (e: IOException) {
                if (e.message?.contains("Stream closed", ignoreCase = true) != true) throw e
            }
            salida.flush()
        } finally {
            cerrarSinError(stream)
        }
    }

    companion object {
        /** Pone una ruta entre comillas para la consola de la TV (por si tiene espacios, paréntesis, etc.). */
        fun comillas(texto: String) = "'" + texto.replace("'", "'\\''") + "'"

        @Volatile
        private var instancia: ConexionTv? = null

        fun obtener(contexto: Context): ConexionTv =
            instancia ?: synchronized(this) {
                instancia ?: ConexionTv(contexto.applicationContext).also { instancia = it }
            }
    }
}
