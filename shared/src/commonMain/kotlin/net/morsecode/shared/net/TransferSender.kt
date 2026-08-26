package net.morsecode.shared.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap

data class SenderProgress(
    val transferId: String,
    val fileId: String,
    val filename: String,
    val totalChunks: Int,
    val verifiedChunks: Int,
    val windowSize: Int,
    val state: String, // transferring | paused | completed | failed
    val speedBps: Long = 0,
)

class TransferAbortedException(val reason: String) : Exception(reason)

/**
 * Windowed / pipelined chunk sender (Section 6). One instance per active
 * file transfer. Reads chunk bytes through [chunkSource] so tests can use
 * in-memory data and platforms can use files or content URIs.
 */
class TransferSender(
    private val scope: CoroutineScope,
    private val connection: MorseConnection,
    private val incoming: Channel<IncomingMessage>,
    private val request: TransferRequest,
    private val manifest: FileManifest,
    private val chunkSource: suspend (chunkIndex: Int) -> ByteArray,
    private val onProgress: (SenderProgress) -> Unit,
    private val persistVerified: suspend (chunkIndex: Int) -> Unit = {},
    private val throttle: TokenBucket? = null,
    private val chunkTransformForTest: ((chunkIndex: Int, bytes: ByteArray) -> ByteArray)? = null,
) {
    var windowSize = 4
        private set
    private val maxWindow = 32
    private val minWindow = 1
    private val chunkTimeoutMs = 3000L

    private class InFlight(val index: Int, val sentAtMs: Long)

    private val inFlight = ConcurrentHashMap<Int, Long>()
    private var nextChunkToSend = 0
    private val verifiedCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val nackCounts = ConcurrentHashMap<Int, Int>()
    private val control = Channel<IncomingMessage>(Channel.UNLIMITED)
    private val done = Channel<Result<Unit>>(Channel.UNLIMITED)
    private var sentAtStart = 0L
    private var sentBytes = java.util.concurrent.atomic.AtomicLong(0)

    suspend fun run(): Result<Unit> = withContext(Dispatchers.IO) {
        sentAtStart = System.currentTimeMillis()
        val tapJob = tapIncoming()
        val sweeper = launchSweeper()
        var lastProgress = 0L
        try {
            while (nextChunkToSend < manifest.total_chunks || inFlight.isNotEmpty()) {
                // 1. fill the window
                while (nextChunkToSend < manifest.total_chunks && inFlight.size < windowSize) {
                    val idx = nextChunkToSend
                    sendChunk(idx)
                    inFlight[idx] = System.currentTimeMillis()
                    nextChunkToSend++
                }
                maybeProgress(lastProgress).let { lastProgress = it }
                // 2. wait for control traffic
                val msg = control.receive()
                when (msg.type) {
                    MsgType.CHUNK_ACK -> {
                        val ack = msg.decodeAs<ChunkAck>()
                        if (inFlight.remove(ack.chunk_index) != null) {
                            persistVerified(ack.chunk_index)
                            verifiedCount.incrementAndGet()
                            windowSize = minOf(windowSize + 1, maxWindow)
                            nackCounts.remove(ack.chunk_index)
                        }
                    }
                    MsgType.CHUNK_NACK -> {
                        val nack = msg.decodeAs<ChunkNack>()
                        windowSize = maxOf(windowSize / 2, minWindow)
                        if (inFlight.remove(nack.chunk_index) != null) {
                            val n = (nackCounts[nack.chunk_index] ?: 0) + 1
                            nackCounts[nack.chunk_index] = n
                            if (n >= 3) {
                                abort("max_retries_exceeded")
                            }
                        }
                        resend(nack.chunk_index)
                    }
                    MsgType.ERROR -> {
                        val err = msg.decodeAs<ErrorMsg>()
                        throw TransferAbortedException(err.code)
                    }
                    MsgType.TRANSFER_CANCEL -> throw TransferAbortedException("cancelled_by_receiver")
                    MsgType.WINDOW_RESIZE -> {
                        val wr = msg.decodeAs<WindowResizeMsg>()
                        windowSize = wr.window_size.coerceIn(minWindow, maxWindow)
                    }
                }
            }
            connection.send(FileCompleteMsg(request.transfer_id, manifest.file_id, ok = true))
            onProgress(currentProgress("completed"))
            Result.success(Unit)
        } catch (e: TransferAbortedException) {
            onProgress(currentProgress("failed"))
            Result.failure(e)
        } catch (e: Exception) {
            onProgress(currentProgress("paused"))
            Result.failure(e)
        } finally {
            tapJob.cancel()
            sweeper.cancel()
        }
    }

    private fun abort(reason: String) {
        runCatching { connection.send(FileCompleteMsg(request.transfer_id, manifest.file_id, ok = false, reason = reason)) }
        throw TransferAbortedException(reason)
    }

    private suspend fun sendChunk(idx: Int) {
        val bytes = chunkSource(idx)
        // Digest covers the ORIGINAL bytes; the optional test transform corrupts
        // the payload after digesting so the receiver detects a checksum
        // mismatch and NACKs (used by the loopback corruption test).
        val digest = Crypto.sha256Hex(bytes)
        val payload = chunkTransformForTest?.invoke(idx, bytes) ?: bytes
        val head = java.io.ByteArrayOutputStream(52)
        val id = hexToBytes(manifest.file_id)
        head.write(id)
        writeInt(head, idx)
        head.write(hexToBytes(digest))
        writeInt(head, payload.size)
        val frame = head.toByteArray() + payload
        throttle?.acquire(frame.size)
        connection.sendRaw(MsgType.CHUNK_DATA, frame)
        inFlight[idx] = System.currentTimeMillis()
        sentBytes.addAndGet(payload.size.toLong())
    }

    private suspend fun resend(idx: Int) {
        sendChunk(idx)
    }

    private fun tapIncoming(): Job = scope.launch(Dispatchers.IO) {
        for (msg in incoming) {
            when (msg.type) {
                MsgType.CHUNK_ACK, MsgType.CHUNK_NACK, MsgType.ERROR,
                MsgType.TRANSFER_CANCEL, MsgType.WINDOW_RESIZE,
                -> control.trySend(msg)
                MsgType.TRANSFER_RESPONSE -> { /* handled before sender starts */ }
                else -> Unit
            }
        }
        done.trySend(Result.failure(java.io.IOException("connection_closed")))
    }

    private fun launchSweeper(): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            delay(500)
            val now = System.currentTimeMillis()
            for ((idx, sentAt) in inFlight) {
                if (now - sentAt > chunkTimeoutMs) {
                    // treat as lost: resend with a new nonce, halve window
                    windowSize = maxOf(windowSize / 2, minWindow)
                    if (inFlight.remove(idx) != null) resend(idx)
                }
            }
        }
    }

    private fun currentProgress(state: String): SenderProgress = SenderProgress(
        transferId = request.transfer_id,
        fileId = manifest.file_id,
        filename = manifest.filename,
        totalChunks = manifest.total_chunks,
        verifiedChunks = verifiedCount.get(),
        windowSize = windowSize,
        state = state,
        speedBps = speedBps(),
    )

    private fun speedBps(): Long {
        val elapsed = (System.currentTimeMillis() - sentAtStart) / 1000.0
        if (elapsed < 0.5) return 0
        return (sentBytes.get() / elapsed).toLong()
    }

    private suspend fun maybeProgress(lastProgressMs: Long): Long {
        val now = System.currentTimeMillis()
        if (now - lastProgressMs > 250) onProgress(currentProgress("transferring"))
        return if (now - lastProgressMs > 250) now else lastProgressMs
    }

    companion object {
        fun hexToBytes(hex: String): ByteArray {
            val n = hex.length / 2
            val out = ByteArray(n)
            for (i in 0 until n) out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            return out
        }

        fun writeInt(out: java.io.ByteArrayOutputStream, v: Int) {
            out.write((v ushr 24) and 0xFF); out.write((v ushr 16) and 0xFF)
            out.write((v ushr 8) and 0xFF); out.write(v and 0xFF)
        }
    }
}
