package app.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
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
import kotlin.math.roundToInt

private const val ALL = "Wszystkie"
private const val FAV = "★ Ulubione"
private const val HIST = "Ostatnio oglądane"
private const val COMPACT_DP = 720

/** Podgląd w miniTV startuje dopiero po tylu ms od zatrzymania kursora na kanale (przewijanie nie przestraja tunera). */
private const val PREVIEW_DELAY_MS = 1750L

/** Po tylu ms bez ruchu kursora (fokus, pilot, mysz, dotyk) miniTV rozwija się na pełny ekran. */
private const val IDLE_FULLSCREEN_MS = 4000L

/** Współdzielone (bez rekompozycji) informacje o miniTV: widok odtwarzacza i jego położenie w oknie. */
private class PreviewHolder {
    var view: PlayerView? = null
    var bounds: Rect = Rect.Zero
    var origin: Offset = Offset.Zero
}

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
            delay(PREVIEW_DELAY_MS)
            previewCb(c)
        }
    }

    // --- miniTV na pełny ekran po 4 s bez ruchu kursora ---
    var idleTick by remember { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf(false) }
    val holder = remember { PreviewHolder() }
    var showGroups by remember { mutableStateOf(false) }
    val canExpand = !compact && !showGroups && shown.isNotEmpty() &&
        controller.current != null && controller.error == null
    LaunchedEffect(idleTick, canExpand, fullscreen) {
        if (canExpand && !fullscreen) {
            delay(IDLE_FULLSCREEN_MS)
            fullscreen = true
        }
    }
    LaunchedEffect(canExpand) { if (!canExpand) fullscreen = false }
    BackHandler(enabled = fullscreen) { fullscreen = false; idleTick++ }
    val progress by animateFloatAsState(
        targetValue = if (fullscreen) 1f else 0f,
        animationSpec = tween(if (fullscreen) 650 else 380, easing = FastOutSlowInEasing),
        label = "miniTvFullscreen",
    )

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
                            .onFocusChanged { if (it.hasFocus) { focusedCh = ch; idleTick++ } },
                        onClick = { onPlay(shown, i) },
                        onLongClick = { onToggleFavorite(ch) },
                    )
                }
            }
        }
    }

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

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { holder.origin = it.positionInWindow() }
            // Każdy klawisz pilota to aktywność; w trybie pełnoekranowym pierwszy klawisz tylko zwija miniTV.
            .onPreviewKeyEvent { e ->
                idleTick++
                if (fullscreen) {
                    if (e.type == KeyEventType.KeyDown) { fullscreen = false }
                    true
                } else false
            }
            // Mysz/dotyk: realny ruch (nie syntetyczne powtórzenia zdarzenia po zmianie layoutu) lub dotknięcie.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var last = Offset.Unspecified
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val pos = ev.changes.firstOrNull()?.position ?: continue
                        val moved = last.isSpecified && (pos - last).getDistance() > 2f
                        last = pos
                        val active = when (ev.type) {
                            PointerEventType.Press, PointerEventType.Scroll -> true
                            PointerEventType.Move -> moved
                            else -> false
                        }
                        if (active) {
                            idleTick++
                            if (fullscreen) fullscreen = false
                        }
                    }
                }
            },
    ) {
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
                PreviewPane(controller, holder, { focusedCh ?: controller.current }, epg, Modifier.width(340.dp).fillMaxHeight())
            }
        }
        if (fullscreen || progress > 0f) {
            FullscreenPreview(progress, controller, holder, epg)
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
private fun PreviewPane(controller: PlayerController, holder: PreviewHolder, chProvider: () -> ChannelEntity?, epg: EpgSnapshot, modifier: Modifier) {
    val ch = chProvider()
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .onGloballyPositioned { holder.bounds = it.boundsInWindow() }
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
                    }.also { holder.view = it }
                },
                onRelease = { holder.view = null; it.player = null },
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

/**
 * miniTV rozwijane na cały ekran: obraz rośnie płynnie z pozycji podglądu, reszta ekranu zanika do czerni,
 * a na końcu wjeżdża pasek z nazwą kanału i bieżącym programem. [progress] 0 = miniTV, 1 = pełny ekran.
 */
@Composable
private fun FullscreenPreview(progress: Float, controller: PlayerController, holder: PreviewHolder, epg: EpgSnapshot) {
    val density = LocalDensity.current
    // Ten sam odtwarzacz ma jedną powierzchnię wideo: po zwinięciu oddajemy ją widokowi w podglądzie.
    DisposableEffect(Unit) {
        onDispose {
            holder.view?.let { v -> v.player = null; v.player = controller.player }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val mini = holder.bounds.translate(-holder.origin.x, -holder.origin.y).let {
            if (it.width > 1f && it.height > 1f) it
            else Rect(fullW * 0.4f, fullH * 0.4f, fullW * 0.6f, fullH * 0.6f)
        }
        val left = lerp(mini.left, 0f, progress)
        val top = lerp(mini.top, 0f, progress)
        val w = lerp(mini.width, fullW, progress)
        val h = lerp(mini.height, fullH, progress)

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = progress)))
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .background(Color.Black),
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
            LoadingOverlay(controller.loading && controller.error == null, controller.current, Modifier.fillMaxSize(), small = progress < 0.5f)
        }

        // pasek informacyjny wjeżdża dopiero, gdy obraz prawie wypełni ekran
        val ch = controller.current
        val a = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
        if (ch != null && a > 0f) {
            val (cur, _) = epg.nowNext(ch)
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .graphicsLayer { alpha = a; translationY = (1f - a) * 48.dp.toPx() }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE60A0E12))))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = ch.logo, contentDescription = null,
                    modifier = Modifier.size(width = 96.dp, height = 60.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        ch.number?.let {
                            Text("$it", style = MaterialTheme.typography.headlineSmall, color = Accent)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(ch.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                    }
                    if (cur != null) {
                        Text(cur.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                        ProgressLine(progressOf(cur, epg.now), Modifier.padding(top = 6.dp))
                    }
                }
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
