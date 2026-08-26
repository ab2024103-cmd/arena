package net.morsecode.shared.media

import kotlinx.coroutines.flow.StateFlow

/**
 * Local media library data source (Section E.2). Android: MediaStore-backed.
 * Desktop: filesystem scan of Pictures/Videos/Music + user Shared Folder.
 * (The spec suggests expect/actual; a common interface delivered through the
 * platform deps achieves the same decoupling and is unit-testable.)
 */
interface MediaLibrary {
    suspend fun getPhotos(): List<PhotoItem>
    suspend fun getVideos(): List<VideoItem>
    suspend fun getAudio(): List<AudioItem>
    suspend fun getAllFiles(): List<GenericFile>
    suspend fun getStorageUsage(): StorageUsage

    /** Folders the user may mark "shared this session" for Web Connect. */
    suspend fun shareRoots(): List<String>
}

/** Installed-app enumeration. Android-only meaningful (Section E.2). */
interface AppLibrary {
    suspend fun getInstalledApps(includeSystemApps: Boolean): List<AppInfo>
}
