package net.morsecode.shared.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.morsecode.shared.ui.rememberQrBitmap

@Composable
fun QrDisplay(content: String, size: Dp = 240.dp) {
    val bitmap: ImageBitmap? = rememberQrBitmap(content, 640)
    Box(
        modifier = Modifier.size(size).background(
            MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(16.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "QR code", modifier = Modifier.size(size - 12.dp))
        }
    }
}
