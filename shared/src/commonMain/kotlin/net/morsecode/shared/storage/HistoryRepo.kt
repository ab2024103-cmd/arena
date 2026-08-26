package net.morsecode.shared.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.morsecode.db.History_entry
import net.morsecode.db.MorseDb

data class HistoryEntry(
    val id: String,
    val batchId: String?,
    val peerDeviceId: String,
    val peerName: String,
    val filename: String,
    val sizeBytes: Long,
    val direction: String, // sent | received
    val kind: String, // file | text | chat | app
    val mime: String?,
    val source: String?, // null | "via Web Connect" | ...
    val path: String? = null,
    val status: String, // completed | failed | rejected | cancelled
    val ts: Long,
)

fun History_entry.toModel() = HistoryEntry(
    id, batch_id, peer_device_id, peer_name, filename, size_bytes,
    direction, kind, mime, source, path, status, ts,
)

class HistoryRepo(private val db: MorseDb) {
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries

    init { refresh() }

    fun refresh() {
        _entries.value = db.historyQueries.historyAll().executeAsList().map { it.toModel() }
    }

    fun add(entry: HistoryEntry) {
        db.historyQueries.insertHistory(
            entry.id, entry.batchId, entry.peerDeviceId, entry.peerName, entry.filename,
            entry.sizeBytes, entry.direction, entry.kind, entry.mime, entry.source, entry.path, entry.status, entry.ts,
        ).execute()
        refresh()
    }

    fun setStatus(id: String, status: String) {
        db.historyQueries.updateHistoryStatus(status, id).execute()
        refresh()
    }
}
