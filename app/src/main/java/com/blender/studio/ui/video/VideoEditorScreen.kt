@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

package com.blender.studio.ui.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.blender.studio.Screen
import com.blender.studio.core.*
import com.blender.studio.ui.*
import com.blender.studio.ui.theme.BColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val tabs = listOf(
    Tab("Clips", Icons.Rounded.ContentCut), Tab("Vitesse", Icons.Rounded.Speed), Tab("Filtres", Icons.Rounded.AutoAwesome),
    Tab("Format", Icons.Rounded.AspectRatio), Tab("Titre", Icons.Rounded.Title), Tab("Audio", Icons.Rounded.VolumeUp),
)

@Composable
fun VideoEditorScreen(screen: Screen.Video, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var initial by remember { mutableStateOf<VideoState?>(null) }
    LaunchedEffect(Unit) {
        initial = withContext(Dispatchers.IO) {
            val restored = screen.state?.let { VideoState.fromJson(it) }
            if (restored != null) restored
            else VideoState(
                clips = screen.uris.map { u -> val d = Media.durationMs(ctx, u); Clip(u.toString(), d, 0, d) },
                aspect = screen.aspect ?: "Original",
            )
        }
    }
    val i = initial
    if (i == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = BColors.Accent) }
    else if (i.clips.isEmpty()) { LaunchedEffect(Unit) { Toast.makeText(ctx, "Projet vide", Toast.LENGTH_SHORT).show(); onBack() } }
    else VideoEditor(screen.id, i, onBack)
}

