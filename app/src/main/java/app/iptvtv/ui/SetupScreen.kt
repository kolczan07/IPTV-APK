package app.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.iptvtv.data.Account
import app.iptvtv.data.DEFAULT_ACCOUNT_ID

private const val KEY_NEW = "new"
private const val KEY_APP = "app"
private const val KEY_ABOUT = "about"
private const val KEY_ACC = "account"

private data class NavItem(val key: String, val label: String)

@Composable
fun SetupScreen(
    accounts: List<Account>,
    activeAccountId: String,
    canCancel: Boolean,
    versionName: String,
    updatesEnabled: Boolean,
    updateText: String,
    onCheckUpdate: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    accent: AccentColor,
    onAccent: (AccentColor) -> Unit,
    showDiag: Boolean,
    onShowDiag: (Boolean) -> Unit,
    epgOffsetMin: Int,
    onEpgOffset: (Int) -> Unit,
    epgLocalTime: Boolean,
    onEpgLocalTime: (Boolean) -> Unit,
    onSaveAccount: (Account) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(KEY_ACC) }
    // Które konto jest otwarte w "Ustawieniach konta" (id konta albo KEY_NEW przy dodawaniu).
    var accSel by rememberSaveable { mutableStateOf(DEFAULT_ACCOUNT_ID) }
    BackHandler(enabled = canCancel, onBack = onCancel)

    val nav = listOf(
        NavItem(KEY_ACC, "Ustawienia konta"),
        NavItem(KEY_APP, "Ustawienia aplikacji"),
        NavItem(KEY_ABOUT, "O aplikacji"),
    )
    // Usunięte konto: wróć na domyślne.
    if (accSel != KEY_NEW && accounts.none { it.id == accSel }) accSel = DEFAULT_ACCOUNT_ID
    val draftId = rememberSaveable { "acc-" + java.util.UUID.randomUUID().toString().take(8) }

    val content: @Composable () -> Unit = {
        when (page) {
            KEY_APP -> AppSection(
                themeMode = themeMode, onThemeMode = onThemeMode,
                accent = accent, onAccent = onAccent,
                showDiag = showDiag, onShowDiag = onShowDiag,
            )
            KEY_ABOUT -> AboutSection(
                versionName = versionName,
                updatesEnabled = updatesEnabled,
                updateText = updateText,
                onCheckUpdate = onCheckUpdate,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Konta: domyślne, dodane i "+" do dodania nowego.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a ->
                        AccountChip((if (a.id == activeAccountId) "✓ " else "") + a.name, a.id == accSel) { accSel = a.id }
                    }
                    AccountChip("+", accSel == KEY_NEW) { accSel = KEY_NEW }
                }
                if (accSel == KEY_NEW) {
                    AccountSection(
                        key = draftId, account = Account(draftId, "", "", ""), isDefault = false, isNew = true,
                        isActive = false,
                        epgOffsetMin = epgOffsetMin, onEpgOffset = onEpgOffset,
                        epgLocalTime = epgLocalTime, onEpgLocalTime = onEpgLocalTime,
                        onSave = { onSaveAccount(it); accSel = it.id },
                        onDelete = {},
                    )
                } else {
                    val acc = accounts.firstOrNull { it.id == accSel } ?: accounts.firstOrNull()
                    if (acc != null) AccountSection(
                        key = acc.id, account = acc, isDefault = acc.id == DEFAULT_ACCOUNT_ID, isNew = false,
                        isActive = acc.id == activeAccountId,
                        epgOffsetMin = epgOffsetMin, onEpgOffset = onEpgOffset,
                        epgLocalTime = epgLocalTime, onEpgLocalTime = onEpgLocalTime,
                        onSave = onSaveAccount,
                        onDelete = { onDeleteAccount(acc.id) },
                    )
                }
            }
        }
    }

    @Composable
    fun NavButton(item: NavItem, modifier: Modifier) {
        val selected = item.key == page
        Button(
            onClick = { page = item.key },
            modifier = modifier,
            colors = ButtonDefaults.colors(
                containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            ),
        ) { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }

    // Wąski ekran (telefon w pionie): sekcje jako rząd przycisków u góry, treść przewijana.
    if (LocalConfiguration.current.screenWidthDp < 720) {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Ustawienia", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                nav.forEach { NavButton(it, Modifier.heightIn(min = 48.dp)) }
                if (canCancel) Button(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Anuluj") }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
        return
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        // Lewa kolumna: konta i sekcje
        Column(Modifier.width(260.dp).fillMaxHeight()) {
            Text("Ustawienia", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(nav) { NavButton(it, Modifier.fillMaxWidth()) }
            }
            if (canCancel) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Anuluj") }
            }
        }

        Spacer(Modifier.width(32.dp))

        // Prawa kolumna: zawartość wybranej sekcji
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { content() }
    }
}

@Composable
private fun AccountChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text(label, maxLines = 1) }
}

