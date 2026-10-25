package app.iptvtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import app.iptvtv.ui.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IptvTheme { App(vm) } }
    }

    // Po wyjściu z aplikacji (Home) wstrzymujemy dźwięk, po powrocie wznawiamy.
    override fun onStart() {
        super.onStart()
        vm.controller.resume()
    }

    override fun onStop() {
        vm.controller.pause()
        super.onStop()
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val channels by vm.channels.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val m3u by vm.m3uUrl.collectAsStateWithLifecycle()
    val epgLink by vm.epgUrl.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val grid by vm.gridData.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (vm.screen) {
            null -> Unit
            Screen.Setup -> SetupScreen(
                initialM3u = m3u, initialEpg = epgLink,
                canCancel = m3u.isNotBlank(),
                versionName = vm.versionName,
                updatesEnabled = vm.updatesEnabled,
                updateText = updateLabel(vm.update),
                onCheckUpdate = { vm.checkUpdate(manual = true) },
                onSave = vm::save, onCancel = vm::backToList,
            )
            Screen.List -> ChannelListScreen(
                channels = channels, history = history, epg = guide, controller = vm.controller,
                status = vm.status, loading = vm.loading,
                onPreview = vm::tune, onPlay = vm::play, onToggleFavorite = vm::toggleFavorite,
                onRefresh = { vm.refresh() }, onSettings = vm::openSettings, onGuide = vm::openGuide,
            )
            Screen.Guide -> GuideScreen(
                channels = vm.guideChannels, byChannel = grid,
                onPlay = { vm.play(vm.guideChannels, it) }, onBack = vm::backToList,
            )
            Screen.Player -> PlayerScreen(
                queue = vm.queue, startIndex = vm.startIndex, epg = guide,
                controller = vm.controller, onTune = vm::tune, onBack = vm::closePlayer,
            )
        }
        if (vm.screen == Screen.List || vm.screen == Screen.Setup) {
            UpdateBanner(
                state = vm.update,
                onInstall = vm::installUpdate, onDismiss = vm::dismissUpdate,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
            )
        }
    }
}
