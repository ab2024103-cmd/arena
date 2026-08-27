package net.morsecode.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.morsecode.shared.platform.AndroidEnv
import net.morsecode.shared.platform.AndroidFilePicker
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.MorseCodeApp

class MainActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var viewModel: AppViewModel? = null

    private val mediaPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // denied storage/media permission -> Library tabs will show empty states
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val notificationsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidEnv.appContext = applicationContext
        AndroidEnv.activity = this
        if (!ServiceLocator.initialized) {
            ServiceLocator.init(
                net.morsecode.shared.platform.buildPlatformDeps(
                    net.morsecode.shared.platform.PlatformContext.from(applicationContext),
                ),
            )
            viewModel = AppViewModel(appScope)
        }
        AndroidFilePicker.register(this)
        MorseForegroundService.ensureStarted(this)

        requestAppPermissions()

        setContent {
            val vm = viewModel ?: return@setContent
            MorseCodeApp(vm)
        }
    }

    override fun onDestroy() {
        if (AndroidEnv.activity === this) AndroidEnv.activity = null
        super.onDestroy()
    }

    /** Runtime permission requests branched per OS version (Section C). */
    private fun requestAppPermissions() {
        val wanted = ArrayList<String>()

        fun need(p: String): Boolean =
            ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= 33) {
            if (need(Manifest.permission.READ_MEDIA_IMAGES)) wanted.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (need(Manifest.permission.READ_MEDIA_VIDEO)) wanted.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (need(Manifest.permission.READ_MEDIA_AUDIO)) wanted.add(Manifest.permission.READ_MEDIA_AUDIO)
            if (need(Manifest.permission.POST_NOTIFICATIONS)) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (Build.VERSION.SDK_INT < 29 && need(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                wanted.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT < 29 && need(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                wanted.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (need(Manifest.permission.CAMERA)) wanted.add(Manifest.permission.CAMERA)

        if (wanted.isNotEmpty()) {
            mediaPermissions.launch(wanted.toTypedArray())
        }
    }

    fun requestCameraIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
