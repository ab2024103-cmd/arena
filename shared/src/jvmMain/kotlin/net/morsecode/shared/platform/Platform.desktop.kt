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
import net.morsecode.shared.media.MediaLibraryDesktop
import net.morsecode.shared.player.AudioPlaybackController
import net.morsecode.shared.player.AudioPlaybackControllerDesktop
import net.morsecode.shared.storage.PlatformDeps

/** Windows dark mode via the AppsUseLightTheme registry value. */
private val windowsSystemDark: Boolean? by lazy {
    if (!System.getProperty("os.name").lowercase().contains("windows")) return@lazy null
    runCatching {
        val p = ProcessBuilder(
            "reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme",
        ).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        when {
            out.contains("0x0") -> true
            out.contains("0x1") -> false
            else -> null
        }
    }.getOrNull()
}

actual fun systemDarkThemeEnabled(): Boolean? = windowsSystemDark

actual val isDesktopPlatform: Boolean = true

actual fun platformDeviceType(): String = "windows"

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
    // Idempotent schema init: rapid relaunches raced the file-existence check
    // and crashed with "table history_entry already exists". Ensure every
    // table exists on every startup instead (matches the .sq definitions).
    val ddl = listOf(
        "CREATE TABLE IF NOT EXISTS transfer_state (transfer_id TEXT NOT NULL, file_id TEXT NOT NULL, batch_id TEXT, peer_device_id TEXT NOT NULL, filename TEXT NOT NULL, total_chunks INTEGER NOT NULL, verified_chunks_bitmap TEXT NOT NULL DEFAULT '', sha256_full TEXT NOT NULL, status TEXT NOT NULL, direction TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (transfer_id, file_id))",
        "CREATE TABLE IF NOT EXISTS chat_message (message_id TEXT NOT NULL PRIMARY KEY, peer_device_id TEXT NOT NULL, text TEXT NOT NULL, direction TEXT NOT NULL, sent_at INTEGER NOT NULL, delivered INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS history_entry (id TEXT NOT NULL PRIMARY KEY, batch_id TEXT, peer_device_id TEXT NOT NULL, peer_name TEXT NOT NULL, filename TEXT NOT NULL, size_bytes INTEGER NOT NULL, direction TEXT NOT NULL, kind TEXT NOT NULL, mime TEXT, source TEXT, path TEXT, status TEXT NOT NULL, ts INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS trusted_device (device_id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, added_at INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS kv (k TEXT NOT NULL PRIMARY KEY, v TEXT NOT NULL)",
    )
    for (stmt in ddl) {
        runCatching { driver.execute(null, stmt, 0) }
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

private suspend fun pickFilesSwing(title: String): List<PickedFile> = withContext(Dispatchers.Main) {
    // JFileChooser (unlike FileDialog) browses all drives / "This PC" and
    // allows multi-select of files and folders. Shown on the EDT (modal).
    val chooser = javax.swing.JFileChooser()
    chooser.dialogTitle = title
    chooser.fileSelectionMode = javax.swing.JFileChooser.FILES_AND_DIRECTORIES
    chooser.isMultiSelectionEnabled = true
    chooser.approveButtonText = "Select"
    val choice = chooser.showOpenDialog(null)
    if (choice != javax.swing.JFileChooser.APPROVE_OPTION) return@withContext emptyList()

    val out = ArrayList<PickedFile>()
    (chooser.selectedFiles ?: emptyArray()).forEach { f ->
        if (f.isFile) {
            out += PickedFile(f.absolutePath, f.name, f.length(), guessMime(f.name))
        } else if (f.isDirectory) {
            // Convenience: include the immediate files of a selected folder.
            f.listFiles()?.filter { it.isFile }?.take(200)?.forEach { sub ->
                out += PickedFile(sub.absolutePath, sub.name, sub.length(), guessMime(sub.name))
            }
        }
        if (out.size >= 500) return@forEach
    }
    out.distinctBy { it.uri }
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

actual suspend fun extractAppApk(app: AppInfo): PickedFile? = null

actual fun installApk(path: String) {
    try {
        val file = if (path.startsWith("file:")) File(java.net.URI(path)) else File(path)
        if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(file.parentFile)
    } catch (e: Exception) { }
}
