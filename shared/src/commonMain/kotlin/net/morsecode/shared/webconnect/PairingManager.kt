package net.morsecode.shared.webconnect

import net.morsecode.shared.net.Crypto
import java.util.concurrent.ConcurrentHashMap

/**
 * PIN generation + session token validation (H.3). The PIN is regenerated
 * every time Web Connect is enabled and never persisted across sessions.
 * Sessions expire after 30 minutes of inactivity or when Web Connect stops.
 */
class PairingManager {
    @Volatile var currentPin: String = Crypto.randomPin()
        private set

    @Volatile private var qrToken: String = Crypto.randomId()
    private val sessions = ConcurrentHashMap<String, Long>() // token -> lastActivity
    private val sessionTimeoutMs = 30 * 60 * 1000L

    fun newPairing() {
        currentPin = Crypto.randomPin()
        qrToken = Crypto.randomId()
        sessions.clear()
    }

    fun qrTokenValue(): String = qrToken

    /** @return session cookie value, or null if PIN/token invalid. */
    fun pair(pinOrToken: String): String? {
        val valid = pinOrToken == currentPin || pinOrToken == qrToken
        if (!valid) return null
        val token = Crypto.randomId()
        sessions[token] = System.currentTimeMillis()
        return token
    }

    fun isValidSession(token: String?): Boolean {
        if (token == null) return false
        val last = sessions[token] ?: return false
        val now = System.currentTimeMillis()
        if (now - last > sessionTimeoutMs) {
            sessions.remove(token)
            return false
        }
        sessions[token] = now
        return true
    }

    fun invalidateAll() = sessions.clear()
}
