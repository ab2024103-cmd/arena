package net.morsecode.shared.net

import java.net.Inet4Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** JmDNS-backed discovery; binds to the Wi-Fi/Ethernet interface (Section 3). */
actual class MdnsDiscovery actual constructor(scope: CoroutineScope) : JmDnsDiscoveryBase(scope) {
    actual override val devices: StateFlow<List<DeviceInfo>> get() = _devices

    actual override fun start(self: SelfProfile, port: Int, roomId: String?) = super.start(self, port, roomId)

    actual override fun updateRoom(roomId: String?) = super.updateRoom(roomId)

    actual override fun stop() = super.stop()

    actual override fun refreshNow() = super.refreshNow()

    override fun bindAddress(): Inet4Address? = firstSiteLocalIPv4()
}
