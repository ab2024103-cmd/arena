package net.morsecode.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // desktop: no system back button
}

@Composable
actual fun rememberQrBitmap(content: String, sizePx: Int): ImageBitmap? = remember(content, sizePx) {
    try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix: BitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = ImageBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val white = Paint().apply { color = Color.White }
        val black = Paint().apply { color = Color.Black }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), white)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix[x, y]) {
                    canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, black)
                }
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
