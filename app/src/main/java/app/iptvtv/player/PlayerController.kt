package app.iptvtv.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import app.iptvtv.data.ChannelEntity

/**
 * Jeden współdzielony ExoPlayer dla podglądu na liście i pełnego ekranu.
 * Przejście lista → pełny ekran tylko podpina inny PlayerView do tego samego playera,
 * więc strumień nie jest ładowany od nowa.
 */
class PlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
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

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Błąd odtwarzania (${e.errorCodeName})"
            }
        })
    }

    fun play(ch: ChannelEntity) {
        val same = current?.url == ch.url
        current = ch
        if (same && error == null && player.playbackState != Player.STATE_IDLE) {
            player.playWhenReady = true
            return
        }
        error = null
        player.setMediaItem(MediaItem.fromUri(ch.url))
        player.prepare()
        player.playWhenReady = true
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
