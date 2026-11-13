package com.driver.pro

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.driver.pro.network.User
import com.driver.pro.utils.ukOutwardCode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.roundToInt
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
    /** Operating cost £/mile from the Start screen (formula 0.5). Sent on score so the server does not keep a stale 0.50. */
    val cost_per_mile: Double? = null,
    val price_per_mile: Double? = null,
)

/** Incomplete OCR attempts stored for developers — never show these on customer History. */
fun isOcrDebugRide(ride: RideRequest): Boolean =
    ride.raw_text.startsWith("OCR debug", ignoreCase = true)

/**
 * Orders that could not be scored (Uber glitch, missing postcode) stay in History as a
 * plain card (price, rating, £/h £/mi) — client 9 Nov 2026. Drop only empty reads with no fare.
 */
fun isCustomerVisibleRide(ride: RideRequest): Boolean {
    if (!isOcrDebugRide(ride)) return true
    if (ride.price <= 0.0) return false
    // Fare is the rating misread (e.g. 4.83) — not a real price, so not a real order card.
    if (ride.raw_text.contains("price looks like passenger rating", ignoreCase = true)) return false
    return true
}

/**
 * Customer History list: hide junk reads, and hide an unscored card when the same offer
 * (same fare, within 5 min) was scored on a later frame — otherwise one job shows twice.
 */
fun customerHistoryRides(rides: List<RideRequest>): List<RideRequest> {
    val visible = rides.filter(::isCustomerVisibleRide)
    val scored = visible.filter { !isOcrDebugRide(it) }
    return visible.filter { ride ->
        if (!isOcrDebugRide(ride)) return@filter true
        val at = parseRideCreatedAtMs(ride.created_at) ?: return@filter true
        scored.none { s ->
            val sAt = parseRideCreatedAtMs(s.created_at) ?: return@none false
            kotlin.math.abs(s.price - ride.price) < 0.005 &&
                kotlin.math.abs(sAt - at) <= 5 * 60_000L
        }
    }
}

private const val MAX_STORED_RIDES = 120
private val rideListType = object : TypeToken<MutableList<RideRequest>>() {}.type

private const val KEY_OPERATING_COST_PER_MILE = "OPERATING_COST_PER_MILE"
const val DEFAULT_OPERATING_COST_PER_MILE = 0.50

fun getOperatingCostPerMile(context: Context?): Double {
    if (context == null) return DEFAULT_OPERATING_COST_PER_MILE
    val raw = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getString(KEY_OPERATING_COST_PER_MILE, null)
    val pounds = raw?.replace(',', '.')?.toDoubleOrNull()
    return pounds?.takeIf { it in 0.0..9.99 } ?: DEFAULT_OPERATING_COST_PER_MILE
}

/** Write the Start-screen cost so capture scoring matches the value on screen. */
fun persistSessionOperatingCost(context: Context?, apiPrice: Double?): Double {
    val display = com.driver.pro.utils.operatingCostForSession(
        local = getOperatingCostPerMile(context),
        apiPrice = apiPrice,
    )
    saveOperatingCostPerMile(context, display)
    return display
}

/** Settings-screen SCORE band, cached so the capture service decides from the local score. */
data class ScoreThresholds(val enabled: Boolean, val reject: Int, val accept: Int)

/** One Settings range slider: Reject < reject, Accept > accept. */
data class RangeFilterThresholds(val enabled: Boolean, val reject: Float, val accept: Float)

/**
 * Map-tab postcode filter: master switch + per-district status
 * (`accept` / `reject` / `decide`).
 */
data class PostcodeFilterState(
    val enabled: Boolean,
    val statuses: Map<String, String> = emptyMap(),
)

/**
 * All filters cached for the capture service.
 * Score / Price / Price-per-mile use Reject < X / Accept > Y.
 * Customer rating uses a single threshold (Reject < X, Accept > X).
 * Postcode uses Accept / Reject / Decide per district when the map switch is on.
 */
data class RideFilterThresholds(
    val score: RangeFilterThresholds? = null,
    val price: RangeFilterThresholds? = null,
    val rate: RangeFilterThresholds? = null,
    val pricePerMile: RangeFilterThresholds? = null,
    val postcode: PostcodeFilterState? = null,
)

