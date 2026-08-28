package net.morsecode.app

import android.app.Application

class MorseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MulticastLockManager.acquire(this)
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            CrashLog.log(this, "uncaught/${t.name}", e)
        }
    }
}
