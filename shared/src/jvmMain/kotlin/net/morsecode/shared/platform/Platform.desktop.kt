package net.morsecode.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.morsecode.db.MorseDb
import net.morsecode.shared.media.AppInfo
import net.morsecode.shared.player.AudioPlaybackController
import net.morsecode.shared.storage.PlatformDeps

actual val isDesktopPlatform: Boolean = true

actual fun platformDeviceType(): String = "windows"

actual class PlatformContext private constructor() {
    companion object { val INSTANCE = PlatformContext() }
}

/** Desktop data directory: ~/MorseCode */
object DesktopDirs {
    val root: File by lazy {
        val f = File(System.getProperty("user.home"), "MorseCode")
        f.mkdirs()
        f
    }
    val received: File by lazy {
        val f = File(root, "Received")
        f.mkdirs()
        f
    }
    val dbFile: File by lazy { File(root, "morse.db") }
}

actual fun buildPlatformDeps(context: PlatformContext?): PlatformDeps {
    val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:" + DesktopDirs.dbFile.absolutePath)
    if (!DesktopDirs.dbFile.exists() || DesktopDirs.dbFile.length() == 0L) {
        MorseDb.Schema.create(driver)
    }
    return PlatformDeps(
        driver = driver,
        fileAdapter = FileAdapterDesktop(),
        mediaLibrary = MediaLibraryDesktop(),
        appLibrary = null, // no installed-Android-app concept on desktop (Section D)
        audioController = AudioPlaybackControllerDesktop(),
        pickFiles = { title -> pickFilesSwing(title) },
        qrScannerSupported = false,
    )
}

private suspend fun pickFilesSwing(title: String): List<PickedFile> = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.filenameFilter = null
    dialog.isVisible = true
    dialog.files.map { f ->
        PickedFile(
            uri = f.absolutePath,
            displayName = f.name,
            sizeBytes = f.length(),
            mime = guessMime(f.name),
        )
    }
}

fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4", "mkv", "avi", "mov", "webm" -> "video/*"
    "mp3", "wav", "flac", "ogg", "m4a", "aac" -> "audio/*"
    "pdf" -> "application/pdf"
    "apk" -> "application/vnd.android.package-archive"
    else -> "application/octet-stream"
}

class FileAdapterDesktop : FileAdapter {
    override suspend fun open(uri: String): InputStream {
        val path = if (uri.startsWith("file:")) java.net.URI(uri).path ?: uri else uri
        return File(path).inputStream().buffered(256 * 1024)
    }

    override fun incomingSink(displayName: String, sizeBytes: Long, mime: String): IncomingSink {
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ ()\\-]"), "_")
        val target = File(DesktopDirs.received, safeName)
        val raf = java.io.RandomAccessFile(target, "rw")
        try { raf.setLength(sizeBytes) } catch (_: Exception) { }
        return object : IncomingSink {
            override val displayPath: String = target.absolutePath
            override fun writeAt(offset: Long, bytes: ByteArray) {
                raf.seek(offset)
                raf.write(bytes)
            }
            override fun complete(ok: Boolean) {
                try { raf.close() } catch (_: Exception) { }
            }
        }
    }

    override fun verifyFullFile(path: String, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return true
        return try {
            val file = if (path.startsWith("file:")) File(java.net.URI(path)) else File(path)
            net.morsecode.shared.net.Crypto.sha256Hex(file.inputStream()) == expectedSha256
        } catch (e: Exception) {
            false
        }
    }

    override fun receivedDir(): String = DesktopDirs.received.absolutePath
}

actual fun platformCopyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // desktop: no system back button
}

@Composable
actual fun rememberQrBitmap(content: String, sizePx: Int): ImageBitmap? = remember(content, sizePx) {
    try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val img = java.awt.image.BufferedImage(sizePx, sizePx, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (x in 0 until sizePx) for (y in 0 until sizePx) {
            img.setRGB(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
        val out = java.io.ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        org.jetbrains.skia.Image.makeFromEncoded(out.toByteArray()).toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}

actual suspend fun extractAppApk(app: AppInfo): PickedFile? = null

actual fun installApk(path: String) {
    try {
        val file = if (path.startsWith("file:")) File(java.net.URI(path)) else File(path)
        if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(file.parentFile)
    } catch (e: Exception) { }
}