private const val KEY_SCORE_ENABLED = "SCORE_FILTER_ENABLED"
private const val KEY_SCORE_REJECT = "SCORE_FILTER_REJECT"
private const val KEY_SCORE_ACCEPT = "SCORE_FILTER_ACCEPT"
private const val KEY_PRICE_ENABLED = "PRICE_FILTER_ENABLED"
private const val KEY_PRICE_REJECT = "PRICE_FILTER_REJECT"
private const val KEY_PRICE_ACCEPT = "PRICE_FILTER_ACCEPT"
private const val KEY_RATE_ENABLED = "RATE_FILTER_ENABLED"
private const val KEY_RATE_ACCEPT = "RATE_FILTER_ACCEPT"
private const val KEY_PPM_ENABLED = "PPM_FILTER_ENABLED"
private const val KEY_PPM_REJECT = "PPM_FILTER_REJECT"
private const val KEY_PPM_ACCEPT = "PPM_FILTER_ACCEPT"
private const val KEY_POSTCODE_FILTER_ENABLED = "POSTCODE_FILTER_ENABLED"
private const val KEY_POSTCODE_STATUSES_JSON = "POSTCODE_FILTER_STATUSES_JSON"

fun saveScoreThresholds(context: Context?, enabled: Boolean, reject: Int, accept: Int) {
    if (context == null) return
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit(commit = true) {
        putBoolean(KEY_SCORE_ENABLED, enabled)
        putInt(KEY_SCORE_REJECT, reject)
        putInt(KEY_SCORE_ACCEPT, accept)
    }
}

/** Persist every Settings filter so accept/reject can resolve conflicts on-device. */
fun saveRideFilterThresholds(context: Context?, thresholds: RideFilterThresholds) {
    if (context == null) return
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit(commit = true) {
        thresholds.score?.let {
            putBoolean(KEY_SCORE_ENABLED, it.enabled)
            putInt(KEY_SCORE_REJECT, it.reject.roundToInt())
            putInt(KEY_SCORE_ACCEPT, it.accept.roundToInt())
        }
        thresholds.price?.let {
            putBoolean(KEY_PRICE_ENABLED, it.enabled)
            putFloat(KEY_PRICE_REJECT, it.reject)
            putFloat(KEY_PRICE_ACCEPT, it.accept)
        }
        thresholds.rate?.let {
            putBoolean(KEY_RATE_ENABLED, it.enabled)
            putFloat(KEY_RATE_ACCEPT, it.accept)
        }
        thresholds.pricePerMile?.let {
            putBoolean(KEY_PPM_ENABLED, it.enabled)
            putFloat(KEY_PPM_REJECT, it.reject)
            putFloat(KEY_PPM_ACCEPT, it.accept)
        }
    }
}

/** Null until the Settings screen has loaded or saved the band at least once. */
fun getScoreThresholds(context: Context?): ScoreThresholds? {
    if (context == null) return null
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    if (!prefs.contains(KEY_SCORE_ACCEPT)) return null
    return ScoreThresholds(
        enabled = prefs.getBoolean(KEY_SCORE_ENABLED, true),
        reject = prefs.getInt(KEY_SCORE_REJECT, 0),
        accept = prefs.getInt(KEY_SCORE_ACCEPT, 0),
    )
}

/** Null until Settings has cached filters at least once (score-only cache still works). */
fun getRideFilterThresholds(context: Context?): RideFilterThresholds? {
    if (context == null) return null
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val score = if (prefs.contains(KEY_SCORE_ACCEPT)) {
        RangeFilterThresholds(
            enabled = prefs.getBoolean(KEY_SCORE_ENABLED, true),
            reject = prefs.getInt(KEY_SCORE_REJECT, 0).toFloat(),
            accept = prefs.getInt(KEY_SCORE_ACCEPT, 0).toFloat(),
        )
    } else {
        null
    }
    val price = if (prefs.contains(KEY_PRICE_ACCEPT)) {
        RangeFilterThresholds(
            enabled = prefs.getBoolean(KEY_PRICE_ENABLED, true),
            reject = prefs.getFloat(KEY_PRICE_REJECT, 0f),
            accept = prefs.getFloat(KEY_PRICE_ACCEPT, 0f),
        )
    } else {
        null
    }
    val rate = if (prefs.contains(KEY_RATE_ACCEPT)) {
        val accept = prefs.getFloat(KEY_RATE_ACCEPT, 3f)
        RangeFilterThresholds(
            enabled = prefs.getBoolean(KEY_RATE_ENABLED, true),
            reject = accept,
            accept = accept,
        )
    } else {
        null
    }
    val ppm = if (prefs.contains(KEY_PPM_ACCEPT)) {
        RangeFilterThresholds(
            enabled = prefs.getBoolean(KEY_PPM_ENABLED, true),
            reject = prefs.getFloat(KEY_PPM_REJECT, 0f),
            accept = prefs.getFloat(KEY_PPM_ACCEPT, 0f),
        )
    } else {
        null
    }
    val postcode = getPostcodeFilterState(context)
    if (score == null && price == null && rate == null && ppm == null && postcode == null) {
        return null
    }
    return RideFilterThresholds(
        score = score,
        price = price,
        rate = rate,
        pricePerMile = ppm,
        postcode = postcode,
    )
}

