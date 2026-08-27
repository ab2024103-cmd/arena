package net.morsecode.shared.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.InputStream
import net.morsecode.db.MorseDb
import net.morsecode.shared.media.ApkExtractor
import net.morsecode.shared.media.ApkInstaller
import net.morsecode.shared.media.AppInfo
import net.morsecode.shared.media.AppLibraryAndroid
import net.morsecode.shared.media.MediaLibraryAndroid
import net.morsecode.shared.media.MediaLibrary
import net.morsecode.shared.player.AudioPlaybackController
import net.morsecode.shared.player.AudioPlaybackControllerAndroid
import net.morsecode.shared.storage.PlatformDeps

actual val isDesktopPlatform: Boolean = false

actual fun platformDeviceType(): String = "android"


/** Global Android context/activity bridge for shared code. */
object AndroidEnv {
    lateinit var appContext: Context
    var activity: android.app.Activity? = null
}

actual fun buildPlatformDeps(context: PlatformContext?): PlatformDeps {
    val ctx = (context?.raw as? Context) ?: AndroidEnv.appContext
    val driver: SqlDriver = AndroidSqliteDriver(MorseDb.Schema, ctx, "morse.db")
    return PlatformDeps(
        driver = driver,
        fileAdapter = FileAdapterAndroid(ctx),
        mediaLibrary = MediaLibraryAndroid(ctx),
        appLibrary = AppLibraryAndroid(ctx),
        audioController = AudioPlaybackControllerAndroid.getInstance(ctx),
        pickFiles = { title -> AndroidFilePicker.pickFiles(title) },
        qrScannerSupported = true,
    )
}

class FileAdapterAndroid(private val context: Context) : FileAdapter {

    override suspend fun open(uri: String): InputStream {
        if (uri.startsWith("content:")) {
            return context.contentResolver.openInputStream(Uri.parse(uri))
                ?: error("cannot open $uri")
        }
        val path = if (uri.startsWith("file:")) java.net.URI(uri).path else uri
        return File(path).inputStream().buffered(256 * 1024)
    }

    override fun incomingSink(displayName: String, sizeBytes: Long, mime: String): IncomingSink {
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ ()\\-]"), "_")
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MorseCode")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed (insufficient_storage)")
            val pfd: ParcelFileDescriptor = resolver.openFileDescriptor(itemUri, "rw")
                ?: error("cannot open destination")
            val channel = java.io.FileOutputStream(pfd.fileDescriptor).channel
            try { channel.truncate(sizeBytes) } catch (_: Exception) { }
            object : IncomingSink {
                override val displayPath: String = "Download/MorseCode/$safeName"
                override fun writeAt(offset: Long, bytes: ByteArray) {
                    channel.position(offset)
                    channel.write(java.nio.ByteBuffer.wrap(bytes))
                }
                override fun complete(ok: Boolean) {
                    try { channel.close() } catch (_: Exception) { }
                    try { pfd.close() } catch (_: Exception) { }
                    try {
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, if (ok) 0 else 1)
                        resolver.update(itemUri, values, null, null)
                        if (ok) {
                            MediaScannerConnection.scanFile(
                                context, arrayOf(displayPath),
                                arrayOf(mime.ifBlank { "application/octet-stream" }), null,
                            )
                        }
                    } catch (_: Exception) { }
                }
            }
        } else {
            val base: File = try {
                val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (pub.canWrite()) File(pub, "MorseCode") else File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MorseCode")
            } catch (e: Exception) {
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MorseCode")
            }
            base.mkdirs()
            val target = File(base, safeName)
            val raf = java.io.RandomAccessFile(target, "rw")
            try { raf.setLength(sizeBytes) } catch (_: Exception) { }
            object : IncomingSink {
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
    }

    override fun verifyFullFile(path: String, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return true
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            if (path.startsWith("content:")) {
                context.contentResolver.openInputStream(Uri.parse(path))?.use { s ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = s.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
                } ?: return true
            } else {
                File(path).inputStream().use { s ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = s.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) } == expectedSha256
        } catch (e: Exception) {
            false
        }
    }

    override fun receivedDir(): String = Environment.DIRECTORY_DOWNLOADS + "/MorseCode"
}

actual suspend fun extractAppApk(app: AppInfo): PickedFile? = ApkExtractor.extract(AndroidEnv.appContext, app)

actual fun installApk(path: String) {
    ApkInstaller.install(AndroidEnv.appContext, path)
}
