package com.driver.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Material [SliderDefaults.Thumb] draws an elevation shadow; use this for a flat thumb. */
@Composable
fun FlatSliderThumb(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .background(color, CircleShape),
    )
}
