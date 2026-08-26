package net.morsecode.shared.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.morsecode.shared.platform.PickedFile

/** Extracts an installed app's base APK so it can be sent (E.4). */
object ApkExtractor {
    /**
     * MVP limitation (documented): only the base APK is transferred; split
     * APKs are not bundled.
     */
    suspend fun extract(context: Context, app: AppInfo): PickedFile? = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(app.packageName, 0)
            val source = info.sourceDir ?: return@withContext null
            val dir = File(context.cacheDir, "apks").apply { mkdirs() }
            val target = File(dir, app.appName.replace(Regex("[^A-Za-z0-9._ \\-]"), "") + ".apk")
            if (!target.exists() || target.length() == 0L) {
                File(source).inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
                }
            }
            PickedFile(
                uri = target.absolutePath,
                displayName = target.name,
                sizeBytes = target.length(),
                mime = "application/vnd.android.package-archive",
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Triggers installation of a received APK (E.4 / Section C). */
object ApkInstaller {
    fun install(context: Context, path: String) {
        try {
            val file = if (path.startsWith("file:")) File(java.net.URI(path)) else File(path)
            val uri: Uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // ignore: unknown-sources setting may be disabled
        }
    }
}
