@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

package com.blender.studio.ui.audio

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.blender.studio.Screen
import com.blender.studio.core.*
import com.blender.studio.ui.*
import com.blender.studio.ui.theme.BColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

data class AudioState(
    val uri: String, val durationMs: Long, val startMs: Long, val endMs: Long,
    val speed: Float = 1f, val pitch: Float = 1f, val volume: Float = 1f, val fadeInMs: Long = 0, val fadeOutMs: Long = 0,
) {
    fun toJson(): String = JSONObject().put("u", uri).put("d", durationMs).put("s", startMs).put("e", endMs)
        .put("sp", speed).put("pi", pitch).put("v", volume).put("fi", fadeInMs).put("fo", fadeOutMs).toString()

    companion object {
        fun fromJson(s: String): AudioState? = runCatching {
            val j = JSONObject(s)
            AudioState(j.getString("u"), j.getLong("d"), j.getLong("s"), j.getLong("e"), j.getDouble("sp").toFloat(),
                j.getDouble("pi").toFloat(), j.getDouble("v").toFloat(), j.getLong("fi"), j.getLong("fo"))
        }.getOrNull()
    }
}

private val tabs = listOf(
    Tab("Découpe", Icons.Rounded.ContentCut), Tab("Vitesse", Icons.Rounded.Speed), Tab("Voix", Icons.Rounded.RecordVoiceOver),
    Tab("Volume", Icons.Rounded.VolumeUp), Tab("Fondus", Icons.Rounded.Gradient),
)
private val voices = linkedMapOf("Grave" to 0.8f, "Normal" to 1f, "Aigu" to 1.3f, "Très aigu" to 1.6f, "Monstre" to 0.6f)

@Composable
fun AudioEditorScreen(screen: Screen.Audio, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var initial by remember { mutableStateOf<AudioState?>(null) }
    LaunchedEffect(Unit) {
        initial = withContext(Dispatchers.IO) {
            screen.state?.let { AudioState.fromJson(it) } ?: Media.durationMs(ctx, screen.uri).let { AudioState(screen.uri.toString(), it, 0, it) }
        }
    }
    val i = initial
    if (i == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = BColors.Accent) }
    else AudioEditor(screen.id, i, onBack)
}

