package net.morsecode.shared

import java.net.ServerSocket
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.morsecode.shared.net.Crypto
import net.morsecode.shared.net.DEFAULT_CHUNK_SIZE
import net.morsecode.shared.net.FileManifest
import net.morsecode.shared.net.Handshake
import net.morsecode.shared.net.MorseConnection
import net.morsecode.shared.net.SelfProfile
import net.morsecode.shared.net.TransferReceiver
import net.morsecode.shared.net.TransferRequest
import net.morsecode.shared.net.TransferSender
import net.morsecode.shared.net.TransferResponse
import net.morsecode.shared.net.ChunkSink
import net.morsecode.shared.net.ReceiverDelegate

class TransferLoopbackTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selfA = SelfProfile("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "Sender", "android")
    private val selfB = SelfProfile("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "Receiver", "windows")

    private val chunkSize = 256 * 1024 // small chunks so many fit the window quickly

    /** In-memory sink collecting random-access writes. */
    private class MemorySink(val size: Long, val chunkSize: Int) : ChunkSink {
        val buf = ByteArray(size.toInt())
        override val displayPath: String = "memory://file"
        override fun writeAt(chunkIndex: Int, bytes: ByteArray) {
            bytes.copyInto(buf, chunkIndex * chunkSize)
        }
        override fun complete(ok: Boolean) { }
    }

    private fun makeDelegate(sink: MemorySink, response: TransferResponse) = object : ReceiverDelegate {
        override suspend fun decide(request: TransferRequest) = response
        override suspend fun openSink(manifest: FileManifest, transferId: String) = sink
        override suspend fun onFileFinished(manifest: FileManifest, ok: Boolean, path: String?, reason: String?) { }
        override suspend fun persistVerified(transferId: String, fileId: String, chunkIndex: Int, totalChunks: Int) { }
        override suspend fun resumeOffset(transferId: String, fileId: String) = -1
        override suspend fun verifyFullFile(path: String, expectedSha256: String): Boolean {
            if (expectedSha256.isBlank()) return true
            return Crypto.sha256Hex(sink.buf.inputStream()) == expectedSha256
        }
    }

    private suspend fun loopback(
        fileSize: Int,
        corruptEveryNth: Int = 0,
    ): Boolean {
        val data = ByteArray(fileSize) { Random.Default.nextInt(0, 255).toByte() }
        val totalChunks = (fileSize + chunkSize - 1) / chunkSize
        val fileId = Crypto.randomId()
        val manifest = FileManifest(
            file_id = fileId, filename = "test.bin", size_bytes = fileSize.toLong(),
            mime_type = "application/octet-stream",
            sha256_full = Crypto.sha256Hex(data.inputStream()),
            chunk_size = chunkSize, total_chunks = totalChunks,
        )
        val request = TransferRequest(Crypto.randomId(), null, listOf(manifest))

        val server = ServerSocket(0)
        val port = server.localPort
        val sink = MemorySink(fileSize.toLong(), chunkSize)
        val response = TransferResponse(
            transfer_id = request.transfer_id, decision = "accept_all",
            accepted_file_ids = listOf(fileId), rejected_file_ids = emptyList(), resume_offsets = emptyMap(),
        )

        val responderJob = scope.launch {
            val socket = server.accept()
            val conn = Handshake.respond(scope, socket, selfB) { Handshake.HelloAcceptance(true) }
            val req = conn.incoming.receive().decodeAs<TransferRequest>()
            TransferReceiver(req, conn, conn.incoming, makeDelegate(sink, response)).run()
        }

        val conn = Handshake.initiate(scope, "127.0.0.1", port, selfA, null, isTrustedRequest = false)
        conn.send(request)
        val resp = conn.incoming.receive().decodeAs<TransferResponse>()

        val sender = TransferSender(
            scope, conn, conn.incoming, request, manifest,
            chunkSource = { idx ->
                val from = idx.toLong() * chunkSize
                val to = minOf(from + chunkSize, fileSize.toLong())
                data.copyOfRange(from.toInt(), to.toInt())
            },
            onProgress = { },
            chunkTransformForTest = if (corruptEveryNth > 0) { idx, bytes ->
                if (idx % corruptEveryNth == 0 && bytes.isNotEmpty()) {
                    bytes.copyOf().also { it[0] = (it[0] + 1).toByte() }
                } else bytes
            } else null,
        )
        val result = sender.run()
        assertTrue(result.isSuccess, "sender failed: ${result.exceptionOrNull()}")
        responderJob.join()
        assertEquals(Crypto.sha256Hex(data.inputStream()), Crypto.sha256Hex(sink.buf.inputStream()))
        conn.close(); server.close()
        return true
    }

    @Test
    fun cleanTransferSucceeds() = runBlocking { loopback(2 * 1024 * 1024 + 137) }

    @Test
    fun corruptedChunksAreRecoveredViaNack() = runBlocking {
        // corrupt ~every 3rd chunk: receiver NACKs, sender resends with fresh nonce
        loopback(1024 * 1024, corruptEveryNth = 3)
    }
}
