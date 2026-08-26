package net.morsecode.shared.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class HandshakeRejectedException(val reason: String) : Exception(reason)

data class HelloAcceptance(
    val accepted: Boolean,
    val reason: String? = null,
)

/**
 * Key exchange + HELLO flow (Section 4). Both peers use fresh ephemeral P-256
 * keypairs; the session key is HKDF-SHA256 over the ECDH secret with the
 * sorted concat of both public keys as salt.
 */
object Handshake {

    /** Initiator side. Connects and performs KEY_EXCHANGE + HELLO/HELLO_ACK. */
    suspend fun initiate(
        scope: kotlinx.coroutines.CoroutineScope,
        host: String,
        port: Int,
        self: SelfProfile,
        pairingToken: String?,
        isTrustedRequest: Boolean,
        timeoutMs: Int = 8000,
    ): MorseConnection = withContext(Dispatchers.IO) {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.soTimeout = 30_000
        try {
            val myPair = Crypto.newKeyPair()
            val myPub = Crypto.encodePublicKey(myPair.public)
            val out = FrameWriter(socket.getOutputStream())
            val inp = FrameReader(socket.getInputStream())

            out.writeFrame(MsgType.KEY_EXCHANGE, KeyExchangePayload(myPub.toBase64()).let {
                MsgJson.json.encodeToString(KeyExchangePayload.serializer(), it)
            }.toByteArray(), crypto = null)

            val theirFrame = inp.readFrame(null)
                ?: throw java.io.IOException("peer closed during key exchange")
            check(theirFrame.type == MsgType.KEY_EXCHANGE) { "expected KEY_EXCHANGE" }
            val their = MsgJson.json.decodeFromString(KeyExchangePayload.serializer(), theirFrame.payload.decodeToString())
            val theirPub = base64ToBytes(their.public_key_base64)

            val sessionKey = Crypto.deriveSessionKey(
                Crypto.ecdh(myPair.private, Crypto.decodePublicKey(theirPub)), myPub, theirPub,
            )
            val crypto = SessionCrypto(sessionKey)

            val conn = MorseConnection(
                socket, crypto, scope,
                MorseConnection.PeerMeta(deviceId = "?", name = "?", deviceType = "?", appVersion = "?"),
            )

            conn.send(
                Hello(
                    device_id = self.deviceId, device_name = self.name, device_type = self.type,
                    app_version = self.appVersion, proto_version = self.protoVersion,
                    pairing_token = pairingToken, is_trusted_request = isTrustedRequest,
                ),
            )
            val ackFrame = inp.readFrame(crypto) ?: throw java.io.IOException("peer closed during hello")
            if (ackFrame.type == MsgType.ERROR) {
                throw HandshakeRejectedException(ackFrame.payload.decodeToString())
            }
            check(ackFrame.type == MsgType.HELLO_ACK) { "expected HELLO_ACK" }
            val ack = ackFrame.decodeAs<HelloAck>()
            if (!ack.accepted) {
                conn.close()
                throw HandshakeRejectedException(ack.reason ?: "rejected")
            }
            conn.peer = MorseConnection.PeerMeta(ack.device_id, ack.device_name, ack.device_type, ack.app_version)
            conn.start()
            conn
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    /**
     * Responder side. Runs the full responder flow on an accepted socket using
     * the [FrameReader]/[FrameWriter] already primed by the server loop.
     * [validate] applies protocol/pairing/trust policy on the HELLO.
     */
    suspend fun respond(
        scope: kotlinx.coroutines.CoroutineScope,
        socket: Socket,
        self: SelfProfile,
        validate: (Hello) -> HelloAcceptance,
    ): MorseConnection = withContext(Dispatchers.IO) {
        socket.soTimeout = 30_000
        socket.tcpNoDelay = true
        try {
            val myPair = Crypto.newKeyPair()
            val myPub = Crypto.encodePublicKey(myPair.public)
            val inp = FrameReader(socket.getInputStream())
            val out = FrameWriter(socket.getOutputStream())

            val theirFrame = inp.readFrame(null)
                ?: throw java.io.IOException("peer closed during key exchange")
            check(theirFrame.type == MsgType.KEY_EXCHANGE) { "expected KEY_EXCHANGE" }
            val their = MsgJson.json.decodeFromString(KeyExchangePayload.serializer(), theirFrame.payload.decodeToString())
            val theirPub = base64ToBytes(their.public_key_base64)

            out.writeFrame(MsgType.KEY_EXCHANGE, MsgJson.json.encodeToString(
                KeyExchangePayload.serializer(), KeyExchangePayload(myPub.toBase64()),
            ).toByteArray(), crypto = null)

            val sessionKey = Crypto.deriveSessionKey(
                Crypto.ecdh(myPair.private, Crypto.decodePublicKey(theirPub)), myPub, theirPub,
            )
            val crypto = SessionCrypto(sessionKey)

            val helloFrame = inp.readFrame(crypto) ?: throw java.io.IOException("peer closed during hello")
            check(helloFrame.type == MsgType.HELLO) { "expected HELLO" }
            val hello = helloFrame.decodeAs<Hello>()

            val decision = validate(hello)
            val conn = MorseConnection(
                socket, crypto, scope,
                MorseConnection.PeerMeta(hello.device_id, hello.device_name, hello.device_type, hello.app_version),
            )
            out.writeFrame(MsgType.HELLO_ACK, encodeMsg(HelloAck(
                device_id = self.deviceId, device_name = self.name, device_type = self.type,
                app_version = self.appVersion, proto_version = self.protoVersion,
                accepted = decision.accepted, reason = decision.reason,
            )).second, crypto)
            if (!decision.accepted) {
                conn.close()
                throw HandshakeRejectedException(decision.reason ?: "rejected")
            }
            conn.start()
            conn
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }
}
