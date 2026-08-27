package net.morsecode.shared.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel

/** Random-access sink for one incoming file (Section 6 receiver algorithm). */
interface ChunkSink {
    val displayPath: String
    fun writeAt(chunkIndex: Int, bytes: ByteArray)
    fun complete(ok: Boolean)
}

data class ReceiverFileState(
    val manifest: FileManifest,
    val accepted: Boolean,
    val resumeFromChunk: Int, // next chunk index to expect (already verified = resume-1)
    var verifiedCount: Int = 0,
)

interface ReceiverDelegate {
    /** Ask the user (or auto-accept policy) what to do with a request. */
    suspend fun decide(request: TransferRequest): TransferResponse

    /** Open a random-access sink for an accepted file. */
    suspend fun openSink(manifest: FileManifest, transferId: String): ChunkSink

    /** Called when a file completes or fails. */
    suspend fun onFileFinished(manifest: FileManifest, ok: Boolean, path: String?, reason: String?)

    /** Persist verified-chunk bitmap (resume support). */
    suspend fun persistVerified(transferId: String, fileId: String, chunkIndex: Int, totalChunks: Int)

    /** Look up already-verified chunks for resume. */
    suspend fun resumeOffset(transferId: String, fileId: String): Int

    /** Full-file SHA-256 verification after the sink is closed. Default trusts per-chunk digests. */
    suspend fun verifyFullFile(path: String, expectedSha256: String): Boolean = true
}

/**
 * Receiver state machine: accepts CHUNK_DATA in any order, verifies SHA-256
 * per chunk, writes to the correct random-access offset, maintains a verified
 * bitmap, and performs the full-file SHA-256 check at the end.
 */
class TransferReceiver(
    private val request: TransferRequest,
    private val connection: MorseConnection,
    private val incoming: Channel<IncomingMessage>,
    private val delegate: ReceiverDelegate,
) {
    private val sinks = HashMap<String, Pair<ChunkSink, ReceiverFileState>>()

    suspend fun run(): Result<List<Pair<FileManifest, String?>>> = withContext(Dispatchers.IO) {
        try {
            // 1. decision
            val acceptedIds = HashSet<String>()
            val resumeOffsets = HashMap<String, Int>()
            for (f in request.files) {
                val offset = delegate.resumeOffset(request.transfer_id, f.file_id)
                if (offset >= f.total_chunks - 1) resumeOffsets[f.file_id] = offset // fully received already
            }
            val response = delegate.decide(request)
            connection.send(response)
            response.accepted_file_ids.forEach { acceptedIds.add(it) }

            // 2. open sinks
            val results = ArrayList<Pair<FileManifest, String?>>()
            val earlyDone = HashSet<String>()
            for (f in request.files) {
                if (f.file_id !in acceptedIds) continue
                val resume = response.resume_offsets[f.file_id] ?: -1
                val sink = delegate.openSink(f, request.transfer_id)
                val state = ReceiverFileState(f, true, resume + 1)
                sinks[f.file_id] = Pair(sink, state)
                if (state.resumeFromChunk >= f.total_chunks) {
                    // already fully received in a previous session
                    finishFile(f.file_id, sinks[f.file_id]!!, results)
                    earlyDone.add(f.file_id)
                }
            }

            // 3. chunk loop
            var pendingFiles = sinks.keys.toHashSet() - earlyDone
            while (pendingFiles.isNotEmpty()) {
                val msg = incoming.receiveCatching().getOrNull() ?: break
                when (msg.type) {
                    MsgType.CHUNK_DATA -> {
                        val chunk = parseChunk(msg.payload) ?: continue
                        val (fileId, index, digest, bytes) = chunk
                        val entry = sinks[fileId] ?: continue
                        val state = entry.second
                        if (Crypto.sha256Hex(bytes) == digest) {
                            entry.first.writeAt(index, bytes)
                            state.verifiedCount++
                            delegate.persistVerified(request.transfer_id, fileId, index, state.manifest.total_chunks)
                            connection.send(ChunkAck(request.transfer_id, fileId, index))
                            if (state.verifiedCount >= state.manifest.total_chunks) {
                                finishFile(fileId, entry, results)
                                pendingFiles.remove(fileId)
                            }
                        } else {
                            connection.send(ChunkNack(request.transfer_id, fileId, index, "checksum_mismatch"))
                        }
                    }
                    MsgType.TRANSFER_CANCEL -> {
                        sinks.forEach { (id, e) -> e.first.complete(false) }
                        pendingFiles.clear()
                    }
                    MsgType.ERROR -> {
                        val err = msg.decodeAs<ErrorMsg>()
                        sinks.forEach { (id, e) -> e.first.complete(false) }
                        pendingFiles.clear()
                    }
                    else -> Unit
                }
            }
            connection.send(TransferCompleteMsg(request.transfer_id, ok = true))
            Result.success(results)
        } catch (e: Exception) {
            sinks.forEach { (_, e2) -> runCatching { e2.first.complete(false) } }
            Result.failure(e)
        }
    }

    private suspend fun finishFile(
        fileId: String,
        entry: Pair<ChunkSink, ReceiverFileState>,
        results: ArrayList<Pair<FileManifest, String?>>,
    ) {
        val (sink, state) = entry
        val fullOk = delegate.verifyFullFile(sink.displayPath, state.manifest.sha256_full)
        sink.complete(fullOk)
        delegate.onFileFinished(state.manifest, fullOk, sink.displayPath, if (fullOk) null else "checksum_mismatch")
        results.add(Pair(state.manifest, if (fullOk) sink.displayPath else null))
    }

    private data class ParsedChunk(
        val fileId: String,
        val index: Int,
        val digest: String,
        val bytes: ByteArray,
    )

    private fun parseChunk(payload: ByteArray): ParsedChunk? {
        if (payload.size < 56) return null
        val fileId = payload.copyOfRange(0, 16).toHex()
        val index = ((payload[16].toInt() and 0xFF) shl 24) or ((payload[17].toInt() and 0xFF) shl 16) or
            ((payload[18].toInt() and 0xFF) shl 8) or (payload[19].toInt() and 0xFF)
        val digest = payload.copyOfRange(20, 52).toHex()
        val len = ((payload[52].toInt() and 0xFF) shl 24) or ((payload[53].toInt() and 0xFF) shl 16) or
            ((payload[54].toInt() and 0xFF) shl 8) or (payload[55].toInt() and 0xFF)
        if (payload.size < 56 + len) return null
        val bytes = payload.copyOfRange(56, 56 + len)
        return ParsedChunk(fileId, index, digest, bytes)
    }
}

