package app.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

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
    BackHandler(enabled = canCancel, onBack = onCancel)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 120.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("Połącz z serwerem", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Logo kanałów i identyfikatory programu (tvg-id) są brane z playlisty M3U. EPG pobierany jest z osobnego adresu.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UrlField("Adres playlisty M3U", m3u) { m3u = it }
        UrlField("Adres EPG (XMLTV, także .xml.gz), opcjonalnie", epg) { epg = it }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { onSave(m3u, epg) }, enabled = m3u.isNotBlank()) { Text("Zapisz i wczytaj kanały") }
            if (canCancel) Button(onClick = onCancel) { Text("Anuluj") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Wersja $versionName", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (updatesEnabled) {
                Button(onClick = onCheckUpdate) { Text("Sprawdź aktualizacje") }
                if (updateText.isNotBlank()) Text(updateText, style = MaterialTheme.typography.bodyMedium)
            }
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
