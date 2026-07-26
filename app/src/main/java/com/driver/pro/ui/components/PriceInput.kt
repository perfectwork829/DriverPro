package com.driver.pro.ui.components

import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AnimatedDigitBox(char: Char, active: Boolean, alpha: Float) {
    // Slide animation when digit changes
    val offsetY by animateDpAsState(
        targetValue = if (active) (-4).dp else 0.dp,
        animationSpec = tween(durationMillis = 200)
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .offset(y = offsetY)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) Color.Blue else Color.Gray,
                shape = RoundedCornerShape(6.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (active && char == '0') {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(22.dp)
                    .alpha(alpha)
                    .background(Color.Blue)
            )
        } else {
            Text(char.toString(), fontSize = 20.sp)
        }
    }
}


@Composable
fun PriceInput(
    value: String,
    onValueChange: (String) -> Unit,
    onValueChangeFinished: (String) -> Unit
) {
    val maxDigits = 3
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Filter input and limit
    val digits = value.filter { it.isDigit() }.take(maxDigits)

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = digits,
                selection = TextRange(digits.length)
            )
        )
    }

    LaunchedEffect(value) {
        val newDigits = value.filter { it.isDigit() }.take(maxDigits)

        textFieldValue = TextFieldValue(
            text = newDigits,
            selection = TextRange(newDigits.length) // cursor always end
        )
    }

    // Assign boxes
    val box0 = digits.getOrNull(0) ?: '-'
    val box1 = digits.getOrNull(1) ?: '-'
    val box2 = digits.getOrNull(2) ?: '-'

    Log.d("focus changeed: value", value.toString())
    Log.d("focus changeed: digits", digits.toString())
    // Active box: next empty
    val activeIndex = when (digits.length) {
        0 -> 0
        1 -> 1
        2 -> 2
        else -> -1
    }

    Log.d("focus changeed: digit length", digits.length.toString())
    Log.d("focus changeed: activeIndex", activeIndex.toString())

    // Blinking animation only if focused
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        )
    )



    BasicTextField(
        value = textFieldValue,
        onValueChange = { input ->

            val filtered = input.text
                .filter { it.isDigit() }
                .take(maxDigits)

            textFieldValue = TextFieldValue(
                text = filtered,
                selection = TextRange(filtered.length) // force cursor end
            )

            onValueChange(filtered)
            if(filtered.length > 2) {
                onValueChangeFinished(filtered)
            }

        },

        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
            },
        decorationBox = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("£", fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                AnimatedDigitBox(box0, active = isFocused && activeIndex == 0, alpha)
                Text(".", fontSize = 22.sp, modifier = Modifier.padding(horizontal = 6.dp))
                AnimatedDigitBox(box1, active = isFocused && activeIndex == 1, alpha)
                AnimatedDigitBox(box2, active = isFocused && activeIndex == 2, alpha)
            }
        }
    )

}