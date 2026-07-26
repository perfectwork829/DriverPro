package com.driver.pro.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driver.pro.network.ApiService
import com.driver.pro.network.City
import com.driver.pro.utils.digitInputToApiPrice
import com.driver.pro.utils.formatApiPriceToDigitInput

import kotlinx.coroutines.launch
import kotlin.collections.emptyList


class StartScreenViewModel(
    private val api: ApiService
) : ViewModel() {

    var cities by mutableStateOf<List<City>>(emptyList())
    var selectedCity by mutableStateOf<City?>(null)

    var pricePerMile by mutableStateOf("")
    var loadError by mutableStateOf<String?>(null)

    fun loadData(userId: Int) {
        viewModelScope.launch {
            loadError = null
            try {
                cities = api.getCities()

                val userCities = api.getUserCities(userId)
                if (userCities.isNotEmpty()) {
                    selectedCity = userCities.first().city
                } else {
                    Log.w(TAG, "No saved city for user $userId")
                }

                val priceRows = api.getPricePerMile(userId)
                if (priceRows.isNotEmpty()) {
                    val apiPrice = priceRows.first().price_per_mile
                    pricePerMile = formatApiPriceToDigitInput(apiPrice.toString())
                    Log.d(TAG, "Loaded price_per_mile api=$apiPrice display=$pricePerMile")
                } else {
                    pricePerMile = ""
                    Log.w(TAG, "GET price-per-mile returned empty list for user $userId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Start screen load failed for user $userId", e)
                loadError = e.message
            }
        }
    }

    companion object {
        private const val TAG = "StartScreenVM"
    }



    fun changeCity(userId: Int, city: City) {

        selectedCity = city

        viewModelScope.launch {
            api.updateUserCity(
                userId,
                mapOf(
                    "user" to userId,
                    "city_id" to city.id
                )
            )
        }
    }
    fun updatePrice(userId: Int, digits: String) {
        val pounds = digitInputToApiPrice(digits)
        viewModelScope.launch {
            try {
                api.updatePricePerMile(
                    userId,
                    mapOf(
                        "user" to userId.toString(),
                        "price_per_mile" to String.format("%.2f", pounds),
                    ),
                )
                Log.d(TAG, "Saved price_per_mile=$pounds for user $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save price_per_mile", e)
            }
        }
    }
}