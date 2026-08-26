package net.morsecode.shared.platform

import net.morsecode.shared.storage.PlatformDeps

expect val isDesktopPlatform: Boolean
expect fun platformDeviceType(): String
expect class PlatformContext
expect fun buildPlatformDeps(context: PlatformContext?): PlatformDeps

/** A file the user picked to send (platform URI on Android, path on Desktop). */
data class PickedFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mime: String,
)

/** Random-access destination for one incoming file. */
interface IncomingSink {
    val displayPath: String
    fun writeAt(offset: Long, bytes: ByteArray)
    fun complete(ok: Boolean)
}

/**
 * Platform file IO adapter: opening picked files for reading, creating
 * incoming-file sinks (MediaStore on API 29+, filesystem fallback otherwise),
 * and full-file verification.
 */
interface FileAdapter {
    suspend fun open(uri: String): java.io.InputStream
    fun incomingSink(displayName: String, sizeBytes: Long, mime: String): IncomingSink
    fun verifyFullFile(path: String, expectedSha256: String): Boolean
    fun receivedDir(): String
}

/** Android: extract an installed app's base APK into a sendable file (E.4). */
expect suspend fun extractAppApk(app: net.morsecode.shared.media.AppInfo): PickedFile?

/** Android: trigger install of a received APK (PermissionsManager path). */
expect fun installApk(path: String)
