package net.morsecode.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.MorseCodeApp

/**
 * Startup crash diagnostics: the jpackage launcher exits silently when the app
 * dies before the window shows, so everything is logged to startup.log (next
 * to the exe for portable installs, otherwise ~/MorseCode) and any fatal
 * error is surfaced in a Swing dialog.
 */
private lateinit var startupLog: File
private val dialogShown = java.util.concurrent.atomic.AtomicBoolean(false)

private fun resolveStartupLog(): File {
    val appDir = System.getProperty("app.dir")?.let { File(it) }
    val base = when {
        appDir != null && appDir.isDirectory -> runCatching { appDir.parentFile }.getOrNull()
        else -> null
    } ?: runCatching { File(System.getProperty("user.home"), "MorseCode").apply { mkdirs() } }.getOrNull()
        ?: File(System.getProperty("java.io.tmpdir"))
    return File(base, "startup.log")
}

private fun log(line: String) {
    try {
        startupLog.appendText("${java.time.Instant.now()} $line\n")
    } catch (_: Exception) {
    }
}

private fun showError(message: String) {
    if (!dialogShown.compareAndSet(false, true)) return
    SwingUtilities.invokeLater {
        JOptionPane.showMessageDialog(
            null,
            message,
            "Morse Code",
            JOptionPane.ERROR_MESSAGE,
        )
    }
}

fun main() {
    startupLog = resolveStartupLog()
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        log("UNCAUGHT in thread '${t.name}': ${e::class.qualifiedName}: ${e.message}\n${e.stackTraceToString()}")
        showError(
            "Morse Code hit an unexpected error and must close.\n\n" +
                "${e::class.simpleName}: ${e.message}\n\nDetails written to:\n${startupLog.absolutePath}",
        )
    }
    try {
        log("---- startup (app.dir=${System.getProperty("app.dir")}) ----")
        startApp()
        log("window closed, exiting normally")
    } catch (t: Throwable) {
        log("FATAL: ${t::class.qualifiedName}: ${t.message}\n${t.stackTraceToString()}")
        showError(
            "Morse Code failed to start.\n\n" +
                "${t::class.qualifiedName}: ${t.message}\n\nDetails written to:\n${startupLog.absolutePath}",
        )
        try {
            // Keep the JVM alive briefly so the dialog can be displayed.
            SwingUtilities.invokeAndWait { }
        } catch (_: Exception) {
        }
        kotlin.system.exitProcess(1)
    }
}

private fun startApp() {
    log("initializing platform services")
    ServiceLocator.init(net.morsecode.shared.platform.buildPlatformDeps(null))
    val vm = AppViewModel(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    log("initializing tray icon")
    TrayManager.install(vm)
    log("opening main window")

    application {
        val state = rememberWindowState(width = 1100.dp, height = 760.dp)

        Window(
            onCloseRequest = {
                vm.shutdown()
                TrayManager.remove()
                exitApplication()
            },
            title = "Morse Code",
            state = state,
            icon = TrayManager.windowIconPainter(),
        ) {
            // Native drag-and-drop (Section D): dropped files queue a send.
            DragDropHost(vm)

            MorseCodeApp(vm)

            LaunchedEffect(Unit) {
                log("main window visible")
                FirewallDiagnostics.checkOnce { msg -> vm.toast(msg) }
                VlcDiagnostics.checkOnce { msg -> vm.toast(msg) }
            }
        }
    }
}
