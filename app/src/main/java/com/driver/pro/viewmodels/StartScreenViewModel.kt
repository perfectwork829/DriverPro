package com.driver.pro.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driver.pro.network.ApiService
import com.driver.pro.network.City
import com.driver.pro.network.PricePerMileResponse
import com.driver.pro.utils.digitInputToApiPounds
import com.driver.pro.utils.formatApiPriceToDigitInput
import kotlin.math.abs
import kotlinx.coroutines.launch


class StartScreenViewModel(
    private val api: ApiService
) : ViewModel() {

    var cities by mutableStateOf<List<City>>(emptyList())
    var selectedCity by mutableStateOf<City?>(null)

    var pricePerMile by mutableStateOf("")
    var loadError by mutableStateOf<String?>(null)

    private var savedPriceRowId: Int? = null

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

                applySavedPrice(api.getPricePerMile(userId))
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
        val pounds = digitInputToApiPounds(digits)
        viewModelScope.launch {
            try {
                val body = linkedMapOf<String, Any>(
                    "user" to userId,
                    "price_per_mile" to pounds,
                )
                savedPriceRowId?.let { body["id"] = it }
                api.updatePricePerMile(userId, body)
                val rows = api.getPricePerMile(userId)
                val saved = rows.maxByOrNull { it.id }
                if (saved == null || abs(saved.price_per_mile - pounds) > 0.009) {
                    Log.e(
                        TAG,
                        "price_per_mile POST did not stick: sent=$pounds got=${rows.map { it.price_per_mile }}",
                    )
                    loadError = "Cost per mile did not save. Please try again."
                } else {
                    loadError = null
                    applySavedPrice(rows)
                    Log.d(TAG, "Saved price_per_mile=${saved.price_per_mile} id=${saved.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save price_per_mile", e)
                loadError = "Could not save cost per mile. Please try again."
            }
        }
    }

    private fun applySavedPrice(rows: List<PricePerMileResponse>) {
        val latest = rows.maxByOrNull { it.id }
        if (latest == null) {
            savedPriceRowId = null
            pricePerMile = ""
            Log.w(TAG, "GET price-per-mile returned empty list")
            return
        }
        savedPriceRowId = latest.id
        pricePerMile = formatApiPriceToDigitInput(latest.price_per_mile.toString())
        Log.d(TAG, "Loaded price_per_mile api=${latest.price_per_mile} display=$pricePerMile id=${latest.id}")
    }
}
