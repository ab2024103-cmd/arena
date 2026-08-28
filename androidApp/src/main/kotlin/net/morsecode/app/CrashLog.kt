package net.morsecode.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/** Writes crash/exception reports to filesDir/crash.log for diagnosability. */
object CrashLog {
    private const val TAG = "MorseCodeCrash"
    private const val MAX_BYTES = 512 * 1024

    fun log(context: Context?, source: String, t: Throwable) {
        Log.e(TAG, "$source: ${t.javaClass.name}: ${t.message}", t)
        try {
            val dir = context?.filesDir ?: return
            val f = File(dir, "crash.log")
            if (f.length() > MAX_BYTES) f.delete()
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            f.appendText("${Instant.now()} [$source] ${t.javaClass.name}: ${t.message}\n${sw}\n\n")
        } catch (_: Exception) {
        }
    }
}
