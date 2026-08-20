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
    // Stamp created_at when missing — Clear History hides rows with blank timestamps.
    val toSave = if (rideRequest.created_at.isBlank()) {
        rideRequest.copy(
            created_at = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        )
    } else {
        rideRequest
    }
    val saved = getRideRequestArray(context, key)?.toMutableList() ?: mutableListOf()
    saved.add(toSave)
    val trimmed = if (saved.size > MAX_STORED_RIDES) saved.takeLast(MAX_STORED_RIDES) else saved
    // commit() so debug rows survive if the app is killed right after an OCR failure.
    return sharedPref.edit()
        .putString(jsonKey(key), gson.toJson(trimmed))
        .commit()
}

/**
 * Merge on-device OCR saves with server history.
 * Local scored rides must stay visible even when the API returns older rows
 * (otherwise Clear History + non-empty API → empty History while scores still overlay).
 */
fun mergeRideHistory(local: List<RideRequest>, fromApi: List<RideRequest>): List<RideRequest> {
    fun key(r: RideRequest): String {
        if (r.id > 0) return "id:${r.id}"
        return "local:${r.price}:${r.pickup_address_postcode}:${r.dropoff_address_postcode}:" +
            "${r.final_score}:${r.created_at}:${r.raw_text.take(40)}"
    }
    val byKey = LinkedHashMap<String, RideRequest>()
    for (r in local) {
        byKey[key(r)] = r
    }
    for (r in fromApi) {
        val k = key(r)
        val existing = byKey[k]
        if (existing == null) {
            byKey[k] = r
        } else {
            byKey[k] = r.copy(
                raw_text = r.raw_text.ifBlank { existing.raw_text },
                ocr_image_uri = r.ocr_image_uri.ifBlank { existing.ocr_image_uri },
                created_at = r.created_at.ifBlank { existing.created_at },
            )
        }
    }
    return byKey.values.toList()
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

private const val HISTORY_CLEARED_AT_MS = "HISTORY_CLEARED_AT_MS"

/** Mark History as cleared on this device so synced server rows disappear from the list too. */
fun markHistoryCleared(context: Context?) {
    if (context == null) return
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
        putLong(HISTORY_CLEARED_AT_MS, System.currentTimeMillis())
    }
}

fun getHistoryClearedAtMs(context: Context?): Long {
    if (context == null) return 0L
    return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getLong(HISTORY_CLEARED_AT_MS, 0L)
}

/**
 * Keep only rides newer than the last Clear History tap.
 * Supports "yyyy-MM-dd HH:mm:ss", ISO-8601, and epoch millis strings.
 */
fun isRideAfterHistoryClear(ride: RideRequest, clearedAtMs: Long): Boolean {
    if (clearedAtMs <= 0L) return true
    val createdMs = parseRideCreatedAtMs(ride.created_at)
    // No timestamp → treat as old synced row and hide after clear.
    if (createdMs == null) return false
    return createdMs > clearedAtMs
}

fun parseRideCreatedAtMs(createdAt: String?): Long? {
    val raw = createdAt?.trim().orEmpty()
    if (raw.isEmpty()) return null
    raw.toLongOrNull()?.let { epoch ->
        return if (epoch < 100_000_000_000L) epoch * 1000L else epoch
    }
    val patterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
    )
    for (p in patterns) {
        try {
            val local = java.time.LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ofPattern(p))
            return local.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
        }
    }
    // Truncate fractional seconds / timezone variants: "2026-08-11T17:30:50.123456+00:00"
    val normalized = raw.replace(' ', 'T')
    return try {
        java.time.OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.Instant.parse(normalized).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
