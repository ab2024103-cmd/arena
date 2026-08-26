package net.morsecode.shared.net

import kotlinx.coroutines.flow.StateFlow

/**
 * mDNS discovery (Section 3). Advertises `_morsecode._tcp.local.` with TXT
 * records and exposes the live list of peers. JmDNS-backed on both platforms.
 */
expect class MdnsDiscovery(scope: kotlinx.coroutines.CoroutineScope) {
    val devices: StateFlow<List<DeviceInfo>>

    /** Starts advertising + browsing. */
    fun start(self: SelfProfile, port: Int, roomId: String? = null)

    /** Updates the advertised TXT (e.g. when a room is created/joined). */
    fun updateRoom(roomId: String?)

    fun stop()

    fun refreshNow()
}

/** QR fallback payload (Section 3): {v:1, device_id, device_name, ip, port, pairing_token}. */
@kotlinx.serialization.Serializable
data class QrPairPayload(
    val v: Int = 1,
    val device_id: String,
    val device_name: String,
    val ip: String,
    val port: Int,
    val pairing_token: String,
)

const val MDNS_SERVICE_TYPE = "_morsecode._tcp.local."
const val DEVICE_LOST_AFTER_MS = 15_000L
