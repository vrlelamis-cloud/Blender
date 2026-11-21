@file:OptIn(UnstableApi::class)

package com.blender.studio.core

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import java.io.File
import java.nio.ByteBuffer

/** Enveloppe Media3 Transformer : export vidéo/audio avec progression et annulation. */
class Exporter(private val ctx: Context) {
    private var transformer: Transformer? = null
    private val holder = ProgressHolder()

    fun start(composition: Composition, out: File, onDone: (Throwable?) -> Unit) {
        if (out.exists()) out.delete()
        val t = Transformer.Builder(ctx)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = onDone(null)
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) = onDone(exportException)
            }).build()
        transformer = t
        t.start(composition, out.absolutePath)
    }

    fun progress(): Float {
        val t = transformer ?: return 0f
        return if (t.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) holder.progress / 100f else 0f
    }

    fun cancel() { transformer?.cancel() }
}

/** Gain (volume) + fondu d'entrée / sortie, appliqué sur du PCM 16 bits. */
class GainFadeProcessor(
    private val gain: Float, private val fadeInUs: Long, private val fadeOutUs: Long, private val totalUs: Long,
) : BaseAudioProcessor() {
    private var frames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val out = replaceOutputBuffer(inputBuffer.remaining())
        val ch = inputAudioFormat.channelCount
        val sr = inputAudioFormat.sampleRate
        while (inputBuffer.remaining() >= 2 * ch) {
            val tUs = frames * 1_000_000L / sr
            var g = gain
            if (fadeInUs > 0 && tUs < fadeInUs) g *= tUs.toFloat() / fadeInUs
            val rem = totalUs - tUs
            if (fadeOutUs > 0 && rem < fadeOutUs) g *= (rem.toFloat() / fadeOutUs).coerceIn(0f, 1f)
            repeat(ch) { out.putShort((inputBuffer.short * g).toInt().coerceIn(-32768, 32767).toShort()) }
            frames++
        }
        out.flip()
    }

    override fun onFlush() { frames = 0 }
}
