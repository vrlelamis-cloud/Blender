package com.blender.studio.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blender.studio.R
import com.blender.studio.Screen
import com.blender.studio.core.Project
import com.blender.studio.core.ProjectStore
import com.blender.studio.ui.theme.BColors
import kotlinx.coroutines.delay
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

private fun newId() = UUID.randomUUID().toString()

@Composable
fun HomeScreen(tick: Int, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var projects by remember(tick) { mutableStateOf(ProjectStore.all(ctx)) }
    var pendingAspect by remember { mutableStateOf<String?>(null) }
    var pendingPreset by remember { mutableStateOf<String?>(null) }
    var showRecorder by remember { mutableStateOf(false) }

    fun persist(u: Uri) { runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }

    val pickVideos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) { uris.forEach(::persist); go(Screen.Video(uris, pendingAspect, null, newId())) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { persist(uri); go(Screen.Image(uri, pendingPreset, null, newId())) }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persist(uri); go(Screen.Audio(uri, null, newId())) }
    }
    fun startVideo(aspect: String?) { pendingAspect = aspect; pickVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
    fun startImage(preset: String?) { pendingPreset = preset; pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    fun startAudio() = pickAudio.launch(arrayOf("audio/*", "video/*"))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF222838), Color(0xFF07080C))))
                ) { androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Blender", style = MaterialTheme.typography.headlineMedium)
                    Text("Vidéo · Image · Son", color = BColors.TextDim, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard(Icons.Rounded.Movie, "Vidéo", "Timeline, découpe, vitesse, filtres, titres", listOf(Color.White, Color(0xFFD9D9D9))) { startVideo(null) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(Icons.Rounded.Image, "Image", "Retouche, filtres, texte", listOf(Color(0xFF1A1A1A), Color(0xFF0A0A0A)), Modifier.weight(1f)) { startImage(null) }
                    ToolCard(Icons.Rounded.GraphicEq, "Son", "Forme d'onde, voix, fondus", listOf(Color(0xFF1A1A1A), Color(0xFF0A0A0A)), Modifier.weight(1f)) { startAudio() }
                }
                OutlinedButton(
                    onClick = { showRecorder = true }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Rounded.Mic, null); Spacer(Modifier.width(8.dp)); Text("Enregistrer une voix") }
            }
        }
        item { Column {
            SectionTitle("Démarrage rapide")
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { QuickChip("Story 9:16") { startVideo("9:16") } }
                item { QuickChip("Paysage 16:9") { startVideo("16:9") } }
                item { QuickChip("Carré 1:1") { startVideo("1:1") } }
                item { QuickChip("Portrait N&B") { startImage("Noir & Blanc") } }
                item { QuickChip("Look cinéma") { startImage("Cinéma") } }
            }
        } }
        item { SectionTitle("Projets récents") }
        if (projects.isEmpty()) {
            item { Text("Vos projets sont sauvegardés automatiquement et apparaîtront ici.", color = BColors.TextDim, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
        items(projects, key = { it.id }) { p ->
            ProjectRow(p, onOpen = {
                val u = Uri.parse(p.uri)
                when (p.type) {
                    "image" -> go(Screen.Image(u, null, p.state, p.id))
                    "video" -> go(Screen.Video(emptyList(), null, p.state, p.id))
                    else -> go(Screen.Audio(u, p.state, p.id))
                }
            }, onDelete = { ProjectStore.delete(ctx, p.id); projects = ProjectStore.all(ctx) })
        }
    }

    if (showRecorder) RecorderDialog(onDismiss = { showRecorder = false }) { f ->
        showRecorder = false; go(Screen.Audio(Uri.fromFile(f), null, newId()))
    }
}

@Composable
private fun SectionTitle(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))

@Composable
private fun ToolCard(icon: ImageVector, title: String, sub: String, colors: List<Color>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fg = if (colors.first() == Color.White) Color.Black else Color.White
    Box(
        modifier.fillMaxWidth().heightIn(min = 112.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(colors)).border(1.dp, BColors.Outline, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = fg)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, shape = RoundedCornerShape(12.dp),
        leadingIcon = { Icon(Icons.Rounded.Bolt, null, tint = BColors.Accent, modifier = Modifier.size(18.dp)) })
}

@Composable
private fun ProjectRow(p: Project, onOpen: () -> Unit, onDelete: () -> Unit) {
    val icon = when (p.type) { "image" -> Icons.Rounded.Image; "video" -> Icons.Rounded.Movie; else -> Icons.Rounded.GraphicEq }
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BColors.Surface)
            .clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(BColors.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = BColors.Accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.updated)), color = BColors.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onDelete) { Icon(Icons.Rounded.DeleteOutline, "Supprimer", tint = BColors.TextDim) }
    }
}

@Composable
private fun RecorderDialog(onDismiss: () -> Unit, onRecorded: (File) -> Unit) {
    val ctx = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    val file = remember { File(ctx.cacheDir, "voix_${System.currentTimeMillis()}.m4a") }

    fun start() {
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(128_000); r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.prepare(); r.start()
        recorder = r; recording = true
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) start() }
    LaunchedEffect(recording) { seconds = 0; while (recording) { delay(1000); seconds++ } }
    DisposableEffect(Unit) { onDispose { runCatching { recorder?.release() } } }

    AlertDialog(
        onDismissRequest = { if (!recording) onDismiss() },
        containerColor = BColors.SurfaceHi,
        title = { Text("Enregistrement vocal") },
        text = { Text("%d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineMedium, color = if (recording) BColors.Danger else BColors.Text) },
        confirmButton = {
            if (!recording) Button({
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                else perm.launch(Manifest.permission.RECORD_AUDIO)
            }) { Icon(Icons.Rounded.FiberManualRecord, null); Spacer(Modifier.width(6.dp)); Text("Démarrer") }
            else Button({
                runCatching { recorder?.stop() }; recorder?.release(); recorder = null; recording = false; onRecorded(file)
            }, colors = ButtonDefaults.buttonColors(containerColor = BColors.Danger)) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(6.dp)); Text("Terminer") }
        },
        dismissButton = { if (!recording) TextButton(onDismiss) { Text("Fermer") } },
    )
}
