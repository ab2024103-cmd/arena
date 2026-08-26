package net.morsecode.app

import android.content.Context
import android.net.wifi.WifiManager

/** Holds a multicast lock so mDNS works on Android Wi-Fi (Section C). */
object MulticastLockManager {
    @Volatile private var lock: WifiManager.MulticastLock? = null

    fun acquire(context: Context) {
        if (lock != null) return
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val l = wifi.createMulticastLock("morsecode-mdns")
        l.setReferenceCounted(false)
        l.acquire()
        lock = l
    }

    fun release() {
        lock?.let { if (it.isHeld) it.release() }
        lock = null
    }
}
