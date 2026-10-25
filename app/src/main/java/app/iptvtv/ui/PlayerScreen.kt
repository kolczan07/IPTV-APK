package app.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.iptvtv.data.ChannelEntity
import app.iptvtv.data.EpgSnapshot
import app.iptvtv.player.PlayerController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    queue: List<ChannelEntity>,
    startIndex: Int,
    epg: EpgSnapshot,
    controller: PlayerController,
    onTune: (ChannelEntity) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    if (queue.isEmpty()) { onBack(); return }

    var index by remember { mutableIntStateOf(startIndex.coerceIn(queue.indices)) }
    var overlay by remember { mutableStateOf(true) }
    val channel = queue[index]

    // Ten sam odtwarzacz co w podglądzie: jeśli kanał już gra, nic się nie przeładowuje.
    LaunchedEffect(channel.url) { onTune(channel) }
    LaunchedEffect(channel.url, overlay) {
        if (overlay) { delay(5000); overlay = false }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { index = (index + 1) % queue.size; overlay = true; true }
                    Key.DirectionDown, Key.ChannelDown -> { index = (index - 1 + queue.size) % queue.size; overlay = true; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { overlay = !overlay; true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    player = controller.player
                }
            },
            onRelease = { it.player = null },
        )
        controller.error?.let {
            Text(it, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
        }
        if (overlay) {
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD10222B))))
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = channel.logo, contentDescription = null,
                    modifier = Modifier.size(width = 96.dp, height = 60.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(channel.number?.toString(), channel.name).joinToString("  "),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    val (cur, next) = epg.nowNext(channel)
                    if (cur != null) {
                        Text(
                            "${clock(cur.start)}–${clock(cur.stop)}  ${cur.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                       )
                        ProgressLine(progressOf(cur, epg.now), Modifier.padding(vertical = 6.dp))
                        cur.description?.let {
                            Text(it, maxLines = 2, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(channel.group, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (next != null) {
                        Text(
                            "Następnie ${clock(next.start)}  ${next.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
