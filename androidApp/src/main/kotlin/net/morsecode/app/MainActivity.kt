package net.morsecode.app

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.morsecode.shared.platform.AndroidEnv
import net.morsecode.shared.platform.AndroidFilePicker
import net.morsecode.shared.platform.PlatformContext
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.MorseCodeApp
import java.io.File

class MainActivity : ComponentActivity() {

    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e ->
                CrashLog.log(applicationContext, "appScope", e)
            },
    )
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
        val started = try {
            if (!ServiceLocator.initialized) {
                ServiceLocator.init(
                    net.morsecode.shared.platform.buildPlatformDeps(
                        PlatformContext.from(applicationContext),
                    ),
                )
            }
            (viewModel ?: AppViewModel(appScope)).also { viewModel = it }
            true
        } catch (t: Throwable) {
            CrashLog.log(applicationContext, "startup", t)
            showStartupError(t)
            false
        }
        if (!started) return

        AndroidFilePicker.register(this)
        MorseForegroundService.ensureStarted(this)

        requestAppPermissions()

        setContent {
            val vm = viewModel ?: return@setContent
            MorseCodeApp(vm)
        }
    }

    /** Never leave the user on a permanent white screen: show why + a reset. */
    private fun showStartupError(t: Throwable) {
        val ctx = this
        setContentView(
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(64, 128, 64, 64)
                addView(
                    TextView(ctx).apply {
                        text = "Morse Code failed to start"
                        textSize = 20f
                    },
                )
                addView(
                    TextView(ctx).apply {
                        text = "${t.javaClass.simpleName}: ${t.message}\n\n" +
                            "If this keeps happening, reset app data below " +
                            "(your received files are not deleted)."
                        textSize = 14f
                        setPadding(0, 24, 0, 48)
                    },
                )
                addView(
                    Button(ctx).apply {
                        text = "Reset app data and restart"
                        setOnClickListener {
                            runCatching {
                                File(ctx.filesDir, "databases").deleteRecursively()
                                File(ctx.filesDir, "morse.db").delete()
                                getDatabasePath("morse.db").delete()
                            }
                            ctx.recreate()
                        }
                    },
                )
            },
        )
    }

    private fun requestAppPermissions() {
        val needed = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            needed += android.Manifest.permission.POST_NOTIFICATIONS
        }
        if (android.os.Build.VERSION.SDK_INT <= 32) {
            needed += android.Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            needed += listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO,
            )
        }
        needed += android.Manifest.permission.CAMERA
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) mediaPermissions.launch(missing.toTypedArray())
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationsPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        cameraPermission.launch(android.Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            MorseForegroundService.stop(this)
        }
    }
}
