package app.iptvtv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.PsExtractor
import androidx.media3.extractor.ts.TsExtractor
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

    private val autoSourceFactory = DefaultMediaSourceFactory(
        context.applicationContext,
        autoExtractors
    ).setDataSourceFactory(http)

    // Tryb PS: jeden wymuszony extractor (ExoPlayer pomija wtedy rozpoznawanie formatu).
    // Używany dopiero po nieudanym AUTO, bo przy strumieniu innym niż PS nie zgłasza błędu.
    private val psSourceFactory = ProgressiveMediaSource.Factory(
        http,
        ExtractorsFactory { arrayOf<Extractor>(PsExtractor()) }
    )

    // Dekodowanie: synchroniczna kolejka MediaCodec + zapasowy dekoder. Domyślna asynchroniczna kolejka
    // (Media3, API 31+ i Fire TV) potrafi zamrażać/psuć obraz przy działającym dźwięku na boxach
    // z układami Realtek/Amlogic; a gdy wybrany dekoder się nie uruchomi, próbujemy następnego.
    private val renderers = DefaultRenderersFactory(context.applicationContext)
        .setEnableDecoderFallback(true)

    // Tryb TS: wymuszony extractor MPEG-TS bez zgadywania formatu (strumienie z tunera Enigma2 :8001 to zawsze TS).
    // Pomija rozpoznawanie, które przy dopiero strojonym tunerze potrafiło zawieść i uruchamiać kolejne próby.
    private val tsSourceFactory = ProgressiveMediaSource.Factory(
        http,
        ExtractorsFactory {
            arrayOf<Extractor>(
                TsExtractor(0)
            )
        }
    )

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext, renderers)
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

    /** true od startu ładowania kanału do pierwszej klatki obrazu (steruje ekranem ładowania zamiast spinnera). */
    var loading by mutableStateOf(false)
        private set

    /** Ostrzeżenie o dźwięku (brak ścieżki / brak dekodera w urządzeniu); null = dźwięk gra albo jeszcze nie wiadomo. */
    var audioInfo by mutableStateOf<String?>(null)
        private set

    /** Jednowierszowa diagnostyka strumienia (format, dekodery, czas do 1. klatki, pominięte klatki). */
    var diag by mutableStateOf("")
        private set
    private var loadStartMs = 0L
    private var ttffMs: Long? = null
    private var droppedFrames = 0
    private var videoDecoder: String? = null
    private var lastErrorName: String? = null
    private var audioDecoder: String? = null

    private val main = Handler(Looper.getMainLooper())
    private val wd = Handler(Looper.getMainLooper())   // stróż zawieszonego strumienia
    private var recoveries = 0
    private var stuckTicks = 0
    private var lastFrames = -1L
    private var retries = 0
    private var httpStatus: Int? = null

    private var attempt = AUTO
    private val tried = mutableSetOf<Int>()        // próby użyte dla bieżącego kanału
    private var firstError: String? = null         // kod błędu z pierwszej próby
    private val learned = HashMap<String, Int>()   // url -> tryb, który zadziałał

    /** Zawsze zaczynamy od AUTO (jak wcześniej), tylko .m3u8 od razu jako HLS. */
    private fun initialAttempt(url: String): Int {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") -> HLS
            ENIGMA_REF.containsMatchIn(path) -> TS      // .../1:0:19:3DCD:640:13E:820000:0:0:0:
            else -> AUTO
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val url = current?.url
                if (firstError == null) firstError = e.errorCodeName
                (e.cause as? HttpDataSource.InvalidResponseCodeException)?.let { httpStatus = it.responseCode }
                val code = e.errorCode
                lastErrorName = e.errorCodeName
                val enigma = url != null && initialAttempt(url) == TS
                // Strumień z tunera Enigmy to zawsze MPEG-TS: zmiana trybu niczego nie naprawia, a każde nowe
                // otwarcie obciąża tuner. Tam tylko ponawiamy (niżej). Dla innych źródeł: 3001..3004 = błędy
                // parsowania, 2000 = nieokreślony IO -> przechodzimy przez pozostałe tryby.
                if (!enigma && url != null && (code in 3001..3004 || code == PlaybackException.ERROR_CODE_IO_UNSPECIFIED)) {
                    val next = listOf(TS, AUTO, HLS, PS).firstOrNull { it !in tried }
                    if (next != null) {
                        load(url, next)
                        return
                    }
                }
                // Błędy przejściowe (sieć, limit połączeń, zajęty dekoder, tuner jeszcze się stroi):
                // ponowienia z rosnącą przerwą, zanim pokażemy błąd.
                val maxRetries = if (enigma) ENIGMA_RETRIES else MAX_RETRIES
                if (url != null && code in RETRYABLE && retries < maxRetries) {
                    retries++
                    main.postDelayed({
                        if (current?.url == url && error == null) {
                            tried.clear()
                            load(url, learned[url] ?: initialAttempt(url))
                        }
                    }, (if (enigma) 1200L else 800L) * retries)
                    return
                }
                error = "Błąd odtwarzania ($firstError; próby: ${tried.sorted()}; ostatni: ${e.errorCodeName}" +
                    (httpStatus?.let { "; HTTP $it" } ?: "") + ")"
            }

            override fun onTracksChanged(tracks: Tracks) = updateAudioInfo()

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) current?.url?.let { learned[it] = attempt }
                updateAudioInfo()
            }
        })
    }

    /**
     * Co 2 s sprawdza, czy strumień naprawdę idzie: w stanie READY muszą przybywać wyrenderowane klatki,
     * a buforowanie nie może trwać w nieskończoność. Po ~10 s bez postępu restartujemy kanał
     * (typowe zawieszenia po szybkim przełączaniu: obraz stoi przy działającym dźwięku albo wieczne kółko).
     */
    private val watchdog = object : Runnable {
        override fun run() {
            tick()
            wd.postDelayed(this, 2000)
        }
    }

    private fun buildDiag(): String {
        val v = player.videoFormat
        val a = player.audioFormat
        val mode = when (attempt) { HLS -> "HLS"; PS -> "PS"; TS -> "TS"; else -> "AUTO" }
        val st = when (player.playbackState) {
            Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "BUFOR"
            Player.STATE_READY -> "GRA"; Player.STATE_ENDED -> "KONIEC"; else -> "?"
        }
        val video = v?.let {
            "${it.sampleMimeType?.substringAfter('/')} ${it.width}x${it.height}" +
                (if (it.frameRate > 0) "@${it.frameRate.toInt()}" else "")
        } ?: "brak"
        val audio = a?.let { "${it.sampleMimeType?.substringAfter('/')} ${it.channelCount}ch" } ?: "brak"
        return "$mode · $st · V: $video [${videoDecoder ?: "—"}] · A: $audio [${audioDecoder ?: "—"}]" +
            " · 1. klatka: ${ttffMs?.let { "$it ms" } ?: "—"} · pominięte: $droppedFrames" +
            " · bufor: ${player.totalBufferedDuration / 1000}s" +
            (lastErrorName?.let { " · ost. błąd: $it (${retries}x)" } ?: "")
    }

    init {
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime, decoderName: String,
                initializedTimestampMs: Long, initializationDurationMs: Long,
            ) { videoDecoder = decoderName }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime, decoderName: String,
                initializedTimestampMs: Long, initializationDurationMs: Long,
            ) { audioDecoder = decoderName }

            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                this@PlayerController.droppedFrames += droppedFrames
            }

            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                if (ttffMs == null) ttffMs = renderTimeMs - loadStartMs
                loading = false
            }
        })
    }

    private fun tick() {
        diag = if (current != null) buildDiag() else ""
        // Strumienie bez obrazu (radio): ekran ładowania znika, gdy gra dźwięk.
        if (loading && player.playbackState == Player.STATE_READY && !player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) loading = false
        val url = current?.url
        if (url == null || error != null || !player.playWhenReady) { stuckTicks = 0; lastFrames = -1; return }
        val frames = player.videoDecoderCounters?.let { it.ensureUpdated(); it.renderedOutputBufferCount.toLong() } ?: -1L
        val hasVideo = player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
        val progressing = player.playbackState == Player.STATE_READY && (!hasVideo || frames != lastFrames)
        lastFrames = frames
        if (progressing) { stuckTicks = 0; return }
        // Przed pierwszą klatką dajemy więcej czasu: tuner Enigmy stroi się i rozkodowuje kanał nawet kilkanaście sekund.
        val limit = if (ttffMs == null) STARTUP_TICKS else STUCK_TICKS
        if (++stuckTicks < limit) return
        stuckTicks = 0
        recover(url)
    }

    private fun recover(url: String) {
        if (recoveries >= MAX_RECOVERIES) {
            error = "Strumień nie odpowiada (brak postępu przez ${(if (ttffMs == null) STARTUP_TICKS else STUCK_TICKS) * 2} s)"
            return
        }
        recoveries++
        lastFrames = -1
        tried.clear()
        player.stop()
        load(url, learned[url] ?: initialAttempt(url))
    }

    init { wd.postDelayed(watchdog, 2000) }

    /** Ostrzega o dźwięku dopiero gdy strumień już gra (READY), a żaden dekoder audio nie został wybrany. */
    private fun updateAudioInfo() {
        if (player.playbackState != Player.STATE_READY) { audioInfo = null; return }
        val groups = player.currentTracks.groups
        val audio = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        audioInfo = when {
            audio.isEmpty() -> "Brak ścieżki audio w strumieniu"
            player.audioFormat != null || audio.any { it.isSelected } -> null
            else -> "Brak dekodera dźwięku w tym urządzeniu (${audio.first().getTrackFormat(0).sampleMimeType ?: "?"})"
        }
    }

    private fun load(url: String, mode: Int) {
        loadStartMs = SystemClock.elapsedRealtime()
        ttffMs = null; droppedFrames = 0
        loading = true
        audioInfo = null
        attempt = mode
        tried += mode
        when (mode) {
            PS -> player.setMediaSource(psSourceFactory.createMediaSource(MediaItem.fromUri(url)))
            TS -> player.setMediaSource(tsSourceFactory.createMediaSource(MediaItem.fromUri(url)))
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
        httpStatus = null
        retries = 0
        recoveries = 0
        lastErrorName = null
        stuckTicks = 0
        lastFrames = -1
        main.removeCallbacksAndMessages(null)
        tried.clear()
        load(ch.url, learned[ch.url] ?: initialAttempt(ch.url))
    }

    fun pause() { player.playWhenReady = false }
    fun resume() { if (current != null) player.playWhenReady = true }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        player.stop()
        player.clearMediaItems()
        loading = false
        current = null
        error = null
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        wd.removeCallbacksAndMessages(null)
        player.release()
    }

    private companion object {
        const val AUTO = 0
        const val HLS = 1
        const val PS = 2
        const val TS = 3
        val ENIGMA_REF = Regex("""/\d+:\d+:[0-9a-f]+:[0-9a-f]+:""")
        const val MAX_RETRIES = 3
        const val ENIGMA_RETRIES = 4
        const val MAX_RECOVERIES = 3
        const val STUCK_TICKS = 5      // 5 x 2 s po starcie
        const val STARTUP_TICKS = 15   // 15 x 2 s do pierwszej klatki
        val RETRYABLE = setOf(
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            // Chwilowo "nie-strumień" z serwera (np. tuner się zajmuje przy zappingu) — drugie podejście zwykle działa.
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
        )
    }
}
