package app.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

private const val SWIPE_PX = 120f

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun overlayButtonColors() = androidx.tv.material3.ButtonDefaults.colors(
    containerColor = Color(0xB30A0E12), contentColor = Color.White,
    focusedContainerColor = Accent, focusedContentColor = Color(0xFF00232B),
)

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
    // Przy szybkim przełączaniu (▲▼) czekamy chwilę na ostatni wybór, żeby nie zasypywać serwera
    // (tuner/limit strumieni) żądaniami o kanały, które i tak zaraz miną.
    LaunchedEffect(channel.url) {
        val cur = controller.current?.url
        if (cur != null && cur != channel.url) delay(250)
        onTune(channel)
    }
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
                    setKeepContentOnPlayerReset(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    player = controller.player
                }
            },
            onRelease = { it.player = null },
        )
        LoadingOverlay(controller.loading && controller.error == null, controller.current, Modifier.fillMaxSize())
        // Dotyk: tap = pokaż/ukryj nakładkę, przeciągnięcie w górę/w dół = następny/poprzedni kanał.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(queue.size) { detectTapGestures(onTap = { overlay = !overlay }) }
                .pointerInput(queue.size) {
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onDragEnd = {
                            if (dy < -SWIPE_PX) { index = (index + 1) % queue.size; overlay = true }
                            else if (dy > SWIPE_PX) { index = (index - 1 + queue.size) % queue.size; overlay = true }
                        },
                        onVerticalDrag = { _, d -> dy += d },
                    )
                },
        )
        controller.error?.let {
            Text(it, modifier = Modifier.align(Alignment.Center).padding(16.dp), color = MaterialTheme.colorScheme.primary)
        }
        controller.audioInfo?.let {
            Text(it, modifier = Modifier.align(Alignment.TopCenter).padding(top = 140.dp, start = 16.dp, end = 16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (overlay && controller.diag.isNotEmpty()) {
            Text(controller.diag,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 68.dp, start = 12.dp, end = 12.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (overlay) {
            // Przyciski dotykowe (na TV nie dostają fokusu, bo pilot obsługuje Box wyżej).
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp), colors = overlayButtonColors()) { Text("◀ Lista") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { index = (index - 1 + queue.size) % queue.size; overlay = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = overlayButtonColors(),
                ) { Text("▼ Poprz.") }
                Button(
                    onClick = { index = (index + 1) % queue.size; overlay = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = overlayButtonColors(),
                ) { Text("▲ Nast.") }
            }
        }
        if (overlay) {
            // Infobar w stylu MetrixHD: płaski ciemny panel, duży numer kanału, pasek postępu z godzinami.
            val (cur, next) = epg.nowNext(channel)
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color(0xE60A0E12))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = channel.logo, contentDescription = null,
                    modifier = Modifier.size(width = 96.dp, height = 60.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        channel.number?.let {
                            Text("$it", style = MaterialTheme.typography.headlineSmall, color = Accent)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(channel.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                    }
                    if (cur != null) {
                        Text(cur.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(clock(cur.start), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ProgressLine(progressOf(cur, epg.now), Modifier.weight(1f).padding(horizontal = 8.dp))
                            Text(clock(cur.stop), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                            color = Accent,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
