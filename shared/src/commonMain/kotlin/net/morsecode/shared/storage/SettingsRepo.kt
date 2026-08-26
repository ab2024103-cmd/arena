package net.morsecode.shared.storage

import net.morsecode.db.MorseDb

class SettingsRepo(private val db: MorseDb) {
    fun get(key: String): String? =
        db.kvQueries.getKv(key).executeAsOneOrNull()?.v

    fun put(key: String, value: String) =
        db.kvQueries.putKv(key, value).execute()

    fun getInt(key: String, def: Int): Int = get(key)?.toIntOrNull() ?: def
    fun putInt(key: String, value: Int) = put(key, value.toString())
    fun getBool(key: String, def: Boolean): Boolean = get(key)?.let { it == "1" || it == "true" } ?: def
    fun putBool(key: String, value: Boolean) = put(key, if (value) "1" else "0")
}
