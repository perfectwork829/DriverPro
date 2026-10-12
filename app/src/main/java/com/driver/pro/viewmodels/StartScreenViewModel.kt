package com.driver.pro.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driver.pro.network.ApiService
import com.driver.pro.network.City
import com.driver.pro.network.PricePerMileResponse
import com.driver.pro.network.rowUsedForScoring
import com.driver.pro.saveOperatingCostPerMile
import com.driver.pro.getOperatingCostPerMile
import com.driver.pro.utils.digitInputToApiPounds
import com.driver.pro.utils.formatApiPriceToDigitInput
import kotlin.math.abs
import kotlinx.coroutines.launch
import retrofit2.HttpException


class StartScreenViewModel(
    private val api: ApiService,
    private val appContext: Context,
) : ViewModel() {

    var cities by mutableStateOf<List<City>>(emptyList())
    var selectedCity by mutableStateOf<City?>(null)

    var pricePerMile by mutableStateOf("")
    var loadError by mutableStateOf<String?>(null)

    private var scoringPriceRowId: Int? = null

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
                pricePerMile = formatApiPriceToDigitInput(
                    getOperatingCostPerMile(appContext).toString(),
                )
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
        saveOperatingCostPerMile(appContext, pounds)
        pricePerMile = formatApiPriceToDigitInput(pounds.toString())
        viewModelScope.launch {
            try {
                val body = linkedMapOf<String, Any>(
                    "user" to userId,
                    "price_per_mile" to pounds,
                )
                scoringPriceRowId?.let { body["id"] = it }
                try {
                    api.replacePricePerMile(userId, body)
                } catch (e: HttpException) {
                    if (e.code() !in setOf(404, 405, 501)) throw e
                    api.updatePricePerMile(userId, body)
                } catch (e: Exception) {
                    api.updatePricePerMile(userId, body)
                }
                val rows = api.getPricePerMile(userId)
                val saved = rows.rowUsedForScoring()
                if (saved == null || abs(saved.price_per_mile - pounds) > 0.009) {
                    Log.e(
                        TAG,
                        "price_per_mile did not stick on scoring row: sent=$pounds got=${rows.map { it.id to it.price_per_mile }}",
                    )
                    loadError = "Cost saved on this phone. Server copy may still be £0.50 — please try again."
                    applyLocalDigits(pounds)
                } else {
                    loadError = null
                    applySavedPrice(rows)
                    Log.d(TAG, "Saved price_per_mile=${saved.price_per_mile} scoringRowId=${saved.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save price_per_mile", e)
                applyLocalDigits(pounds)
                loadError = "Cost saved on this phone. Could not update the server. Please try again."
            }
        }
    }

    private fun applyLocalDigits(pounds: Double) {
        pricePerMile = formatApiPriceToDigitInput(pounds.toString())
    }

    private fun applySavedPrice(rows: List<PricePerMileResponse>) {
        val scoring = rows.rowUsedForScoring()
        val local = getOperatingCostPerMile(appContext)
        if (scoring == null) {
            scoringPriceRowId = null
            pricePerMile = formatApiPriceToDigitInput(local.toString())
            Log.w(TAG, "GET price-per-mile returned empty list, using local=$local")
            return
        }
        scoringPriceRowId = scoring.id
        val apiPrice = scoring.price_per_mile
        // Prefer the value the driver just set locally if the scoring row is still the old 0.50.
        val display = if (abs(local - apiPrice) > 0.009 && abs(apiPrice - 0.50) < 0.009 && local != 0.50) {
            local
        } else {
            apiPrice
        }
        pricePerMile = formatApiPriceToDigitInput(display.toString())
        Log.d(TAG, "Loaded price_per_mile scoring=$apiPrice local=$local display=$pricePerMile id=${scoring.id}")
    }
}
