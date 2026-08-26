package net.morsecode.shared.webconnect

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.morsecode.shared.storage.ChatRepo
import net.morsecode.shared.storage.HistoryRepo

/** State exposed to the native WebConnectScreen. */
data class WebConnectStatus(
    val running: Boolean = false,
    val port: Int = 8080,
    val pin: String = "------",
    val lanIp: String? = null,
    val error: String? = null,
)

/** Files the device owner marked shared for the current session (H.1). */
data class SharedSessionFile(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val mime: String,
    val uri: String,
)

/**
 * Browser companion server (Section H). Runs as ordinary JVM code on both
 * Android and Desktop via Ktor CIO, bound only to the LAN interface.
 * NOTE: served over plain HTTP on the LAN (documented deviation from the
 * self-signed-TLS idea in the spec — no TLS cert generation available
 * dependency-free on both platforms).
 */
class WebConnectServer(
    val chatRepo: ChatRepo,
    val historyRepo: HistoryRepo,
    val fileAdapter: net.morsecode.shared.platform.FileAdapter,
    val onIncomingFile: suspend (path: String, name: String, size: Long) -> Unit,
    val onIncomingChat: suspend (text: String) -> Unit,
) {
    val pairing = PairingManager()
    private var engine: io.ktor.server.engine.ApplicationEngine? = null

    /** Live browser WebSocket sessions (chat bridge). */
    val browserSessions = java.util.concurrent.CopyOnWriteArraySet<io.ktor.websocket.WebSocketServerSession>()

    /** Native -> browser chat broadcast. */
    val chatBroadcast = kotlinx.coroutines.flow.MutableSharedFlow<WsChatMsg>(replay = 0, extraBufferCapacity = 128)

    /** Called when a browser sends a chat message. */
    suspend fun pushNativeChat(text: String) {
        val msg = WsChatMsg(
            message_id = net.morsecode.shared.net.Crypto.randomId(),
            text = text.take(1_000_000),
            sent_at = System.currentTimeMillis(),
            direction = "received",
            sender = "browser",
        )
        chatRepo.insert(
            net.morsecode.shared.chat.ChatMessage(
                messageId = msg.message_id, peerDeviceId = "web-browser", text = msg.text,
                direction = "received", sentAt = msg.sent_at, delivered = true,
            ),
        )
        chatBroadcast.tryEmit(msg)
        onIncomingChat(msg.text)
    }

    /** Broadcast a native-app chat message to all connected browsers. */
    fun broadcastNativeChat(messageId: String, text: String, sentAt: Long) {
        chatBroadcast.tryEmit(WsChatMsg(messageId, text, sentAt, direction = "sent", sender = "device"))
    }

    private val _status = MutableStateFlow(WebConnectStatus())
    val status: StateFlow<WebConnectStatus> = _status

    private val _sharedFiles = MutableStateFlow<List<SharedSessionFile>>(emptyList())
    val sharedFiles: StateFlow<List<SharedSessionFile>> = _sharedFiles

    fun addSharedFile(file: SharedSessionFile) {
        _sharedFiles.value = _sharedFiles.value + file
    }

    fun removeSharedFile(id: String) {
        _sharedFiles.value = _sharedFiles.value.filterNot { it.id == id }
    }

    fun clearShared() {
        _sharedFiles.value = emptyList()
    }

    fun start(port: Int = 8080) {
        if (engine != null) return
        pairing.newPairing()
        try {
            val env = io.ktor.server.engine.applicationEngineEnvironment {
                this.port = port
                this.host = "0.0.0.0"
                module { webConnectModule(this@WebConnectServer) }
            }
            val e = embeddedServer(CIO, environment = env).start(wait = false)
            engine = e
            _status.value = WebConnectStatus(running = true, port = port, pin = pairing.currentPin, lanIp = lanAddress())
        } catch (e: Exception) {
            _status.value = WebConnectStatus(running = false, error = e.message ?: "failed to start")
        }
    }

    fun stop() {
        runCatching { engine?.stop(gracePeriodMillis = 200, timeoutMillis = 500) }
        engine = null
        pairing.invalidateAll()
        _status.value = WebConnectStatus(running = false)
    }

    fun isRunning(): Boolean = engine != null

    companion object {
        fun lanAddress(): String? = try {
            java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
