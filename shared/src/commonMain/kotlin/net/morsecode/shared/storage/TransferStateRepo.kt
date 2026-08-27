package net.morsecode.shared.storage

import net.morsecode.db.MorseDb

/** Resume support: verified-chunk bitmaps persisted per (transfer, file). */
class TransferStateRepo(private val db: MorseDb) {

    fun upsert(
        transferId: String, fileId: String, batchId: String?, peerDeviceId: String,
        filename: String, totalChunks: Int, sha256Full: String, status: String, direction: String,
    ) {
        db.morseQueries.upsertState(
            transferId, fileId, batchId, peerDeviceId, filename, totalChunks,
            "", sha256Full, status, direction, System.currentTimeMillis(),
        )
    }

    fun bitmap(transferId: String, fileId: String): String =
        db.morseQueries.findState(transferId, fileId).executeAsOneOrNull()?.verified_chunks_bitmap ?: ""

    fun markVerified(transferId: String, fileId: String, chunkIndex: Int, totalChunks: Int) {
        val cur = bitmap(transferId, fileId)
        val sb = StringBuilder(cur)
        while (sb.length <= chunkIndex) sb.append('0')
        sb.setCharAt(chunkIndex, '1')
        db.morseQueries.updateBitmap(sb.toString(), System.currentTimeMillis(), transferId, fileId)
    }

    /** Index of the last contiguous verified chunk, or -1. */
    fun lastContiguousVerified(transferId: String, fileId: String): Int {
        val b = bitmap(transferId, fileId)
        var i = 0
        while (i < b.length && b[i] == '1') i++
        return i - 1
    }

    fun setStatus(transferId: String, fileId: String, status: String) =
        db.morseQueries.updateStatus(status, System.currentTimeMillis(), transferId, fileId)

    fun clear(transferId: String, fileId: String) =
        db.morseQueries.deleteState(transferId, fileId).execute()
}
