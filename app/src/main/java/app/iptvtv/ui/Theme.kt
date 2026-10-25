package app.iptvtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Głęboki morski grafit + bursztynowy akcent jak kontrolka na wyświetlaczu.
val Ink = Color(0xFF10222B)
val Panel = Color(0xFF18323D)
val Amber = Color(0xFFFFB300)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber, onPrimary = Color(0xFF1A1200),
            background = Ink, onBackground = Color(0xFFEAF2F5),
            surface = Panel, onSurface = Color(0xFFEAF2F5),
            surfaceVariant = Color(0xFF244552), onSurfaceVariant = Color(0xFFB8CBD3),
        ),
        content = content,
    )
}
