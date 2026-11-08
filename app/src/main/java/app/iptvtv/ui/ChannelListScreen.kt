package app.iptvtv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import app.iptvtv.data.ChannelEntity
import app.iptvtv.data.EpgSnapshot
import app.iptvtv.player.PlayerController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val ALL = "Wszystkie"
private const val FAV = "★ Ulubione"
private const val HIST = "Ostatnio oglądane"
private const val COMPACT_DP = 720

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
    // Wąski ekran (telefon w pionie): jedna kolumna, bez panelu podglądu.
    val compact = LocalConfiguration.current.screenWidthDp < COMPACT_DP
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
    // Stan fokusu jest czytany tylko tu (snapshotFlow) i w PreviewPane, żeby krok kursora nie przerysowywał całej listy.
    val previewCb by rememberUpdatedState(onPreview)
    val isCompact by rememberUpdatedState(compact)
    LaunchedEffect(Unit) {
        snapshotFlow { focusedCh }.collectLatest { c ->
            if (c == null || isCompact) return@collectLatest
            delay(1000)
            previewCb(c)
        }
    }

    val channelList: @Composable (Modifier) -> Unit = { mod ->
        if (shown.isEmpty()) {
            Box(mod, contentAlignment = Alignment.Center) {
                Text(if (loading) "Wczytywanie kanałów…" else "Brak kanałów w tej grupie")
            }
        } else {
            LazyColumn(
                mod,
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
    }

    var showGroups by remember { mutableStateOf(false) }
    if (showGroups) {
        GroupsDialog(groups, active, onPick = { selected = it; showGroups = false }, onDismiss = { showGroups = false })
    }

    if (compact) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Telewizja", style = MaterialTheme.typography.headlineSmall, color = Accent)
                Text("  ·  $active", maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showGroups = true }, modifier = Modifier.heightIn(min = TOUCH_MIN)) { Text("Grupy kanałów") }
                Button(onClick = { onGuide(shown) }, modifier = Modifier.heightIn(min = TOUCH_MIN)) { Text("EPG") }
                Button(onClick = onSettings, modifier = Modifier.heightIn(min = TOUCH_MIN)) { Text("Ustawienia") }
            }
            Spacer(Modifier.height(8.dp))
            channelList(Modifier.weight(1f).fillMaxWidth())
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // pasek u góry: grupy ukryte w oknie, ustawienia są tuż pod ręką
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Telewizja", style = MaterialTheme.typography.headlineMedium, color = Accent)
            Text("·  $active", maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { showGroups = true }) { Text("Grupy kanałów") }
            Button(onClick = { onGuide(shown) }) { Text("Przewodnik EPG") }
            Button(onClick = onSettings) { Text("Ustawienia") }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // lista kanałów
            channelList(Modifier.weight(1f).fillMaxHeight())

            Spacer(Modifier.width(24.dp))

            // panel podglądu
            PreviewPane(controller, { focusedCh ?: controller.current }, epg, Modifier.width(340.dp).fillMaxHeight())
        }
    }
}

private val TOUCH_MIN = 48.dp

@Composable
private fun GroupsDialog(groups: List<String>, active: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        Column(
            Modifier.width(380.dp).heightIn(max = 520.dp)
                .background(Panel, RoundedCornerShape(20.dp)).padding(20.dp),
        ) {
            Text("Grupy kanałów", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups) { g ->
                    GroupButton(
                        g, g == active,
                        Modifier.fillMaxWidth().heightIn(min = TOUCH_MIN)
                            .then(if (g == active) Modifier.focusRequester(first) else Modifier),
                    ) { onPick(g) }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupButton(group: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = if (active) MaterialTheme.colorScheme.primary else PanelHi,
            focusedContentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
    ) { Text(group, maxLines = 1) }
}

@Composable
private fun PreviewPane(controller: PlayerController, chProvider: () -> ChannelEntity?, epg: EpgSnapshot, modifier: Modifier) {
    val ch = chProvider()
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(2.dp)).background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        isFocusable = false
                        setKeepContentOnPlayerReset(true)
                        player = controller.player
                    }
                },
                onRelease = { it.player = null },
            )
            LoadingOverlay(controller.loading && controller.error == null, controller.current, Modifier.fillMaxSize(), small = true)
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
    val shape = RoundedCornerShape(16.dp)
    Card(
        onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth(),
        shape = CardDefaults.shape(shape),
        border = CardDefaults.border(
            border = Border(BorderStroke(if (playing) 1.5.dp else 0.dp, if (playing) Accent else Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(2.5.dp, Accent), shape = shape),
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f),
        colors = CardDefaults.colors(
            containerColor = Panel,
            focusedContainerColor = FocusBg,
        ),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // logo w zaokrąglonym kafelku
            Box(
                Modifier.size(width = 68.dp, height = 44.dp).clip(RoundedCornerShape(12.dp)).background(PanelHi),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ch.logo, contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ch.number != null) {
                        Text("${ch.number}", color = Accent, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 8.dp))
                    }
                    Text(ch.name, maxLines = 1, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false))
                }
                if (cur != null) {
                    Text("${clock(cur.start)}  ${cur.title}", maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ProgressLine(progressOf(cur, epg.now), Modifier.padding(top = 6.dp))
                }
            }
            if (ch.favorite) Text("  ★", color = Accent)
            if (playing) Text("  ▶", color = Accent)
        }
    }
}
