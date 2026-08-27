package net.morsecode.shared.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.morsecode.db.MorseDb

data class TrustedDevice(val deviceId: String, val name: String, val addedAt: Long)

class TrustedDeviceRepo(private val db: MorseDb) {
    private val _devices = MutableStateFlow<List<TrustedDevice>>(emptyList())
    val devices: StateFlow<List<TrustedDevice>> = _devices

    init { refresh() }

    fun refresh() {
        _devices.value = db.historyQueries.trustedAll().executeAsList()
            .map { TrustedDevice(it.device_id, it.name, it.added_at) }
    }

    fun isTrusted(deviceId: String): Boolean =
        db.historyQueries.isTrusted(deviceId).executeAsOneOrNull() != null

    fun trust(deviceId: String, name: String) {
        db.historyQueries.insertTrusted(deviceId, name, System.currentTimeMillis())
        refresh()
    }

    fun forget(deviceId: String) {
        db.historyQueries.deleteTrusted(deviceId)
        refresh()
    }
}