@Composable
private fun VideoEditor(projectId: String, initial: VideoState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { History(initial) }
    val s = history.current
    var sel by remember { mutableIntStateOf(0) }
    val idx = sel.coerceIn(0, s.clips.lastIndex)
    val clip = s.clips[idx]
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val exporter = remember { Exporter(ctx) }

    // Lecteur
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(clip.uri, clip.startMs, clip.endMs) {
        player.setMediaItem(MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()).build())
        player.prepare(); player.playWhenReady = false
    }
    LaunchedEffect(s.speed) { player.playbackParameters = PlaybackParameters(s.speed) }
    LaunchedEffect(player) { while (true) { pos = player.currentPosition; playing = player.isPlaying; delay(100) } }

    // Sauvegarde automatique
    LaunchedEffect(s) {
        delay(800)
        ProjectStore.save(ctx, Project(projectId, "video", s.clips.first().uri,
            "Projet vidéo · ${s.clips.size} clip${if (s.clips.size > 1) "s" else ""}", System.currentTimeMillis(), s.toJson()))
    }
    LaunchedEffect(exporting) { while (exporting) { progress = exporter.progress(); delay(200) } }

    val addClips = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) { uris.map { u -> val d = Media.durationMs(ctx, u); Clip(u.toString(), d, 0, d) } }
            history.commit(history.current.copy(clips = history.current.clips + added))
        }
    }

    Column(Modifier.fillMaxSize()) {
        EditorTopBar("Vidéo", onBack, history.canUndo, history.canRedo, history::undo, history::redo, advanced, { advanced = it }) {
            player.pause()
            exporting = true; progress = 0f
            val out = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
            exporter.start(buildComposition(history.current), out) { err ->
                exporting = false
                if (err == null) scope.launch {
                    val ok = withContext(Dispatchers.IO) { Media.saveFile(ctx, out, "video/mp4", "Blender_${System.currentTimeMillis()}.mp4", MediaKind.Video) != null }
                    Toast.makeText(ctx, if (ok) "Vidéo enregistrée dans Movies/Blender" else "Échec de l'enregistrement", Toast.LENGTH_LONG).show()
                } else Toast.makeText(ctx, "Export impossible : ${err.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Aperçu
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            val ratio = aspectRatio(s.aspect)
            Box(
                (if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize()).clip(RoundedCornerShape(14.dp)).background(Color.Black)
                    .clickable { if (player.isPlaying) player.pause() else player.play() },
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { c -> PlayerView(c).apply { this.player = player; useController = false } },
                    update = { it.resizeMode = if (ratio != null) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
                    modifier = Modifier.fillMaxSize(),
                )
                if (s.title.isNotBlank()) Text(s.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
                if (!playing) Icon(Icons.Rounded.PlayCircle, "Lire", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(64.dp))
            }
        }

        Panel {
            when (tabs[tab].label) {
                "Clips" -> ClipsPanel(s, idx, pos, history, { sel = it }, { addClips.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, player)
                "Vitesse" -> {
                    ChipRow(listOf("0.25x", "0.5x", "1x", "1.5x", "2x", "4x"), "${s.speed.toString().removeSuffix(".0")}x") { history.commit(s.copy(speed = it.removeSuffix("x").toFloat())) }
                    if (advanced) LabeledSlider("Vitesse personnalisée", s.speed, 0.25f..4f, "%.2fx".format(s.speed), { history.preview(s.copy(speed = it)) }, { history.commit() })
                }
                "Filtres" -> {
                    val cur = VideoPresets.entries.firstOrNull { it.value == Triple(s.brightness, s.contrast, s.saturation) }?.key
                    ChipRow(VideoPresets.keys.toList(), cur) { n -> val p = VideoPresets[n]!!; history.commit(s.copy(brightness = p.first, contrast = p.second, saturation = p.third)) }
                    if (advanced) {
                        LabeledSlider("Luminosité", s.brightness, -0.5f..0.5f, "%+d".format((s.brightness * 100).roundToInt()), { history.preview(s.copy(brightness = it)) }, { history.commit() })
                        LabeledSlider("Contraste", s.contrast, -0.5f..0.5f, "%+d".format((s.contrast * 100).roundToInt()), { history.preview(s.copy(contrast = it)) }, { history.commit() })
                        LabeledSlider("Saturation", s.saturation, -100f..100f, "%+d".format(s.saturation.roundToInt()), { history.preview(s.copy(saturation = it)) }, { history.commit() })
                    }
                }
                "Format" -> {
                    ChipRow(VideoAspects, s.aspect) { history.commit(s.copy(aspect = it)) }
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction(Icons.Rounded.RotateRight, "Pivoter 90°", { history.commit(s.copy(rotation = (s.rotation + 90) % 360)) })
                    }
                    Text("9:16 Stories/Reels/TikTok · 1:1 Instagram · 16:9 YouTube", color = BColors.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                }
                "Titre" -> OutlinedTextField(s.title, { history.commit(s.copy(title = it)) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text("Titre affiché sur la vidéo") }, singleLine = true)
                else -> Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Couper le son d'origine", Modifier.weight(1f)); Switch(s.mute, { history.commit(s.copy(mute = it)) })
                }
            }
        }
        ToolTabs(tabs, tab) { tab = it }
    }
    if (exporting) ProgressDialog(progress, "Export de la vidéo…") { exporter.cancel(); exporting = false }
}

@Composable
private fun ClipsPanel(
    s: VideoState, idx: Int, pos: Long, history: History<VideoState>,
    onSelect: (Int) -> Unit, onAdd: () -> Unit, player: ExoPlayer,
) {
    val ctx = LocalContext.current
    val clip = s.clips[idx]
    var range by remember(clip) { mutableStateOf(clip.startMs.toFloat()..clip.endMs.toFloat()) }
    var thumbs by remember(clip.uri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(clip.uri) {
        thumbs = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, android.net.Uri.parse(clip.uri))
                (0 until 8).mapNotNull { r.getScaledFrameAtTime(clip.durationMs * 1000L * it / 8, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 120, 120) }
            } catch (e: Exception) { emptyList() } finally { r.release() }
        }
    }

    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(s.clips) { i, c ->
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(BColors.SurfaceHi)
                    .border(if (i == idx) 2.dp else 1.dp, if (i == idx) BColors.Accent else BColors.Outline, RoundedCornerShape(10.dp))
                    .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 10.dp),
            ) { Text("${i + 1} · ${Media.fmt(c.lengthMs)}", style = MaterialTheme.typography.bodySmall) }
        }
        item { FilledTonalIconButton(onAdd) { Icon(Icons.Rounded.Add, "Ajouter un clip") } }
    }
    // Pellicule + plage de découpe
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(BColors.SurfaceHi)) {
        Row(Modifier.fillMaxSize()) { thumbs.forEach { Image(it.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop) } }
    }
    RangeSlider(
        range, { range = it }, Modifier.padding(horizontal = 12.dp),
        valueRange = 0f..clip.durationMs.toFloat().coerceAtLeast(1f),
        onValueChangeFinished = {
            val st = range.start.toLong(); val en = range.endInclusive.toLong()
            if (en - st >= 200) history.commit(s.copy(clips = s.clips.toMutableList().also { it[idx] = clip.copy(startMs = st, endMs = en) }))
            else range = clip.startMs.toFloat()..clip.endMs.toFloat()
        },
    )
    Text("${Media.fmt(pos)} / ${Media.fmt(clip.lengthMs)}   ·   total ${Media.fmt(s.clips.sumOf { it.lengthMs })}",
        color = BColors.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SmallAction(Icons.Rounded.CallSplit, "Scinder", {
                val abs = clip.startMs + player.currentPosition
                if (abs > clip.startMs + 200 && abs < clip.endMs - 200) {
                    val l = s.clips.toMutableList(); l[idx] = clip.copy(endMs = abs); l.add(idx + 1, clip.copy(startMs = abs))
                    history.commit(s.copy(clips = l))
                }
            })
        }
        item { SmallAction(Icons.Rounded.ArrowBack, "Avant", { if (idx > 0) { val l = s.clips.toMutableList(); l.add(idx - 1, l.removeAt(idx)); onSelect(idx - 1); history.commit(s.copy(clips = l)) } }, idx > 0) }
        item { SmallAction(Icons.Rounded.ArrowForward, "Après", { if (idx < s.clips.lastIndex) { val l = s.clips.toMutableList(); l.add(idx + 1, l.removeAt(idx)); onSelect(idx + 1); history.commit(s.copy(clips = l)) } }, idx < s.clips.lastIndex) }
        item { SmallAction(Icons.Rounded.DeleteOutline, "Supprimer", { if (s.clips.size > 1) { onSelect(0); history.commit(s.copy(clips = s.clips.filterIndexed { i, _ -> i != idx })) } }, s.clips.size > 1) }
    }
}
