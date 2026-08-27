package net.morsecode.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.morsecode.shared.media.AppInfo
import net.morsecode.shared.media.AudioItem
import net.morsecode.shared.media.GenericFile
import net.morsecode.shared.media.VideoItem
import net.morsecode.shared.ui.formatBytes
import net.morsecode.shared.ui.formatDuration

@Composable
fun PhotoGridItem(
    uri: String,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.aspectRatio(1f))
        if (selecting) {
            Box(Modifier.matchParentSize().clickable(onClick = onToggle))
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                SelectionOverlayCheckbox(selected = selected)
            }
        }
    }
}

@Composable
fun VideoListItem(
    item: VideoItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = !selecting, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = item.thumbnailUri ?: item.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
            Surface(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
            ) {
                Text(
                    formatDuration(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.filename, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "${item.relativePath} · ${formatBytes(item.sizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        if (selecting) {
            Box(Modifier.clickable(onClick = onToggle).padding(8.dp)) {
                SelectionOverlayCheckbox(selected = selected)
            }
        }
    }
}

@Composable
fun AudioListItem(
    item: AudioItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = !selecting, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(
                Icons.Filled.Audiotrack, contentDescription = null,
                modifier = Modifier.padding(10.dp).size(22.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.filename, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                listOfNotNull(item.artist, item.album).joinToString(" · ").ifEmpty { "Unknown artist" } +
                    " · ${formatDuration(item.durationMs)} · ${formatBytes(item.sizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        if (selecting) {
            Box(Modifier.clickable(onClick = onToggle).padding(8.dp)) {
                SelectionOverlayCheckbox(selected = selected)
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                app.appName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(10.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.appName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "${app.versionName} · ${formatBytes(app.apkSizeBytes)}" +
                    if (app.isSystemApp) " · system" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SelectionOverlayCheckbox(selected = selected, modifier = Modifier.clickable(onClick = onToggle))
    }
}

@Composable
fun FileCategoryCard(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(6.dp).fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text("$count files", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GenericFileRow(
    file: GenericFile,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = !selecting, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (file.extension.lowercase()) {
                "apk" -> Icons.Filled.InsertDriveFile
                "txt", "pdf", "doc", "docx" -> Icons.Filled.Description
                else -> Icons.Filled.Folder
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.filename, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "${file.relativePath} · ${formatBytes(file.sizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        if (selecting) {
            Box(Modifier.clickable(onClick = onToggle).padding(8.dp)) {
                SelectionOverlayCheckbox(selected = selected)
            }
        }
    }
}
