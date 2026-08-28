package net.morsecode.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.imageio.ImageIO
import net.morsecode.shared.ui.AppViewModel

/** System tray icon with Open / Pause Discovery / Quit (Section D). */
object TrayManager {
    private var trayIcon: TrayIcon? = null
    private var discoveryPaused = false

    fun install(vm: AppViewModel) {
        if (!SystemTray.isSupported()) return
        try {
            val image: Image = ImageIO.read(TrayManager::class.java.getResourceAsStream("/tray.png"))
            val popup = PopupMenu()
            val toggle = MenuItem("Pause Discovery")
            val quit = MenuItem("Quit")
            popup.add(toggle); popup.add(quit)
            val icon = TrayIcon(image, "Morse Code", popup)
            icon.isImageAutoSize = true
            toggle.addActionListener {
                discoveryPaused = !discoveryPaused
                toggle.label = if (discoveryPaused) "Resume Discovery" else "Pause Discovery"
                if (discoveryPaused) {
                    vm.discovery.stop()
                } else {
                    vm.discovery.start(vm.profile, 53317, vm.roomManager.room.value?.roomId)
                }
            }
            quit.addActionListener {
                vm.shutdown()
                kotlin.system.exitProcess(0)
            }
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        } catch (e: Exception) {
            // tray unavailable (headless / unsupported DE): non-fatal
        }
    }

    fun remove() {
        trayIcon?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        trayIcon = null
    }

    fun windowIconPainter(): Painter? = try {
        val bytes = TrayManager::class.java.getResourceAsStream("/icon256.png")!!.readBytes()
        BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
    } catch (e: Exception) {
        null
    }
}
