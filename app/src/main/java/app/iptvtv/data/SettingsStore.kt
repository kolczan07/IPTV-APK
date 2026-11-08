package app.iptvtv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

// Domyślne adresy wstrzykiwane w czasie budowania (sekrety CI); puste w lokalnych buildach.
private val DEFAULT_M3U = app.iptvtv.BuildConfig.DEFAULT_M3U
private val DEFAULT_EPG = app.iptvtv.BuildConfig.DEFAULT_EPG

class SettingsStore(private val ctx: Context) {
    private val m3uKey = stringPreferencesKey("m3u_url")
    private val epgKey = stringPreferencesKey("epg_url")

    private val epgAtKey = longPreferencesKey("epg_fetched_at")

    /** Kiedy ostatnio pobrano EPG (ms); 0 = nigdy. Pozwala nie ściągać wielkiego XML-a przy każdym starcie. */
    val epgFetchedAt: Flow<Long> = ctx.dataStore.data.map { it[epgAtKey] ?: 0L }
    suspend fun markEpgFetched(at: Long) { ctx.dataStore.edit { it[epgAtKey] = at } }

    val m3uUrl: Flow<String> = ctx.dataStore.data.map { it[m3uKey] ?: DEFAULT_M3U }
    val epgUrl: Flow<String> = ctx.dataStore.data.map { it[epgKey] ?: DEFAULT_EPG }

    suspend fun save(m3u: String, epg: String) {
        ctx.dataStore.edit {
            it[m3uKey] = m3u
            it[epgKey] = epg
        }
    }
}
