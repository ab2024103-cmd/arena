package net.morsecode.shared.media

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Filesystem-scan-backed library (Section E.2, desktop): scans the standard
 * Pictures/Videos/Music folders plus the user-configurable Shared Folder,
 * classifying by extension.
 */
class MediaLibraryDesktop : MediaLibrary {

    private val photoExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
    private val videoExt = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg")
    private val audioExt = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "wma", "opus")

    // Session caches: the filesystem scans are expensive (network shares,
    // OneDrive placeholders) and re-running them on every tab switch freezes
    // the UI for minutes. Restart the app (or call refreshLibrary) to rescan.
    @Volatile private var cachedPhotos: List<PhotoItem>? = null
    @Volatile private var cachedVideos: List<VideoItem>? = null
    @Volatile private var cachedAudio: List<AudioItem>? = null
    @Volatile private var cachedFiles: List<GenericFile>? = null

    fun refreshLibrary() {
        cachedPhotos = null; cachedVideos = null; cachedAudio = null; cachedFiles = null
    }

    private fun home(name: String) = File(System.getProperty("user.home"), name)
    private val sharedFolder: File
        get() = File(
            net.morsecode.shared.storage.ServiceLocator.settings.get("shared_folder")
                ?: home("MorseCodeShared").absolutePath,
        ).apply { mkdirs() }

    override suspend fun getPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        cachedPhotos ?: collectMatches(photoExt, limit = 1500).map { f ->
            PhotoItem(
                uri = f.toURI().toString(), filename = f.name, sizeBytes = f.length(),
                dateTakenEpochMs = f.lastModified(), widthPx = 0, heightPx = 0,
            )
        }.also { cachedPhotos = it }
    }

    override suspend fun getVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        cachedVideos ?: collectMatches(videoExt, limit = 1500).map { f ->
            VideoItem(
                uri = f.toURI().toString(), filename = f.name,
                relativePath = f.parentFile?.name ?: "Videos", sizeBytes = f.length(),
                dateAddedEpochMs = f.lastModified(), durationMs = 0, thumbnailUri = null,
            )
        }.also { cachedVideos = it }
    }

    override suspend fun getAudio(): List<AudioItem> = withContext(Dispatchers.IO) {
        cachedAudio ?: collectMatches(audioExt, limit = 1500).map { f ->
            AudioItem(
                uri = f.toURI().toString(), filename = f.name, artist = null, album = null,
                sizeBytes = f.length(), durationMs = 0,
            )
        }.also { cachedAudio = it }
    }

    override suspend fun getAllFiles(): List<GenericFile> = withContext(Dispatchers.IO) {
        cachedFiles ?: run {
            val out = ArrayList<GenericFile>()
            listOf(sharedFolder, home("Downloads"), home("Documents")).forEach { root ->
                walk(root, out, 0)
            }
            out.distinctBy { it.uri }.also { cachedFiles = it }
        }
    }

    override suspend fun getStorageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        val f = sharedFolder
        StorageUsage(f.totalSpace - f.usableSpace, f.totalSpace)
    }

    override suspend fun shareRoots(): List<String> = listOf(sharedFolder.absolutePath)

    fun setSharedFolder(path: String) {
        net.morsecode.shared.storage.ServiceLocator.settings.put("shared_folder", path)
    }

    private fun collectMatches(extensions: Set<String>, limit: Int = 1500): List<File> {
        val out = ArrayList<File>(256)
        val roots = listOf(home("Pictures"), home("Videos"), home("Music"), sharedFolder)
        for (root in roots) walkMatching(root, extensions, out, 0, limit)
        return out
    }

    private fun walkMatching(dir: File?, extensions: Set<String>, out: MutableList<File>, depth: Int, limit: Int) {
        if (dir == null || depth > 3 || out.size >= limit) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (out.size >= limit) return
            if (f.isDirectory) {
                walkMatching(f, extensions, out, depth + 1, limit)
            } else if (f.isFile && f.extension.lowercase() in extensions) {
                out.add(f)
            }
        }
    }

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
