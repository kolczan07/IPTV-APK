@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.iptvtv.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api

/**
 * Komponenty tv-material3 (1.0.0) reagują tylko na pilota (D-pad) — dotyk i mysz ignorują.
 * Poniższe opakowania dokładają obsługę dotyku (tap / długie przytrzymanie), a pilot działa jak wcześniej.
 * Są w tym samym pakiecie co ekrany, więc zastępują androidx.tv.material3.Button/Card bez zmian w wywołaniach.
 */
fun Modifier.touchClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    pointerInput(Unit) {
        detectTapGestures(
            onTap = { click() },
            onLongPress = { long?.invoke() },
        )
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.colors(),
    content: @Composable RowScope.() -> Unit,
) {
    androidx.tv.material3.Button(
        onClick = onClick,
        modifier = if (enabled) modifier.touchClick(onClick) else modifier,
        enabled = enabled,
        colors = colors,
        content = content,
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    colors: CardColors = CardDefaults.colors(),
    shape: CardShape = CardDefaults.shape(),
    border: CardBorder = CardDefaults.border(),
    scale: CardScale = CardDefaults.scale(),
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.tv.material3.Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.touchClick(onClick, onLongClick),
        colors = colors,
        shape = shape,
        border = border,
        scale = scale,
        content = content,
    )
}
