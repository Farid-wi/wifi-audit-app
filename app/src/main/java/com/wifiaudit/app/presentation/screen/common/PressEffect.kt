package com.wifiaudit.app.presentation.screen.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Returns a scale factor (0.97 when pressed, 1.0 at rest) paired with a
 * [MutableInteractionSource] to share with a Material3 Button. Pass both so the
 * visual animation tracks the Button's own press state — no double tap handling.
 *
 * Usage:
 * ```
 * val (scale, source) = rememberPressedScale()
 * Button(
 *     onClick        = { … },
 *     interactionSource = source,
 *     modifier       = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
 * ) { … }
 * ```
 */
@Composable
fun rememberPressedScale(enabled: Boolean = true): Pair<Float, MutableInteractionSource> {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue    = if (enabled && isPressed) 0.97f else 1f,
        animationSpec  = spring(stiffness = Spring.StiffnessMedium),
        label          = "pressScale"
    )
    return scale to interactionSource
}
