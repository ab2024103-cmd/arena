package net.morsecode.shared.media

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val apkSizeBytes: Long,
    val isSystemApp: Boolean,
    val iconUri: String?,
)

data class PhotoItem(
    val uri: String,
    val filename: String,
    val sizeBytes: Long,
    val dateTakenEpochMs: Long,
    val widthPx: Int,
    val heightPx: Int,
)

data class VideoItem(
    val uri: String,
    val filename: String,
    val relativePath: String,
    val sizeBytes: Long,
    val dateAddedEpochMs: Long,
    val durationMs: Long,
    val thumbnailUri: String?,
)

data class AudioItem(
    val uri: String,
    val filename: String,
    val artist: String?,
    val album: String?,
    val sizeBytes: Long,
    val durationMs: Long,
)

data class GenericFile(
    val uri: String,
    val filename: String,
    val relativePath: String,
    val sizeBytes: Long,
    val extension: String,
    val modifiedEpochMs: Long,
)

data class StorageUsage(val usedBytes: Long, val totalBytes: Long)

data class FileCategory(val id: String, val label: String)
