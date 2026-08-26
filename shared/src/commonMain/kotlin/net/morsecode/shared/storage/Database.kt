package net.morsecode.shared.storage

import app.cash.sqldelight.db.SqlDriver
import net.morsecode.db.MorseDb
import net.morsecode.shared.net.Crypto
import net.morsecode.shared.net.SelfProfile
import net.morsecode.shared.platform.platformDeviceType

fun createDatabase(driver: SqlDriver): MorseDb = MorseDb(driver)

/** Central service locator for platform-provided capabilities. */
class PlatformDeps(
    val driver: SqlDriver,
    val fileAdapter: net.morsecode.shared.platform.FileAdapter,
    val mediaLibrary: net.morsecode.shared.media.MediaLibrary,
    val appLibrary: net.morsecode.shared.media.AppLibrary?,
    val audioController: net.morsecode.shared.player.AudioPlaybackController,
    val pickFiles: suspend (title: String) -> List<net.morsecode.shared.platform.PickedFile>,
    val qrScannerSupported: Boolean,
)

object ServiceLocator {
    lateinit var db: MorseDb
        private set
    lateinit var deps: PlatformDeps
        private set
    lateinit var settings: SettingsRepo
        private set
    lateinit var profile: SelfProfile
        private set
    var initialized = false
        private set

    fun init(deps: PlatformDeps) {
        if (initialized) return
        this.deps = deps
        this.db = createDatabase(deps.driver)
        this.settings = SettingsRepo(db)
        val savedName = settings.get(KEY_DEVICE_NAME)
        this.profile = SelfProfile(
            deviceId = settings.get(KEY_DEVICE_ID) ?: Crypto.randomId().also {
                settings.put(KEY_DEVICE_ID, it)
            },
            name = savedName ?: defaultDeviceName(),
            type = platformDeviceType(),
        )
        initialized = true
    }

    private fun defaultDeviceName(): String =
        platformDeviceType().replaceFirstChar { it.uppercase() } + "-" + profileDeviceSuffix()

    private fun profileDeviceSuffix(): String = (1000 + (0..8999).random()).toString()

    fun setDeviceName(name: String) {
        settings.put(KEY_DEVICE_NAME, name)
        profile = profile.copy(name = name)
    }

    const val KEY_DEVICE_ID = "device_id"
    const val KEY_DEVICE_NAME = "device_name"
}
