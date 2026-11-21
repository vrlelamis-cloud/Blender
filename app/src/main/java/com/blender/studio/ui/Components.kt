package com.blender.studio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blender.studio.ui.theme.BColors

@Composable
fun EditorTopBar(
    title: String, onBack: () -> Unit,
    canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit,
    advanced: Boolean, onAdvanced: (Boolean) -> Unit, onExport: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onUndo, enabled = canUndo) { Icon(Icons.Rounded.Undo, "Annuler") }
        IconButton(onRedo, enabled = canRedo) { Icon(Icons.Rounded.Redo, "Rétablir") }
        FilterChip(
            selected = advanced, onClick = { onAdvanced(!advanced) }, label = { Text("Avancé") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BColors.AccentSoft, selectedLabelColor = BColors.Accent),
        )
        Spacer(Modifier.width(8.dp))
        Button(onExport, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Exporter") }
        Spacer(Modifier.width(8.dp))
    }
}

data class Tab(val label: String, val icon: ImageVector)

@Composable
fun ToolTabs(tabs: List<Tab>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().background(BColors.Surface).padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(tabs.size) { i ->
            val on = i == selected
            Column(
                Modifier.clip48().clickable { onSelect(i) }
                    .background(if (on) BColors.AccentSoft else Color.Transparent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tabs[i].icon, null, tint = if (on) BColors.Accent else BColors.TextDim, modifier = Modifier.size(22.dp))
                Text(tabs[i].label, style = MaterialTheme.typography.bodySmall, color = if (on) BColors.Accent else BColors.TextDim)
            }
        }
    }
}

private fun Modifier.clip48() = this.defaultMinSize(minWidth = 64.dp, minHeight = 48.dp).then(Modifier)

@Composable
fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String,
    onValue: (Float) -> Unit, onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = BColors.TextDim, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.bodyMedium, color = BColors.Text)
        }
        Slider(value, onValue, valueRange = range, onValueChangeFinished = onDone)
    }
}

@Composable
fun ChipRow(items: List<String>, selected: String?, onSelect: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { name ->
            FilterChip(
                selected = name == selected, onClick = { onSelect(name) }, label = { Text(name) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BColors.AccentSoft, selectedLabelColor = BColors.Accent),
                border = FilterChipDefaults.filterChipBorder(true, name == selected, borderColor = BColors.Outline, selectedBorderColor = BColors.Accent),
            )
        }
    }
}

@Composable
fun ProgressDialog(progress: Float, label: String, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = BColors.SurfaceHi,
        title = { Text(label) },
        text = {
            Column {
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(8.dp), trackColor = BColors.Outline)
                Spacer(Modifier.height(8.dp))
                Text("${(progress * 100).toInt()} %", color = BColors.TextDim)
            }
        },
        confirmButton = { TextButton(onCancel) { Text("Annuler") } },
    )
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(BColors.Surface).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content,
    )
}

@Composable
fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick, enabled = enabled, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BColors.Outline), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label) }
}

@Composable
fun Dot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).background(color, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) BColors.Accent else BColors.Outline, CircleShape)
            .clickable(onClick = onClick)
    )
}