fun savePostcodeFilterEnabled(context: Context?, enabled: Boolean) {
    if (context == null) return
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit(commit = true) {
        putBoolean(KEY_POSTCODE_FILTER_ENABLED, enabled)
    }
}

fun getPostcodeFilterState(context: Context?): PostcodeFilterState? {
    if (context == null) return null
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    if (!prefs.contains(KEY_POSTCODE_FILTER_ENABLED) && !prefs.contains(KEY_POSTCODE_STATUSES_JSON)) {
        return null
    }
    return PostcodeFilterState(
        enabled = prefs.getBoolean(KEY_POSTCODE_FILTER_ENABLED, false),
        statuses = readPostcodeStatuses(prefs),
    )
}

/** Merge statuses from a map-area load or a single tap (keys are outward codes). */
fun mergePostcodeFilterStatuses(context: Context?, updates: Map<String, String>) {
    if (context == null || updates.isEmpty()) return
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val merged = readPostcodeStatuses(prefs).toMutableMap()
    for ((code, status) in updates) {
        val key = code.trim().uppercase()
        if (key.isEmpty()) continue
        val normalized = status.trim().lowercase()
        if (normalized == "decide") {
            merged.remove(key)
        } else {
            merged[key] = normalized
        }
    }
    prefs.edit(commit = true) {
        putString(KEY_POSTCODE_STATUSES_JSON, Gson().toJson(merged))
    }
}

fun savePostcodeFilterStatus(context: Context?, code: String, status: String) {
    mergePostcodeFilterStatuses(context, mapOf(code to status))
}

private fun readPostcodeStatuses(prefs: SharedPreferences): Map<String, String> {
    val raw = prefs.getString(KEY_POSTCODE_STATUSES_JSON, null) ?: return emptyMap()
    return runCatching {
        val type = object : TypeToken<Map<String, String>>() {}.type
        val parsed: Map<String, String> = Gson().fromJson(raw, type) ?: emptyMap()
        parsed.mapKeys { it.key.trim().uppercase() }
            .mapValues { it.value.trim().lowercase() }
            .filterValues { it == "accept" || it == "reject" }
    }.getOrDefault(emptyMap())
}

/** Reject < reject, Accept > accept; otherwise undecided. */
fun rangeFilterDecision(value: Double, reject: Float, accept: Float): Int = when {
    value > accept -> 1
    value < reject -> -1
    else -> 0
}

/**
 * One vote from the map postcode filter for this ride.
 * Pickup and drop each contribute Accept/Reject when set; Decide is ignored.
 * Null = filter off or no configured status for this trip.
 */
fun postcodeFilterDecision(
    pickupPostcode: String?,
    dropPostcode: String?,
    filter: PostcodeFilterState?,
): Int? {
    if (filter == null || !filter.enabled) return null
    if (filter.statuses.isEmpty()) return null
    fun voteFor(raw: String?): Int? {
        val outward = ukOutwardCode(raw)?.uppercase() ?: return null
        val status = filter.statuses[outward]
            ?: filter.statuses.entries.firstOrNull {
                it.key.equals(outward, ignoreCase = true)
            }?.value
            ?: return null
        return when (status.lowercase()) {
            "accept" -> 1
            "reject" -> -1
            else -> null
        }
    }
    val votes = listOfNotNull(voteFor(pickupPostcode), voteFor(dropPostcode))
    return combineFilterDecisions(votes)
}

/**
 * Merge per-filter votes. Accept + Reject from different filters → no action (0).
 * Empty list means no enabled local filter voted.
 */
fun combineFilterDecisions(decisions: List<Int>): Int? {
    if (decisions.isEmpty()) return null
    val hasAccept = decisions.any { it == 1 }
    val hasReject = decisions.any { it == -1 }
    if (hasAccept && hasReject) return 0
    if (hasAccept) return 1
    if (hasReject) return -1
    return 0
}

/**
 * Accept (1) / reject (-1) / undecided (0) using every enabled filter.
 *
 * Score, Price, Rating, Price-per-mile, and Postcode each vote. If one says Accept
 * and another says Reject, the app takes no action. A single enabled filter keeps
 * the old behaviour.
 */
