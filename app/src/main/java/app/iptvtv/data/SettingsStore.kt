package app.iptvtv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

// Domyślne adresy (używane, gdy użytkownik nie zapisał własnych w Ustawieniach).
const val DEFAULT_M3U = "http://root:pkteam@192.168.0.121:80/file?file=/media/hdd/IPTV_Server/playlist_http.m3u"
const val DEFAULT_EPG = "http://root:pkteam@192.168.0.121:80/file?file=/media/hdd/IPTV_Server/epg.xml"

class SettingsStore(private val ctx: Context) {
    private val m3uKey = stringPreferencesKey("m3u_url")
    private val epgKey = stringPreferencesKey("epg_url")

    val m3uUrl: Flow<String> = ctx.dataStore.data.map { it[m3uKey] ?: DEFAULT_M3U }
    val epgUrl: Flow<String> = ctx.dataStore.data.map { it[epgKey] ?: DEFAULT_EPG }

    suspend fun save(m3u: String, epg: String) {
        ctx.dataStore.edit {
            it[m3uKey] = m3u
            it[epgKey] = epg
        }
    }
}
