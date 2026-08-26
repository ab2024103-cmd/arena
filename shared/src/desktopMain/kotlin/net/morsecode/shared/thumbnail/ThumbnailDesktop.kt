package net.morsecode.shared.thumbnail

import net.morsecode.shared.platform.guessMime
import java.io.File
import javax.imageio.ImageIO

/** Desktop thumbnail generation via ImageIO. */
actual fun generateThumbnailBase64(uri: String, maxPx: Int): String? = try {
    val path = if (uri.startsWith("file:")) java.net.URI(uri).path ?: uri else uri
    val img = ImageIO.read(File(path)) ?: return null
    val ratio = maxPx.toFloat() / maxOf(img.width, img.height)
    val scaled = if (ratio < 1f) {
        val target = java.awt.image.BufferedImage(
            (img.width * ratio).toInt().coerceAtLeast(1),
            (img.height * ratio).toInt().coerceAtLeast(1),
            java.awt.image.BufferedImage.TYPE_INT_RGB,
        )
        val g = target.createGraphics()
        g.drawImage(img, 0, 0, target.width, target.height, null)
        g.dispose()
        target
    } else img
    val out = java.io.ByteArrayOutputStream()
    ImageIO.write(scaled, "jpg", out)
    java.util.Base64.getEncoder().encodeToString(out.toByteArray())
} catch (e: Exception) {
    null
}
