package com.weeklymeals

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "weekly_meals")

/** Keeps everything you choose (week, sides, favorites, checks, King Soopers settings) on the phone. */
class Store(private val context: Context) {
    private val key = stringPreferencesKey("state")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(text: String?): UserState =
        if (text == null) UserState()
        else runCatching { json.decodeFromString(UserState.serializer(), text) }.getOrDefault(UserState())

    val flow: Flow<UserState> = context.dataStore.data.map { decode(it[key]) }

    suspend fun update(block: (UserState) -> UserState) {
        context.dataStore.edit { prefs ->
            val next = block(decode(prefs[key]))
            prefs[key] = json.encodeToString(UserState.serializer(), next)
        }
    }
}
