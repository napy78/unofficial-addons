package com.napy.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Dati condivisi tra servizio (analisi) e UI (disegno): stesso processo, quindi basta un singleton. */
object SpectrumBus {
    const val BANDS = 20
    const val FLOOR_DB = -72f
    const val RANGE_DB = 66f

    @Volatile var levels = FloatArray(BANDS)          // 0..1 per banda
    @Volatile var centers = FloatArray(BANDS)         // frequenza centrale (Hz)
    @Volatile var stamp = 0L                          // System.nanoTime() dell'ultimo aggiornamento
}

/**
 * Processore audio "trasparente": lascia passare il PCM invariato e intanto calcola una FFT
 * (2048 campioni, finestra di Hann) raggruppata in 20 bande logaritmiche, 40 Hz - 16 kHz.
 * Lavora sull'audio realmente decodificato, quindi funziona con qualsiasi formato (anche FFmpeg).
 */
class SpectrumProcessor : BaseAudioProcessor() {
    private val n = 2048
    private val hop = 1024
    private val ring = FloatArray(n)
    private var pos = 0
    private var sinceFft = 0
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val win = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }
    private val cosT = FloatArray(n / 2) { cos(2.0 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { -sin(2.0 * PI * it / n).toFloat() }
    private val lo = IntArray(SpectrumBus.BANDS)
    private val hi = IntArray(SpectrumBus.BANDS)
    private val tilt = FloatArray(SpectrumBus.BANDS)
    private var analyze = false
    private var channels = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        analyze = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT   // altri formati: solo passaggio
        channels = max(1, inputAudioFormat.channelCount)
        val bw = inputAudioFormat.sampleRate.toFloat() / n
        val fMin = 40f
        val fMax = min(16000f, inputAudioFormat.sampleRate / 2f * 0.95f)
        val centers = FloatArray(SpectrumBus.BANDS)
        for (i in 0 until SpectrumBus.BANDS) {
            val a = fMin * (fMax / fMin).pow(i / SpectrumBus.BANDS.toFloat())
            val b = fMin * (fMax / fMin).pow((i + 1) / SpectrumBus.BANDS.toFloat())
            lo[i] = max(1, (a / bw).roundToInt())
            hi[i] = max(lo[i], (b / bw).toInt())
            centers[i] = sqrt(a * b)
            tilt[i] = 3f * (ln(centers[i] / 700f) / ln(2f))   // +3 dB/ottava: compensa il calo naturale degli acuti
        }
        SpectrumBus.centers = centers
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (analyze) analyzePcm(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }

    private fun analyzePcm(b: ByteBuffer) {
        val frame = 2 * channels
        while (b.remaining() >= frame) {
            var sum = 0f
            for (c in 0 until channels) sum += b.short / 32768f
            ring[pos] = sum / channels
            pos = (pos + 1) % n
            if (++sinceFft >= hop) { sinceFft = 0; fft() }
        }
    }

    private fun fft() {
        for (i in 0 until n) { re[i] = ring[(pos + i) % n] * win[i]; im[i] = 0f }
        // permutazione bit-reversal
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val wr = cosT[k * step]; val wi = sinT[k * step]
                    val a = i + k; val c = a + half
                    val xr = re[c] * wr - im[c] * wi
                    val xi = re[c] * wi + im[c] * wr
                    re[c] = re[a] - xr; im[c] = im[a] - xi
                    re[a] += xr; im[a] += xi
                }
                i += len
            }
            len = len shl 1
        }
        val out = FloatArray(SpectrumBus.BANDS)
        for (band in 0 until SpectrumBus.BANDS) {
            var m = 0f
            for (k in lo[band]..min(hi[band], n / 2 - 1)) m = max(m, re[k] * re[k] + im[k] * im[k])
            val norm = sqrt(m) / (n / 4f)                    // 1.0 = sinusoide a fondo scala
            val db = 20f * log10(norm + 1e-9f) + tilt[band]
            out[band] = ((db - SpectrumBus.FLOOR_DB) / SpectrumBus.RANGE_DB).coerceIn(0f, 1f)
        }
        SpectrumBus.levels = out
        SpectrumBus.stamp = System.nanoTime()
    }

    private fun clear() {
        ring.fill(0f); pos = 0; sinceFft = 0
        SpectrumBus.levels = FloatArray(SpectrumBus.BANDS)
    }

    override fun onFlush() = clear()
    override fun onReset() = clear()
}
