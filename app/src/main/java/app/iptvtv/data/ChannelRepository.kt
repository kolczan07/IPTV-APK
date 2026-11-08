package app.iptvtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

private const val HOUR = 3_600_000L

/** Zapas okna EPG na korektę czasu (maks. ±12 h, patrz EPG_OFFSET_MAX_MIN). */
private const val OFFSET_MARGIN = 12 * HOUR

class ChannelRepository(
    private val dao: ChannelDao,
    private val epg: EpgDao,
    private val history: HistoryDao,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    val channels = dao.all()
    val recentUrls = history.recentUrls()

    // OkHttp nie wysyła danych logowania z adresu (user:pass@host) — dokładamy nagłówek Basic Auth.
    private fun requestFor(url: String): Request {
        val b = Request.Builder().url(url)
        url.toHttpUrlOrNull()?.takeIf { it.username.isNotEmpty() }?.let {
            b.header("Authorization", Credentials.basic(it.username, it.password))
        }
        return b.build()
    }

    suspend fun markWatched(url: String) = history.mark(url, System.currentTimeMillis())
    suspend fun clearHistory() = history.clear()

    fun epgWindow(from: Long, to: Long) = epg.window(from, to)

    /** Pobiera M3U i podmienia kanały w bazie. Zwraca liczbę kanałów. */
    suspend fun refresh(url: String): Int = withContext(Dispatchers.IO) {
        val request = requestFor(url)
        val parsed = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body!!.byteStream().bufferedReader().useLines { M3uParser.parse(it) }
        }
        if (parsed.isEmpty()) throw IOException("Playlista jest pusta")
        dao.replaceAll(parsed.mapIndexed { i, c ->
            ChannelEntity(
                tvgId = c.tvgId, name = c.name, logo = c.logo,
                group = c.group ?: "Inne", number = c.number, url = c.url, position = i,
            )
        })
        parsed.size
    }

    /** Pobiera XMLTV (także .gz) i zapisuje programy z okna -14 h … +48 h (zapas na korektę czasu). Zwraca liczbę programów. */
    suspend fun refreshEpg(url: String, localTime: Boolean = false): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val request = requestFor(url)
        val items = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val raw = BufferedInputStream(resp.body!!.byteStream())
            raw.mark(2)
            val b0 = raw.read()
            val b1 = raw.read()
            raw.reset()
            val stream = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(raw) else raw
            XmltvParser.parse(stream, from = now - 2 * HOUR - OFFSET_MARGIN, to = now + 36 * HOUR + OFFSET_MARGIN,
                localZone = if (localTime) java.util.TimeZone.getDefault() else null)
        }
        if (items.isEmpty()) throw IOException("brak programów w oknie czasowym")
        epg.replaceAll(items)
        items.size
    }

    suspend fun setFavorite(id: Long, fav: Boolean) = dao.setFavorite(id, fav)
}
