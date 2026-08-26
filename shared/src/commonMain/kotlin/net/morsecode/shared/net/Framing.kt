package net.morsecode.shared.net

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FrameTooLargeException : IOException("frame_too_large")
class DecryptionFailedException(cause: Throwable) : IOException("decryption_failed", cause)

data class Frame(val type: Byte, val payload: ByteArray)

/**
 * Wire framing:
 *  PRE-HANDSHAKE (type 0x00 only, plaintext):
 *    [4B len][1B type=0x00][N bytes JSON]
 *  POST-HANDSHAKE (encrypted):
 *    [4B len][1B type][12B gcm nonce][N ct][16B tag]
 * len covers everything after the length field. Max decrypted payload 16 MiB.
 */
class FrameReader(private val input: InputStream) {
    private val din = DataInputStream(input)

    /** @return next frame, or null on clean EOF. */
    @Throws(FrameTooLargeException::class, DecryptionFailedException::class)
    fun readFrame(crypto: SessionCrypto?): Frame? {
        val len = try {
            din.readInt()
        } catch (e: EOFException) {
            return null
        }
        if (len < 1 || len > MAX_PAYLOAD_BYTES + 1024) throw FrameTooLargeException()
        val body = ByteArray(len)
        din.readFully(body)
        val type = body[0]
        return if (type == MsgType.KEY_EXCHANGE || crypto == null) {
            Frame(type, body.copyOfRange(1, body.size))
        } else {
            if (body.size < 13) throw IOException("short_frame")
            val nonce = body.copyOfRange(1, 13)
            val ct = body.copyOfRange(13, body.size)
            try {
                Frame(type, crypto.decrypt(nonce, ct))
            } catch (e: Exception) {
                throw DecryptionFailedException(e)
            }
        }
    }
}

class FrameWriter(private val output: OutputStream) {
    fun writeFrame(type: Byte, payload: ByteArray, crypto: SessionCrypto?) {
        val body: ByteArray =
            if (crypto == null) {
                check(type == MsgType.KEY_EXCHANGE) { "plaintext only allowed for KEY_EXCHANGE" }
                byteArrayOf(type) + payload
            } else {
                val (nonce, ct) = crypto.encrypt(payload)
                byteArrayOf(type) + nonce + ct
            }
        synchronized(output) {
            val header = ByteArray(4)
            val l = body.size
            header[0] = (l ushr 24).toByte(); header[1] = (l ushr 16).toByte()
            header[2] = (l ushr 8).toByte(); header[3] = l.toByte()
            output.write(header)
            output.write(body)
            output.flush()
        }
    }
}
