package net.morsecode.shared.net

import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** ECDH P-256 + HKDF-SHA256 + AES-256-GCM session crypto (Section 4). */
object Crypto {
    const val SESSION_INFO = "morsecode-session-v1"
    private val random = SecureRandom()

    fun newKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"), random)
        return gen.generateKeyPair()
    }

    /** Uncompressed point: 0x04 || X(32) || Y(32), big-endian. */
    fun encodePublicKey(pub: PublicKey): ByteArray {
        val point = pub as java.security.interfaces.ECPublicKey
        return encodePoint(point.w)
    }

    private fun encodePoint(w: ECPoint): ByteArray {
        val out = ByteArray(65)
        out[0] = 0x04
        putInt32Pad(out, 1, w.affineX)
        putInt32Pad(out, 33, w.affineY)
        return out
    }

    private fun putInt32Pad(out: ByteArray, off: Int, v: java.math.BigInteger) {
        val b = v.toByteArray()
        // b may have a leading zero or may be shorter than 32
        var src = b.size - 32
        if (src < 0) src = 0
        val len = b.size - src
        java.util.Arrays.fill(out, off, off + 32, 0)
        System.arraycopy(b, src, out, off + (32 - len), len)
    }

    fun decodePublicKey(bytes: ByteArray): PublicKey {
        require(bytes.size == 65 && bytes[0] == 0x04.toByte()) { "bad EC point" }
        val x = java.math.BigInteger(1, bytes.copyOfRange(1, 33))
        val y = java.math.BigInteger(1, bytes.copyOfRange(33, 65))
        val params = ecParams()
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
    }

    private fun ecParams(): ECParameterSpec {
        val alg = java.security.AlgorithmParameters.getInstance("EC")
        alg.init(ECGenParameterSpec("secp256r1"))
        return alg.getParameterSpec(ECParameterSpec::class.java)
    }

    fun ecdh(priv: PrivateKey, peerPub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(peerPub, true)
        return ka.generateSecret()
    }

    /** HKDF-SHA256 (RFC 5869) implemented manually over javax.crypto.Mac. */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // extract
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        // expand
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }

    /**
     * Session key from ECDH secret. Salt = lexicographically sorted concat of both
     * public key blobs, so both peers derive the same key.
     */
    fun deriveSessionKey(sharedSecret: ByteArray, myPub: ByteArray, theirPub: ByteArray): SecretKeySpec {
        val salt = if (compareUnsigned(myPub, theirPub) <= 0) myPub + theirPub else theirPub + myPub
        val key = hkdfSha256(sharedSecret, salt, SESSION_INFO.toByteArray(Charsets.UTF_8), 32)
        return SecretKeySpec(key, "AES")
    }

    fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun sha256Hex(stream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return md.digest().toHex()
    }

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun randomId(): String = randomBytes(16).toHex()

    /** 6-digit numeric PIN. */
    fun randomPin(): String = (100000 + random.nextInt(900000)).toString()
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun ByteArray.toBase64(): String = java.util.Base64.getEncoder().encodeToString(this)
fun base64ToBytes(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)

/**
 * Per-connection symmetric crypto with strictly-incrementing per-direction
 * 64-bit counters encoded as 4 zero bytes + 8-byte BE counter (never reused).
 */
class SessionCrypto(private val key: SecretKeySpec) {
    private val sendCounter = AtomicLong(0)
    private val recvCounter = AtomicLong(0)

    /** @return nonce(12) and ciphertext+tag */
    fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val counter = sendCounter.incrementAndGet()
        val nonce = nonceFor(counter)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return nonce to cipher.doFinal(plain)
    }

    fun decrypt(nonce: ByteArray, ct: ByteArray): ByteArray {
        val expected = recvCounter.incrementAndGet()
        if (!nonce.contentEquals(nonceFor(expected))) {
            throw SecurityException("nonce_out_of_order")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return cipher.doFinal(ct) // throws AEADBadTagException on tag failure
    }

    companion object {
        fun nonceFor(counter: Long): ByteArray {
            val n = ByteArray(12)
            n[0] = 0; n[1] = 0; n[2] = 0; n[3] = 0
            for (i in 0 until 8) n[4 + i] = (counter ushr ((7 - i) * 8)).toByte()
            return n
        }
    }
}
