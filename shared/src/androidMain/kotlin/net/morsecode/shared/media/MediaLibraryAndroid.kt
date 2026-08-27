package net.morsecode.shared.media

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** MediaStore-backed library (Section E.2). */
class MediaLibraryAndroid(private val context: Context) : MediaLibrary {

    override suspend fun getPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI) { c ->
            val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()).toString()
            PhotoItem(
                uri = uri,
                filename = str(c, MediaStore.Images.Media.DISPLAY_NAME) ?: "photo_$id.jpg",
                sizeBytes = lng(c, MediaStore.Images.Media.SIZE),
                dateTakenEpochMs = lng(c, MediaStore.Images.Media.DATE_TAKEN)
                    .takeIf { it > 0 } ?: lng(c, MediaStore.Images.Media.DATE_ADDED) * 1000,
                widthPx = int0(c, MediaStore.Images.Media.WIDTH),
                heightPx = int0(c, MediaStore.Images.Media.HEIGHT),
            )
        }
    }

    override suspend fun getVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI) { c ->
            val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
            val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()).toString()
            VideoItem(
                uri = uri,
                filename = str(c, MediaStore.Video.Media.DISPLAY_NAME) ?: "video_$id.mp4",
                relativePath = str(c, MediaStore.Video.Media.RELATIVE_PATH)
                    ?: str(c, MediaStore.Video.Media.BUCKET_DISPLAY_NAME) ?: "Movies",
                sizeBytes = lng(c, MediaStore.Video.Media.SIZE),
                dateAddedEpochMs = lng(c, MediaStore.Video.Media.DATE_ADDED) * 1000,
                durationMs = lng(c, MediaStore.Video.Media.DURATION),
                thumbnailUri = uri, // Coil can decode a frame from the content URI on Android
            )
        }
    }

    override suspend fun getAudio(): List<AudioItem> = withContext(Dispatchers.IO) {
        queryCollection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) { c ->
            val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()).toString()
            AudioItem(
                uri = uri,
                filename = str(c, MediaStore.Audio.Media.DISPLAY_NAME) ?: "track_$id",
                artist = str(c, MediaStore.Audio.Media.ARTIST),
                album = str(c, MediaStore.Audio.Media.ALBUM),
                sizeBytes = lng(c, MediaStore.Audio.Media.SIZE),
                durationMs = lng(c, MediaStore.Audio.Media.DURATION),
            )
        }
    }

    override suspend fun getAllFiles(): List<GenericFile> = withContext(Dispatchers.IO) {
        val out = ArrayList<GenericFile>()
        // Downloads-ish roots we can walk without extra permissions
        val roots = mutableListOf<File>()
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { roots.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { roots.add(it) }
            context.getExternalFilesDir(null)?.parentFile?.let { roots.add(File(it, "MorseCode")) }
        } catch (e: Exception) { }
        for (root in roots) walk(root, out, depth = 0)
        out
    }

    override suspend fun getStorageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        val dir: File? = try { Environment.getExternalStorageDirectory() } catch (e: Exception) { null }
        if (dir != null && dir.exists()) {
            StorageUsage(dir.totalSpace - dir.usableSpace, dir.totalSpace)
        } else {
            StorageUsage(0, 0)
        }
    }

    override suspend fun shareRoots(): List<String> = listOf("Download/MorseCode")

    private fun <T> queryCollection(uri: Uri, map: (android.database.Cursor) -> T): List<T> {
        val out = ArrayList<T>(256)
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < 5000) out.add(map(c))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun str(c: android.database.Cursor, col: String): String? =
        runCatching { c.getString(c.getColumnIndexOrThrow(col)) }.getOrNull()

    private fun lng(c: android.database.Cursor, col: String): Long =
        runCatching { c.getLong(c.getColumnIndexOrThrow(col)) }.getOrDefault(0L)

    private fun int0(c: android.database.Cursor, col: String): Int =
        runCatching { c.getInt(c.getColumnIndexOrThrow(col)) }.getOrDefault(0)

    private fun walk(dir: File?, out: MutableList<GenericFile>, depth: Int) {
        if (dir == null || depth > 3 || out.size > 5000) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (f.isDirectory) walk(f, out, depth + 1)
            else if (f.isFile && !f.name.startsWith(".")) {
                out.add(
                    GenericFile(
                        uri = f.absolutePath, filename = f.name,
                        relativePath = dir.name, sizeBytes = f.length(),
                        extension = f.extension, modifiedEpochMs = f.lastModified(),
                    ),
                )
            }
        }
    }
}
