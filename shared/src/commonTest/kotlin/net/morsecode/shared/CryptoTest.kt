package net.morsecode.shared

import net.morsecode.shared.net.Crypto
import net.morsecode.shared.net.SessionCrypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CryptoTest {

    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val okm = Crypto.hkdfSha256(ikm, salt, info, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun ecdhBothSidesDeriveSameSecret() {
        val a = Crypto.newKeyPair()
        val b = Crypto.newKeyPair()
        val aPub = Crypto.encodePublicKey(a.public)
        val bPub = Crypto.encodePublicKey(b.public)
        assertEquals(65, aPub.size)
        assertEquals(0x04, aPub[0].toInt() and 0xFF)
        val s1 = Crypto.ecdh(a.private, Crypto.decodePublicKey(bPub))
        val s2 = Crypto.ecdh(b.private, Crypto.decodePublicKey(aPub))
        assertContentEquals(s1, s2)
    }

    @Test
    fun sessionKeySymmetricBetweenPeers() {
        val a = Crypto.newKeyPair()
        val b = Crypto.newKeyPair()
        val aPub = Crypto.encodePublicKey(a.public)
        val bPub = Crypto.encodePublicKey(b.public)
        val secret = Crypto.ecdh(a.private, Crypto.decodePublicKey(bPub))
        val k1 = Crypto.deriveSessionKey(secret, aPub, bPub)
        val k2 = Crypto.deriveSessionKey(secret, bPub, aPub)
        assertContentEquals(k1.encoded, k2.encoded)
    }

    @Test
    fun aesGcmRoundTripAndTamperDetection() {
        val key = Crypto.hkdfSha256("seed".toByteArray(), ByteArray(32), "i".toByteArray(), 32)
        val crypto = SessionCrypto(javax.crypto.spec.SecretKeySpec(key, "AES"))
        val (nonce, ct) = crypto.encrypt("hello morse".toByteArray())
        assertEquals(12, nonce.size)
        val other = SessionCrypto(javax.crypto.spec.SecretKeySpec(key, "AES"))
        val plain = other.decrypt(nonce, ct)
        assertEquals("hello morse", plain.decodeToString())

        // next message uses a different nonce
        val (n2, _) = crypto.encrypt("second".toByteArray())
        assertNotEquals(nonce.contentToString(), n2.contentToString())

        // tampered ciphertext must fail
        assertFailsWith<Exception> {
            val tampered = ct.copyOf().also { it[0] = (it[0] + 1).toByte() }
            other.decrypt(nonce, tampered)
        }
    }

    @Test
    fun nonceCounterIsStrictlyIncreasing() {
        val n1 = SessionCrypto.nonceFor(1)
        val n2 = SessionCrypto.nonceFor(2)
        assertTrue(String(n1) < String(n2))
    }

    private fun hex(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) out[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }
}