@Composable
private fun AudioEditor(projectId: String, initial: AudioState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { History(initial) }
    val s = history.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var peaks by remember { mutableStateOf(FloatArray(0)) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val exporter = remember { Exporter(ctx) }
    val name = remember { Media.displayName(ctx, Uri.parse(initial.uri), "Audio") }

    LaunchedEffect(Unit) { peaks = Waveform.compute(ctx, Uri.parse(initial.uri)) }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(s.startMs, s.endMs) {
        player.setMediaItem(MediaItem.Builder().setUri(s.uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(s.startMs).setEndPositionMs(s.endMs).build()).build())
        player.prepare(); player.playWhenReady = false
    }
    LaunchedEffect(s.speed, s.pitch) { player.playbackParameters = PlaybackParameters(s.speed, s.pitch) }
    LaunchedEffect(s.volume) { player.volume = s.volume.coerceAtMost(1f) }
    LaunchedEffect(player) { while (true) { pos = player.currentPosition; playing = player.isPlaying; delay(80) } }
    LaunchedEffect(s) {
        delay(800)
        ProjectStore.save(ctx, Project(projectId, "audio", s.uri, name, System.currentTimeMillis(), s.toJson()))
    }
    LaunchedEffect(exporting) { while (exporting) { progress = exporter.progress(); delay(200) } }

    var range by remember(s.startMs, s.endMs) { mutableStateOf(s.startMs.toFloat()..s.endMs.toFloat()) }
    val dur = s.durationMs.coerceAtLeast(1)

    Column(Modifier.fillMaxSize()) {
        EditorTopBar(name, onBack, history.canUndo, history.canRedo, history::undo, history::redo, advanced, { advanced = it }) {
            player.pause(); exporting = true; progress = 0f
            val st = history.current
            val total = ((st.endMs - st.startMs) / st.speed * 1000).toLong()
            val audio = mutableListOf<AudioProcessor>(
                SonicAudioProcessor().apply { setSpeed(st.speed); setPitch(st.pitch) },
                GainFadeProcessor(st.volume, st.fadeInMs * 1000, st.fadeOutMs * 1000, total),
            )
            val item = EditedMediaItem.Builder(MediaItem.Builder().setUri(st.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(st.startMs).setEndPositionMs(st.endMs).build()).build())
                .setRemoveVideo(true).setEffects(Effects(audio, emptyList())).build()
            val out = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
            exporter.start(Composition.Builder(listOf(EditedMediaItemSequence(listOf(item)))).build(), out) { err ->
                exporting = false
                if (err == null) scope.launch {
                    val ok = withContext(Dispatchers.IO) { Media.saveFile(ctx, out, "audio/mp4", "Blender_${System.currentTimeMillis()}.m4a", MediaKind.Audio) != null }
                    Toast.makeText(ctx, if (ok) "Audio enregistré dans Music/Blender" else "Échec de l'enregistrement", Toast.LENGTH_LONG).show()
                } else Toast.makeText(ctx, "Export impossible : ${err.message}", Toast.LENGTH_LONG).show()
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(BColors.Surface).padding(12.dp)) {
                if (peaks.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center), color = BColors.Accent)
                else WaveformView(peaks, range.start / dur, range.endInclusive / dur, (s.startMs + pos).toFloat() / dur)
            }
            Spacer(Modifier.height(12.dp))
            Text("${Media.fmt(pos)} / ${Media.fmt(s.endMs - s.startMs)}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FilledIconButton({ if (player.isPlaying) player.pause() else player.play() }, Modifier.size(64.dp)) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Lecture", Modifier.size(34.dp))
            }
        }

        Panel {
            when (tabs[tab].label) {
                "Découpe" -> {
                    RangeSlider(range, { range = it }, Modifier.padding(horizontal = 12.dp), valueRange = 0f..dur.toFloat(), onValueChangeFinished = {
                        val st = range.start.toLong(); val en = range.endInclusive.toLong()
                        if (en - st >= 200) history.commit(s.copy(startMs = st, endMs = en)) else range = s.startMs.toFloat()..s.endMs.toFloat()
                    })
                    Text("${Media.fmt(range.start.toLong())} → ${Media.fmt(range.endInclusive.toLong())}", color = BColors.TextDim,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        SmallAction(Icons.Rounded.ContentCut, "Rogner les silences", {
                            val n = peaks.size
                            val first = peaks.indexOfFirst { it > 0.06f }; val last = peaks.indexOfLast { it > 0.06f }
                            if (n > 0 && first in 0 until last) {
                                val st = (first.toLong() * dur / n).coerceAtLeast(0); val en = ((last + 1).toLong() * dur / n).coerceAtMost(dur)
                                history.commit(s.copy(startMs = st, endMs = en))
                            }
                        }, peaks.isNotEmpty())
                    }
                }
                "Vitesse" -> {
                    ChipRow(listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"), "${s.speed.toString().removeSuffix(".0")}x") { history.commit(s.copy(speed = it.removeSuffix("x").toFloat())) }
                    if (advanced) LabeledSlider("Vitesse", s.speed, 0.5f..2f, "%.2fx".format(s.speed), { history.preview(s.copy(speed = it)) }, { history.commit() })
                }
                "Voix" -> {
                    ChipRow(voices.keys.toList(), voices.entries.firstOrNull { it.value == s.pitch }?.key) { history.commit(s.copy(pitch = voices[it] ?: 1f)) }
                    if (advanced) LabeledSlider("Hauteur de la voix", s.pitch, 0.5f..2f, "%.2fx".format(s.pitch), { history.preview(s.copy(pitch = it)) }, { history.commit() })
                }
                "Volume" -> LabeledSlider("Volume (export jusqu'à 200 %)", s.volume, 0f..2f, "${(s.volume * 100).roundToInt()} %", { history.preview(s.copy(volume = it)) }, { history.commit() })
                else -> {
                    val maxFade = ((s.endMs - s.startMs) / 2).coerceAtLeast(500).toFloat()
                    LabeledSlider("Fondu d'entrée", s.fadeInMs.toFloat(), 0f..maxFade, "%.1f s".format(s.fadeInMs / 1000f), { history.preview(s.copy(fadeInMs = it.toLong())) }, { history.commit() })
                    LabeledSlider("Fondu de sortie", s.fadeOutMs.toFloat(), 0f..maxFade, "%.1f s".format(s.fadeOutMs / 1000f), { history.preview(s.copy(fadeOutMs = it.toLong())) }, { history.commit() })
                }
            }
        }
        ToolTabs(tabs, tab) { tab = it }
    }
    if (exporting) ProgressDialog(progress, "Export de l'audio…") { exporter.cancel(); exporting = false }
}

@Composable
private fun WaveformView(peaks: FloatArray, startF: Float, endF: Float, playF: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val n = peaks.size; val bw = size.width / n
        for (i in 0 until n) {
            val f = i.toFloat() / n
            val h = (peaks[i] * size.height * 0.95f).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                if (f in startF..endF) BColors.Accent else BColors.Outline,
                Offset(i * bw + bw * 0.15f, (size.height - h) / 2), Size(bw * 0.7f, h), CornerRadius(bw * 0.35f),
            )
        }
        val x = (playF.coerceIn(0f, 1f)) * size.width
        drawRect(Color.White, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }
}
