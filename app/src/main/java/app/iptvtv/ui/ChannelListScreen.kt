package app.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import app.iptvtv.data.ChannelEntity
import app.iptvtv.data.EpgSnapshot
import app.iptvtv.player.PlayerController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private const val ALL = "Wszystkie"
private const val FAV = "★ Ulubione"
private const val HIST = "Ostatnio oglądane"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelListScreen(
    channels: List<ChannelEntity>,
    history: List<ChannelEntity>?,
    epg: EpgSnapshot,
    controller: PlayerController,
    status: String,
    loading: Boolean,
    onPreview: (ChannelEntity) -> Unit,
    onPlay: (List<ChannelEntity>, Int) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onGuide: (List<ChannelEntity>) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(ALL) }
    val hist = history.orEmpty()
    val groups = remember(channels, hist.isNotEmpty()) {
        buildList {
            add(ALL)
            if (channels.any { it.favorite }) add(FAV)
            if (hist.isNotEmpty()) add(HIST)
            addAll(channels.map { it.group }.distinct())
        }
    }
    val active = if (selected in groups) selected else ALL
    val shown = remember(channels, hist, active) {
        when (active) {
            ALL -> channels
            FAV -> channels.filter { it.favorite }
            HIST -> hist
            else -> channels.filter { it.group == active }
        }
    }

    // --- start: fokus na ostatnio oglądanym kanale ---
    val listState = rememberLazyListState()
    val restoreRequester = remember { FocusRequester() }
    var restoreUrl by remember { mutableStateOf<String?>(null) }
    var focusDone by remember { mutableStateOf(false) }
    LaunchedEffect(shown.isNotEmpty(), history != null) {
        if (focusDone || shown.isEmpty() || history == null) return@LaunchedEffect
        val url = controller.current?.url ?: history.firstOrNull()?.url
        val idx = shown.indexOfFirst { it.url == url }.let { if (it < 0) 0 else it }
        restoreUrl = shown[idx].url
        listState.scrollToItem(idx)
        withFrameNanos { }
        runCatching { restoreRequester.requestFocus() }
        focusDone = true
    }

    // --- podgląd: kanał pod fokusem startuje po krótkim opóźnieniu ---
    var focusedCh by remember { mutableStateOf<ChannelEntity?>(null) }
    LaunchedEffect(focusedCh?.url) {
        val c = focusedCh ?: return@LaunchedEffect
        delay(1000)
        onPreview(c)
    }

    Row(Modifier.fillMaxSize().padding(24.dp)) {
        // kategorie i akcje
        Column(Modifier.width(200.dp).fillMaxHeight()) {
            Text("IPTV", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups) { g ->
                    Button(
                        onClick = { selected = g },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.colors(
                            containerColor = if (g == active) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface,
                        ),
                    ) { Text(g, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(8.dp))
Button(onClick = { onGuide(shown) }, modifier = Modifier.fillMaxWidth()) { Text("Przewodnik EPG") }
Spacer(Modifier.height(6.dp))
Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Ustawienia") }
        }

        Spacer(Modifier.width(20.dp))

        // lista kanałów
        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(if (loading) "Wczytywanie kanałów…" else "Brak kanałów w tej grupie")
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(shown, key = { _, c -> c.id }) { i, ch ->
                    ChannelRow(
                        ch = ch, epg = epg,
                        playing = controller.current?.url == ch.url,
                        modifier = Modifier
                            .then(if (ch.url == restoreUrl) Modifier.focusRequester(restoreRequester) else Modifier)
                            .onFocusChanged { if (it.hasFocus) focusedCh = ch },
                        onClick = { onPlay(shown, i) },
                        onLongClick = { onToggleFavorite(ch) },
                    )
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        // panel podglądu
        PreviewPane(controller, focusedCh ?: controller.current, epg, Modifier.width(320.dp).fillMaxHeight())
    }
}

@Composable
private fun PreviewPane(controller: PlayerController, ch: ChannelEntity?, epg: EpgSnapshot, modifier: Modifier) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)).background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        isFocusable = false
                        player = controller.player
                    }
                },
                onRelease = { it.player = null },
            )
            controller.error?.let {
                Text(it, modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (ch != null) {
            Text(ch.name, maxLines = 2, style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface)
            val (cur, next) = epg.nowNext(ch)
            if (cur != null) {
                Spacer(Modifier.height(6.dp))
                Text("${clock(cur.start)}–${clock(cur.stop)}  ${cur.title}", maxLines = 2,
                    style = MaterialTheme.typography.titleSmall)
                ProgressLine(progressOf(cur, epg.now), Modifier.padding(vertical = 6.dp))
                cur.description?.let {
                    Text(it, maxLines = 4, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (next != null) {
                Spacer(Modifier.height(6.dp))
                Text("Następnie ${clock(next.start)}  ${next.title}", maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(
    ch: ChannelEntity,
    epg: EpgSnapshot,
    playing: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cur = epg.nowNext(ch).first
    Card(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ch.logo, contentDescription = null,
                modifier = Modifier.size(width = 56.dp, height = 36.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ch.number != null) {
                        Text("${ch.number}  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(ch.name, maxLines = 1, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false))
                }
                if (cur != null) {
                    Text("${clock(cur.start)}  ${cur.title}", maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ProgressLine(progressOf(cur, epg.now), Modifier.padding(top = 4.dp))
                }
            }
            if (playing) Text(" ▶", color = MaterialTheme.colorScheme.primary)
            if (ch.favorite) Text(" ★", color = MaterialTheme.colorScheme.primary)
        }
    }
}