@Composable
private fun AccountSection(
    key: String,
    account: Account,
    isDefault: Boolean,
    isNew: Boolean,
    isActive: Boolean,
    epgOffsetMin: Int,
    onEpgOffset: (Int) -> Unit,
    epgLocalTime: Boolean,
    onEpgLocalTime: (Boolean) -> Unit,
    onSave: (Account) -> Unit,
    onDelete: () -> Unit,
) {
    // Stan pól zależy od konta (key), więc przełączenie konta w nawigacji wczytuje jego wartości.
    var name by remember(key, account.name) { mutableStateOf(account.name) }
    var m3u by remember(key, account.m3u) { mutableStateOf(account.m3u) }
    var epg by remember(key, account.epg) { mutableStateOf(account.epg) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            when {
                isNew -> "Nowe konto"
                isDefault -> "Konto domyślne"
                else -> account.name
            },
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            (if (isActive) "To konto jest teraz aktywne. " else "") +
                "Logo kanałów i identyfikatory programu (tvg-id) są brane z playlisty M3U. " +
                "EPG pobierany jest z osobnego adresu. Pola włączają się po kliknięciu.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isDefault) {
            UrlField("Nazwa konta", name, { name = it }, keyboardType = KeyboardType.Text, placeholder = "np. Dom, Działka")
        }
        val pickM3u = rememberFilePicker { m3u = it }
        val pickEpg = rememberFilePicker { epg = it }
        UrlField("Adres playlisty M3U lub plik", m3u, { m3u = it }, onPickFile = pickM3u)
        UrlField("Adres EPG (XMLTV, także .xml.gz) lub plik, opcjonalnie", epg, { epg = it }, onPickFile = pickEpg)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Strefa czasowa EPG", style = MaterialTheme.typography.labelLarge)
            Button(onClick = { onEpgLocalTime(!epgLocalTime) }) {
                Text(if (epgLocalTime) "Czas lokalny urządzenia (ignoruj strefę z pliku)" else "Według strefy z pliku EPG")
            }
            Text(
                "Włącz, jeśli plik EPG ma błędną strefę po zmianie czasu: godziny są wtedy czytane jako czas lokalny " +
                    "urządzenia, z automatyczną zmianą lato/zima. Zmiana pobiera EPG ponownie. Dotyczy wszystkich kont.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EpgOffsetRow(epgOffsetMin, onEpgOffset)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    onSave(Account(account.id, if (isDefault) "Konto domyślne" else name.trim(), m3u, epg))
                },
                enabled = m3u.isNotBlank() && (isDefault || name.isNotBlank()),
            ) { Text(if (isNew) "Dodaj i wczytaj kanały" else "Zapisz i wczytaj kanały") }
            if (!isDefault && !isNew) Button(onClick = onDelete) { Text("Usuń konto") }
        }
    }
}

@Composable
private fun AppSection(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    accent: AccentColor,
    onAccent: (AccentColor) -> Unit,
    showDiag: Boolean,
    onShowDiag: (Boolean) -> Unit,
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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Odtwarzacz", style = MaterialTheme.typography.labelLarge)
            Button(onClick = { onShowDiag(!showDiag) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (showDiag) "✓ Pokazuj diagnostykę strumienia" else "Pokazuj diagnostykę strumienia")
            }
            Text(
                "Linia z formatem, dekoderami i buforem nad obrazem po wywołaniu nakładki. Przydatna tylko przy szukaniu błędów.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UrlField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Uri,
    placeholder: String = "Kliknij, aby wpisać",
    onPickFile: (() -> Unit)? = null,
) {
    // Pole tekstowe (i klawiatura) pojawia się dopiero po kliknięciu wiersza; najechanie kursorem nic nie otwiera.
    var editing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (onPickFile != null) Button(onClick = onPickFile) { Text("Wybierz plik…") }
        }
        if (!editing) {
            Card(
                onClick = { editing = true },
                modifier = Modifier.fillMaxWidth(),
                shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                border = CardDefaults.border(
                    border = Border(BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(10.dp)),
                    focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(10.dp)),
                ),
                colors = CardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(
                    when {
                        value.isBlank() -> placeholder
                        value.startsWith("content://") ->
                            "Plik: " + (android.net.Uri.parse(value).lastPathSegment?.substringAfterLast('/') ?: value)
                        else -> value
                    },
                    modifier = Modifier.padding(16.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 20.sp),
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            val fr = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            var hadFocus by remember { mutableStateOf(false) }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { editing = false }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fr)
                    .onFocusChanged {
                        if (it.isFocused) hadFocus = true else if (hadFocus) editing = false
                    }
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    .padding(16.dp),
            )
            LaunchedEffect(Unit) {
                fr.requestFocus()
                keyboard?.show()
            }
        }
    }
}

/** Wybór pliku z menedżera plików; zwraca adres content:// z trwałym dostępem do odczytu (przetrwa restart). */
@Composable
private fun rememberFilePicker(onPicked: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onPicked(uri.toString())
        }
    }
    // Na części dekoderów/TV nie ma menedżera plików systemu; wtedy po prostu nic się nie dzieje.
    return { runCatching { launcher.launch(arrayOf("*/*")) } }
}
