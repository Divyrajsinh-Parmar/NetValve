package dev.netvalve.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.netvalve.scheduler.Tier
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Persists which package belongs to which priority tier (only the non-default ones). */
interface TierRepository {
    suspend fun current(): Map<String, Tier>
    suspend fun save(tiers: Map<String, Tier>)
}

/**
 * DataStore-backed [TierRepository]. Stored as one JSON object `{"package": "HIGH"}` under its own
 * key, so it does not touch the existing settings or rules blobs. Unknown or damaged entries are
 * skipped on read instead of failing.
 */
class DataStoreTierRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : TierRepository {

    private val key = stringPreferencesKey("tiers_json")
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    override suspend fun current(): Map<String, Tier> {
        val raw = dataStore.data.first()[key] ?: return emptyMap()
        val names = runCatching { json.decodeFromString(serializer, raw) }.getOrNull() ?: return emptyMap()
        return names.mapNotNull { (pkg, name) ->
            runCatching { Tier.valueOf(name) }.getOrNull()?.let { pkg to it }
        }.toMap()
    }

    override suspend fun save(tiers: Map<String, Tier>) {
        val names = tiers.mapValues { it.value.name }
        dataStore.edit { it[key] = json.encodeToString(serializer, names) }
    }
}
