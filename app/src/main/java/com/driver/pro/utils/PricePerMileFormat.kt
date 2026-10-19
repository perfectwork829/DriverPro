package com.driver.pro.utils

import com.driver.pro.DEFAULT_OPERATING_COST_PER_MILE
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Start-screen price field uses 3 digit boxes for pounds.pence (e.g. "050" → £0.50).
 * The API stores pounds as a decimal (e.g. 0.50).
 */
fun formatApiPriceToDigitInput(apiValue: String?): String {
    if (apiValue.isNullOrBlank()) return ""
    val pounds = apiValue.trim().replace(',', '.').toDoubleOrNull() ?: return ""
    val cents = (pounds * 100.0).roundToInt().coerceIn(0, 999)
    return cents.toString().padStart(3, '0')
}

fun digitInputToApiPrice(digits: String): Float {
    val cents = digits.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0
    return cents / 100f
}

/** Always a dot decimal so a Polish/German phone locale cannot POST "0,55". */
fun digitInputToApiPounds(digits: String): Double {
    val cents = digits.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0
    return cents / 100.0
}

fun formatApiPriceForRequest(pounds: Double): String =
    String.format(Locale.US, "%.2f", pounds)

/**
 * Cost used for live scoring. The Start screen can show £0.10 from the API
 * while local prefs are still the default £0.50 until the driver edits a digit.
 * Persist whatever this returns as soon as the Start screen loads.
 */
fun operatingCostForSession(
    local: Double,
    apiPrice: Double?,
    defaultCost: Double = DEFAULT_OPERATING_COST_PER_MILE,
): Double {
    if (apiPrice == null) return local
    val localIsCustom = abs(local - defaultCost) > 0.009
    val apiIsDefault = abs(apiPrice - defaultCost) < 0.009
    if (localIsCustom && apiIsDefault && abs(local - apiPrice) > 0.009) return local
    return apiPrice
}
