package dev.netvalve.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Keeps the long-term [HealthCounters] across app restarts. */
interface HealthRepository {
    suspend fun load(): HealthCounters
    suspend fun save(counters: HealthCounters)
}

/** DataStore-backed; one text value under its own key `health_v1`. A damaged value reads as empty. */
class DataStoreHealthRepository(
    private val dataStore: DataStore<Preferences>,
) : HealthRepository {
    private val key = stringPreferencesKey("health_v1")

    override suspend fun load(): HealthCounters = HealthCounters.decode(dataStore.data.first()[key])

    override suspend fun save(counters: HealthCounters) {
        dataStore.edit { prefs -> prefs[key] = counters.encode() }
    }
}
