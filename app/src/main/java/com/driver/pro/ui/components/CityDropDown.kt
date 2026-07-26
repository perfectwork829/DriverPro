package com.driver.pro.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.driver.pro.network.City



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityDropdown(
    cities: List<City>,
    selectedCity: City?,
    onCitySelected: (City) -> Unit,
    textFieldFontSize: TextUnit = 16.sp,
    dropdownFontSize: TextUnit = 16.sp
) {

    var expanded by remember { mutableStateOf(false) }

    val selectedText = selectedCity?.let {
        "${it.name} (${it.type})"
    } ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {

        TextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            textStyle = TextStyle(fontSize = textFieldFontSize),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .clickable { expanded = !expanded }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {

            cities.forEach { city ->

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${city.name} (${city.type})",
                            fontSize = dropdownFontSize
                        )
                    },
                    onClick = {
                        onCitySelected(city)
                        expanded = false
                    }
                )
            }
        }
    }
}