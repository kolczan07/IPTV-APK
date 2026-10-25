package app.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private enum class Section(val label: String) {
    Account("Ustawienia konta"),
    About("O aplikacji"),
}

@Composable
fun SetupScreen(
    initialM3u: String,
    initialEpg: String,
    canCancel: Boolean,
    versionName: String,
    updatesEnabled: Boolean,
    updateText: String,
    onCheckUpdate: () -> Unit,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var m3u by remember { mutableStateOf(initialM3u) }
    var epg by remember { mutableStateOf(initialEpg) }
    var section by rememberSaveable { mutableStateOf(Section.Account) }
    BackHandler(enabled = canCancel, onBack = onCancel)

    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        // Lewa kolumna: nawigacja między sekcjami
        Column(Modifier.width(240.dp).fillMaxHeight()) {
            Text("Ustawienia", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Section.entries) { s ->
                    Button(
                        onClick = { section = s },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.colors(
                            containerColor = if (s == section) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface,
                        ),
                    ) { Text(s.label, maxLines = 1) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (canCancel) {
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Anuluj") }
            }
        }

        Spacer(Modifier.width(32.dp))

        // Prawa kolumna: zawartość wybranej sekcji
        Column(Modifier.weight(1f).fillMaxHeight()) {
            when (section) {
                Section.Account -> AccountSection(
                    m3u = m3u, epg = epg,
                    onM3u = { m3u = it }, onEpg = { epg = it },
                    onSave = { onSave(m3u, epg) },
                )
                Section.About -> AboutSection(
                    versionName = versionName,
                    updatesEnabled = updatesEnabled,
                    updateText = updateText,
                    onCheckUpdate = onCheckUpdate,
                )
            }
        }
    }
}

@Composable
private fun AccountSection(
    m3u: String,
    epg: String,
    onM3u: (String) -> Unit,
    onEpg: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Połącz z serwerem", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Logo kanałów i identyfikatory programu (tvg-id) są brane z playlisty M3U. " +
                "EPG pobierany jest z osobnego adresu.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UrlField("Adres playlisty M3U", m3u, onM3u)
        UrlField("Adres EPG (XMLTV, także .xml.gz), opcjonalnie", epg, onEpg)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onSave, enabled = m3u.isNotBlank()) { Text("Zapisz i wczytaj kanały") }
        }
    }
}

@Composable
private fun AboutSection(
    versionName: String,
    updatesEnabled: Boolean,
    updateText: String,
    onCheckUpdate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("O aplikacji", style = MaterialTheme.typography.headlineLarge)
        Text("IPTV", style = MaterialTheme.typography.titleLarge)
        Text(
            "Odtwarzacz IPTV na Android TV (Kotlin, Compose for TV, Media3). " +
                "Własne źródła: link do M3U + link do EPG (XMLTV).",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Wersja $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (updatesEnabled) {
            Button(onClick = onCheckUpdate) { Text("Sprawdź aktualizacje") }
            if (updateText.isNotBlank()) {
                Text(updateText, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text(
                "Aktualizacje są wyłączone w tej wersji (build lokalny).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UrlField(label: String, value: String, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                )
                .padding(16.dp),
        )
    }
}
