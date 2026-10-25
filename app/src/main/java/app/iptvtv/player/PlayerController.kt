package app.iptvtv.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.TsExtractor
import app.iptvtv.data.ChannelEntity

/**
 * Jeden współdzielony ExoPlayer dla podglądu na liście i pełnego ekranu.
 * Przejście lista → pełny ekran tylko podpina inny PlayerView do tego samego playera,
 * więc strumień nie jest ładowany od nowa.
 */
class PlayerController(context: Context) {
    // Wiele paneli IPTV blokuje domyślnego "ExoPlayer" UA albo przekierowuje http -> https.
    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)

    // Flagi dla strumieni TS/PS bez AUD i bez IDR — inaczej obraz staje, a dźwięk leci.
    private val extractorsFactory = DefaultExtractorsFactory()
        .setTsExtractorFlags(
            TsExtractor.FLAG_DETECT_ACCESS_UNITS or TsExtractor.FLAG_ALLOW_NON_IDR_KEYFRAMES
        )

    // Wspólna fabryka źródeł: http + extractorsFactory.
    private val mediaSourceFactory = DefaultMediaSourceFactory(
        context.applicationContext,
        extractorsFactory
    ).setDataSourceFactory(http)

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(
            DefaultLoadControl.Builder()
                // krótki bufor startowy = szybki zapping na live
                .setBufferDurationsMs(15_000, 30_000, 1_000, 2_500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        .build()
        .apply { playWhenReady = true }

    var current by mutableStateOf<ChannelEntity?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // Gdy serwer nie podaje rozszerzenia ani poprawnego Content-Type, ExoPlayer nie rozpozna
    // formatu (PARSING_CONTAINER_UNSUPPORTED). Próbujemy kolejno: auto -> HLS -> MPEG-TS -> MPEG-PS.
    private val mimes = listOf<String?>(
        null,
        MimeTypes.APPLICATION_M3U8,
        MimeTypes.VIDEO_MP2T,
        MimeTypes.VIDEO_PS,
    )
    private var attempt = 0
    private val learned = HashMap<String, Int>()   // url -> indeks działającej próby

    /** Dla adresów bez rozszerzenia zakładamy MPEG-PS (serwery Enigma2 itp.). */
    private fun initialAttempt(url: String): Int {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> 1  // HLS
            lower.contains(".ts")   -> 2  // MPEG-TS
            lower.contains(".mp4")  -> 0  // auto
            else                    -> 3  // brak rozszerzenia → MPEG-PS
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val url = current?.url
                if (url != null && e.errorCode in 3001..3004 && attempt < mimes.size - 1) {
                    load(url, attempt + 1)
                    return
                }
                error = "Błąd odtwarzania (${e.errorCodeName})"
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) current?.url?.let { learned[it] = attempt }
            }
        })
    }

    private fun load(url: String, idx: Int) {
        attempt = idx
        val item = MediaItem.Builder().setUri(url).apply { mimes[idx]?.let { setMimeType(it) } }.build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    fun play(ch: ChannelEntity) {
        val same = current?.url == ch.url
        current = ch
        if (same && error == null && player.playbackState != Player.STATE_IDLE) {
            player.playWhenReady = true
            return
        }
        error = null
        load(ch.url, learned[ch.url] ?: initialAttempt(ch.url))
    }

    fun pause() { player.playWhenReady = false }
    fun resume() { if (current != null) player.playWhenReady = true }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        current = null
        error = null
    }

    fun release() = player.release()
}
