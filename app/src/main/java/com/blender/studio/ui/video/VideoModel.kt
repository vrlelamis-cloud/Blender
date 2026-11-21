@file:OptIn(UnstableApi::class)

package com.blender.studio.ui.video

import android.text.SpannableString
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import org.json.JSONArray
import org.json.JSONObject

data class Clip(val uri: String, val durationMs: Long, val startMs: Long, val endMs: Long) {
    val lengthMs get() = endMs - startMs
}

data class VideoState(
    val clips: List<Clip>, val speed: Float = 1f, val aspect: String = "Original",
    val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f,
    val rotation: Int = 0, val title: String = "", val mute: Boolean = false,
) {
    fun toJson(): String = JSONObject().put("clips", JSONArray().also { a ->
        clips.forEach { a.put(JSONObject().put("u", it.uri).put("d", it.durationMs).put("s", it.startMs).put("e", it.endMs)) }
    }).put("sp", speed).put("as", aspect).put("b", brightness).put("c", contrast).put("sa", saturation)
        .put("r", rotation).put("t", title).put("m", mute).toString()

    companion object {
        fun fromJson(s: String): VideoState? = runCatching {
            val j = JSONObject(s); val a = j.getJSONArray("clips")
            VideoState(
                (0 until a.length()).map { a.getJSONObject(it) }.map { Clip(it.getString("u"), it.getLong("d"), it.getLong("s"), it.getLong("e")) },
                j.getDouble("sp").toFloat(), j.getString("as"), j.getDouble("b").toFloat(), j.getDouble("c").toFloat(),
                j.getDouble("sa").toFloat(), j.getInt("r"), j.getString("t"), j.getBoolean("m"),
            )
        }.getOrNull()
    }
}

val VideoAspects = listOf("Original", "9:16", "1:1", "16:9")
fun aspectRatio(a: String): Float? = when (a) { "9:16" -> 9f / 16; "1:1" -> 1f; "16:9" -> 16f / 9; else -> null }

/** Presets en un tap : luminosité, contraste (-1..1), saturation (-100..100). */
val VideoPresets = linkedMapOf(
    "Original" to Triple(0f, 0f, 0f), "Cinéma" to Triple(-0.05f, 0.2f, -15f), "Vif" to Triple(0f, 0.15f, 30f),
    "Noir & Blanc" to Triple(0f, 0.2f, -100f), "Doux" to Triple(0.08f, -0.1f, -10f),
)

fun videoEffects(s: VideoState, forExport: Boolean): List<Effect> {
    val l = mutableListOf<Effect>()
    if (s.rotation != 0) l += ScaleAndRotateTransformation.Builder().setRotationDegrees(s.rotation.toFloat()).build()
    if (forExport) {
        when (s.aspect) {
            "9:16" -> l += Presentation.createForWidthAndHeight(720, 1280, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            "1:1" -> l += Presentation.createForWidthAndHeight(1080, 1080, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            "16:9" -> l += Presentation.createForWidthAndHeight(1280, 720, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            else -> if (s.clips.size > 1) l += Presentation.createForWidthAndHeight(1280, 720, Presentation.LAYOUT_SCALE_TO_FIT)
        }
    }
    if (s.brightness != 0f) l += Brightness(s.brightness)
    if (s.contrast != 0f) l += Contrast(s.contrast)
    if (s.saturation != 0f) l += HslAdjustment.Builder().adjustSaturation(s.saturation).build()
    if (forExport) {
        if (s.speed != 1f) l += SpeedChangeEffect(s.speed)
        if (s.title.isNotBlank()) {
            val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, -0.7f).build()
            l += OverlayEffect(ImmutableList.of<TextureOverlay>(TextOverlay.createStaticTextOverlay(SpannableString(s.title), settings)))
        }
    }
    return l
}

/** Assemble tous les clips (découpés) en une séquence exportable : fusion multi-clips. */
fun buildComposition(s: VideoState): Composition {
    val items = s.clips.map { c ->
        val mi = MediaItem.Builder().setUri(c.uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()).build()
        val audio = mutableListOf<AudioProcessor>()
        if (s.speed != 1f) audio += SonicAudioProcessor().apply { setSpeed(s.speed) }
        EditedMediaItem.Builder(mi).setRemoveAudio(s.mute).setEffects(Effects(audio, videoEffects(s, true))).build()
    }
    return Composition.Builder(listOf(EditedMediaItemSequence(items))).build()
}
