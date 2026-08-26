package net.morsecode.shared.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import net.morsecode.shared.platform.AndroidEnv
import java.io.ByteArrayOutputStream

/** Decodes a scaled-down JPEG thumbnail for TRANSFER_REQUEST previews. */
actual fun generateThumbnailBase64(uri: String, maxPx: Int): String? = try {
    val resolver = AndroidEnv.appContext.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (uri.startsWith("content:")) {
        resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
    } else {
        val path = if (uri.startsWith("file:")) java.net.URI(uri).path ?: uri else uri
        BitmapFactory.decodeFile(path, bounds)
    }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = if (uri.startsWith("content:")) {
        resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
    } else {
        val path = if (uri.startsWith("file:")) java.net.URI(uri).path ?: uri else uri
        BitmapFactory.decodeFile(path, opts)
    } ?: return null
    val scaled = if (bmp.width > maxPx || bmp.height > maxPx) {
        val ratio = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
        Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
    } else bmp
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
} catch (e: Exception) {
    null
}
