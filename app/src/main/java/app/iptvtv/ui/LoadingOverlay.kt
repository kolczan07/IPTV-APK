package app.iptvtv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.iptvtv.data.ChannelEntity
import coil.compose.AsyncImage

/**
 * Ekran ładowania zamiast kręcącego się kółka: logo i nazwa kanału na przyciemnionej ostatniej klatce poprzedniego kanału, z cienkim paskiem.
 * Znika łagodnym zanikiem (crossfade) w chwili, gdy pojawi się pierwsza klatka obrazu.
 */
@Composable
fun LoadingOverlay(
    visible: Boolean,
    channel: ChannelEntity?,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(350)),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), // przyciemnia ostatnią klatkę poprzedniego kanału
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Color.White) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (small) 6.dp else 14.dp)) {
                if (channel?.logo != null) {
                    AsyncImage(
                        model = channel.logo, contentDescription = null,
                        modifier = Modifier.size(width = if (small) 72.dp else 160.dp, height = if (small) 44.dp else 96.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                if (channel != null && !small) {
                    Text(channel.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2)
                }
                SweepBar(Modifier.width(if (small) 90.dp else 220.dp))
            }
            }
        }
    }
}

/** Cienki pasek z przesuwającym się akcentem (zamiast kółka). */
@Composable
private fun SweepBar(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "sweep")
    val x by t.animateFloat(
        initialValue = -0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    BoxWithConstraints(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.10f))) {
        val w = maxWidth
        Box(
            Modifier.offset(x = w * x).width(w * 0.35f).fillMaxHeight()
                .clip(RoundedCornerShape(2.dp)).background(Accent),
        )
    }
}
