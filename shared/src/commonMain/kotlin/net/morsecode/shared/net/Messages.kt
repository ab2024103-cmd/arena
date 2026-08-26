package net.morsecode.shared.net

import kotlinx.serialization.Serializable

const val PROTO_VERSION = 1
const val APP_VERSION = "1.0.0"
const val MAX_PAYLOAD_BYTES = 16 * 1024 * 1024 // 16 MiB mandatory cap
const val DEFAULT_CHUNK_SIZE = 4 * 1024 * 1024

/** Wire message types (Section 2 of the spec). */
object MsgType {
    const val KEY_EXCHANGE: Byte = 0x00
    const val HELLO: Byte = 0x01
    const val HELLO_ACK: Byte = 0x02
    const val TRANSFER_REQUEST: Byte = 0x03
    const val TRANSFER_RESPONSE: Byte = 0x04
    const val CHUNK_DATA: Byte = 0x05
    const val CHUNK_ACK: Byte = 0x06
    const val CHUNK_NACK: Byte = 0x07
    const val FILE_COMPLETE: Byte = 0x08
    const val TRANSFER_COMPLETE: Byte = 0x09
    const val TRANSFER_CANCEL: Byte = 0x0A
    const val ERROR: Byte = 0x0B
    const val PING: Byte = 0x0C
    const val PONG: Byte = 0x0D
    const val ROOM_ANNOUNCE: Byte = 0x0E
    const val ROOM_MEMBER_LIST: Byte = 0x0F
    const val TEXT_SHARE: Byte = 0x10
    const val WINDOW_RESIZE: Byte = 0x11
    const val CHAT_MESSAGE: Byte = 0x12
}

@Serializable
data class KeyExchangePayload(val public_key_base64: String)

@Serializable
data class Hello(
    val device_id: String,
    val device_name: String,
    val device_type: String,
    val app_version: String,
    val proto_version: Int,
    val pairing_token: String? = null,
    val is_trusted_request: Boolean = false,
)

@Serializable
data class HelloAck(
    val device_id: String,
    val device_name: String,
    val device_type: String,
    val app_version: String,
    val proto_version: Int,
    val accepted: Boolean,
    val reason: String? = null,
)

@Serializable
data class FileManifest(
    val file_id: String,
    val filename: String,
    val relative_path: String? = null,
    val size_bytes: Long,
    val mime_type: String,
    val sha256_full: String,
    val chunk_size: Int = DEFAULT_CHUNK_SIZE,
    val total_chunks: Int,
    val thumbnail_base64: String? = null,
)

@Serializable
data class TransferRequest(
    val transfer_id: String,
    val batch_id: String? = null,
    val files: List<FileManifest>,
)

@Serializable
data class TransferResponse(
    val transfer_id: String,
    val decision: String, // accept_all | reject_all | partial
    val accepted_file_ids: List<String> = emptyList(),
    val rejected_file_ids: List<String> = emptyList(),
    val resume_offsets: Map<String, Int> = emptyMap(), // file_id -> last verified chunk index
)

@Serializable
data class ChunkAck(val transfer_id: String, val file_id: String, val chunk_index: Int)

@Serializable
data class ChunkNack(val transfer_id: String, val file_id: String, val chunk_index: Int, val reason: String)

@Serializable
data class FileCompleteMsg(val transfer_id: String, val file_id: String, val ok: Boolean, val reason: String? = null)

@Serializable
data class TransferCompleteMsg(val transfer_id: String, val ok: Boolean, val reason: String? = null)

@Serializable
data class TransferCancelMsg(val transfer_id: String, val reason: String? = null)

@Serializable
data class ErrorMsg(val code: String, val message: String? = null)

@Serializable
data class PingMsg(val ts: Long)

@Serializable
data class PongMsg(val ts: Long)

@Serializable
data class RoomAnnounceMsg(val room_id: String, val room_token: String, val member_device_id: String, val member_name: String)

@Serializable
data class RoomMemberListMsg(val room_id: String, val members: List<RoomMember>)

@Serializable
data class RoomMember(val device_id: String, val name: String)

@Serializable
data class TextShareMsg(val text: String, val sent_at: Long)

@Serializable
data class WindowResizeMsg(val window_size: Int)

@Serializable
data class ChatMsgPayload(
    val message_id: String,
    val text: String,
    val sent_at: Long,
    val sender_device_id: String,
)

/** Identity of a discovered peer (mDNS TXT record + QR fallback). */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val deviceType: String,
    val ip: String,
    val port: Int,
    val appVersion: String = "?",
    val protoVersion: Int = PROTO_VERSION,
    val roomId: String? = null,
    val lastSeenMs: Long = 0L,
)

/** Local identity advertised to peers. */
data class SelfProfile(
    val deviceId: String,
    val name: String,
    val type: String,
    val appVersion: String = APP_VERSION,
    val protoVersion: Int = PROTO_VERSION,
)
