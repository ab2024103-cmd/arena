package net.morsecode.shared.net

import java.net.Inet4Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** JmDNS-backed discovery; binds to the Wi-Fi/Ethernet interface (Section 3). */
actual class MdnsDiscovery actual constructor(scope: CoroutineScope) {
    private val base = JmDnsDiscoveryBase(scope) { JmDnsDiscoveryBase.firstSiteLocalIPv4() }

    actual val devices: StateFlow<List<DeviceInfo>> get() = base.devices

    actual fun start(self: SelfProfile, port: Int, roomId: String?) = base.start(self, port, roomId)

    actual fun updateRoom(roomId: String?) = base.updateRoom(roomId)

    actual fun stop() = base.stop()

    actual fun refreshNow() = base.refreshNow()
}
