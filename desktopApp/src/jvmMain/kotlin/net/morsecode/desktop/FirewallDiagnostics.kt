package net.morsecode.desktop

import java.io.File
import kotlin.concurrent.thread
import net.morsecode.shared.ui.AppViewModel

/**
 * netsh-based Windows Firewall detection + remediation banner (Section D).
 * Non-Windows platforms are skipped silently.
 */
object FirewallDiagnostics {
    private var checked = false

    fun checkOnce(onIssue: (String) -> Unit) {
        if (checked) return
        checked = true
        if (!isWindows()) return
        thread(isDaemon = true) {
            try {
                val proc = ProcessBuilder(
                    "netsh", "advfirewall", "firewall", "show", "rule", "name=MorseCode",
                ).start()
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                if (!output.contains("MorseCode", ignoreCase = true) || proc.exitValue() != 0) {
                    onIssue(
                        "Windows Firewall may block Morse Code. To allow incoming transfers, run once as admin: " +
                            "netsh advfirewall firewall add rule name=\"MorseCode\" dir=in action=allow protocol=TCP localport=53317",
                    )
                }
            } catch (e: Exception) {
                // netsh unavailable or non-Windows: skip
            }
        }
    }

    fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")
}

/** VLC presence check for the desktop media players (Section D note). */
object VlcDiagnostics {
    fun checkOnce(onIssue: (String) -> Unit) {
        val candidates = listOf(
            File("C:/Program Files/VideoLAN/VLC/libvlc.dll"),
            File("C:/Program Files (x86)/VideoLAN/VLC/libvlc.dll"),
        )
        if (candidates.none { it.exists() }) {
            onIssue(
                "VLC Media Player was not found. Videos/audio playback needs VLC installed: https://www.videolan.org/",
            )
        }
    }
}