fun decideRideFromFilters(
    localScore: Int?,
    price: Double?,
    rating: Double?,
    poundsPerMile: Double?,
    thresholds: RideFilterThresholds?,
    serverDecision: Int,
    pickupPostcode: String? = null,
    dropPostcode: String? = null,
): Int {
    if (localScore != null && localScore < 0) return 0 // banner only; avoid triple-fire dismiss taps
    val votes = mutableListOf<Int>()
    thresholds?.score?.takeIf { it.enabled }?.let { band ->
        if (localScore != null) {
            votes.add(rangeFilterDecision(localScore.toDouble(), band.reject, band.accept))
        }
    }
    thresholds?.price?.takeIf { it.enabled }?.let { band ->
        if (price != null && price >= 1.0) {
            votes.add(rangeFilterDecision(price, band.reject, band.accept))
        }
    }
    thresholds?.rate?.takeIf { it.enabled }?.let { band ->
        if (rating != null && rating > 0.0) {
            votes.add(rangeFilterDecision(rating, band.reject, band.accept))
        }
    }
    thresholds?.pricePerMile?.takeIf { it.enabled }?.let { band ->
        if (poundsPerMile != null && poundsPerMile > 0.0) {
            votes.add(rangeFilterDecision(poundsPerMile, band.reject, band.accept))
        }
    }
    postcodeFilterDecision(pickupPostcode, dropPostcode, thresholds?.postcode)?.let { votes.add(it) }
    combineFilterDecisions(votes)?.let { return it }
    // No local filter voted: never auto-accept a weak local score on the server's word alone.
    if (serverDecision == 1 && localScore != null && localScore < 70) return 0
    return serverDecision
}

/**
 * Accept (1) / reject (-1) / undecided (0) for a scored offer.
 *
 * The server stores scores with the hundreds digit dropped (118 → 18), so its decision
 * rejected good offers. When the SCORE filter is on, the local formula score decides,
 * matching the Settings labels "Reject < X" and "Accept > Y".
 */
fun decideRideFromScore(
    localScore: Int?,
    thresholds: ScoreThresholds?,
    serverDecision: Int,
): Int {
    val filters = thresholds?.let {
        RideFilterThresholds(
            score = RangeFilterThresholds(
                enabled = it.enabled,
                reject = it.reject.toFloat(),
                accept = it.accept.toFloat(),
            ),
        )
    }
    return decideRideFromFilters(
        localScore = localScore,
        price = null,
        rating = null,
        poundsPerMile = null,
        thresholds = filters,
        serverDecision = serverDecision,
    )
}

fun saveOperatingCostPerMile(context: Context?, pounds: Double) {
    if (context == null) return
    val clamped = pounds.coerceIn(0.0, 9.99)
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit(commit = true) {
        putString(
            KEY_OPERATING_COST_PER_MILE,
            java.lang.String.format(java.util.Locale.US, "%.2f", clamped),
        )
    }
}

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

private val localCreatedAtFormatter =
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun saveNewRequest(context: Context?, key: String, rideRequest: RideRequest): Boolean {
    if (context == null) return false
    val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val gson = Gson()
    // Always stamp device-local time. Scoring API returns "yyyy/MM/dd ..." which the Clear
    // History filter could not parse, so newly scored rides vanished from History.
    val toSave = rideRequest.copy(
        created_at = java.time.LocalDateTime.now().format(localCreatedAtFormatter),
    )
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
/** Keep 110 when the server later returns 10 (hundreds digit dropped). Prefer the on-device score. */
internal fun preferDisplayedScore(local: Int?, api: Int?): Int? {
    if (local == null) return api
    if (api == null) return local
    if (local >= 100 && api in 0..99 && local % 100 == api) return local
    if (api >= 100 && local in 0..99 && api % 100 == local) return api
    return local
}

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
                created_at = existing.created_at.ifBlank { r.created_at },
                final_score = preferDisplayedScore(existing.final_score, r.final_score),
                // On-device decision used the real score; the server's used the truncated one.
                acceptedOrRejected = existing.acceptedOrRejected,
                cost_per_mile = existing.cost_per_mile ?: r.cost_per_mile,
                price_per_mile = existing.price_per_mile ?: r.price_per_mile,
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
        "yyyy/MM/dd HH:mm:ss", // scoring API (POST /ride-request/) uses slashes
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy/MM/dd'T'HH:mm:ss",
    )
    for (p in patterns) {
        try {
            val local = java.time.LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ofPattern(p))
            return local.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
        }
    }
    // Truncate fractional seconds / timezone variants: "2026-08-11T17:30:50.123456+00:00"
    val normalized = raw.replace(' ', 'T').replace('/', '-')
    return try {
        java.time.OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.Instant.parse(normalized).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(
                    normalized.take(19),
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                ).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
