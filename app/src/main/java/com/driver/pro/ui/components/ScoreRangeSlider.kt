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
import androidx.compose.material3.RangeSlider
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
import com.driver.pro.HEADER_FONT_SIZE
import com.driver.pro.TITLE_FONT_SIZE
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreRangeSlider(
    title: String,
    unit: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    min: Float = 0f,
    max: Float = 150f,
    initialStart: Float = 30f,
    initialEnd: Float = 70f,
    /** Below reject thumb (left). */
    leftInactiveColor: Color = Color(0xFFE53935),
    /** Between reject and accept thumbs. */
    middleBandColor: Color = Color(0xFF9E9E9E),
    /** Above accept thumb (right) — accept band. */
    acceptBandColor: Color = Color(0xFF7FEA13),
    thumbColor: Color = Color.Black,
    onValueChange: ((Float, Float) -> Unit)? = null,
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
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = TITLE_FONT_SIZE)
            Switch(
                checked = enabled,
                onCheckedChange = { onEnabledChange(it) },
                modifier = Modifier.scale(0.8f),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackHeight = 12.dp.toPx()
                val widthPx = size.width
                val centerY = size.height / 2

                val leftPx = ((initialStart - min) / (max - min) * widthPx)
                val rightPx = ((initialEnd - min) / (max - min) * widthPx)

                drawRect(
                    color = leftInactiveColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2),
                    size = androidx.compose.ui.geometry.Size(leftPx, trackHeight),
                )
                drawRect(
                    color = middleBandColor,
                    topLeft = Offset(leftPx, centerY - trackHeight / 2),
                    size = androidx.compose.ui.geometry.Size(rightPx - leftPx, trackHeight),
                )
                drawRect(
                    color = acceptBandColor,
                    topLeft = Offset(rightPx, centerY - trackHeight / 2),
                    size = androidx.compose.ui.geometry.Size(widthPx - rightPx, trackHeight),
                )
            }

            RangeSlider(
                value = initialStart..initialEnd,
                onValueChange = {
                    if (enabled) {
                        onValueChange?.invoke(it.start, it.endInclusive)
                    }
                },
                onValueChangeFinished = {
                    if (enabled) {
                        onChangeFinished()
                    }
                },
                valueRange = min..max,
                steps = 0,
                colors = thumbColors,
                enabled = enabled,
                modifier = Modifier.fillMaxSize(),
                startThumb = {
                    FlatSliderThumb(color = thumbColor, enabled = enabled)
                },
                endThumb = {
                    FlatSliderThumb(color = thumbColor, enabled = enabled)
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Reject < $unit ${initialStart.roundToInt()}", fontSize = HEADER_FONT_SIZE)
            Text("Accept > $unit ${initialEnd.roundToInt()}", fontSize = HEADER_FONT_SIZE)
        }
    }
}
