package com.blender.studio.ui.image

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    Tab("Filtres", Icons.Rounded.AutoAwesome), Tab("Recadrer", Icons.Rounded.Crop),
    Tab("Ajuster", Icons.Rounded.Tune), Tab("Texte", Icons.Rounded.TextFields),
)
private val ratios = linkedMapOf("Original" to 0f, "1:1" to 1f, "4:5" to 0.8f, "9:16" to 9f / 16f, "16:9" to 16f / 9f, "4:3" to 4f / 3f)

@Composable
fun ImageEditorScreen(screen: Screen.Image, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember {
        val base = screen.state?.let { ImageState.fromJson(it) } ?: ImageState()
        screen.preset?.let { ImageEngine.applyPreset(base, it) } ?: base
    }
    val history = remember { History(initial) }
    val s = history.current
    val uri = remember { screen.uri }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showExport by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val name = remember { Media.displayName(ctx, uri, "Image") }

    LaunchedEffect(uri) { source = withContext(Dispatchers.IO) { ImageEngine.load(ctx, uri, 1280) } }
    LaunchedEffect(source, s) {
        val src = source ?: return@LaunchedEffect
        delay(30)
        preview = withContext(Dispatchers.Default) { ImageEngine.render(src, s) }
    }
    LaunchedEffect(s) { // sauvegarde automatique
        delay(800)
        ProjectStore.save(ctx, Project(screen.id, "image", uri.toString(), name, System.currentTimeMillis(), s.toJson()))
    }

    val visible = if (advanced) tabs.indices.toList() else listOf(0, 1)
    val shownTabs = visible.map { tabs[it] }
    val sel = tab.coerceIn(0, shownTabs.size - 1)

    Column(Modifier.fillMaxSize()) {
        EditorTopBar(name, onBack, history.canUndo, history.canRedo, history::undo, history::redo, advanced, { advanced = it }, { showExport = true })
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val p = preview
            if (p != null) Image(p.asImageBitmap(), "Aperçu", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else CircularProgressIndicator(color = BColors.Accent)
        }
        Panel {
            when (shownTabs[sel].label) {
                "Filtres" -> ChipRow(ImageEngine.presets.keys.toList(), s.preset) { history.commit(ImageEngine.applyPreset(s, it)) }
                "Recadrer" -> {
                    ChipRow(ratios.keys.toList(), ratios.entries.firstOrNull { it.value == s.aspect }?.key) { history.commit(s.copy(aspect = ratios[it] ?: 0f)) }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { SmallAction(Icons.Rounded.RotateLeft, "Gauche", { history.commit(s.copy(rotation = (s.rotation + 270) % 360)) }) }
                        item { SmallAction(Icons.Rounded.RotateRight, "Droite", { history.commit(s.copy(rotation = (s.rotation + 90) % 360)) }) }
                        item { SmallAction(Icons.Rounded.Flip, "Miroir", { history.commit(s.copy(flipH = !s.flipH)) }) }
                    }
                }
                "Ajuster" -> Column(Modifier.heightIn(max = 260.dp)) {
                    LabeledSlider("Luminosité", s.brightness, -0.5f..0.5f, "%+d".format((s.brightness * 100).roundToInt()), { history.preview(s.copy(brightness = it)) }, { history.commit() })
                    LabeledSlider("Contraste", s.contrast, 0.5f..1.8f, "%d%%".format((s.contrast * 100).roundToInt()), { history.preview(s.copy(contrast = it)) }, { history.commit() })
                    LabeledSlider("Saturation", s.saturation, 0f..2f, "%d%%".format((s.saturation * 100).roundToInt()), { history.preview(s.copy(saturation = it)) }, { history.commit() })
                    LabeledSlider("Chaleur", s.warmth, -1f..1f, "%+d".format((s.warmth * 100).roundToInt()), { history.preview(s.copy(warmth = it)) }, { history.commit() })
                    LabeledSlider("Netteté", s.sharpness, 0f..1f, "${(s.sharpness * 100).roundToInt()}", { history.preview(s.copy(sharpness = it)) }, { history.commit() })
                    LabeledSlider("Vignette", s.vignette, 0f..1f, "${(s.vignette * 100).roundToInt()}", { history.preview(s.copy(vignette = it)) }, { history.commit() })
                }
                else -> Column {
                    OutlinedTextField(
                        s.text, { history.commit(s.copy(text = it)) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        label = { Text("Texte à afficher") }, singleLine = true,
                    )
                    LabeledSlider("Taille", s.textSize, 0.03f..0.2f, "${(s.textSize * 100).roundToInt()}", { history.preview(s.copy(textSize = it)) }, { history.commit() })
                    LabeledSlider("Position horizontale", s.textX, 0f..1f, "${(s.textX * 100).roundToInt()}%", { history.preview(s.copy(textX = it)) }, { history.commit() })
                    LabeledSlider("Position verticale", s.textY, 0.05f..1f, "${(s.textY * 100).roundToInt()}%", { history.preview(s.copy(textY = it)) }, { history.commit() })
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(AColor.WHITE, AColor.BLACK, 0xFFFF7A1A.toInt(), 0xFF5B8CFF.toInt(), 0xFF3DDC97.toInt(), 0xFFFF5C93.toInt()).forEach { c ->
                            Dot(Color(c), s.textColor == c) { history.commit(s.copy(textColor = c)) }
                        }
                    }
                }
            }
        }
        ToolTabs(shownTabs, sel) { tab = it }
    }

    if (showExport) ExportImageDialog(onDismiss = { showExport = false }) { fmt, quality ->
        showExport = false; busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val full = ImageEngine.load(ctx, uri, 3000)
                    val out = ImageEngine.render(full, history.current)
                    val (cf, ext, mime) = when (fmt) {
                        "PNG" -> Triple(Bitmap.CompressFormat.PNG, "png", "image/png")
                        "WEBP" -> Triple(Bitmap.CompressFormat.WEBP_LOSSY, "webp", "image/webp")
                        else -> Triple(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
                    }
                    val f = File(ctx.cacheDir, "export.$ext")
                    f.outputStream().use { out.compress(cf, quality, it) }
                    Media.saveFile(ctx, f, mime, "Blender_${System.currentTimeMillis()}.$ext", MediaKind.Image) != null
                }.getOrDefault(false)
            }
            busy = false
            Toast.makeText(ctx, if (ok) "Image enregistrée dans Pictures/Blender" else "Échec de l'export", Toast.LENGTH_LONG).show()
        }
    }
    if (busy) AlertDialog({}, confirmButton = {}, containerColor = BColors.SurfaceHi,
        title = { Text("Export en cours…") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) })
}

@Composable
private fun ExportImageDialog(onDismiss: () -> Unit, onExport: (String, Int) -> Unit) {
    var fmt by remember { mutableStateOf("JPEG") }
    var q by remember { mutableFloatStateOf(92f) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = BColors.SurfaceHi,
        title = { Text("Exporter l'image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("JPEG", "PNG", "WEBP").forEach { f -> FilterChip(fmt == f, { fmt = f }, { Text(f) }) }
                }
                if (fmt != "PNG") { Text("Qualité : ${q.roundToInt()} %", color = BColors.TextDim); Slider(q, { q = it }, valueRange = 50f..100f) }
            }
        },
        confirmButton = { Button({ onExport(fmt, q.roundToInt()) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } },
    )
}
