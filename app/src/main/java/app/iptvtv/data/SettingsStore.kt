package app.iptvtv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val ctx: Context) {
    private val m3uKey = stringPreferencesKey("m3u_url")
    private val epgKey = stringPreferencesKey("epg_url")

    val m3uUrl: Flow<String> = ctx.dataStore.data.map { it[m3uKey] ?: "" }
    val epgUrl: Flow<String> = ctx.dataStore.data.map { it[epgKey] ?: "" }

    suspend fun save(m3u: String, epg: String) {
        ctx.dataStore.edit {
            it[m3uKey] = m3u
            it[epgKey] = epg
        }
    }
}
