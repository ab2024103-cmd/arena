package net.morsecode.app

import android.app.Application

class MorseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MulticastLockManager.acquire(this)
    }
}
