package dev.netvalve.calibration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** The last measured shared speed of one network type. */
@Serializable
data class CalibrationEntry(val bytesPerSec: Long, val measuredAtMillis: Long)

/** The last known shared speed per network type ("WIFI", "MOBILE", "ETHERNET"). */
interface CalibrationRepository {
    suspend fun get(key: String): CalibrationEntry?
    suspend fun put(key: String, entry: CalibrationEntry)
}

/** DataStore-backed; one JSON object under its own key `calibration_json`. A damaged value reads as empty. */
class DataStoreCalibrationRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : CalibrationRepository {

    private val key = stringPreferencesKey("calibration_json")
    private val serializer = MapSerializer(String.serializer(), CalibrationEntry.serializer())

    private fun decode(raw: String?): Map<String, CalibrationEntry> {
        if (raw == null) return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull() ?: emptyMap()
    }

    override suspend fun get(key: String): CalibrationEntry? = decode(dataStore.data.first()[this.key])[key]

    override suspend fun put(key: String, entry: CalibrationEntry) {
        dataStore.edit { prefs ->
            val map = decode(prefs[this.key]).toMutableMap()
            map[key] = entry
            prefs[this.key] = json.encodeToString(serializer, map)
        }
    }
}
