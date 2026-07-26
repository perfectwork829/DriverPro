package com.driver.pro

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.driver.pro.network.User
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.Serializable


@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RideRequest(
    val id: Int,
    val price: Double,
    val rating: Double,
    val pickup_time_minutes: Int?,
    val pickup_distance_value: Double?,
    val pickup_address_postcode: String?,
    val trip_time_minutes: Int?,
    val trip_distance_value: Double?,
    val dropoff_address_postcode: String?,
    var start_time_window: String?,
    var end_time_window: String?,
    val acceptedOrRejected: Int,
    /** Server may return null when a ride was logged before scoring completed. */
    val final_score: Int? = null,
    var raw_text: String = "",
    /** Local OCR screenshot URI for debugging; server may ignore this field. */
    var ocr_image_uri: String = "",
    var created_at: String = "",
    var type: String = "confirm",
    /** OCR confidence; older API rows may omit this — default keeps history JSON parsing working. */
    var accuracy: Int = 100,
)

private const val MAX_STORED_RIDES = 120
private val rideListType = object : TypeToken<MutableList<RideRequest>>() {}.type

fun getToken(context: Context, key: String): String? {
    val sharedPref: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return sharedPref.getString(key, null)
}

fun saveToken(context: Context, token: String, refreshToken: String, user: User) {
    val sharedPref: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    sharedPref.edit {
        putString("JWT_TOKEN", token)
        putString("REFRESH_TOKEN", refreshToken)
        putInt("USER_ID", user.id)
        putString("USER_NAME", user.username)
        putString("USER_EMAIL", user.email)
        putBoolean("USER_SUBSCRIBED", user.subscribed)
    }
}

/** JSON array storage — Android StringSet is unreliable for frequent append (debug saves). */
private fun jsonKey(legacyKey: String): String = "${legacyKey}_JSON"

private fun migrateLegacyStringSet(context: Context, legacyKey: String, gson: Gson): MutableList<RideRequest> {
    val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val legacy = sharedPref.getStringSet(legacyKey, null) ?: return mutableListOf()
    val migrated = legacy.mapNotNull { runCatching { gson.fromJson(it, RideRequest::class.java) }.getOrNull() }
        .toMutableList()
    if (migrated.isNotEmpty()) {
        sharedPref.edit {
            putString(jsonKey(legacyKey), gson.toJson(migrated))
            remove(legacyKey)
        }
    }
    return migrated
}

fun getRideRequestArray(context: Context?, key: String): Array<RideRequest>? {
    if (context == null) return emptyArray()
    val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val gson = Gson()
    val json = sharedPref.getString(jsonKey(key), null)
    val list: MutableList<RideRequest> = when {
        json != null -> gson.fromJson(json, rideListType) ?: mutableListOf()
        else -> migrateLegacyStringSet(context, key, gson)
    }
    return list.toTypedArray()
}

fun saveNewRequest(context: Context?, key: String, rideRequest: RideRequest): Boolean {
    if (context == null) return false
    val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val gson = Gson()
    val saved = getRideRequestArray(context, key)?.toMutableList() ?: mutableListOf()
    saved.add(rideRequest)
    val trimmed = if (saved.size > MAX_STORED_RIDES) saved.takeLast(MAX_STORED_RIDES) else saved
    // commit() so debug rows survive if the app is killed right after an OCR failure.
    return sharedPref.edit()
        .putString(jsonKey(key), gson.toJson(trimmed))
        .commit()
}

fun checkIfNewRequest(context: Context?, key: String, rideRequest: RideRequest): Boolean {
    if (context == null) return false
    val saved = getRideRequestArray(context, key)?.toMutableList() ?: mutableListOf()
    val isDuplicate = saved.any {
        it.price == rideRequest.price && it.rating == rideRequest.rating &&
            it.pickup_distance_value == rideRequest.pickup_distance_value &&
            it.trip_distance_value == rideRequest.trip_distance_value
    }
    return !isDuplicate
}

fun clearAllRequests(context: Context?, key: String) {
    if (context == null) return
    val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    sharedPreferences.edit {
        remove(key)
        remove(jsonKey(key))
    }
}
