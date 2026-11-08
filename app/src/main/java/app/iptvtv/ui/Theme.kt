package app.iptvtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Styl w duchu MetrixHD z Enigmy2: płaskie, prawie czarne panele i jeden turkusowy akcent.
val Ink = Color(0xFF0A0E12)
val Panel = Color(0xFF131A21)
val PanelHi = Color(0xFF1C2630)
val Accent = Color(0xFF1FC2E0)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color(0xFF00232B),
            background = Ink, onBackground = Color(0xFFE8EEF2),
            surface = Panel, onSurface = Color(0xFFE8EEF2),
            surfaceVariant = PanelHi, onSurfaceVariant = Color(0xFF9FB0BB),
        ),
        content = content,
    )
}
