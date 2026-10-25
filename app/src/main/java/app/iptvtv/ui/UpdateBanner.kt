package app.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.iptvtv.update.UpdateState

fun updateLabel(s: UpdateState): String = when (s) {
    UpdateState.Checking -> "Sprawdzam…"
    UpdateState.UpToDate -> "Masz najnowszą wersję"
    is UpdateState.Available -> "Dostępna wersja ${s.info.version}"
    is UpdateState.Downloading -> "Pobieranie ${(s.progress * 100).toInt()}%"
    is UpdateState.Ready -> "Pobrano ${s.info.version}"
    is UpdateState.Failed -> s.message
    UpdateState.Idle -> ""
}

/** Mała karta w rogu ekranu: nowa wersja, postęp pobierania, instalacja. */
@Composable
fun UpdateBanner(state: UpdateState, onInstall: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val visible = state is UpdateState.Available || state is UpdateState.Downloading ||
        state is UpdateState.Ready || state is UpdateState.Failed
    if (!visible) return

    Column(
        modifier.width(300.dp).background(Panel, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            is UpdateState.Available -> {
                Text("Dostępna wersja ${state.info.version}", style = MaterialTheme.typography.titleMedium)
                state.info.notes.takeIf { it.isNotBlank() }?.let {
                    Text(it, maxLines = 3, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is UpdateState.Downloading -> {
                Text("Pobieranie ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                ProgressLine(state.progress)
            }
            is UpdateState.Ready -> Text(
                "Pobrano ${state.info.version}. Jeśli instalacja się nie uruchomiła, zezwól aplikacji na instalowanie z nieznanych źródeł i naciśnij ponownie.",
                style = MaterialTheme.typography.bodySmall,
            )
            is UpdateState.Failed -> Text(state.message, style = MaterialTheme.typography.bodySmall)
            else -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state is UpdateState.Available || state is UpdateState.Ready) {
                Button(onClick = onInstall) {
                    Text(if (state is UpdateState.Ready) "Zainstaluj ponownie" else "Zainstaluj")
                }
            }
            if (state !is UpdateState.Downloading) Button(onClick = onDismiss) { Text("Później") }
        }
    }
}
