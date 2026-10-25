package app.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.iptvtv.data.ChannelEntity
import app.iptvtv.data.ProgrammeEntity
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private const val SLOT_MS = 30 * 60_000L
private const val SLOTS = 16                       // 8 godzin
private const val DP_PER_MIN = 5f                  // 30 min = 150 dp
private val CHANNEL_W = 220.dp
private val ROW_H = 56.dp

private fun dpFor(ms: Long): Dp = (ms / 60_000f * DP_PER_MIN).dp
private val TOTAL_W = dpFor(SLOT_MS * SLOTS)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GuideScreen(
    channels: List<ChannelEntity>,
    byChannel: Map<String, List<ProgrammeEntity>>,
    onPlay: (Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val t0 = remember { System.currentTimeMillis() / SLOT_MS * SLOT_MS - SLOT_MS }
    val t1 = t0 + SLOT_MS * SLOTS

    val hScroll = rememberScrollState()
    var focused by remember { mutableStateOf<ProgrammeEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        // Szczegóły programu pod fokusem
        Column(Modifier.fillMaxWidth().height(96.dp)) {
            val f = focused
            if (f == null) {
                Text("Wybierz program, aby zobaczyć szczegóły. OK odtwarza kanał.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${clock(f.start)}–${clock(f.stop)}  ${f.title}", maxLines = 1,
                    style = MaterialTheme.typography.titleLarge)
                f.description?.let {
                    Text(it, maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Oś czasu
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(CHANNEL_W + 6.dp))
            Row(Modifier.weight(1f).horizontalScroll(hScroll, enabled = false)) {
                Row(Modifier.width(TOTAL_W)) {
                    repeat(SLOTS) { i ->
                        Box(Modifier.width(dpFor(SLOT_MS))) {
                            Text(clock(t0 + i * SLOT_MS), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, ch ->
                val list = ch.tvgId?.trim()?.lowercase()?.let { byChannel[it] }
                Row(Modifier.fillMaxWidth().height(ROW_H)) {
                    ChannelHeader(ch, onClick = { onPlay(index) })
                    Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                        ProgrammeCells(list, t0, t1, now, onFocus = { focused = it }, onClick = { onPlay(index) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelHeader(ch: ChannelEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(CHANNEL_W).fillMaxHeight().padding(end = 6.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ch.logo, contentDescription = null,
                modifier = Modifier.size(width = 48.dp, height = 30.dp), contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(10.dp))
            Text(ch.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProgrammeCells(
    list: List<ProgrammeEntity>?,
    t0: Long,
    t1: Long,
    now: Long,
    onFocus: (ProgrammeEntity) -> Unit,
    onClick: () -> Unit,
) {
    Row(Modifier.width(TOTAL_W).fillMaxHeight()) {
        if (list.isNullOrEmpty()) {
            Box(Modifier.fillMaxHeight().padding(start = 10.dp), contentAlignment = Alignment.CenterStart) {
                Text("Brak danych programu", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Row
        }
        var cursor = t0
        list.forEach { p ->
            val s = maxOf(p.start, cursor)
            val e = minOf(p.stop, t1)
            if (e <= s) return@forEach
            if (s > cursor) Spacer(Modifier.width(dpFor(s - cursor)))
            val airing = p.start <= now && p.stop > now
            Card(
                onClick = onClick,
                modifier = Modifier
                    .width(dpFor(e - s))
                    .fillMaxHeight()
                    .padding(end = 2.dp)
                    .onFocusChanged { if (it.hasFocus) onFocus(p) },
                colors = CardDefaults.colors(
                    containerColor = if (airing) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    Text(p.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                }
            }
            cursor = e
        }
    }
}
