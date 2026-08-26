package net.morsecode.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Renders a QR code bitmap (ZXing -> platform bitmap). */
@Composable
expect fun rememberQrBitmap(content: String, sizePx: Int = 640): ImageBitmap?

/** Android back handling; no-op on desktop. */
@Composable
expect fun AppBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Formats bytes as "1.4 MB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun formatSpeed(bps: Long): String = "${formatBytes(bps)}/s"
