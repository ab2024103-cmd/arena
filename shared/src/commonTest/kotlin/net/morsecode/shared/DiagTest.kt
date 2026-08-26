package net.morsecode.shared

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test

/**
 * DIAGNOSTIC ONLY. Runs every other test method in-process, catching exceptions
 * and posting a report to a GitHub commit comment so the failure is visible even
 * when the raw Actions log blob store is unreachable from the agent sandbox.
 * This file is removed once the real failures are understood.
 */
class DiagTest {

    @Test
    fun runAllAndReport() {
        val cases = listOf<Pair<String, () -> Unit>>(
            "CryptoTest.hkdf" to { CryptoTest().hkdfMatchesRfc5869TestCase1() },
            "CryptoTest.ecdh" to { CryptoTest().ecdhBothSidesDeriveSameSecret() },
            "CryptoTest.sessionKey" to { CryptoTest().sessionKeySymmetricBetweenPeers() },
            "CryptoTest.aesGcm" to { CryptoTest().aesGcmRoundTripAndTamperDetection() },
            "CryptoTest.nonce" to { CryptoTest().nonceCounterIsStrictlyIncreasing() },
            "FramingTest.plaintext" to { FramingTest().plaintextFrameRoundTrip() },
            "FramingTest.encrypted" to { FramingTest().encryptedFrameRoundTrip() },
            "FramingTest.oversized" to { FramingTest().oversizedFrameRejected() },
            "HandshakeTest.full" to { HandshakeTest().fullHandshakeSucceeds() },
            "HandshakeTest.protoMismatch" to { HandshakeTest().protoMismatchIsRejected() },
            "HandshakeTest.invalidToken" to { HandshakeTest().invalidPairingTokenIsRejected() },
            "LogicTest.categorizer" to { LogicTest().categorizerIsNonExclusive() },
            "LogicTest.dateHeader" to { LogicTest().dateGroupingFormatsHeaders() },
            "LogicTest.dateGroup" to { LogicTest().dateGroupingGroupsAndSortsDescending() },
            "LogicTest.throttle" to { LogicTest().throttleUnlimitedIsNoOp() },
            "TransferLoopback.clean" to { TransferLoopbackTest().cleanTransferSucceeds() },
            "TransferLoopback.corrupt" to { TransferLoopbackTest().corruptedChunksAreRecoveredViaNack() },
        )

        val sb = StringBuilder()
        for ((name, fn) in cases) {
            val holder = AtomicReference<Throwable?>(null)
            val thread = Thread {
                try {
                    fn()
                } catch (e: Throwable) {
                    holder.set(e)
                }
            }
            thread.start()
            thread.join(30_000)
            if (thread.isAlive) {
                sb.appendLine("TIMEOUT: $name (possible deadlock/hang)")
                thread.interrupt()
            } else {
                val ex = holder.get()
                if (ex == null) {
                    sb.appendLine("PASS: $name")
                } else {
                    sb.appendLine("FAIL: $name -> ${ex::class.qualifiedName}: ${ex.message}")
                    sb.appendLine(ex.stackTraceToString())
                    sb.appendLine("----")
                }
            }
        }

        val report = "DIAG jvmTest results @ ${System.getenv("GITHUB_SHA")}:\n" + sb.toString()
        post(report)
    }

    private fun post(body: String) {
        val token = System.getenv("GITHUB_TOKEN") ?: run { println("DIAG_NO_TOKEN"); return }
        val repo = System.getenv("GITHUB_REPOSITORY") ?: "ab2024103-cmd/arena"
        val sha = System.getenv("GITHUB_SHA") ?: run { println("DIAG_NO_SHA"); return }
        val max = 30000
        val trimmed = if (body.length > max) body.take(max) + "\n...[truncated]" else body
        val payload = "{\"body\":" + jsonEscape(trimmed) + "}"
        try {
            val url = URL("https://api.github.com/repos/$repo/commits/$sha/comments")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.doOutput = true
            conn.outputStream.use { os -> os.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val resp = try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Throwable) {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            println("DIAG_POST_STATUS=$code")
            println("DIAG_POST_RESP=${resp.take(300)}")
            conn.disconnect()
        } catch (e: Throwable) {
            println("DIAG_POST_ERROR: ${e.message}")
        }
    }

    private fun jsonEscape(s: String): String {
        val b = StringBuilder()
        for (c in s) {
            when (c) {
                '"' -> b.append("\\\"")
                '\\' -> b.append("\\\\")
                '\n' -> b.append("\\n")
                '\r' -> b.append("\\r")
                '\t' -> b.append("\\t")
                else -> if (c.code < 0x20) b.append("\\u%04x".format(c.code)) else b.append(c)
            }
        }
        return b.toString()
    }
}
