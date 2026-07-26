package com.driver.pro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.driver.pro.network.ApiService
import com.driver.pro.network.User
import com.driver.pro.viewmodels.StartScreenViewModel
import com.driver.pro.ui.components.CityDropdown
import com.driver.pro.ui.components.PriceInput
import com.driver.pro.viewmodels.StartScreenViewModelFactory

@Composable
fun StartScreen(
    user: User,
    api: ApiService
) {

    val viewModel: StartScreenViewModel = viewModel(
        factory = StartScreenViewModelFactory(api)
    )

    val cities = viewModel.cities
    val selectedCity = viewModel.selectedCity
    val price = viewModel.pricePerMile

    LaunchedEffect(user.id) {
        viewModel.loadData(user.id)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 100.dp, horizontal = 40.dp)
    ) {

        Text(
            text = "WELCOME ${user.username}",
            fontSize = 32.sp
        )

        Spacer(modifier = Modifier.height(70.dp))

        Text(
            "IN WHAT CITY DO YOU\nHAVE YOUR LICENCE?",
            fontSize = 26.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        CityDropdown(
            cities = viewModel.cities,
            selectedCity = viewModel.selectedCity,
            onCitySelected = { city ->
                viewModel.changeCity(user.id, city)
            },
            textFieldFontSize = 20.sp,
            dropdownFontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(70.dp))

        Text(
            "HOW MUCH DOES IT\nCOST YOU TO DRIVE\n1 MILE?",
            fontSize = 26.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        PriceInput(
            value = price,
            onValueChange = {
                viewModel.pricePerMile = it
            },
            onValueChangeFinished = { digits ->
                viewModel.updatePrice(user.id, digits)
            },
        )

    }
}