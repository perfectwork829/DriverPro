package com.driver.pro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreSlider(
    title: String,
    value: Float,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color = Color(0xFFE53935),
    inactiveColor: Color = Color(0xFF9E9E9E),
    thumbColor: Color = Color.Black,
    onValueChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val thumbColors = SliderDefaults.colors(
        activeTrackColor = Color.Transparent,
        inactiveTrackColor = Color.Transparent,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        thumbColor = thumbColor,
        disabledThumbColor = thumbColor,
        disabledActiveTrackColor = Color.Transparent,
        disabledInactiveTrackColor = Color.Transparent,
        disabledActiveTickColor = Color.Transparent,
        disabledInactiveTickColor = Color.Transparent,
    )

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 22.sp)
            Switch(
                checked = enabled,
                onCheckedChange = { onEnabledChange(it) },
                modifier = Modifier.scale(0.8f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackHeight = 12.dp.toPx()
                val widthPx = size.width
                val centerY = size.height / 2

                val percent =
                    (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)

                val activeWidth = percent * widthPx

                drawRect(
                    color = activeColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2),
                    size = androidx.compose.ui.geometry.Size(activeWidth, trackHeight),
                )

                drawRect(
                    color = inactiveColor,
                    topLeft = Offset(activeWidth, centerY - trackHeight / 2),
                    size = androidx.compose.ui.geometry.Size(widthPx - activeWidth, trackHeight),
                )
            }

            Slider(
                value = value,
                onValueChange = {
                    if (enabled) onValueChange(it)
                },
                onValueChangeFinished = {
                    if (enabled) onChangeFinished()
                },
                valueRange = valueRange,
                steps = 0,
                colors = thumbColors,
                enabled = enabled,
                modifier = Modifier.fillMaxSize(),
                thumb = {
                    FlatSliderThumb(color = thumbColor, enabled = enabled)
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = String.format("%.1f", value),
            fontSize = 18.sp,
        )
    }
}
