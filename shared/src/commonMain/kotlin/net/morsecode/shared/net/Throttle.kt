package net.morsecode.shared.net

import kotlinx.coroutines.delay

/**
 * Global token bucket shared across all concurrent transfers (Section 12).
 * rateKbps = 0 disables throttling.
 */
class TokenBucket(private val rateKbps: Int) {
    private var tokens: Double = 0.0
    private var lastRefill: Long = System.nanoTime()

    private fun refill() {
        if (rateKbps <= 0) return
        val now = System.nanoTime()
        val elapsed = (now - lastRefill) / 1_000_000.0
        lastRefill = now
        tokens = minOf(tokens + (rateKbps * 1024.0) * (elapsed / 1000.0), rateKbps * 1024.0 * 2.0)
    }

    /** Suspends until the bucket can pay for [bytes]; no-op when unlimited. */
    suspend fun acquire(bytes: Int) {
        if (rateKbps <= 0) return
        while (true) {
            refill()
            if (tokens >= bytes) {
                tokens -= bytes
                return
            }
            val missing = bytes - tokens
            val ms = (missing / (rateKbps * 1024.0) * 1000.0).coerceIn(5.0, 1000.0)
            delay(ms.toLong())
        }
    }
}
