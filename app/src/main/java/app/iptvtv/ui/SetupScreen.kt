package app.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private enum class Section(val label: String) {
    Account("Ustawienia konta"),
    App("Ustawienia aplikacji"),
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
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    accent: AccentColor,
    onAccent: (AccentColor) -> Unit,
    epgOffsetMin: Int,
    onEpgOffset: (Int) -> Unit,
    epgLocalTime: Boolean,
    onEpgLocalTime: (Boolean) -> Unit,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var m3u by remember { mutableStateOf(initialM3u) }
    var epg by remember { mutableStateOf(initialEpg) }
    var section by rememberSaveable { mutableStateOf(Section.Account) }
    BackHandler(enabled = canCancel, onBack = onCancel)

    val content: @Composable () -> Unit = {
        when (section) {
            Section.Account -> AccountSection(
                m3u = m3u, epg = epg,
                onM3u = { m3u = it }, onEpg = { epg = it },
                epgOffsetMin = epgOffsetMin, onEpgOffset = onEpgOffset,
                epgLocalTime = epgLocalTime, onEpgLocalTime = onEpgLocalTime,
                onSave = { onSave(m3u, epg) },
            )
            Section.App -> AppSection(
                themeMode = themeMode, onThemeMode = onThemeMode,
                accent = accent, onAccent = onAccent,
            )
            Section.About -> AboutSection(
                versionName = versionName,
                updatesEnabled = updatesEnabled,
                updateText = updateText,
                onCheckUpdate = onCheckUpdate,
            )
        }
    }

    // Wąski ekran (telefon w pionie): sekcje jako rząd przycisków u góry, treść przewijana.
    if (LocalConfiguration.current.screenWidthDp < 720) {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Ustawienia", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Section.entries.forEach { s ->
                    Button(
                        onClick = { section = s },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = if (s == section) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface,
                        ),
                    ) { Text(s.label, maxLines = 1) }
                }
                if (canCancel) Button(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Anuluj") }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
        return
    }

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
        Column(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

@Composable
private fun AccountSection(
    m3u: String,
    epg: String,
    onM3u: (String) -> Unit,
    onEpg: (String) -> Unit,
    epgOffsetMin: Int,
    onEpgOffset: (Int) -> Unit,
    epgLocalTime: Boolean,
    onEpgLocalTime: (Boolean) -> Unit,
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Strefa czasowa EPG", style = MaterialTheme.typography.labelLarge)
            Button(onClick = { onEpgLocalTime(!epgLocalTime) }) {
                Text(if (epgLocalTime) "Czas lokalny urządzenia (ignoruj strefę z pliku)" else "Według strefy z pliku EPG")
            }
            Text(
                "Włącz, jeśli plik EPG ma błędną strefę po zmianie czasu: godziny są wtedy czytane jako czas lokalny " +
                    "urządzenia, z automatyczną zmianą lato/zima. Zmiana pobiera EPG ponownie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EpgOffsetRow(epgOffsetMin, onEpgOffset)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onSave, enabled = m3u.isNotBlank()) { Text("Zapisz i wczytaj kanały") }
        }
    }
}

@Composable
private fun EpgOffsetRow(offsetMin: Int, onChange: (Int) -> Unit) {
    val step = app.iptvtv.EPG_OFFSET_STEP_MIN
    val label = when {
        offsetMin == 0 -> "0 (bez korekty)"
        else -> {
            val a = kotlin.math.abs(offsetMin)
            val t = if (a % 60 == 0) "${a / 60} h" else if (a < 60) "$a min" else "${a / 60} h ${a % 60} min"
            (if (offsetMin > 0) "+" else "−") + t
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Korekta czasu EPG", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { onChange(-step) }) { Text("−") }
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.widthIn(min = 160.dp))
            Button(onClick = { onChange(step) }) { Text("+") }
            if (offsetMin != 0) Button(onClick = { onChange(-offsetMin) }) { Text("Zeruj") }
        }
        Text(
            "Jeśli program pokazuje się za późno (np. po zmianie czasu), ustaw −1 h; jeśli za wcześnie, +1 h. Działa od razu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppSection(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    accent: AccentColor,
    onAccent: (AccentColor) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Ustawienia aplikacji", style = MaterialTheme.typography.headlineLarge)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Motyw", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeMode.entries.forEach { m ->
                    Button(
                        onClick = { onThemeMode(m) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = if (m == themeMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (m == themeMode) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text((if (m == themeMode) "✓ " else "") + m.label) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kolor wiodący", style = MaterialTheme.typography.labelLarge)
            AccentColor.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { a ->
                        Button(
                            onClick = { onAccent(a) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(18.dp).background(a.color, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text((if (a == accent) "✓ " else "") + a.label, maxLines = 1)
                            }
                        }
                    }
                }
            }
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
        Text("Telewizja", style = MaterialTheme.typography.titleLarge)
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
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
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
