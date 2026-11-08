package app.iptvtv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    private val themeKey = stringPreferencesKey("theme_mode")
    private val accentKey = intPreferencesKey("accent_idx")

    val themeMode: Flow<String> = ctx.dataStore.data.map { it[themeKey] ?: "Dark" }
    val accentIdx: Flow<Int> = ctx.dataStore.data.map { it[accentKey] ?: 0 }
    suspend fun setThemeMode(v: String) { ctx.dataStore.edit { it[themeKey] = v } }
    suspend fun setAccentIdx(v: Int) { ctx.dataStore.edit { it[accentKey] = v } }

    private val diagKey = booleanPreferencesKey("show_diag")

    /** Linia diagnostyczna strumienia w odtwarzaczu (domyślnie wyłączona). */
    val showDiag: Flow<Boolean> = ctx.dataStore.data.map { it[diagKey] ?: false }
    suspend fun setShowDiag(v: Boolean) { ctx.dataStore.edit { it[diagKey] = v } }

    private val epgLocalKey = booleanPreferencesKey("epg_local_time")

    /** Traktuj godziny w XMLTV jako czas lokalny urządzenia, ignorując strefę zapisaną w pliku. */
    val epgLocalTime: Flow<Boolean> = ctx.dataStore.data.map { it[epgLocalKey] ?: false }
    suspend fun setEpgLocalTime(v: Boolean) { ctx.dataStore.edit { it[epgLocalKey] = v } }

    private val epgOffsetKey = intPreferencesKey("epg_offset_min")

    /** Korekta czasu EPG w minutach (może być ujemna); stosowana przy odczycie, więc nie wymaga ponownego pobrania. */
    val epgOffsetMin: Flow<Int> = ctx.dataStore.data.map { it[epgOffsetKey] ?: 0 }
    suspend fun setEpgOffsetMin(min: Int) { ctx.dataStore.edit { it[epgOffsetKey] = min } }

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
