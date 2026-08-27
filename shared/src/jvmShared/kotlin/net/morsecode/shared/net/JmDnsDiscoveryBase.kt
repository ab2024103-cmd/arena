package net.morsecode.shared.net

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * JmDNS implementation shared by the Android and Desktop actuals (Section 3).
 * Advertises `_morsecode._tcp.local.` and prunes devices unseen for 15s.
 */
class JmDnsDiscoveryBase(
    protected val scope: CoroutineScope,
    private val bindAddressProvider: () -> Inet4Address?,
) {
    protected val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> get() = _devices

    private var jmdns: JmDNS? = null
    private var pruneJob: kotlinx.coroutines.Job? = null
    private val known = ConcurrentHashMap<String, DeviceInfo>()
    private var selfId: String = ""
    @Volatile private var currentRoomId: String? = null
    @Volatile private var advertisingPort: Int = MorseServer.DEFAULT_PORT
    @Volatile private var self: SelfProfile? = null

    fun start(self: SelfProfile, port: Int, roomId: String?) {
        this.self = self
        this.selfId = self.deviceId
        this.advertisingPort = port
        this.currentRoomId = roomId
        val addr = bindAddressProvider() ?: return
        try {
            val jm = JmDNS.create(addr, "morse-" + self.deviceId.take(8))
            jmdns = jm

            val txt = mapOf(
                "device_id" to self.deviceId,
                "device_name" to self.name,
                "device_type" to self.type,
                "app_version" to self.appVersion,
                "proto_version" to self.protoVersion.toString(),
                "room_id" to (roomId ?: ""),
            )
            val info = ServiceInfo.create(
                MDNS_SERVICE_TYPE, "morse-" + self.deviceId.take(12), port, 0, 0, txt,
            )
            jm.registerService(info)

            jm.addServiceListener(MDNS_SERVICE_TYPE, object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    // request full resolution (async; resolved() will follow)
                    runCatching { jm.requestServiceInfo(event.type, event.name, true, 3000) }
                }

                override fun serviceResolved(event: ServiceEvent) {
                    val svc = event.info ?: return
                    val id = svc.getPropertyString("device_id") ?: return
                    if (id == selfId) return
                    val host = svc.inetAddresses.firstOrNull { it is Inet4Address } ?: return
                    val device = DeviceInfo(
                        deviceId = id,
                        name = svc.getPropertyString("device_name") ?: event.name,
                        deviceType = svc.getPropertyString("device_type") ?: "unknown",
                        ip = host.hostAddress ?: return,
                        port = svc.port,
                        appVersion = svc.getPropertyString("app_version") ?: "?",
                        protoVersion = svc.getPropertyString("proto_version")?.toIntOrNull() ?: PROTO_VERSION,
                        roomId = svc.getPropertyString("room_id")?.takeIf { it.isNotBlank() },
                        lastSeenMs = System.currentTimeMillis(),
                    )
                    known[id] = device
                    publish()
                }

                override fun serviceRemoved(event: ServiceEvent) {
                    val id = svcId(event) ?: return
                    known.remove(id)
                    publish()
                }

                private fun svcId(event: ServiceEvent): String? =
                    runCatching { event.info?.getPropertyString("device_id") }.getOrNull()
                        ?: event.name.removePrefix("morse-")
            })

            pruneJob = scope.launch(Dispatchers.IO) {
                while (true) {
                    delay(5000)
                    // re-announce periodically is built into JmDNS; prune stale peers
                    val now = System.currentTimeMillis()
                    known.entries.removeIf { now - it.value.lastSeenMs > DEVICE_LOST_AFTER_MS }
                    publish()
                    // touch our own advertisement with fresh room id
                    runCatching {
                        self?.let {
                            val t = mapOf(
                                "device_id" to it.deviceId, "device_name" to it.name,
                                "device_type" to it.type, "app_version" to it.appVersion,
                                "proto_version" to it.protoVersion.toString(),
                                "room_id" to (currentRoomId ?: ""),
                            )
                            jm.registerService(ServiceInfo.create(MDNS_SERVICE_TYPE, "morse-" + it.deviceId.take(12), advertisingPort, 0, 0, t))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // mDNS unavailable; discovery empty (manual connect still works)
        }
    }

    fun updateRoom(roomId: String?) {
        currentRoomId = roomId
    }

    fun refreshNow() = publish()

    fun stop() {
        pruneJob?.cancel()
        runCatching { jmdns?.unregisterAllServices() }
        runCatching { jmdns?.close() }
        jmdns = null
        known.clear()
        publish()
    }

    protected fun publish() {
        _devices.value = known.values.sortedBy { it.name.lowercase() }
    }

    companion object {
        fun firstSiteLocalIPv4(): Inet4Address? {
            val interfaces: Enumeration<NetworkInterface> = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrDefault(
                java.util.Collections.emptyEnumeration(),
            )
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) return addr
                }
            }
            return null
        }
    }
}
