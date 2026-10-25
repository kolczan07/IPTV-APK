package app.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import app.iptvtv.data.ProgrammeEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

fun clock(ms: Long): String = clockFormat.format(Date(ms))

fun progressOf(p: ProgrammeEntity, now: Long): Float =
    ((now - p.start).toFloat() / (p.stop - p.start).coerceAtLeast(1)).coerceIn(0f, 1f)

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}
