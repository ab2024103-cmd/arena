package net.morsecode.desktop

import java.io.File

/**
 * Windows Registry Run-key autostart management (Section D).
 * Uses `reg add/delete/query` via ProcessBuilder.
 */
object AutostartManager {
    private const val RUN_KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "MorseCode"

    fun isEnabled(): Boolean {
        if (!FirewallDiagnostics.isWindows()) return false
        return try {
            val proc = ProcessBuilder("reg", "query", RUN_KEY, "/v", VALUE_NAME).start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out.contains(VALUE_NAME)
        } catch (e: Exception) {
            false
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (!FirewallDiagnostics.isWindows()) return
        try {
            if (enabled) {
                val jar = locateJar()
                val cmd = "javaw -jar \"$jar\""
                ProcessBuilder("reg", "add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", cmd, "/f").start().waitFor()
            } else {
                ProcessBuilder("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f").start().waitFor()
            }
        } catch (e: Exception) {
            // registry access denied: non-fatal
        }
    }

    private fun locateJar(): String {
        val dir = File(AutostartManager::class.java.protectionDomain.codeSource.location.toURI()).parentFile
        return File(dir, "MorseCode-desktop.jar").absolutePath
    }
}
