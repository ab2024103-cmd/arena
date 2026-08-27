package net.morsecode.shared.platform

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * SAF-backed multiple-file picker. The launcher is registered by MainActivity;
 * a pending continuation is completed with the results.
 */
object AndroidFilePicker {
    @Volatile private var launcher: ActivityResultLauncher<Array<String>>? = null
    @Volatile private var pending: kotlinx.coroutines.CompletableDeferred<List<PickedFile>>? = null

    fun register(activity: Activity): ActivityResultLauncher<Array<String>> {
        val l = (activity as androidx.activity.ComponentActivity).activityResultRegistry.register(
            "morse-pick-files",
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris: List<Uri> ->
            val ctx = AndroidEnv.appContext
            val files = uris.mapNotNull { uri ->
                try {
                    var name = "file"
                    var size = 0L
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (c.moveToFirst()) {
                            if (ni >= 0) name = c.getString(ni) ?: name
                            if (si >= 0) size = c.getLong(si)
                        }
                    }
                    val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
                    PickedFile(uri.toString(), name, size, mime)
                } catch (e: Exception) {
                    null
                }
            }
            pending?.complete(files)
            pending = null
        }
        launcher = l
        return l
    }

    suspend fun pickFiles(title: String): List<PickedFile> {
        val l = launcher ?: return emptyList()
        val deferred = kotlinx.coroutines.CompletableDeferred<List<PickedFile>>()
        pending = deferred
        try {
            l.launch(arrayOf()) // OpenMultipleDocuments: multiple "*/*" documents
        } catch (e: Exception) {
            pending = null
            deferred.complete(emptyList())
        }
        return deferred.await()
    }
}
