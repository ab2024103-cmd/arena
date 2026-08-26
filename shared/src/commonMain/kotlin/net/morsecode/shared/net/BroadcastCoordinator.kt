package net.morsecode.shared.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A file the local device wants to send. */
data class OutgoingFile(
    val displayName: String,
    val sizeBytes: Long,
    val mime: String,
    val relativePath: String? = null,
    val thumbnailBase64: String? = null,
    val openChunk: suspend (fileId: String, chunkIndex: Int, chunkSize: Int) -> ByteArray,
)

data class RecipientTransferState(
    val deviceId: String,
    val deviceName: String,
    val state: String, // queued | connecting | transferring | completed | failed | paused
    val percent: Float = 0f,
    val filename: String = "",
    val speedBps: Long = 0,
    val error: String? = null,
)

/**
 * Multi-recipient parallel broadcast (Section 7). One user action -> one
 * batch_id -> one independent TransferSender coroutine per target, capped at
 * MAX_CONCURRENT_RECIPIENTS = 6 simultaneous; overflow is queued.
 */
class BroadcastCoordinator(
    private val scope: CoroutineScope,
    private val self: SelfProfile,
    private val targets: List<DeviceInfo>,
    private val files: List<OutgoingFile>,
    private val pairingToken: String? = null,
    private val isTrusted: (DeviceInfo) -> Boolean = { false },
    private val throttle: TokenBucket? = null,
) {
    val batchId: String = Crypto.randomId()
    private val semaphore = Semaphore(MAX_CONCURRENT_RECIPIENTS)

    private val _states = MutableStateFlow<List<RecipientTransferState>>(
        targets.map { RecipientTransferState(it.deviceId, it.name, "queued") },
    )
    val states: StateFlow<List<RecipientTransferState>> = _states

    val overallPercent: Float
        get() {
            val s = _states.value
            if (s.isEmpty()) return 0f
            return s.map { it.percent }.average().toFloat()
        }

    fun updateState(deviceId: String, transform: (RecipientTransferState) -> RecipientTransferState) {
        val current = _states.value.toMutableList()
        val idx = current.indexOfFirst { it.deviceId == deviceId }
        if (idx >= 0) {
            current[idx] = transform(current[idx])
            _states.value = current
        }
    }

    fun start() {
        for (target in targets) {
            scope.launch {
                semaphore.withPermit { runFor(target) }
            }
        }
    }

    private suspend fun runFor(target: DeviceInfo) {
        updateState(target.deviceId) { it.copy(state = "connecting") }
        try {
            val connection = Handshake.initiate(
                scope, target.ip, target.port, self,
                pairingToken = pairingToken,
                isTrustedRequest = isTrusted(target),
            )
            val transferId = Crypto.randomId()
            val manifests = files.map { f ->
                FileManifest(
                    file_id = Crypto.randomId(),
                    filename = f.displayName,
                    relative_path = f.relativePath,
                    size_bytes = f.sizeBytes,
                    mime_type = f.mime,
                    sha256_full = "",
                    total_chunks = ((f.sizeBytes + (DEFAULT_CHUNK_SIZE - 1)) / DEFAULT_CHUNK_SIZE).toInt().coerceAtLeast(1),
                    thumbnail_base64 = f.thumbnailBase64,
                )
            }
            val request = TransferRequest(transferId, batchId, manifests)
            connection.send(request)
            val response = waitForResponse(connection)
            if (response.decision == "reject_all") {
                connection.close()
                updateState(target.deviceId) { it.copy(state = "rejected") }
                return
            }
            val accepted = response.accepted_file_ids.toHashSet()
            manifests.zip(files).forEach { (m, f) ->
                if (m.file_id !in accepted) return@forEach
                updateState(target.deviceId) { it.copy(state = "transferring", filename = m.filename) }
                val sender = TransferSender(
                    scope, connection, connection.incoming, request, m,
                    chunkSource = { idx -> f.openChunk(m.file_id, idx, m.chunk_size) },
                    onProgress = { p ->
                        val pct = if (m.total_chunks <= 0) 0f else p.verifiedChunks.toFloat() / m.total_chunks
                        updateState(target.deviceId) {
                            it.copy(percent = pct, state = p.state, speedBps = p.speedBps, filename = p.filename)
                        }
                    },
                    throttle = throttle,
                )
                val result = sender.run()
                result.exceptionOrNull()?.let {
                    updateState(target.deviceId) { st ->
                        st.copy(state = if (st.state == "paused") "paused" else "failed", error = it.message)
                    }
                    connection.close()
                    return
                }
            }
            connection.send(TransferCompleteMsg(transferId, ok = true))
            updateState(target.deviceId) { it.copy(state = "completed", percent = 1f) }
            connection.close()
        } catch (e: HandshakeRejectedException) {
            updateState(target.deviceId) { it.copy(state = "failed", error = e.reason) }
        } catch (e: Exception) {
            updateState(target.deviceId) { it.copy(state = "failed", error = e.message) }
        }
    }

    private suspend fun waitForResponse(connection: MorseConnection): TransferResponse {
        // Response may be preceded by nothing else on a fresh connection.
        while (true) {
            val msg = connection.incoming.receiveCatching().getOrNull()
                ?: throw java.io.IOException("connection_closed_awaiting_response")
            return when (msg.type) {
                MsgType.TRANSFER_RESPONSE -> msg.decodeAs<TransferResponse>()
                MsgType.ERROR -> throw TransferAbortedException(msg.decodeAs<ErrorMsg>().code)
                MsgType.HELLO_ACK -> continue
                else -> continue
            }
        }
    }

    companion object {
        const val MAX_CONCURRENT_RECIPIENTS = 6
    }
}
