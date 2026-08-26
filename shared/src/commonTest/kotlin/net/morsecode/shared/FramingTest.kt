package net.morsecode.shared

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import net.morsecode.shared.net.Crypto
import net.morsecode.shared.net.FrameReader
import net.morsecode.shared.net.FrameTooLargeException
import net.morsecode.shared.net.FrameWriter
import net.morsecode.shared.net.MsgType
import net.morsecode.shared.net.SessionCrypto
import javax.crypto.spec.SecretKeySpec

class FramingTest {

    @Test
    fun plaintextFrameRoundTrip() {
        val bytes = ByteArrayOutputStream()
        val w = FrameWriter(bytes)
        w.writeFrame(MsgType.KEY_EXCHANGE, "{\"a\":1}".toByteArray(), null)
        val r = FrameReader(ByteArrayInputStream(bytes.toByteArray()))
        val frame = r.readFrame(null)!!
        assertEquals(MsgType.KEY_EXCHANGE, frame.type)
        assertContentEquals("{\"a\":1}".toByteArray(), frame.payload)
        assertNull(r.readFrame(null))
    }

    @Test
    fun encryptedFrameRoundTrip() {
        val key = Crypto.randomBytes(32)
        val sender = SessionCrypto(SecretKeySpec(key, "AES"))
        val receiver = SessionCrypto(SecretKeySpec(key, "AES"))
        val bytes = ByteArrayOutputStream()
        val w = FrameWriter(bytes)
        w.writeFrame(MsgType.HELLO, "payload-1".toByteArray(), sender)
        w.writeFrame(MsgType.CHAT_MESSAGE, "payload-2".toByteArray(), sender)
        val r = FrameReader(ByteArrayInputStream(bytes.toByteArray()))
        val f1 = r.readFrame(receiver)!!
        assertEquals(MsgType.HELLO, f1.type)
        assertEquals("payload-1", f1.payload.decodeToString())
        val f2 = r.readFrame(receiver)!!
        assertEquals(MsgType.CHAT_MESSAGE, f2.type)
        assertEquals("payload-2", f2.payload.decodeToString())
    }

    @Test
    fun oversizedFrameRejected() {
        val huge = 20 * 1024 * 1024 // > 16 MiB cap
        val bytes = ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).writeInt(huge)
        val r = FrameReader(ByteArrayInputStream(bytes.toByteArray()))
        assertFailsWith<FrameTooLargeException> { r.readFrame(null) }
    }
}
