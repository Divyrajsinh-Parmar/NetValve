package dev.netvalve.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Persists the user's ordered priority list (first package = most important). */
interface PriorityOrderRepository {
    suspend fun current(): List<String>
    suspend fun save(order: List<String>)
}

/**
 * DataStore-backed [PriorityOrderRepository]. Stored as one JSON array `["pkg.one", "pkg.two"]`
 * under its own key (`priority_order_json`), so it does not touch the settings, rules or the old
 * `tiers_json` blobs. A damaged value is read as an empty list instead of failing.
 */
class DataStorePriorityOrderRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : PriorityOrderRepository {

    private val key = stringPreferencesKey("priority_order_json")
    private val serializer = ListSerializer(String.serializer())

    override suspend fun current(): List<String> {
        val raw = dataStore.data.first()[key] ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull() ?: emptyList()
    }

    override suspend fun save(order: List<String>) {
        dataStore.edit { it[key] = json.encodeToString(serializer, order) }
    }
}
