package net.morsecode.shared.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** A decoded incoming message. */
data class IncomingMessage(val type: Byte, val payload: ByteArray) {
    inline fun <reified T> decodeAs(): T {
        @Suppress("UNCHECKED_CAST")
        val ser = MsgJson.serializerFor(type) as KSerializer<T>
        return MsgJson.json.decodeFromString(ser, payload.decodeToString())
    }
}

/** Decode a raw handshake frame (pre-connection) into its message type. */
inline fun <reified T> Frame.decodeAs(): T {
    @Suppress("UNCHECKED_CAST")
    val ser = MsgJson.serializerFor(type) as KSerializer<T>
    return MsgJson.json.decodeFromString(ser, payload.decodeToString())
}

object MsgJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serializerFor(type: Byte): KSerializer<out Any> = when (type) {
        MsgType.HELLO -> Hello.serializer()
        MsgType.HELLO_ACK -> HelloAck.serializer()
        MsgType.TRANSFER_REQUEST -> TransferRequest.serializer()
        MsgType.TRANSFER_RESPONSE -> TransferResponse.serializer()
        MsgType.CHUNK_ACK -> ChunkAck.serializer()
        MsgType.CHUNK_NACK -> ChunkNack.serializer()
        MsgType.FILE_COMPLETE -> FileCompleteMsg.serializer()
        MsgType.TRANSFER_COMPLETE -> TransferCompleteMsg.serializer()
        MsgType.TRANSFER_CANCEL -> TransferCancelMsg.serializer()
        MsgType.ERROR -> ErrorMsg.serializer()
        MsgType.PING -> PingMsg.serializer()
        MsgType.PONG -> PongMsg.serializer()
        MsgType.ROOM_ANNOUNCE -> RoomAnnounceMsg.serializer()
        MsgType.ROOM_MEMBER_LIST -> RoomMemberListMsg.serializer()
        MsgType.TEXT_SHARE -> TextShareMsg.serializer()
        MsgType.WINDOW_RESIZE -> WindowResizeMsg.serializer()
        MsgType.CHAT_MESSAGE -> ChatMsgPayload.serializer()
        MsgType.KEY_EXCHANGE -> KeyExchangePayload.serializer()
        else -> throw IllegalArgumentException("unknown message type 0x%02x".format(type))
    }

    fun typeOf(msg: Any): Byte = when (msg) {
        is Hello -> MsgType.HELLO
        is HelloAck -> MsgType.HELLO_ACK
        is TransferRequest -> MsgType.TRANSFER_REQUEST
        is TransferResponse -> MsgType.TRANSFER_RESPONSE
        is ChunkAck -> MsgType.CHUNK_ACK
        is ChunkNack -> MsgType.CHUNK_NACK
        is FileCompleteMsg -> MsgType.FILE_COMPLETE
        is TransferCompleteMsg -> MsgType.TRANSFER_COMPLETE
        is TransferCancelMsg -> MsgType.TRANSFER_CANCEL
        is ErrorMsg -> MsgType.ERROR
        is PingMsg -> MsgType.PING
        is PongMsg -> MsgType.PONG
        is RoomAnnounceMsg -> MsgType.ROOM_ANNOUNCE
        is RoomMemberListMsg -> MsgType.ROOM_MEMBER_LIST
        is TextShareMsg -> MsgType.TEXT_SHARE
        is WindowResizeMsg -> MsgType.WINDOW_RESIZE
        is ChatMsgPayload -> MsgType.CHAT_MESSAGE
        else -> throw IllegalArgumentException("unserializable message ${msg::class.simpleName}")
    }
}

@Suppress("UNCHECKED_CAST")
fun encodeMsg(msg: Any): Pair<Byte, ByteArray> {
    val type = MsgJson.typeOf(msg)
    val ser = MsgJson.serializerFor(type) as KSerializer<Any>
    return type to MsgJson.json.encodeToString(ser, msg).toByteArray()
}

/**
 * An established, encrypted connection. One reader loop pushes incoming
 * messages to a channel; writes are serialized inside FrameWriter.
 */
class MorseConnection(
    val socket: Socket,
    val crypto: SessionCrypto,
    private val scope: CoroutineScope,
    var peer: PeerMeta,
) {
    data class PeerMeta(val deviceId: String, val name: String, val deviceType: String, val appVersion: String)

    private val reader = FrameReader(BufferedInputStream(socket.getInputStream(), 256 * 1024))
    private val writer = FrameWriter(BufferedOutputStream(socket.getOutputStream(), 256 * 1024))
    val incoming = Channel<IncomingMessage>(Channel.UNLIMITED)
    private val closed = AtomicBoolean(false)
    private var readJob: Job? = null
    private var pingJob: Job? = null
    var onClosed: (() -> Unit)? = null

    fun start() {
        readJob = scope.launch(Dispatchers.IO) { readLoop() }
        pingJob = scope.launch(Dispatchers.IO) {
            while (!closed.get()) {
                try { Thread.sleep(10_000) } catch (_: InterruptedException) { break }
                if (closed.get()) break
                runCatching { send(PingMsg(System.currentTimeMillis())) }
            }
        }
    }

    private fun readLoop() {
        try {
            while (!closed.get()) {
                val frame = reader.readFrame(crypto) ?: break
                if (frame.type == MsgType.PING) {
                    val ping = frame.decodeAs<PingMsg>()
                    runCatching { send(PongMsg(ping.ts)) }
                    continue
                }
                if (frame.type == MsgType.PONG) continue
                incoming.trySend(IncomingMessage(frame.type, frame.payload))
            }
        } catch (_: Exception) {
            // connection dropped -> close
        } finally {
            close()
        }
    }

    fun send(msg: Any) {
        if (closed.get()) throw IOException("connection_closed")
        val (type, bytes) = encodeMsg(msg)
        writer.writeFrame(type, bytes, crypto)
    }

    /** Sends a raw payload (used for CHUNK_DATA's binary layout). */
    fun sendRaw(type: Byte, payload: ByteArray) {
        if (closed.get()) throw IOException("connection_closed")
        writer.writeFrame(type, payload, crypto)
    }


    fun isOpen(): Boolean = !closed.get() && socket.isConnected && !socket.isClosed

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        incoming.close()
        readJob?.cancel()
        pingJob?.cancel()
        onClosed?.invoke()
    }
}
