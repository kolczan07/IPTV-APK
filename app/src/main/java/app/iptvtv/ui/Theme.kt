package app.iptvtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

enum class ThemeMode(val label: String) { Dark("Ciemny"), Light("Jasny") }

/** Kolor wiodący (akcent) do wyboru w Ustawieniach aplikacji. */
enum class AccentColor(val label: String, val color: Color) {
    Teal("Turkusowy", Color(0xFF1FC2E0)),
    Blue("Niebieski", Color(0xFF4C8DFF)),
    Green("Zielony", Color(0xFF3DD68C)),
    Orange("Pomarańczowy", Color(0xFFFF9F43)),
    Red("Czerwony", Color(0xFFFF5C5C)),
    Purple("Fioletowy", Color(0xFFB07CFF)),
}

/** Paleta aplikacji budowana z trybu (ciemny/jasny) i koloru wiodącego. */
@Immutable
class Palette(
    val ink: Color, val panel: Color, val panelHi: Color,
    val accent: Color, val onAccent: Color,
    val focus: Color, val airing: Color,
    val text: Color, val textDim: Color,
    val dark: Boolean,
)

fun buildPalette(mode: ThemeMode, accentColor: AccentColor): Palette {
    val dark = mode == ThemeMode.Dark
    val panel = if (dark) Color(0xFF131A21) else Color(0xFFFFFFFF)
    // W jasnym motywie akcent jest przyciemniony, żeby tekst w tym kolorze był czytelny na bieli.
    val accent = if (dark) accentColor.color else lerp(accentColor.color, Color.Black, 0.25f)
    return Palette(
        ink = if (dark) Color(0xFF0A0E12) else Color(0xFFF0F4F7),
        panel = panel,
        panelHi = if (dark) Color(0xFF1C2630) else Color(0xFFE1E8ED),
        accent = accent,
        onAccent = if (accent.luminance() > 0.45f) Color(0xFF0A0E12) else Color.White,
        focus = lerp(panel, accent, if (dark) 0.30f else 0.22f),
        airing = lerp(panel, accent, if (dark) 0.18f else 0.12f),
        text = if (dark) Color(0xFFE8EEF2) else Color(0xFF12202A),
        textDim = if (dark) Color(0xFF9FB0BB) else Color(0xFF56687A),
        dark = dark,
    )
}

val LocalPalette = staticCompositionLocalOf { buildPalette(ThemeMode.Dark, AccentColor.Teal) }

// Styl w duchu MetrixHD z Enigmy2: płaskie panele i jeden kolor wiodący; kolory zależą od motywu.
val Ink: Color @Composable get() = LocalPalette.current.ink
val Panel: Color @Composable get() = LocalPalette.current.panel
val PanelHi: Color @Composable get() = LocalPalette.current.panelHi
val Accent: Color @Composable get() = LocalPalette.current.accent
val FocusBg: Color @Composable get() = LocalPalette.current.focus
val AiringBg: Color @Composable get() = LocalPalette.current.airing

@Composable
fun IptvTheme(mode: ThemeMode, accent: AccentColor, content: @Composable () -> Unit) {
    val p = remember(mode, accent) { buildPalette(mode, accent) }
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.ink, onBackground = p.text,
            surface = p.panel, onSurface = p.text,
            surfaceVariant = p.panelHi, onSurfaceVariant = p.textDim,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.ink, onBackground = p.text,
            surface = p.panel, onSurface = p.text,
            surfaceVariant = p.panelHi, onSurfaceVariant = p.textDim,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme,
            content = {
                // Teksty bez jawnego koloru (nagłówki, etykiety pól) dostawały czarny kolor na ciemnym tle.
                CompositionLocalProvider(LocalContentColor provides p.text, content = content)
            },
        )
    }
}
