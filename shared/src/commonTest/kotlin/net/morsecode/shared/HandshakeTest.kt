package net.morsecode.shared

import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.morsecode.shared.net.Crypto
import net.morsecode.shared.net.Handshake
import net.morsecode.shared.net.HandshakeRejectedException
import net.morsecode.shared.net.MorseConnection
import net.morsecode.shared.net.PROTO_VERSION
import net.morsecode.shared.net.SelfProfile

class HandshakeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selfA = SelfProfile("11111111111111111111111111111111", "DeviceA", "android")
    private val selfB = SelfProfile("22222222222222222222222222222222", "DeviceB", "windows")

    @Test
    fun fullHandshakeSucceeds() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val responder = scope.async {
            val socket = server.accept()
            Handshake.respond(scope, socket, selfB) { Handshake.HelloAcceptance(true) }
        }
        val conn = Handshake.initiate(scope, "127.0.0.1", port, selfA, null, isTrustedRequest = false)
        val responderConn = responder.await()
        assertEquals(selfB.deviceId, conn.peer.deviceId)
        assertEquals(selfA.deviceId, responderConn.peer.deviceId)
        // both directions can send encrypted messages
        conn.send(net.morsecode.shared.net.PingMsg(123L))
        conn.close(); responderConn.close(); server.close()
        scope.cancel()
    }

    @Test
    fun protoMismatchIsRejected() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val olderSelf = selfA.copy(protoVersion = PROTO_VERSION + 99)
        scope.launch {
            val socket = server.accept()
            runCatching {
                Handshake.respond(scope, socket, selfB) { hello ->
                    Handshake.HelloAcceptance(hello.proto_version == PROTO_VERSION, "protocol_version_mismatch")
                }
            }
        }
        assertFailsWith<HandshakeRejectedException> {
            Handshake.initiate(scope, "127.0.0.1", port, olderSelf, null, isTrustedRequest = false)
        }
        server.close()
        scope.cancel()
    }

    @Test
    fun invalidPairingTokenIsRejected() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        scope.launch {
            val socket = server.accept()
            runCatching {
                Handshake.respond(scope, socket, selfB) { hello ->
                    val ok = hello.pairing_token == "goodtoken"
                    Handshake.HelloAcceptance(ok, if (ok) null else "invalid_pairing_token")
                }
            }
        }
        assertFailsWith<HandshakeRejectedException> {
            Handshake.initiate(scope, "127.0.0.1", port, selfA, "badtoken", isTrustedRequest = false)
        }
        server.close()
        scope.cancel()
    }
}
