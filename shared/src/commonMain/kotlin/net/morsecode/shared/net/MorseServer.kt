package net.morsecode.shared.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Listens on TCP 53317 (or an ephemeral fallback), accepts incoming
 * connections, runs the responder handshake, and hands established sessions
 * to [SessionManager].
 */
class MorseServer(
    private val scope: CoroutineScope,
    private val self: SelfProfile,
    private val sessions: SessionManager,
) {
    var port: Int = 0
        private set
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    /** Policy hook: validate an incoming HELLO (protocol/pairing/trust/auto-accept). */
    var validateHello: ((Hello) -> Handshake.HelloAcceptance) = { Handshake.HelloAcceptance(true) }

    fun start(): Int {
        running = true
        val ss = ServerSocket()
        ss.reuseAddress = true
        try {
            ss.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), DEFAULT_PORT))
        } catch (e: Exception) {
            ss.bind(InetSocketAddress(0)) // ephemeral fallback
        }
        serverSocket = ss
        port = ss.localPort
        scope.launch(Dispatchers.IO) {
            while (running) {
                try {
                    val socket: Socket = ss.accept()
                    handle(socket)
                } catch (e: Exception) {
                    if (running) {
                        try { Thread.sleep(200) } catch (_: InterruptedException) { break }
                    }
                }
            }
        }
        return port
    }

    private fun handle(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            try {
                val conn = Handshake.respond(scope, socket, self) { hello -> validateHello(hello) }
                sessions.register(conn)
            } catch (e: HandshakeRejectedException) {
                // rejected inside respond(); socket already closed
            } catch (e: Exception) {
                runCatching { socket.close() }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
    }

    companion object {
        const val DEFAULT_PORT = 53317
    }
}

/**
 * Tracks active encrypted sessions and routes application-level messages
 * (chat, text share, transfer requests) to registered listeners.
 */
class SessionManager(private val scope: CoroutineScope) {
    private val sessions = ConcurrentHashMap<String, MorseConnection>()
    private val listeners = CopyOnWriteArrayList<(MorseConnection, IncomingMessage) -> Unit>()
    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<Channel<IncomingMessage>>>()

    private val _activePeers = MutableStateFlow<List<MorseConnection.PeerMeta>>(emptyList())
    val activePeers: StateFlow<List<MorseConnection.PeerMeta>> = _activePeers

    fun register(conn: MorseConnection) {
        sessions[conn.peer.deviceId] = conn
        _activePeers.value = sessions.values.map { it.peer }
        conn.onClosed = { sessions.remove(conn.peer.deviceId); _activePeers.value = sessions.values.map { it.peer } }
        scope.launch(Dispatchers.IO) {
            for (msg in conn.incoming) {
                listeners.forEach { runCatching { it(conn, msg) } }
                subscribers[conn.peer.deviceId]?.forEach { runCatching { it.trySend(msg) } }
            }
        }
    }

    /** A private copy-channel of everything arriving on the peer's connection. */
    fun subscribe(deviceId: String): Channel<IncomingMessage> {
        val ch = Channel<IncomingMessage>(Channel.UNLIMITED)
        subscribers.getOrPut(deviceId) { CopyOnWriteArrayList() }.add(ch)
        return ch
    }

    fun unsubscribe(deviceId: String, ch: Channel<IncomingMessage>) {
        subscribers[deviceId]?.remove(ch)
        ch.close()
    }

    fun get(deviceId: String): MorseConnection? = sessions[deviceId]?.takeIf { it.isOpen() }

    fun onMessage(listener: (MorseConnection, IncomingMessage) -> Unit) {
        listeners.add(listener)
    }

    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        _activePeers.value = emptyList()
    }
}
