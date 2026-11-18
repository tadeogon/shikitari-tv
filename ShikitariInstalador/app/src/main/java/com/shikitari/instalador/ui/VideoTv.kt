package com.shikitari.instalador.ui

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.shikitari.instalador.ConexionTv
import io.github.muntashirakon.adb.AdbStream
import java.io.IOException

/**
 * La pantalla de la TV como video en vivo (en vez de una foto por segundo).
 *
 * Cómo funciona:
 * 1. En la TV corre «screenrecord», la grabadora de pantalla que trae Android, mandando el video
 *    comprimido (H.264, 960×540) por la conexión en lugar de guardarlo en un archivo.
 * 2. Acá se corta ese chorro de datos en pedacitos (NAL) y se los pasa al decodificador de video
 *    del celular (MediaCodec), que dibuja directo en [superficie].
 * 3. screenrecord corta solo a los 3 minutos: se vuelve a abrir sin que se note.
 *
 * Si la TV no puede (o falla 3 veces seguidas), avisa con [Estado.FALLO] y la pantalla vuelve a las fotos.
 */
class VideoTv(
    private val tv: ConexionTv,
    private val superficie: Surface,
    private val alCambiar: (Estado) -> Unit,
) {
    enum class Estado { CONECTANDO, EN_VIVO, FALLO }

    @Volatile private var activo = true
    @Volatile private var canal: AdbStream? = null
    private val hilo = Thread({ bucle() }, "video-tv")

    fun empezar() = hilo.start()

    fun parar() {
        activo = false
        try { canal?.close() } catch (_: Exception) {}
        hilo.interrupt()
    }

    private fun bucle() {
        var fallos = 0
        alCambiar(Estado.CONECTANDO)
        while (activo) {
            val mostro = try { sesion() } catch (e: Exception) { false }
            if (!activo) break
            if (mostro) {
                fallos = 0
            } else if (++fallos >= 3) {
                alCambiar(Estado.FALLO)
                return
            } else {
                try { Thread.sleep(700) } catch (_: InterruptedException) { return }
            }
        }
    }

    /** Una tanda de video. Devuelve true si llegó a mostrar al menos una imagen. */
    private fun sesion(): Boolean {
        val c = tv.abrirCanal("screenrecord --output-format=h264 --size ${ANCHO}x$ALTO --bit-rate $BITS -")
        canal = c
        val dec = MediaCodec.createDecoderByType("video/avc")
        val mostro = java.util.concurrent.atomic.AtomicBoolean(false)
        val andando = java.util.concurrent.atomic.AtomicBoolean(true)
        val salida = Thread({
            // Saca las imágenes ya decodificadas y las dibuja apenas están (sin esperar a la próxima).
            val info = MediaCodec.BufferInfo()
            while (andando.get()) {
                val i = try { dec.dequeueOutputBuffer(info, 20_000) } catch (e: Exception) { break }
                if (i >= 0) {
                    try { dec.releaseOutputBuffer(i, true) } catch (e: Exception) { break }
                    if (mostro.compareAndSet(false, true)) alCambiar(Estado.EN_VIVO)
                }
            }
        }, "video-tv-salida")
        try {
            val formato = MediaFormat.createVideoFormat("video/avc", ANCHO, ALTO)
            if (Build.VERSION.SDK_INT >= 30) formato.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            dec.configure(formato, superficie, null, 0)
            dec.start()
            salida.start()

            // Un hilo solo lee de la conexión y deja los trozos en una cola; así acá se puede
            // esperar «un ratito» sin quedar trabado en la lectura.
            val entrada = c.openInputStream()
            val cola = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
            val lector = Thread({
                val trozo = ByteArray(64 * 1024)
                while (andando.get()) {
                    val n = try { entrada.read(trozo) } catch (e: IOException) { -1 }
                    if (n < 0) {
                        cola.put(ByteArray(0)) // fin
                        break
                    }
                    if (n > 0) cola.put(trozo.copyOf(n))
                }
            }, "video-tv-lector")
            lector.start()

            var buf = ByteArray(512 * 1024)
            var largo = 0
            while (activo) {
                val nuevo = cola.poll(QUIETO_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (nuevo != null) {
                    if (nuevo.isEmpty()) break // terminó la tanda (los 3 minutos) o se cortó
                    if (largo + nuevo.size > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, largo + nuevo.size))
                    System.arraycopy(nuevo, 0, buf, largo, nuevo.size)
                    largo += nuevo.size
                }
                if (largo == 0) continue
                // Lo que hay que tener siempre: los datos arrancan con 00 00 01. Si no, algo se perdió
                // y la imagen quedaría «pegada»: se reabre el video para empezar limpio.
                if (!empiezaNal(buf, largo)) {
                    if (largo < 4) continue
                    throw IOException("video desfasado")
                }
                // Separar en NAL: cada uno empieza con 00 00 01 (o 00 00 00 01).
                val cortes = inicios(buf, largo)
                // Los que tienen otro detrás están completos. El último, solo si pasaron
                // QUIETO_MS sin que llegue nada (screenrecord manda cada imagen de un tirón).
                val completos = if (nuevo == null) cortes.size else cortes.size - 1
                for (k in 0 until completos) {
                    val desde = cortes[k]
                    val fin = if (k + 1 < cortes.size) cortes[k + 1] else largo
                    meter(dec, buf, desde, fin - desde)
                }
                val resto = if (completos == cortes.size) largo else cortes[completos]
                val quedan = largo - resto
                if (quedan > 0) System.arraycopy(buf, resto, buf, 0, quedan)
                largo = quedan
            }
        } catch (e: IOException) {
            // Video desfasado o decodificador trabado: se cierra esta tanda y el bucle abre otra limpia.
            // (Si ya se había visto algo, no cuenta como falla.)
        } finally {
            andando.set(false)
            try { c.close() } catch (_: Exception) {}
            try { salida.join(300) } catch (_: InterruptedException) {}
            try { dec.stop() } catch (_: Exception) {}
            try { dec.release() } catch (_: Exception) {}
        }
        return mostro.get()
    }

    /** Posiciones donde empieza cada NAL (contando el 00 de más del código de 4 bytes). */
    private fun inicios(b: ByteArray, largo: Int): List<Int> {
        val r = ArrayList<Int>()
        var i = 0
        while (i + 2 < largo) {
            if (b[i].toInt() == 0 && b[i + 1].toInt() == 0 && b[i + 2].toInt() == 1) {
                r += if (i > 0 && b[i - 1].toInt() == 0) i - 1 else i
                i += 3
            } else {
                i++
            }
        }
        return r
    }

    private fun empiezaNal(b: ByteArray, largo: Int): Boolean =
        largo >= 3 && b[0].toInt() == 0 && b[1].toInt() == 0 &&
            (b[2].toInt() == 1 || (largo >= 4 && b[2].toInt() == 0 && b[3].toInt() == 1))

    private fun meter(dec: MediaCodec, b: ByteArray, desde: Int, largo: Int) {
        if (largo <= 4) return
        // Nunca se saltea un pedazo: si se pierde uno, la imagen queda rota hasta reabrir.
        val i = dec.dequeueInputBuffer(1_000_000)
        if (i < 0) throw IOException("el decodificador no da abasto")
        val destino = dec.getInputBuffer(i) ?: throw IOException("sin lugar en el decodificador")
        destino.clear()
        destino.put(b, desde, largo)
        val cabecera = if (b[desde + 2].toInt() == 1) desde + 3 else desde + 4
        val tipo = b[cabecera].toInt() and 0x1f
        // 7 = SPS y 8 = PPS: la «receta» del video, que va antes de las imágenes.
        val marca = if (tipo == 7 || tipo == 8) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
        dec.queueInputBuffer(i, 0, largo, System.nanoTime() / 1000, marca)
    }

    companion object {
        const val ANCHO = 960
        const val ALTO = 540
        const val BITS = 2_000_000
        /** Cuánto silencio hace falta para dar por terminada la imagen que está llegando. */
        const val QUIETO_MS = 35L
    }
}
