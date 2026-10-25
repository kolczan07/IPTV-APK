package app.iptvtv.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.PsExtractor
import app.iptvtv.data.ChannelEntity

/**
 * Jeden współdzielony ExoPlayer dla podglądu na liście i pełnego ekranu.
 * Przejście lista → pełny ekran tylko podpina inny PlayerView do tego samego playera,
 * więc strumień nie jest ładowany od nowa.
 */
@OptIn(UnstableApi::class)
class PlayerController(context: Context) {
    // Wiele paneli IPTV blokuje domyślnego "ExoPlayer" UA albo przekierowuje http -> https.
    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)

    // Tryb AUTO: ExoPlayer sam rozpoznaje kontener (+ flagi dla TS bez AUD/IDR).
    private val autoExtractors = DefaultExtractorsFactory()
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
        )

    private val autoSourceFactory = DefaultMediaSourceFactory(
        context.applicationContext,
        autoExtractors
    ).setDataSourceFactory(http)

    // Tryb PS: jeden wymuszony extractor = brak rozpoznawania formatu, więc żadnego
    // ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED z błędnego "zgadnięcia".
    private val psSourceFactory = ProgressiveMediaSource.Factory(
        http,
        ExtractorsFactory { arrayOf<Extractor>(PsExtractor()) }
    )

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(autoSourceFactory)
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

    private var attempt = AUTO
    private val tried = mutableSetOf<Int>()        // próby użyte dla bieżącego kanału
    private var firstError: String? = null         // kod błędu z pierwszej próby
    private val learned = HashMap<String, Int>()   // url -> próba, która zadziałała

    /** Adres bez rozszerzenia = MPEG-PS (serwery Enigma2 itp.). */
    private fun initialAttempt(url: String): Int {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") -> HLS
            path.endsWith(".ts") || path.endsWith(".mp4") || path.endsWith(".mkv") -> AUTO
            else -> PS
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val url = current?.url
                if (firstError == null) firstError = e.errorCodeName
                // 3001..3004 = błędy parsowania kontenera / manifestu
                if (url != null && e.errorCode in 3001..3004) {
                    val next = listOf(AUTO, HLS, PS).firstOrNull { it !in tried }
                    if (next != null) {
                        load(url, next)
                        return
                    }
                }
                error = "Błąd odtwarzania ($firstError; próby: ${tried.sorted()}; ostatni: ${e.errorCodeName})"
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) current?.url?.let { learned[it] = attempt }
            }
        })
    }

    private fun load(url: String, mode: Int) {
        attempt = mode
        tried += mode
        when (mode) {
            PS -> player.setMediaSource(psSourceFactory.createMediaSource(MediaItem.fromUri(url)))
            HLS -> player.setMediaItem(
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            )
            else -> player.setMediaItem(MediaItem.fromUri(url))
        }
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
        firstError = null
        tried.clear()
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

    private companion object {
        const val AUTO = 0
        const val HLS = 1
        const val PS = 2
    }
}
