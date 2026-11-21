package com.blender.studio.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

object Waveform {
    /** Décode la piste audio et retourne `bars` crêtes normalisées (0..1). */
    suspend fun compute(ctx: Context, uri: Uri, bars: Int = 400): FloatArray = withContext(Dispatchers.IO) {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            var idx = -1
            for (i in 0 until ex.trackCount) {
                if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio") == true) { idx = i; break }
            }
            if (idx < 0) return@withContext FloatArray(bars)
            ex.selectTrack(idx)
            val fmt = ex.getTrackFormat(idx)
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 1L
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0); codec.start()
            val peaks = FloatArray(bars)
            val info = MediaCodec.BufferInfo()
            var inEos = false; var outEos = false
            while (!outEos) {
                ensureActive()
                if (!inEos) {
                    val i = codec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inEos = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5_000)
                if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    val sb = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                    var peak = 0
                    while (sb.hasRemaining()) peak = max(peak, abs(sb.get().toInt()))
                    val b = ((info.presentationTimeUs.toDouble() / durUs) * bars).toInt().coerceIn(0, bars - 1)
                    peaks[b] = max(peaks[b], peak / 32768f)
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outEos = true
                }
            }
            val m = peaks.max().coerceAtLeast(0.001f)
            FloatArray(bars) { (peaks[it] / m) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            FloatArray(bars)
        } finally {
            runCatching { codec?.stop(); codec?.release() }
            ex.release()
        }
    }
}
