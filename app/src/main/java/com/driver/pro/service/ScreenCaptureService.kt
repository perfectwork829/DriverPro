package com.driver.pro.service

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import android.util.Log
import androidx.annotation.RequiresApi
import com.driver.pro.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.driver.pro.RideRequest
import com.driver.pro.getToken
import com.driver.pro.network.calculateRideRequest
import com.driver.pro.network.logRideRequestPayload
import com.driver.pro.network.rideRequestHttpBody
import com.driver.pro.network.validateRideBeforeScoring
import com.driver.pro.saveNewRequest
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.*
import java.time.LocalTime
import kotlin.text.toDoubleOrNull
import kotlin.text.toInt
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.math.RoundingMode
import kotlin.math.max
import kotlin.sequences.mapNotNull

fun fixWrongLetterToNumber(text: String): String {
    var result = text
    // Whole-token only — raw "CRO"→"CR0" turned "CROSS"/"across" into "CR0SS"/"ACR0SS" → fake CR0S.
    result = result.replace(Regex("""\bCRO\b""", RegexOption.IGNORE_CASE), "CR0")
    result = result.replace(Regex("""\bBRO\b""", RegexOption.IGNORE_CASE), "BR0")
    result = result.replace(",", ".")
    result = result.replace("..", ".")
    result = result.replace(".,", ".")
    result = result.replace(",.", ".")

    result = result.replace(Regex("m[l1]n", RegexOption.IGNORE_CASE), "min")
    result = result.replace(Regex("m[l1]\\)", RegexOption.IGNORE_CASE), "mi)")

    return result
}

/**
 * Uber often wraps the inward on the next line: "London, NW4" + "4XW", or "London, SW1E" + "6LB".
 * Also recovers "NW1" + "1 8AT" → "NW11 8AT" when the district tens digit wrapped.
 * OCR sometimes puts the inward *above* the address: "7EX" then "… London. El".
 */
internal fun joinSplitPostcodeLines(text: String): String {
    val lines = text.lineSequence().toList()
    if (lines.size < 2) return text
    val out = mutableListOf<String>()
    var i = 0
    val inwardOnly = Regex("""^\s*([0-9oO][A-Za-z]{2})\s*$""", RegexOption.IGNORE_CASE)
    // "1 8AT" / "18AT" after a truncated "NW1" that should be NW11
    val wrappedDistrictInward = Regex(
        """^\s*([0-9])\s*([0-9oO][A-Za-z]{2})\s*$""",
        RegexOption.IGNORE_CASE,
    )
    val trailingOutward = Regex(
        """\b([A-Za-z]{1,2})([0-9iIlLoOzZ]{1,2})([A-Za-z]?)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    while (i < lines.size) {
        val line = lines[i]
        val next = lines.getOrNull(i + 1)?.trim().orEmpty()
        val inwardMatch = inwardOnly.find(next)
        val wrapMatch = wrappedDistrictInward.find(next)
        val trail = trailingOutward.find(line.trim())
        // Inward-only line *before* the address: "7EX" / "Travelodge … El"
        val prevInward = if (out.isNotEmpty()) inwardOnly.find(out.last().trim()) else null
        val trailOnCurrent = trailingOutward.find(line.trim())
        when {
            prevInward != null && trailOnCurrent != null &&
                !Regex("""\s+[0-9oO][A-Za-z]{2}\s*$""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(line.trim()) -> {
                val inward = prevInward.groupValues[1]
                out[out.lastIndex] = line.trimEnd() + " " + inward
                i += 1
            }
            inwardMatch != null && trail != null -> {
                val joined = line.trimEnd() + " " + inwardMatch.groupValues[1]
                out.add(joined)
                i += 2
            }
            wrapMatch != null && trail != null && trail.groupValues[3].isEmpty() -> {
                // "London, NW1" + "1 8AT" → "London, NW11 8AT"
                val prefix = trail.groupValues[1]
                val district = fixOcrNumberPart(trail.groupValues[2])
                val extra = wrapMatch.groupValues[1]
                val inward = wrapMatch.groupValues[2]
                if (district.length == 1 && extra.isNotEmpty()) {
                    val before = line.trimEnd().replace(trail.value, "").trimEnd()
                    val merged = listOf(before, "$prefix$district$extra $inward")
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    out.add(merged)
                    i += 2
                } else {
                    out.add(line)
                    i += 1
                }
            }
            else -> {
                out.add(line)
                i += 1
            }
        }
    }
    return out.joinToString("\n")
}

fun getTimeWindow(currentTime: LocalTime): String {
    val hours = currentTime.hour
    return when {
        hours in 0..5 -> "00-06"
        hours in 6..9 -> "06-10"
        hours in 10..15 -> "10-16"
        hours in 16..19 -> "16-20"
        else -> "20-24"
    }
}

fun extractRatings(text: String): List<String> {
    val found = mutableListOf<String>()

    text.lineSequence().forEach { raw ->
        parseRatingFromLine(raw.trim())?.let { found.add(String.format("%.2f", it)) }
    }

    if (found.isEmpty()) {
        Regex("""(?:★|\*)\s*([1-5]\.\d{1,2})""").findAll(text).forEach { m ->
            found.add(m.groupValues[1])
        }
        Regex("""\b([1-5]\.\d{1,2})\s*(?:★|\*)""").findAll(text).forEach { m ->
            found.add(m.groupValues[1])
        }
    }

    return found.distinct()
}


/** Ordered pickup/trip durations in minutes (Driver app: "2 min (...)" then "26 mins (...)"). */
fun extractTime(text: String): List<String> {
    data class TimeHit(val index: Int, val totalMinutes: Int)
    val hits = mutableListOf<TimeHit>()

    val hourThenMin = Regex(
        """([0-9ilILoO]{1,2})\s*h(?:ou)?r?s?\s*([0-9ilILoO]{1,3})\s*mins?""",
        RegexOption.IGNORE_CASE,
    )
    hourThenMin.findAll(text).forEach { match ->
        val hours = match.groupValues[1].fixOcrDigits().toIntOrNull() ?: return@forEach
        val mins = match.groupValues[2].fixOcrDigits().toIntOrNull() ?: 0
        val total = hours * 60 + mins
        if (total in 1..600) hits.add(TimeHit(match.range.first, total))
    }

    // Prefer "N min (" — same pattern as on-screen; avoids rating/distances.
    // "[nr]?" tolerates "50 nmins (" OCR garble.
    val minWithMiles = Regex(
        """([0-9ilILoO]{1,3})\s*[nr]?\s?mins?\s*\(""",
        RegexOption.IGNORE_CASE,
    )
    minWithMiles.findAll(text).forEach { match ->
        val idx = match.range.first
        val lookBack = text.substring((idx - 28).coerceAtLeast(0), idx)
        if (Regex("""\d+\s*h(?:ou)?r?s?\s*$""", RegexOption.IGNORE_CASE).containsMatchIn(lookBack)) {
            return@forEach
        }
        val total = match.groupValues[1].fixOcrDigits().toIntOrNull() ?: return@forEach
        if (total in 1..180) hits.add(TimeHit(idx, total))
    }

    val ordered = hits
        .sortedBy { it.index }
        .distinctBy { it.totalMinutes }
        .map { it.totalMinutes.toString() }

    if (ordered.size >= 2) return ordered.take(2)

    if (ordered.isEmpty()) {
        val relaxed = Regex(
            """(?:([0-9ilILoO]{1,2})\s*h(?:ou)?r?s?\s*([0-9ilILoO]{1,3})|([0-9ilILoO]{1,3}))\s*mins?\b""",
            RegexOption.IGNORE_CASE,
        )
        relaxed.findAll(text).forEach { match ->
            val hrStr = match.groupValues[1]
            val minAfterHr = match.groupValues[2]
            val minOnlyVal = match.groupValues[3]
            val total = when {
                hrStr.isNotEmpty() -> {
                    val h = hrStr.fixOcrDigits().toIntOrNull() ?: return@forEach
                    val m = minAfterHr.fixOcrDigits().toIntOrNull() ?: 0
                    h * 60 + m
                }
                minOnlyVal.isNotEmpty() -> minOnlyVal.fixOcrDigits().toIntOrNull() ?: return@forEach
                else -> return@forEach
            }
            if (total in 1..600) hits.add(TimeHit(match.range.first, total))
        }
        return hits.sortedBy { it.index }.map { it.totalMinutes.toString() }.take(2)
    }

    return ordered
}

fun extractPrice(text: String): List<Double> {
    val priceRegex = Regex("""£\s*($OCR_DIGIT{1,3})(?:\.($OCR_DIGIT{1,2}))?""", RegexOption.IGNORE_CASE)
    return priceRegex.findAll(text).mapNotNull { match ->
        val frac = match.groupValues[2]
        if (frac.isNotEmpty()) {
            "${match.groupValues[1].fixOcrDigits()}.${frac.fixOcrDigits()}".toDoubleOrNull()
        } else {
            val raw = match.groupValues[1].fixOcrDigits().toIntOrNull()?.toDouble() ?: return@mapNotNull null
            normalizeFareWithoutDecimal(raw)
        }
    }.filterNotNull()
        .filter { it >= 1.0 }
        .toList()
}

/** Best-effort fare from OCR — prefers main £ amount, then large standalone decimals (e.g. 42.47). */
fun extractBestPrice(text: String): Double {
    val poundDecimalAmounts = mutableListOf<Double>()
    val poundWholeAmounts = mutableListOf<Double>()
    val bareAmounts = mutableListOf<Double>()
    for (line in text.lineSequence()) {
        if (isAddonFareLine(line)) continue
        val normalized = normalizeOcrCurrencyLine(line)
        if (lineHasCurrencySymbol(normalized)) {
            parseFareFromLine(normalized)?.let { fare ->
                if (Regex("""[£$]\s*\d+[.,]\d{2}""").containsMatchIn(normalized)) {
                    poundDecimalAmounts.add(fare)
                } else {
                    poundWholeAmounts.add(fare)
                }
            }
            // Collect every £X.YZ on the line (parseFareFromLine returns max only).
            Regex("""[£$]\s*($OCR_DIGIT{1,3})[.,]($OCR_DIGIT{2})\b""").findAll(normalized).forEach { m ->
                val v = "${m.groupValues[1].fixOcrDigits()}.${m.groupValues[2].fixOcrDigits()}".toDoubleOrNull()
                if (v != null && v in 3.0..500.0) poundDecimalAmounts.add(v)
            }
        }
    }

    collectBareTripFares(text).forEach { fare ->
        if (fare !in bareAmounts) bareAmounts.add(fare)
    }

    for (line in text.lineSequence()) {
        if (isAddonFareLine(line)) continue
        val normalized = normalizeOcrCurrencyLine(line)
        Regex("""[£$]\s*($OCR_DIGIT{2,3})(?!\d|[.,])""").findAll(normalized).forEach { m ->
            val raw = m.groupValues[1].fixOcrDigits().toIntOrNull() ?: return@forEach
            normalizeFareWithoutDecimal(raw.toDouble())?.let { poundWholeAmounts.add(it) }
        }
    }

    Regex("""(?m)(?<![\d.])(\d{1,2}\.\d{2})(?![\d.])""")
        .findAll(text)
        .mapNotNull { match ->
            val lineStart = text.lastIndexOf('\n', match.range.first).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', match.range.first).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            if (isAddonFareLine(line)) return@mapNotNull null
            if (lineHasCurrencySymbol(normalizeOcrCurrencyLine(line))) return@mapNotNull null
            // "★ 4.48" is the passenger rating, never a bare fare.
            if (line.contains('★') || line.contains('*') ||
                line.contains("Verified", ignoreCase = true)
            ) {
                return@mapNotNull null
            }
            if (line.contains("est", ignoreCase = true) && line.contains("holiday", ignoreCase = true)) {
                null
            } else {
                val v = match.groupValues[1].toDoubleOrNull()
                if (v != null && v in 4.5..5.05) null else v
            }
        }
        .filter { it != null && it in 3.0..500.0 }
        .forEach { bareAmounts.add(it!!) }

    fun pick(amounts: List<Double>): Double {
        if (amounts.isEmpty()) return 0.0
        val viable = amounts.filter { it >= 3.0 }
        if (viable.isEmpty()) return amounts.maxOrNull() ?: 0.0
        val tripFares = viable.filter { it >= 5.0 }
        if (tripFares.isNotEmpty()) return tripFares.maxOrNull()!!
        val fares = viable.filter { it !in 4.0..5.05 }
        return if (fares.isNotEmpty()) fares.maxOrNull()!! else viable.maxOrNull()!!
    }

    // Prefer £X.YZ decimals so bare "30" / whole £30 map noise cannot beat £9.94.
    val decimalBest = pick(poundDecimalAmounts)
    if (decimalBest >= 5.0) return decimalBest

    // £11.64 OCR'd as £1.16: if we only have a sub-£5 £ fare, prefer a nearby XX.YZ trip fare.
    val lowPound = pick(poundDecimalAmounts + poundWholeAmounts)
    if (lowPound in 1.0..4.99) {
        val recovered = bareAmounts.filter { it in 8.0..80.0 && it >= lowPound * 5 }
            .minByOrNull { kotlin.math.abs(it - lowPound * 10) }
            ?: bareAmounts.filter { it in 8.0..80.0 }.maxOrNull()
        if (recovered != null) return recovered
        // Digits on next line: "£1.16" / "11.64"
        Regex("""(?m)(?<![\d.])(1[0-9])[.,](\d{2})(?![\d.])""").find(text)?.let { m ->
            val v = "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()
            if (v != null && v in 8.0..80.0) return v
        }
        // £37.41 OCR'd as £3.74 on long XL trips — tens digit dropped.
        val longTrip = Regex(
            """\b(?:1\s*hr|60\+\s*min|Long trip)\b""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(text) ||
            Regex("""\b([6-9]\d|[1-9]\d{2})\s*mins?\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)
        if (longTrip && lowPound in 3.0..4.99) {
            val timesTen = lowPound * 10.0
            if (timesTen in 20.0..80.0) return timesTen
            Regex("""(?m)(?<![\d.])([2-4][0-9])[.,](\d{2})(?![\d.])""").find(text)?.let { m ->
                val v = "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()
                if (v != null && v in 20.0..80.0 &&
                    kotlin.math.abs(v - timesTen) < 1.0
                ) {
                    return v
                }
            }
        }
    }

    if (decimalBest >= 3.0) return decimalBest
    val wholeBest = pick(poundWholeAmounts)
    // Whole £ amounts only if no better decimal; still prefer over bare map noise when ≥5.
    val bareBest = pick(bareAmounts)
    // £4.67 on card must not lose to bare map "20" from road labels.
    if (bareBest >= 5.0 && poundDecimalAmounts.isEmpty() && poundWholeAmounts.isEmpty()) {
        // Prefer bare decimals (9.94) over bare integers (30 from "30 mins"/map noise).
        val bareDecimals = bareAmounts.filter { it >= 5.0 && kotlin.math.abs(it - it.toInt().toDouble()) > 0.001 }
        if (bareDecimals.isNotEmpty()) {
            val dec = bareDecimals.maxOrNull()!!
            // Integer only wins if much larger and no plausible decimal trip fare.
            if (bareBest >= dec + 10.0 && bareDecimals.none { it in 5.0..25.0 }) {
                return bareBest
            }
            return dec
        }
        return bareBest
    }
    if (wholeBest >= 3.0 && wholeBest !in 4.0..5.05) return wholeBest
    if (bareBest >= 5.0) {
        val bareDecimals = bareAmounts.filter { it >= 5.0 && kotlin.math.abs(it - it.toInt().toDouble()) > 0.001 }
        if (bareDecimals.isNotEmpty()) {
            val dec = bareDecimals.maxOrNull()!!
            if (bareBest >= dec + 10.0 && bareDecimals.none { it in 5.0..25.0 }) {
                return bareBest
            }
            return dec
        }
        return bareBest
    }
    if (bareBest >= 1.0) return bareBest
    return maxOf(decimalBest, wholeBest)
}

fun extractDistance(text: String): List<String> {

    // Driver app often shows "(4.0 miles)" — older regex only matched "mi)" / "mi " and missed "miles".
    // Also tolerate OCR "nmi" / "mni".
    val distanceRegex =
        "([0-9ilILoO]+(?:\\.[0-9ilILoO])?)\\s*(?:miles?\\b|n?mi\\s*\\)|n?mi\\)|mni\\b)".toRegex(RegexOption.IGNORE_CASE)

    return distanceRegex.findAll(text)
        .map { it.groupValues[1].replace('i', '1').replace('l', '1')
        .replace('I', '1').replace('L', '1').replace('o', '0')
        .replace('O', '0')}
        .toList()
}

/**
 * Second pass when [parseRideInfo] leaves null times/distances (slideshows, photos of screens, weak OCR).
 * Uses simpler patterns on the full OCR string so scoring/API still runs for local testing.
 */
fun fillMissingTripMetrics(ocr: String, ride: RideRequest): RideRequest {
    var reconciled = reconcileDuplicatedPickupDrop(ride, ocr)

    val lower = ocr.lowercase()
    val timeValues = extractTime(ocr).mapNotNull { it.toIntOrNull() }
    val parenMiValues = Regex("\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*n?mi")
        .findAll(lower)
        .mapNotNull { m -> m.groupValues[1].toDoubleOrNull() }
        .toList()

    var pTime = reconciled.pickup_time_minutes
    var tTime = reconciled.trip_time_minutes
    var pDist = reconciled.pickup_distance_value
    var tDist = reconciled.trip_distance_value
    var price = reconciled.price

    if (pTime == null && timeValues.isNotEmpty()) pTime = timeValues[0]
    if (tTime == null && timeValues.size >= 2) tTime = timeValues[1]
    if (pDist == null && parenMiValues.isNotEmpty()) {
        pDist = parseOcrMiles(parenMiValues[0].toString(), pTime)
            ?: parenMiValues[0]
    }
    if (tDist == null && parenMiValues.size >= 2) {
        tDist = parseOcrMiles(parenMiValues[1].toString(), tTime)
            ?: parenMiValues[1]
    }
    // Pickup leg miles garbled (only trip "3.6 mi" readable) — use the sole distance as trip.
    if (tDist == null && pDist != null && parenMiValues.size == 1 && tTime != null && pTime != null &&
        tTime > pTime
    ) {
        tDist = pDist
        pDist = null
        // Try to recover pickup miles from garbled patterns still in OCR.
        Regex(
            """(\d+)\s*mins?\s*\(\s*([0-9ilILoO]+(?:[.,][0-9ilILoO]+)?)\s*mi""",
            RegexOption.IGNORE_CASE,
        ).find(ocr)?.let { m ->
            val mins = m.groupValues[1].fixOcrDigits().toIntOrNull()
            if (mins == pTime) {
                parseOcrMiles(m.groupValues[2].replace(',', '.'), pTime)?.let { pDist = it }
            }
        }
    }
    // "1.2 mi" OCR'd as "2.0" on Exclusive cards — prefer explicit 1.2/1,2 in OCR.
    if (pDist != null && pDist in 1.85..2.15 && pTime != null && pTime in 3..12) {
        if (Regex("""\b1[.,]2\s*mi""", RegexOption.IGNORE_CASE).containsMatchIn(ocr) ||
            Regex("""\b${pTime}\s*mins?\s*\(\s*1[.,]2\s*mi""", RegexOption.IGNORE_CASE)
                .containsMatchIn(ocr)
        ) {
            pDist = 1.2
        }
    }
    if (price < 1.0) {
        val recovered = extractBestPrice(ocr)
        if (recovered >= 1.0) price = recovered
    }

    val postcodes = extractOuterLondonPostcodes(ocr)
    var pickupPc = reconciled.pickup_address_postcode.orEmpty()
    var dropPc = reconciled.dropoff_address_postcode.orEmpty()
    if (pickupPc.isBlank() && postcodes.size >= 2) pickupPc = postcodes[0]
    if (dropPc.isBlank() && postcodes.size >= 2) dropPc = postcodes[1]
    val zonePostcodes = resolvePostcodesFromLegZones(ocr)
    if (pickupPc.isBlank() && zonePostcodes.first.isNotBlank()) pickupPc = zonePostcodes.first
    if (dropPc.isBlank() && zonePostcodes.second.isNotBlank()) dropPc = zonePostcodes.second

    return reconciled.copy(
        price = price,
        pickup_time_minutes = pTime,
        trip_time_minutes = tTime,
        pickup_distance_value = pDist,
        trip_distance_value = tDist,
        pickup_address_postcode = pickupPc,
        dropoff_address_postcode = dropPc,
    )
}

fun fixOcrNumberPart(part: String): String {
    return part
        .replace("[iIlL]".toRegex(), "1")
        .replace("[oO]".toRegex(), "0")
        // CRZ → CR7 (7 misread as Z on Croydon cards)
        .replace("[zZ]".toRegex(), "7")
}

fun fixOcrPostcodeDistrict(part: String): String = fixOcrNumberPart(part)

fun extractOuterLondonPostcodes(text: String): List<String> {
    // Join Uber-wrapped postcodes: "London, NW4" / "4XW" and "London, SW1E" / "6LB"
    val text = joinSplitPostcodeLines(text)

    // London + common M25 / home-counties areas (Guildford GU, Redhill RH, etc.)
    val londonPrefixes = listOf(
        // Inner London
        "E", "EC", "N", "NW", "SE", "SW", "W", "WC",
        // Outer London
        "BR", "CM", "CR", "DA", "EN", "HA", "IG", "SL",
        "TN", "KT", "RM", "SM", "TW", "UB", "WD",
        // Around the M25 (previously missing → empty Pickup/Drop codes)
        "GU", "RH", "HP", "SG", "AL", "CO", "SS", "ME", "MK", "OX",
        "BN", "PO", "RG", "SO", "CB", "LU",
    )

    data class PostcodeDistrict(
        val prefix: String,
        val maxGeographicNumber: Int,
        val description: String,
        val isInnerLondon: Boolean
    )
    val londonPostcodes: Map<String, PostcodeDistrict> = listOf(
        // Inner London
        PostcodeDistrict("E", 20, "East (Olympic Park/Stratford)", true),
        PostcodeDistrict("EC", 4, "East Central (City of London)", true),
        PostcodeDistrict("N", 22, "North (Wood Green)", true),
        PostcodeDistrict("NW", 11, "North West (Golders Green)", true),
        PostcodeDistrict("SE", 28, "South East (Thamesmead)", true),
        PostcodeDistrict("SW", 20, "South West (West Wimbledon)", true),
        PostcodeDistrict("W", 14, "West (West Kensington)", true),
        PostcodeDistrict("WC", 2, "West Central (Holborn/Strand)", true),

        // Outer London
        PostcodeDistrict("BR", 8, "Bromley", false),
        PostcodeDistrict("CR", 9, "Croydon", false),
        PostcodeDistrict("DA", 18, "Dartford/Bexley", false),
        PostcodeDistrict("EN", 11, "Enfield", false),
        PostcodeDistrict("HA", 9, "Harrow/Wembley", false),
        PostcodeDistrict("IG", 11, "Ilford", false),
        PostcodeDistrict("KT", 24, "Kingston", false),
        PostcodeDistrict("RM", 20, "Romford/Dagenham", false),
        PostcodeDistrict("SM", 7, "Sutton", false),
        PostcodeDistrict("TW", 20, "Twickenham/Hounslow", false),
        PostcodeDistrict("UB", 11, "Uxbridge", false),
        PostcodeDistrict("WD", 25, "Watford", false),
        PostcodeDistrict("SL", 9, "Slough (Marginal)", false),
        PostcodeDistrict("GU", 52, "Guildford / Surrey", false),
        PostcodeDistrict("RH", 20, "Redhill / Reigate", false),
        PostcodeDistrict("HP", 27, "Hemel Hempstead", false),
        PostcodeDistrict("SG", 19, "Stevenage", false),
        PostcodeDistrict("AL", 10, "St Albans", false),
        PostcodeDistrict("CO", 16, "Colchester", false),
        PostcodeDistrict("SS", 17, "Southend", false),
        PostcodeDistrict("ME", 20, "Medway", false),
        PostcodeDistrict("MK", 19, "Milton Keynes", false),
        PostcodeDistrict("OX", 49, "Oxford", false),
        PostcodeDistrict("BN", 45, "Brighton", false),
        PostcodeDistrict("PO", 22, "Portsmouth", false),
        PostcodeDistrict("RG", 31, "Reading", false),
        PostcodeDistrict("SO", 53, "Southampton", false),
        PostcodeDistrict("CB", 25, "Cambridge", false),
        PostcodeDistrict("LU", 7, "Luton", false),
    ).associateBy { it.prefix } // This creates the Map using prefix as the key

    val wrongCode = listOf(
        "E30", "CROS"
    )

    /** Full postcodes in OCR (e.g. GU1 4PH) — accept any valid UK outward, not only London whitelist. */
    fun outwardFromFullPostcode(prefix: String, numberPart: Int, letter: String): String? {
        val p = prefix.uppercase()
        if (!Regex("^[A-Z]{1,2}$").matches(p)) return null
        if (p.length == 1 && p !in londonPostcodes) return null
        // "Hill" → HI11 and other map-label fakes: area must be a real UK postcode area.
        if (p !in UK_POSTCODE_AREAS) return null
        if (numberPart !in 0..99) return null
        // District 0 only exists in Croydon (CR0) — "NOR" must not become N0R.
        if (numberPart == 0 && p != "CR") return null
        // Only central London sectors use a trailing letter (W1A, SW1A, EC1A, …).
        // Do not coerce CR0S → CR0 (that invented Croydon codes from OCR garbage).
        val letterDistrictPrefixes = setOf("E", "EC", "N", "NW", "SE", "SW", "W", "WC")
        if (letter.isNotEmpty() && p !in letterDistrictPrefixes) {
            return null
        }
        val postCode = p + numberPart.toString() + letter.uppercase()
        return when {
            postCode in wrongCode -> null
            postCode == "SL11" -> "SL1"
            p in londonPostcodes && numberPart > londonPostcodes[p]!!.maxGeographicNumber -> null
            else -> postCode
        }
    }

    fun outwardFromDistrictParts(prefix: String, districtRaw: String, sourceLine: String = ""): String? {
        var normalizedPrefix = prefix.uppercase()
        var normalizedDistrictRaw = districtRaw
        val lineUpper = sourceLine.uppercase()

        // OCR sometimes reads E16 as EL6 / EI6 (or W12 as WL2, etc.).
        // Do NOT rewrite real UK area codes that end in L/I (notably Slough "SL").
        val realTwoLetterAreas = setOf("SL", "AL", "BL", "DL", "GL", "ML", "PL", "OL", "LL")
        if (
            normalizedPrefix.length == 2 &&
            normalizedPrefix !in realTwoLetterAreas &&
            normalizedPrefix[0] in setOf('E', 'N', 'S', 'W') &&
            normalizedPrefix[1] in setOf('I', 'L')
        ) {
            normalizedDistrictRaw = "1$normalizedDistrictRaw"
            normalizedPrefix = normalizedPrefix.substring(0, 1)
        }

        val digitPart = normalizedDistrictRaw.takeWhile { it.isDigit() || it in "iIlLoOzZ" }
        val letterPart = normalizedDistrictRaw.drop(digitPart.length)

        fun londonAddressLine(): Boolean =
            lineUpper.contains("LONDON") || lineUpper.contains("STREET") ||
                lineUpper.contains("ROAD") || lineUpper.contains("GARDENS") ||
                lineUpper.contains("HOTEL") || lineUpper.contains("BRASSERIE") ||
                lineUpper.contains("WELLINGTON") || lineUpper.contains("ARCHER") ||
                lineUpper.contains("COURT") || lineUpper.contains("LANGHAM")

        // W + single letter (digit 1 dropped): WD 7AP → W1D, WF → W1F.
        if (normalizedPrefix == "W" && digitPart.isEmpty() && letterPart.length == 1 && londonAddressLine()) {
            outwardFromFullPostcode("W", 1, letterPart)?.let { return it }
        }

        if (normalizedPrefix == "WI" && digitPart.isEmpty() && letterPart.length == 1 && londonAddressLine()) {
            outwardFromFullPostcode("W", 1, letterPart)?.let { return it }
        }

        // N17 OCR'd as N1R (7 misread as R) — only this district, not valid codes like EC3R.
        if (normalizedPrefix == "N" && normalizedDistrictRaw.uppercase() == "1R") {
            outwardFromFullPostcode("N", 17, "")?.let { return it }
        }

        val districtNum = fixOcrPostcodeDistrict(digitPart).toIntOrNull() ?: return null

        fun enfieldLine(): Boolean =
            lineUpper.contains("ENFIELD") || lineUpper.contains("WALTHAM CROSS") ||
                lineUpper.contains("PONDERS END") || lineUpper.contains("BROXBOURNE") ||
                lineUpper.contains("ENFIELD TOWN")

        // S14 / N0 / NO → SL4 / N15 / EN* (letter–digit OCR confusion in outward district).
        if (normalizedPrefix == "N" && districtNum == 0) {
            if (enfieldLine()) {
                when {
                    Regex("""\bN0\s+8""").containsMatchIn(lineUpper) ||
                        lineUpper.contains("WALTHAM CROSS") ->
                        outwardFromFullPostcode("EN", 8, letterPart)?.let { return it }
                    Regex("""\bN0\s+4""").containsMatchIn(lineUpper) ->
                        outwardFromFullPostcode("EN", 1, letterPart)?.let { return it }
                    Regex("""\bN0\s+[567]""").containsMatchIn(lineUpper) ->
                        outwardFromFullPostcode("EN", 3, letterPart)?.let { return it }
                }
            }
            val chelseaContext = lineUpper.contains("CHELSEA") ||
                lineUpper.contains("HARBOUR") ||
                lineUpper.contains("SW6") ||
                Regex("""\bN0\s+2""").containsMatchIn(lineUpper)
            if (chelseaContext) {
                outwardFromFullPostcode("SW", 6, letterPart)?.let { return it }
            }
            if (Regex("""\bN0\s+4""", RegexOption.IGNORE_CASE).containsMatchIn(lineUpper) &&
                !enfieldLine()
            ) {
                outwardFromFullPostcode("N", 1, letterPart)?.let { return it }
            }
            if (Regex("""\bN0\s+5""", RegexOption.IGNORE_CASE).containsMatchIn(lineUpper) ||
                lineUpper.contains("PENRITH")
            ) {
                outwardFromFullPostcode("N", 15, letterPart)?.let { return it }
            }
            val westLondonUb = lineUpper.contains("SOUTHALL") || lineUpper.contains("GREENFORD") ||
                lineUpper.contains("HOUNSLOW") || lineUpper.contains("UXBRIDGE") ||
                lineUpper.contains("JALEBI") || lineUpper.contains("ROSE GARDENS") ||
                Regex("""\bUB\d""").containsMatchIn(lineUpper)
            if (westLondonUb) {
                when {
                    Regex("""\bN0\s+1""", RegexOption.IGNORE_CASE).containsMatchIn(lineUpper) ->
                        outwardFromFullPostcode("UB", 1, letterPart)?.let { return it }
                }
            }
        }

        if (normalizedPrefix == "S" && districtNum == 14) {
            outwardFromFullPostcode("SL", 4, letterPart)?.let { return it }
        }

        // SW1W / SW1P often OCR as AL0N / AL0P (S→A, W→L/0/N/P confusion on driver cards).
        if (normalizedPrefix == "AL" && districtNum == 0) {
            when (letterPart.uppercase()) {
                "N" -> outwardFromFullPostcode("SW", 1, "W")?.let { return it }
                "P" -> outwardFromFullPostcode("SW", 1, "P")?.let { return it }
            }
            if (enfieldLine()) {
                Regex("""\bAL0\s+(\d)""").find(lineUpper)?.let { m ->
                    when (m.groupValues[1]) {
                        "4" -> outwardFromFullPostcode("EN", 1, "")?.let { return it }
                        "8" -> outwardFromFullPostcode("EN", 8, "")?.let { return it }
                        "2" -> outwardFromFullPostcode("EN", 2, "")?.let { return it }
                        "6", "7", "5", "3" -> outwardFromFullPostcode("EN", 3, "")?.let { return it }
                    }
                }
                if (Regex("""\bAL0\b""").containsMatchIn(lineUpper)) {
                    return null
                }
            }
        }

        // EC3M OCR'd as N6 on City/Fenchurch lines (E→N, C3M→6).
        if (normalizedPrefix == "N" && districtNum == 6 &&
            (lineUpper.contains("FENCHURCH") || lineUpper.contains("EC3") ||
                Regex("""\b5AD\b""").containsMatchIn(lineUpper))
        ) {
            outwardFromFullPostcode("EC", 3, "M")?.let { return it }
        }

        // W1J 0AB OCR'd as W6 0AB on Air Street / St James's.
        if (normalizedPrefix == "W" && districtNum == 6 &&
            Regex("""\b0AB\b""").containsMatchIn(lineUpper)
        ) {
            outwardFromFullPostcode("W", 1, "J")?.let { return it }
        }

        // AL1/AL2 on London/Southwark lines → SE1 (S→A, E→L OCR on driver cards).
        // Keep real St Albans / Colney Street / Parkbury AL* codes.
        if (normalizedPrefix == "AL" && districtNum in 1..9) {
            val keepAl = lineUpper.contains("ST ALBANS") || lineUpper.contains("HARPENDEN") ||
                lineUpper.contains("COLNEY") || lineUpper.contains("PARKBURY") ||
                lineUpper.contains("VENTURA") || lineUpper.contains("ST ALBAN")
            val southLondon = !keepAl && (
                lineUpper.contains("LONDON") || lineUpper.contains("MAWBEY") ||
                lineUpper.contains("WATERLOO") || lineUpper.contains("BANKSIDE") ||
                lineUpper.contains("SOUTH BANK") || lineUpper.contains("SIDINGS") ||
                Regex("""\bSE\d""").containsMatchIn(lineUpper)
            )
            if (southLondon) {
                val seDistrict = if (districtNum <= 2) 1 else districtNum
                outwardFromFullPostcode("SE", seDistrict, letterPart)?.let { return it }
            }
        }

        // E1–E15 on Waterloo/South Bank lines → SE when leading S was dropped (not E16+ Docklands).
        if (normalizedPrefix == "E" && districtNum in 1..15) {
            val seContext = lineUpper.contains("WATERLOO") || lineUpper.contains("SIDINGS") ||
                lineUpper.contains("SOUTH BANK") || lineUpper.contains("BANKSIDE") ||
                lineUpper.contains("MAWBEY") || lineUpper.contains("SOUTHWARK") ||
                Regex("""\bSE\d""").containsMatchIn(lineUpper)
            if (seContext) {
                outwardFromFullPostcode("SE", districtNum, letterPart)?.let { return it }
            }
        }

        // W1P / W1W on Westminster cards (Wiw, WIU). Do not remap to SW1* unless SW appears on the line.
        if (normalizedPrefix == "W" && normalizedPrefix.length == 1) {
            when (districtNum) {
                1 -> when (letterPart.uppercase()) {
                    "P" -> {
                        if (lineUpper.contains("SW1") || lineUpper.contains("SOUTH KENSINGTON")) {
                            outwardFromFullPostcode("SW", 1, "P")?.let { return it }
                        }
                        outwardFromFullPostcode("W", 1, "P")?.let { return it }
                    }
                    "W" -> {
                        if (lineUpper.contains("SW1") || lineUpper.contains("SOUTH KENSINGTON")) {
                            outwardFromFullPostcode("SW", 1, "W")?.let { return it }
                        }
                        outwardFromFullPostcode("W", 1, "W")?.let { return it }
                    }
                }
            }
        }

        return outwardFromFullPostcode(normalizedPrefix, districtNum, letterPart)
    }

    fun normalizeOutward(prefix: String, numberPart: Int, letter: String, sourceLine: String = ""): String? {
        if (prefix !in londonPrefixes) return null
        val districtRaw = if (letter.isEmpty()) numberPart.toString() else numberPart.toString() + letter
        outwardFromDistrictParts(prefix, districtRaw, sourceLine)?.let { return it }
        val maxAllowed = londonPostcodes[prefix]?.maxGeographicNumber ?: 99
        if (numberPart !in 0..maxAllowed) return null
        return outwardFromFullPostcode(prefix, numberPart, letter)
    }

    val foundByPosition = mutableListOf<Pair<Int, String>>()

    fun pickBestOutwardCandidate(codes: List<String>, sourceLine: String): String {
        if (codes.size == 1) return codes[0]

        /** True when the line shows this outward, including OCR El/SEl/ECIN for E1/SE1/EC1N. */
        fun lineHasOutwardOcr(code: String): Boolean {
            if (lineContainsOutwardStrict(sourceLine, code)) return true
            val letters = code.takeWhile { it.isLetter() }
            val district = code.drop(letters.length)
            if (letters.isEmpty() || district.isEmpty()) return false
            val districtPat = buildString {
                for (ch in district) {
                    when {
                        ch == '1' -> append("[1iIlL]")
                        ch == '0' -> append("[0oO]")
                        ch.isDigit() -> append(ch)
                        else -> append(Regex.escape(ch.toString()))
                    }
                }
            }
            return Regex(
                """\b${Regex.escape(letters)}$districtPat(?![0-9A-Za-z])""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(sourceLine)
        }

        val sw = codes.find { it.startsWith("SW") }
        val wOnly = codes.find { it.length >= 2 && it[0] == 'W' && it[1].isDigit() }
        if (sw != null && wOnly != null && wOnly == "W${sw.drop(2)}") {
            val hasSw = lineHasOutwardOcr(sw)
            val hasW = lineHasOutwardOcr(wOnly)
            return when {
                hasSw -> sw
                hasW && sw.drop(2) == "7" -> sw // "W7" on driver app cards is usually SW7
                hasW -> wOnly
                sw.drop(2) == "7" -> sw
                else -> wOnly
            }
        }

        val se = codes.find { it.startsWith("SE") }
        val eOnly = codes.find {
            it.length >= 2 && it[0] == 'E' && it[1].isDigit() && !it.startsWith("EN") && !it.startsWith("EC")
        }
        if (se != null && eOnly != null && eOnly == "E${se.drop(2)}") {
            // OCR often drops the leading S: "SE10" → "E10" (Greenwich Naval College).
            // Do NOT rewrite real East districts (E1 City, E15/E16 Docklands) to SE*.
            val hasSe = lineHasOutwardOcr(se)
            val hasE = lineHasOutwardOcr(eOnly)
            val eDistrictNum = eOnly.drop(1).takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            val eastLondonHint = sourceLine.contains("Spitalfields", ignoreCase = true) ||
                sourceLine.contains("Whitechapel", ignoreCase = true) ||
                sourceLine.contains("Travelodge London City", ignoreCase = true) ||
                sourceLine.contains("Shoreditch", ignoreCase = true) ||
                sourceLine.contains("Aldgate", ignoreCase = true) ||
                sourceLine.contains("Brick Lane", ignoreCase = true) ||
                sourceLine.contains("Bethnal", ignoreCase = true) ||
                sourceLine.contains("Hackney", ignoreCase = true) ||
                sourceLine.contains("Pizza Pilgrims", ignoreCase = true)
            val docklandsOrStratford = eDistrictNum in 14..20 ||
                sourceLine.contains("Docklands", ignoreCase = true) ||
                sourceLine.contains("Canary", ignoreCase = true) ||
                sourceLine.contains("Stratford", ignoreCase = true) ||
                sourceLine.contains("Canning", ignoreCase = true) ||
                sourceLine.contains("Custom House", ignoreCase = true)
            val seDroppedSHint = sourceLine.contains("Naval", ignoreCase = true) ||
                sourceLine.contains("Greenwich", ignoreCase = true) ||
                sourceLine.contains("Deptford", ignoreCase = true) ||
                sourceLine.contains("Lewisham", ignoreCase = true) ||
                sourceLine.contains("Catford", ignoreCase = true) ||
                sourceLine.contains("Sydenham", ignoreCase = true) ||
                sourceLine.contains("Peckham", ignoreCase = true) ||
                sourceLine.contains("Dulwich", ignoreCase = true) ||
                sourceLine.contains("Waterloo", ignoreCase = true) ||
                sourceLine.contains("Southwark", ignoreCase = true) ||
                sourceLine.contains("Mawbey", ignoreCase = true) ||
                sourceLine.contains("London Bridge", ignoreCase = true)
            return when {
                hasSe && !eastLondonHint -> se
                eastLondonHint && hasE -> eOnly
                docklandsOrStratford && hasE -> eOnly
                // Naval College / Greenwich: OCR "E10" means SE10 (leading S dropped).
                seDroppedSHint -> se
                hasE -> eOnly
                hasSe -> se
                else -> eOnly // Prefer real E* when OCR used El/EI — do not default to SE
            }
        }

        val en = codes.filter { it.startsWith("EN") }.maxByOrNull { it.length }
        val eDistrict = codes.find {
            it.length >= 2 && it[0] == 'E' && it[1].isDigit() && !it.startsWith("EN")
        }
        if (en != null && eDistrict != null) {
            return when {
                lineHasOutwardOcr(en) -> en
                sourceLine.contains("Enfield", ignoreCase = true) -> en
                lineHasOutwardOcr(eDistrict) -> eDistrict
                else -> en
            }
        }

        fun lineHasOutward(code: String): Boolean = lineHasOutwardOcr(code)

        for (code in codes.sortedByDescending { it.length }) {
            if (lineHasOutward(code)) return code
        }

        return codes.maxByOrNull { it.length } ?: codes.first()
    }

    // Full UK postcodes in addresses: "KT3 5PN", "N19 4DJ" — inward/district digits may OCR as O/o/I/l/Z.
    val inwardDigit = """[0-9oO]"""
    val districtPart = """[0-9iIlLoOzZ][0-9A-Za-ziIlLoOzZ]?"""
    val fullPostcodeRegex = Regex(
        """\b([A-Za-z]{1,2})($districtPart)\s+($inwardDigit)([A-Za-z]{2})\b""",
        RegexOption.IGNORE_CASE,
    )

    // Driver app Match/Confirm often show truncated inward: "MK10 7" / "CR7 7".
    val truncatedInwardRegex = Regex(
        """\b([A-Za-z]{1,2})($districtPart)\s+($inwardDigit)(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE,
    )

    fun lineAlreadyHasStructuredPostcode(line: String): Boolean {
        return fullPostcodeRegex.containsMatchIn(line) || truncatedInwardRegex.containsMatchIn(line)
    }

    fun isInsideStationParentheses(line: String, matchStart: Int): Boolean {
        val open = line.lastIndexOf('(', matchStart)
        if (open < 0) return false
        val close = line.indexOf(')', matchStart)
        if (close < 0 || open > matchStart) return false
        val inner = line.substring(open + 1, close)
        return inner.length in 2..4 && inner.all { it.isLetter() } && inner.none { it.isDigit() }
    }

    fun addOutward(at: Int, prefix: String, districtRaw: String, inferSwFromW: Boolean, sourceLine: String) {
        val districtNum = fixOcrPostcodeDistrict(districtRaw).toIntOrNull()
        if (districtNum != null && isMotorwayDistrictOutwardToken(prefix, districtNum, sourceLine)) {
            return
        }
        if (lineLooksLikeMotorwayMapLabel(sourceLine)) return
        if (lineLooksLikeBareEastMapPostcode(sourceLine)) return
        val codes = mutableListOf<String>()
        outwardFromDistrictParts(prefix, districtRaw, sourceLine)?.let { codes.add(it) }
        // Only for outward-only OCR tokens: "W7" may be missing S. Full "W8 4SG" must stay W8.
        if (inferSwFromW && prefix.equals("W", ignoreCase = true) && prefix.length == 1) {
            outwardFromDistrictParts("SW", districtRaw, sourceLine)?.let { sw ->
                if (sw !in codes) codes.add(sw)
            }
        }
        // Full "W7 4LH" on screen is often SW7 with dropped S.
        if (!inferSwFromW && prefix.equals("W", ignoreCase = true) && fixOcrNumberPart(districtRaw) == "7") {
            outwardFromDistrictParts("SW", districtRaw, sourceLine)?.let { sw ->
                if (sw !in codes) codes.add(sw)
            }
        }
        // Full "E1 7BH" / outward-only "E1" is often SE1 with dropped S.
        if (!inferSwFromW && prefix.equals("E", ignoreCase = true) && prefix.length == 1) {
            outwardFromDistrictParts("SE", districtRaw, sourceLine)?.let { se ->
                if (se !in codes) codes.add(se)
            }
        }
        // EN1/EN3 often OCR as AL0 or N0 on Enfield lines — try EN when E/N was dropped.
        if (!inferSwFromW && prefix.equals("E", ignoreCase = true) && prefix.length == 1) {
            outwardFromDistrictParts("EN", districtRaw, sourceLine)?.let { en ->
                if (en !in codes) codes.add(en)
            }
        }
        val valid = codes.filter { isValidUkOutward(it) }
        if (valid.isEmpty()) return
        foundByPosition.add(at to pickBestOutwardCandidate(valid, sourceLine))
    }

    fullPostcodeRegex.findAll(text).forEach { match ->
        val lineStart = text.lastIndexOf('\n', (match.range.first - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', match.range.first).let { if (it < 0) text.length else it }
        val sourceLine = text.substring(lineStart, lineEnd)
        addOutward(match.range.first, match.groupValues[1].uppercase(), match.groupValues[2], inferSwFromW = false, sourceLine)
    }

    truncatedInwardRegex.findAll(text).forEach { match ->
        val lineStart = text.lastIndexOf('\n', (match.range.first - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', match.range.first).let { if (it < 0) text.length else it }
        val sourceLine = text.substring(lineStart, lineEnd)
        addOutward(match.range.first, match.groupValues[1].uppercase(), match.groupValues[2], inferSwFromW = false, sourceLine)
    }

    // Outward-only tokens in address lines (search full OCR, not only tail lines).
    // Include Z so "CRZ" / "CRZ7" recover as CR7.
    val outwardRegex = Regex(
        """\b([A-Za-z]{1,2})([0-9iIlLoOzZ]{1,2})([A-Za-z]?)\b""",
        RegexOption.IGNORE_CASE,
    )
    outwardRegex.findAll(text).forEach { match ->
        val prefix = match.groupValues[1].uppercase()
        val districtRaw = match.groupValues[2]
        // Require a real digit, or a single OCR digit-letter on London areas (El→E1, not Pol→PO1).
        if (!districtRaw.any { it.isDigit() }) {
            if (districtRaw.length != 1 || districtRaw[0] !in "iIlLoOzZ") return@forEach
            val londonAreas = setOf("E", "EC", "N", "NW", "SE", "SW", "W", "WC", "CR", "BR")
            if (prefix !in londonAreas) return@forEach
        }
        val numberPart = fixOcrNumberPart(districtRaw).toIntOrNull() ?: return@forEach
        val letter = match.groupValues[3]
        val lineStart = text.lastIndexOf('\n', (match.range.first - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', match.range.first).let { if (it < 0) text.length else it }
        val sourceLine = text.substring(lineStart, lineEnd)
        if (lineAlreadyHasStructuredPostcode(sourceLine)) return@forEach
        if (lineLooksLikeMotorwayMapLabel(sourceLine)) return@forEach
        if (lineLooksLikeBareEastMapPostcode(sourceLine)) return@forEach
        if (isInsideStationParentheses(sourceLine, match.range.first)) return@forEach
        if (isMotorwayDistrictOutwardToken(prefix, numberPart, sourceLine)) return@forEach
        // Call / notification banners: "Wojtek Pol Heavy" must not invent PO1/P01.
        if (sourceLine.contains("incoming", ignoreCase = true) ||
            sourceLine.contains("call", ignoreCase = true) ||
            Regex("""\b(Heavy|Haulage|Ltd|Limited|Inc)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(sourceLine)
        ) {
            return@forEach
        }
        if (Regex("""\bA10\b""").containsMatchIn(sourceLine) &&
            prefix.equals("AL", ignoreCase = true) && fixOcrPostcodeDistrict(districtRaw) == "0"
        ) {
            return@forEach
        }
        if (Regex("""\bHale\b""", RegexOption.IGNORE_CASE).containsMatchIn(sourceLine) &&
            !Regex("""\bHA\d""", RegexOption.IGNORE_CASE).containsMatchIn(sourceLine)
        ) {
            return@forEach
        }
        val codes = mutableListOf<String>()
        normalizeOutward(prefix, numberPart, letter, sourceLine)?.let { codes.add(it) }
        // Only infer SW when outward is numeric-only (e.g. W7). W1W/W1J must not become SW1W.
        if (prefix == "W" && prefix.length == 1 && letter.isEmpty()) {
            normalizeOutward("SW", numberPart, letter, sourceLine)?.let { sw ->
                if (sw !in codes) codes.add(sw)
            }
        }
        if (prefix == "E" && prefix.length == 1) {
            normalizeOutward("SE", numberPart, letter, sourceLine)?.let { se ->
                if (se !in codes) codes.add(se)
            }
            normalizeOutward("EN", numberPart, letter, sourceLine)?.let { en ->
                if (en !in codes) codes.add(en)
            }
        }
        val valid = codes.filter { isValidUkOutward(it) }
        if (valid.isNotEmpty()) {
            foundByPosition.add(match.range.first to pickBestOutwardCandidate(valid, sourceLine))
        }
    }

    // Recover full postcodes with NO space where the outward's trailing digit OCR'd as a letter,
    // e.g. "BRI1QE" = BR1 1QE (1→I, space dropped), "SL41LH" = SL4 1LH, or "SWi9AB" = SW1 9AB.
    // Prefer 1-digit then 2-digit district so "SL41LH" does not greedily consume "41" as district.
    listOf(
        Regex("""\b([A-Za-z]{1,2})([0-9ilLoOzZ])([0-9])([A-Za-z]{2})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Za-z]{1,2})([0-9ilLoOzZ]{2})([0-9])([A-Za-z]{2})\b""", RegexOption.IGNORE_CASE),
        // Truncated concatenated with OCR letter-as-digit: "CRZ7" = CR7 7 (not "SE14")
        Regex("""\b([A-Za-z]{1,2})([iIlLoOzZ])([0-9])\b""", RegexOption.IGNORE_CASE),
    ).forEach { concatenatedPostcodeRegex ->
        concatenatedPostcodeRegex.findAll(text).forEach { match ->
            val at = match.range.first
            if (foundByPosition.any { kotlin.math.abs(it.first - at) <= 2 }) return@forEach
            val prefix = match.groupValues[1].uppercase()
            val districtRaw = match.groupValues[2]
            val lineStart = text.lastIndexOf('\n', (at - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', at).let { if (it < 0) text.length else it }
            val sourceLine = text.substring(lineStart, lineEnd)
            if (lineLooksLikeMotorwayMapLabel(sourceLine)) return@forEach
            val districtNum = fixOcrPostcodeDistrict(districtRaw).toIntOrNull() ?: return@forEach
            normalizeOutward(prefix, districtNum, "", sourceLine)?.let { code ->
                if (isValidUkOutward(code)) foundByPosition.add(at to code)
            }
        }
    }

    // Partial W1 outwards when digit 1 is dropped: "London. WF" → W1F (no digit for outward regex).
    val partialW1OnLondon = mapOf(
        "WF" to "W1F", "WD" to "W1D", "WIH" to "W1H", "WIF" to "W1F",
        "WIW" to "W1W", "WIU" to "W1U", "W1U" to "W1U",
    )
    partialW1OnLondon.forEach { (token, outward) ->
        Regex("""\b${Regex.escape(token)}\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val lineStart = text.lastIndexOf('\n', (m.range.first - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', m.range.first).let { if (it < 0) text.length else it }
            val sourceLine = text.substring(lineStart, lineEnd)
            if (sourceLine.uppercase().contains("LONDON") && isValidUkOutward(outward) &&
                foundByPosition.none { it.second == outward }
            ) {
                foundByPosition.add(m.range.first to outward)
            }
        }
    }

    // Orphan inward on its own line (map noise splits postcodes): "8JB" near "London. WF" → W1F.
    // Also "8LS" after "... Thornton Heath. CR7" → CR7, and "4SF" after Enfield → EN3.
    run {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val partialOutward = mapOf(
            "WF" to "W1F", "WD" to "W1D", "WIH" to "W1H", "WIF" to "W1F",
            "WIW" to "W1W", "WIU" to "W1U", "W1U" to "W1U",
        )
        lines.forEachIndexed { i, line ->
            val inward = Regex("""\b([0-9oO][A-Za-z]{2})\b""").find(line)?.groupValues?.get(1)
                ?: return@forEachIndexed
            if (line.length > 14) return@forEachIndexed
            val window = lines.subList(maxOf(0, i - 5), i + 1).joinToString(" ").uppercase()
            if (window.contains("ENFIELD") &&
                Regex("""\b4SF\b""", RegexOption.IGNORE_CASE).containsMatchIn(line) &&
                foundByPosition.none { it.second == "EN3" }
            ) {
                foundByPosition.add(text.indexOf(line).coerceAtLeast(0) to "EN3")
            }
            for (j in i - 1 downTo maxOf(0, i - 4)) {
                val prev = lines[j]
                val prevUpper = prev.uppercase()
                partialOutward.forEach { (token, outward) ->
                    if (Regex("""\b${Regex.escape(token)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(prevUpper) &&
                        isValidUkOutward(outward) &&
                        foundByPosition.none { it.second == outward }
                    ) {
                        foundByPosition.add(text.indexOf(line).coerceAtLeast(0) to outward)
                    }
                }
                if (Regex("""\bN1\b""").containsMatchIn(prevUpper) &&
                    inward.startsWith("4", ignoreCase = true) &&
                    foundByPosition.none { it.second == "N1" }
                ) {
                    foundByPosition.add(text.indexOf(line).coerceAtLeast(0) to "N1")
                }
                // Trailing outward on previous address line: "… Heath. CR7" / "… London, SW1E"
                Regex(
                    """\b([A-Za-z]{1,2})([0-9iIlLoOzZ]{1,2})([A-Za-z]?)\s*$""",
                    RegexOption.IGNORE_CASE,
                ).find(prev)?.let { m ->
                    val prefix = m.groupValues[1].uppercase()
                    val districtNum = fixOcrPostcodeDistrict(m.groupValues[2]).toIntOrNull() ?: return@let
                    val letter = m.groupValues[3]
                    normalizeOutward(prefix, districtNum, letter, prev)?.let { code ->
                        if (isValidUkOutward(code) && foundByPosition.none { it.second == code }) {
                            foundByPosition.add(text.indexOf(line).coerceAtLeast(0) to code)
                        }
                    }
                }
            }
        }
    }

    // "N17 ORD" / "N18 20G" — inward OCR with O/0 confusion; ensure outward is kept.
    Regex(
        """\b([A-Za-z]{1,2})([0-9]{1,2})\s+([0-9oO])([A-Za-z0-9]{2})\b""",
        RegexOption.IGNORE_CASE,
    ).findAll(text).forEach { m ->
        val at = m.range.first
        if (foundByPosition.any { kotlin.math.abs(it.first - at) <= 2 }) return@forEach
        val prefix = m.groupValues[1].uppercase()
        val district = m.groupValues[2]
        val lineStart = text.lastIndexOf('\n', (at - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', at).let { if (it < 0) text.length else it }
        val sourceLine = text.substring(lineStart, lineEnd)
        val districtNum = district.toIntOrNull() ?: return@forEach
        normalizeOutward(prefix, districtNum, "", sourceLine)?.let { code ->
            if (isValidUkOutward(code)) foundByPosition.add(at to code)
        }
    }

    // Document order (pickup before drop on Match cards); dedupe keeps first sighting.
    // Prefer longer outwards: NW11 beats NW1, SE14 beats SE1 when both were OCR'd.
    val ordered = LinkedHashSet<String>()
    foundByPosition.sortedBy { it.first }.forEach { (_, code) ->
        if (!isValidUkOutward(code)) return@forEach
        val shorter = ordered.filter { code.startsWith(it) && code.length > it.length }
        ordered.removeAll(shorter.toSet())
        if (ordered.none { it.startsWith(code) && it.length > code.length }) {
            ordered.add(code)
        }
    }
    // Map labels like bare "E15" must not displace City address codes (E1W / EC2R).
    val addressBacked = foundByPosition.mapNotNull { (at, code) ->
        val lineStart = text.lastIndexOf('\n', (at - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', at).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val onAddress = line.contains(',') ||
            line.contains("London", ignoreCase = true) ||
            Regex(
                """\b(Hotel|Station|Road|Street|News|Palace|Garden|Square|Bridge)\b""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(line)
        if (onAddress) code else null
    }.toSet()
    if (addressBacked.any { it.startsWith("EC") || it.startsWith("E1") || it.startsWith("WC") }) {
        ordered.removeAll { code ->
            val n = code.dropWhile { it.isLetter() }.takeWhile { it.isDigit() }.toIntOrNull() ?: return@removeAll false
            code.startsWith("E") && !code.startsWith("EC") && !code.startsWith("EN") &&
                n in 14..20 && code !in addressBacked
        }
    }
    return ordered.toList()
}


fun isReserved(ocrText: String): Boolean {
    return ocrText.contains("Reserved")
}

@SuppressLint("DefaultLocale")
fun parseRideInfo(ocrText: String, visionText: Text? = null): RideRequest {

    var accuracy = 100
    val ocrLines = visionText?.let { collectOcrLines(it) }.orEmpty()
    val structured = if (ocrLines.isNotEmpty()) {
        parseStructuredFromLines(ocrLines, ocrText)
    } else {
        null
    }

    val structuredPrice = structured?.price
    val textPrice = extractBestPrice(ocrText)
    // Prefer OCR £ fare over structured max — map/OCR noise can invent a higher bare amount.
    var priceReal = when {
        textPrice >= 5.0 && structuredPrice != null && structuredPrice >= 5.0 -> {
            if (kotlin.math.abs(structuredPrice - textPrice) > 2.5) {
                minOf(structuredPrice, textPrice)
            } else {
                textPrice
            }
        }
        textPrice >= 5.0 -> textPrice
        structuredPrice != null && structuredPrice >= 5.0 -> structuredPrice
        textPrice >= 1.0 -> textPrice
        structuredPrice != null -> structuredPrice
        else -> 0.0
    }
    if (priceReal in 1.0..4.99) {
        extractBestPrice(ocrText).let { recovered ->
            if (recovered >= 5.0) priceReal = recovered
        }
        // £11.64 OCR'd as £1.16 — recover leading tens digit when XX.YZ appears in OCR.
        if (priceReal in 1.0..1.99) {
            Regex("""(?<![\d.])(1[0-9])[.,](\d{2})(?![\d.])""").findAll(ocrText).forEach { m ->
                val v = "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull() ?: return@forEach
                if (v in 8.0..80.0) priceReal = maxOf(priceReal, v)
            }
        }
    }

    if (priceReal < 1.0) {
        accuracy = 50
    } else if (extractPrice(ocrText).isEmpty() && structured?.price == null) {
        accuracy -= 5
    }

    priceReal = priceReal.toBigDecimal()
        .setScale(2, RoundingMode.HALF_UP)
        .toDouble()

    // £8.01 OCR'd as £3.01 — leading digit 8 misread as 3.
    if (priceReal in 3.0..3.99) {
        Regex("""(?<![\d.])(8[.,]0\d)(?![\d.])""").findAll(ocrText).forEach { m ->
            m.groupValues[1].replace(',', '.').toDoubleOrNull()?.let { v ->
                if (v in 7.5..8.5) priceReal = v
            }
        }
        Regex("""[£$]\s*801\b""").find(ocrText)?.let { priceReal = 8.01 }
    }
    // Bare map "20" must not beat a real sub-£5 £ fare (e.g. £4.67).
    // Do NOT shrink a recovered XL fare (£37.41 from OCR £3.74) back down.
    if (priceReal >= 15.0) {
        val wholeMapNoise = kotlin.math.abs(priceReal - priceReal.toInt().toDouble()) < 0.001
        if (wholeMapNoise) {
            extractPrice(ocrText).filter { it in 3.0..9.99 }.maxOrNull()?.let { priceReal = it }
        }
    }

    var rating = structured?.rating
        ?: pickBestPassengerRating(ocrLines, ocrText)
        ?: extractRatings(ocrText).mapNotNull { it.toDoubleOrNull() }
            .filter { it in 4.0..5.05 }
            .maxOrNull()
    if (rating == null) {
        // Last resort before 3.50 default: any ★/Verified 4.xx in the offer header.
        Regex(
            """(?:★|\*|☆)[ \t]*([4-5])[.,](\d{2})\b|\b([4-5])[.,](\d{2})[ \t]*(?:Verified|★|\*)""",
            RegexOption.IGNORE_CASE,
        ).find(ocrText)?.let { m ->
            val a = m.groupValues[1].ifEmpty { m.groupValues[3] }
            val b = m.groupValues[2].ifEmpty { m.groupValues[4] }
            "$a.$b".toDoubleOrNull()?.let { if (it in 4.0..5.05) rating = it }
        }
    }
    if (rating != null) {
        rating = upgradeTruncatedRating(rating, ocrText)
    }
    val finalRating = rating ?: 3.50

    // £11.44 unreadable over the map: the ★ 4.48 rating must not be reported as the fare.
    if (priceReal in 4.0..5.05 && kotlin.math.abs(priceReal - finalRating) < 0.02 &&
        extractPrice(ocrText).isEmpty() && structured?.price == null
    ) {
        priceReal = 0.0
        accuracy = 50
    }

    val timeList = extractTime(ocrText)
    val legPairs = extractTripLegPairsInOrder(ocrText)
    var pickupTime = structured?.pickupMinutes?.toDouble()
        ?: legPairs.getOrNull(0)?.first?.toDouble()
        ?: timeList.getOrNull(0)?.toDouble()
    var tripTime = structured?.tripMinutes?.toDouble()
        ?: legPairs.getOrNull(1)?.first?.toDouble()
        ?: timeList.getOrNull(1)?.toDouble()

    val distanceList = extractDistance(ocrText)
    // One leg-line parsed but two trip times (e.g. pickup "1 min" line lost) — use time/distance lists.
    if (legPairs.size == 1 && timeList.size >= 2) {
        pickupTime = timeList[0].toDouble()
        tripTime = timeList[1].toDouble()
    } else if (legPairs.size >= 2 &&
        legPairs[0].first == legPairs[1].first &&
        timeList.size >= 2 &&
        timeList[0] != timeList[1]
    ) {
        pickupTime = timeList[0].toDouble()
        tripTime = timeList[1].toDouble()
    }

    var rawPickup = structured?.pickupMiles?.toString()
        ?: legPairs.getOrNull(0)?.second?.toString()
        ?: distanceList.getOrNull(0)
    var rawTrip = structured?.tripMiles?.toString()
        ?: legPairs.getOrNull(1)?.second?.toString()
        ?: distanceList.getOrNull(1)
    if ((legPairs.size == 1 && timeList.size >= 2) ||
        (legPairs.size >= 2 && legPairs[0].first == legPairs[1].first && timeList.size >= 2)
    ) {
        if (distanceList.size >= 2) {
            rawPickup = distanceList[0]
            rawTrip = distanceList[1]
        } else if (distanceList.size == 1 && legPairs.size == 1) {
            // Pickup miles garbled; sole "(X mi)" is the trip leg.
            val soleMins = legPairs[0].first
            val tripMins = tripTime?.toInt()
            if (tripMins != null && kotlin.math.abs(soleMins - tripMins) <= 1) {
                rawTrip = distanceList[0]
                rawPickup = null
            } else {
                rawPickup = distanceList[0]
            }
        } else if (distanceList.isNotEmpty()) {
            rawPickup = distanceList[0]
        }
    }

    var pickupDistance: Double? = rawPickup?.let { raw ->
        parseOcrMiles(raw, pickupTime?.toInt()) ?: run {
            val numericValue = raw.toDoubleOrNull() ?: 0.0
            if (!raw.contains(".")) numericValue / 10.0 else numericValue
        }
    }

    var tripDistance: Double? = rawTrip?.let { raw ->
        parseOcrMiles(raw, tripTime?.toInt()) ?: run {
            val numericValue = raw.toDoubleOrNull() ?: 0.0
            if (!raw.contains(".")) numericValue / 10.0 else numericValue
        }
    }
    // "1.2 mi" OCR'd as "2.0" — prefer explicit 1.2/1,2 in OCR on short pickups.
    if (pickupDistance != null && pickupDistance in 1.85..2.15 &&
        pickupTime != null && pickupTime in 3.0..12.0 &&
        Regex("""\b1[.,]2\s*mi""", RegexOption.IGNORE_CASE).containsMatchIn(ocrText)
    ) {
        pickupDistance = 1.2
    }

    var pickupPostcode = structured?.pickupPostcode.orEmpty()
    var dropoffPostcode = structured?.dropPostcode.orEmpty()

    val zonePostcodes = resolvePostcodesFromLegZones(ocrText)
    if (pickupPostcode.isBlank() && zonePostcodes.first.isNotBlank()) {
        pickupPostcode = zonePostcodes.first
    }
    if (dropoffPostcode.isBlank() && zonePostcodes.second.isNotBlank()) {
        dropoffPostcode = zonePostcodes.second
    }
    if (pickupPostcode.isNotBlank() && pickupPostcode == dropoffPostcode &&
        zonePostcodes.first.isNotBlank() && zonePostcodes.first != zonePostcodes.second
    ) {
        pickupPostcode = zonePostcodes.first
        if (zonePostcodes.second.isNotBlank()) {
            dropoffPostcode = zonePostcodes.second
        }
    }
    // Drop map label must not overwrite a distinct pickup outward when leg zones disagree.
    if (pickupPostcode.isNotBlank() && pickupPostcode == dropoffPostcode &&
        zonePostcodes.first.isNotBlank() && zonePostcodes.first != pickupPostcode
    ) {
        pickupPostcode = zonePostcodes.first
    }
    if (pickupPostcode.isNotBlank() && pickupPostcode == dropoffPostcode) {
        val lines = ocrText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val legIndices = lines.mapIndexedNotNull { index, line ->
            if (parseTripLegFromLine(line) != null) index else null
        }
        if (legIndices.size >= 2) {
            val pickupLegIdx = legIndices[0]
            val dropLegIdx = legIndices[1]
            val dropAddrIdx = findDropAddressLineIndex(lines, dropLegIdx)
            val pickupZoneEnd = findPickupZoneEnd(lines, pickupLegIdx, dropLegIdx)
            val retryPickup = pickBestPostcodeInLineRange(
                lines, pickupLegIdx, pickupZoneEnd, minScore = 60, preferEarliest = true,
            )
            val dropStart = dropAddrIdx.coerceAtLeast(dropLegIdx + 1)
            val retryDrop = pickBestPostcodeInLineRange(
                lines, dropStart, lines.size, minScore = 60, preferEarliest = false,
            )
            if (retryPickup.isNotBlank() && retryDrop.isNotBlank() && retryPickup != retryDrop) {
                pickupPostcode = retryPickup
                dropoffPostcode = retryDrop
            } else {
                val ordered = extractOuterLondonPostcodes(ocrText)
                if (ordered.size >= 2 && ordered[0] != ordered[1]) {
                    pickupPostcode = ordered[0]
                    dropoffPostcode = ordered[1]
                }
            }
        }
    }

    if (structured == null) {
        val postCodeList = extractOuterLondonPostcodes(ocrText)
        if (postCodeList.size >= 2) {
            if (pickupPostcode.isBlank()) pickupPostcode = postCodeList[0]
            if (dropoffPostcode.isBlank()) dropoffPostcode = postCodeList[1]
        }
    } else if (pickupPostcode.isBlank() && dropoffPostcode.isNotBlank()) {
        if (zonePostcodes.first.isNotBlank()) {
            pickupPostcode = zonePostcodes.first
            // Same-district trips (NW4→NW4): keep the drop code; only clear when zones disagree.
            if (zonePostcodes.second.isNotBlank() && zonePostcodes.second != zonePostcodes.first) {
                dropoffPostcode = zonePostcodes.second
            }
        }
    }

    // Safety net for layouts where leg-zone logic misassigns (e.g. scheduled/Reserved trips whose
    // drop address sits ABOVE the final "N mins (X mi)" line). When we ended up with a blank or
    // duplicated drop but the card clearly shows two distinct postcodes, fall back to document order
    // (pickup appears above drop on the card).
    if (dropoffPostcode.isBlank() || pickupPostcode == dropoffPostcode) {
        val ordered = extractOuterLondonPostcodes(ocrText).distinct()
        if (ordered.size >= 2) {
            pickupPostcode = ordered[0]
            dropoffPostcode = ordered[1]
        }
    }
    val allOutwards = extractOuterLondonPostcodes(ocrText)
    // Same-district return (N17→N17): one unique outward appears twice in OCR.
    if (allOutwards.size == 1) {
        val code = allOutwards[0]
        // Count both spaced "SL4 1LH" and concatenated "SL41LH" / truncated "SL3 6" / OCR "SEl 7EH".
        val letters = code.takeWhile { it.isLetter() }
        val district = code.drop(letters.length)
        val mentionRegex = Regex(
            """\b${Regex.escape(letters)}[0-9iIlLoO]{${district.length}}(?!\d)""",
            RegexOption.IGNORE_CASE,
        )
        // Require the code on 2+ distinct lines — a single drop line (WC1N 2AD) must not
        // fill a blank pickup with the drop's postcode.
        // Also: glare-obscured pickup ("Jasmine News, London,") must not inherit drop W1C.
        val mentionLines = ocrText.lineSequence().count { mentionRegex.containsMatchIn(it) }
        val pickupAddressMissingPc = ocrHasPickupAddressWithoutPostcode(ocrText)
        if (mentionRegex.findAll(ocrText).count() >= 2 && mentionLines >= 2 &&
            !pickupAddressMissingPc
        ) {
            if (pickupPostcode.isBlank()) pickupPostcode = code
            if (dropoffPostcode.isBlank()) dropoffPostcode = code
        }
    }
    if (dropoffPostcode.isBlank() &&
        allOutwards.any { it == "EN3" } &&
        ocrText.contains("Enfield", ignoreCase = true)
    ) {
        dropoffPostcode = "EN3"
    }

    if (structured != null) {
        accuracy = minOf(accuracy, 95)
    }

    if (pickupPostcode == "") {
        accuracy -= 5
    }
    if (dropoffPostcode == "") {
        accuracy -= 5
    }

    val type = when {
        ocrText.contains("confirm", ignoreCase = true) -> "confirm"
        ocrText.contains("Confirnm", ignoreCase = true) -> "confirm"
        Regex("\\bconf[i1l!|]rm\\b", RegexOption.IGNORE_CASE).containsMatchIn(ocrText) -> "confirm"
        // OCR often splits or garbles "Match"; treat obvious variants as match.
        ocrText.contains("match", ignoreCase = true) -> "match"
        else -> ""
    }

    return RideRequest(
        id = 0,
        price = priceReal,
        rating = finalRating,
        pickup_time_minutes = pickupTime?.toInt(),
        pickup_distance_value = pickupDistance,
        trip_time_minutes = tripTime?.toInt(),
        trip_distance_value = tripDistance,
        pickup_address_postcode = pickupPostcode,
        dropoff_address_postcode = dropoffPostcode,
        start_time_window = "",
        end_time_window = "",
        acceptedOrRejected = 0,
        final_score = 0,
        raw_text = "",
        created_at = "",
        type = type,
        accuracy = accuracy,
    ).let { reconcileDuplicatedPickupDrop(it, ocrText) }
}


/** Broadcast when capture stops so Settings (START button) can reset. */
const val ACTION_DRIVERPRO_CAPTURE_STOPPED = "com.driver.pro.ACTION_CAPTURE_STOPPED"

/** Broadcast when capture actually begins (MediaProjection ready) for global “Capturing…” UI. */
const val ACTION_DRIVERPRO_CAPTURE_STARTED = "com.driver.pro.ACTION_CAPTURE_STARTED"

/** OCR could not place a tap — ask [DriverAppAccessibilityService] to find Confirm / X in the node tree. */
const val ACTION_A11Y_TAP_DECISION = "com.driver.pro.ACTION_A11Y_TAP_DECISION"

/** Show touchable Accept / Decline / Skip overlay when OCR is incomplete or low-confidence. */
const val ACTION_SHOW_MANUAL_CONFIRM = "com.driver.pro.ACTION_SHOW_MANUAL_CONFIRM"

/** Below this OCR accuracy, auto-accept/reject pauses for a manual confirm window. */
const val LOW_OCR_CONFIDENCE_THRESHOLD = 80

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private lateinit var projectionManager: MediaProjectionManager
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val handlerThread = HandlerThread("SCThread").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastRun = 0L
    private val OCR_INTERVAL = 1500L

    private var lastText: String? = null
    private var lastRideRequest: RideRequest? = null
    private var sameRideIndex: Int = 0
    private val ocrStabilityGate = OcrStabilityGate(requiredMatches = 1)
    private var lastReadingOfferToastAt: Long = 0L
    private var lastScreenHash: Int? = null
    private var cropHeight: Int = 0

    /** Full-screen capture size (for mapping OCR boxes → tap coordinates). */
    private var screenCaptureWidth: Int = 0
    private var screenCaptureHeight: Int = 0
    /** [scaleDownForOcr] frame before crop; crop starts at [ocrCropTopPx]. */
    private var ocrSourceWidth: Int = 0
    private var ocrSourceHeight: Int = 0
    private var ocrCropTopPx: Int = 0

    private val acceptTapKeys = listOf("confirm", "match", "accept", "acept", "confirnm")
    private val rejectTapKeys = listOf("decline", "pass", "close", "dismiss", "no thanks", "not now")




    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private fun projectionConsentIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("data")
        }

    /** Remove foreground notification and stop service when consent / extras are invalid. */
    private fun tearDownForegroundAndStop() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun releaseProjectionAndDisplay() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
        } catch (_: Exception) {
        }
        try {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d("MY-BROADCAST", "service start")
        startForegroundService()
        toastOnMain("driverPRO: capturing (see notification shade)")

        val resultCode = intent.getIntExtra("code", Activity.RESULT_CANCELED)
        val resultData = projectionConsentIntent(intent)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.w("MY-BROADCAST", "Invalid capture intent extras; stopping without projection")
            tearDownForegroundAndStop()
            return START_NOT_STICKY
        }

        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Log.e("MY-BROADCAST", "getMediaProjection returned null")
            tearDownForegroundAndStop()
            return START_NOT_STICKY
        }

        mediaProjection = projection

        // MUST REGISTER CALLBACK BEFORE capture
        projection.registerCallback(mediaProjectionCallback, handler)

        try {
            sendBroadcast(
                Intent(ACTION_DRIVERPRO_CAPTURE_STARTED).apply {
                    setPackage(packageName)
                },
            )
        } catch (_: Exception) {
        }

        setupCapture()

        return START_NOT_STICKY
    }

    // ------------------------------------------------------------
    // Notification for Foreground service (required)
    // ------------------------------------------------------------
    private fun startForegroundService() {

        // New channel id: IMPORTANCE_* cannot be upgraded on an existing channel after creation.
        val channelId = "driverpro_capture_fg2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "Screen capture",
                // High visibility: user must see capture is active; requires notification permission on API 33+.
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shown while driverPRO captures the screen"
                setShowBadge(true)
            }
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.driver.pro.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Capturing screen")
            .setContentText("Full-screen capture is active — switch to Driver app")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .build()

        startForeground(1, notification)
    }

    /** Toast works over other apps’ fullscreen UI; overlay score text requires accessibility + overlay permission. */
    private fun toastOnMain(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------
    // Capture Setup
    // ------------------------------------------------------------
    private fun setupCapture() {
        Log.d("MY-BROADCAST", "setup Capture....")
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val dpi = displayMetrics.densityDpi

        // 1s delay

        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)

            imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2
            )

            if (imageReader == null) return@launch

            val mp = mediaProjection ?: return@launch
            virtualDisplay = mp.createVirtualDisplay(
                "ScreenCaptureDisplay",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                handler
            )

            imageReader!!.setOnImageAvailableListener({ reader ->

                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

                val ts = System.currentTimeMillis()
                if (ts - lastRun < OCR_INTERVAL) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                lastRun = ts
                Log.d("MY-BROADCAST", "Starting Capture....")

                val bitmap = imageToBitmap(image)
                image.close()

                if (bitmap == null) {
                    Log.d("MY-BROADCAST", "bitmap null")
                    return@setOnImageAvailableListener
                }

                val newHash = bitmapHash(bitmap)

                if (newHash != lastScreenHash || ocrStabilityGate.needsMoreReads()) {
                    lastScreenHash = newHash
                    //                Log.d("MY-BROADCAST", "Running OCR....")
                    runOCR(bitmap)
                } else {
                    Log.d("MY-BROADCAST", "same bitmap")
                    if (!bitmap.isRecycled) bitmap.recycle()
                }

            }, handler)
        }
    }

    /** Cheap change detector — full-screen IntArray was ~10MB+ per frame and caused OOM. */
    private fun bitmapHash(bmp: Bitmap): Int {
        val maxSide = 180
        val scale = minOf(maxSide.toFloat() / bmp.width, maxSide.toFloat() / bmp.height, 1f)
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        return try {
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            pixels.contentHashCode()
        } finally {
            if (!small.isRecycled) small.recycle()
        }
    }

    // ------------------------------------------------------------
    // MediaProjection Callback (REQUIRED)
    // ------------------------------------------------------------
    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(
                "driverPRO-Capture",
                "MediaProjection stopped (system/user revoked capture, low memory, or policy). Capture ends — not a 1-minute product limit.",
            )
            releaseProjectionAndDisplay()
            stopSelf()
        }
    }

    // ------------------------------------------------------------
    // IMAGE → Bitmap
    // ------------------------------------------------------------
    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val rowPadding = rowStride - pixelStride * width

            val bmp = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )

            bmp.copyPixelsFromBuffer(buffer)
            val result = Bitmap.createBitmap(bmp, 0, 0, width, height)
            if (!bmp.isRecycled && bmp !== result) {
                bmp.recycle()
            }
            result
        } catch (e: Exception) {
            Log.e("Capture", "Bitmap error: ${e.message}")
            null
        }
    }


    private fun preprocessBitmapForOCR(bitmap: Bitmap): Bitmap {
        // Crop bottom half
        val height = bitmap.height
        cropHeight = height * 1 / 3
        val cropped = Bitmap.createBitmap(bitmap, 0, cropHeight, bitmap.width, height - cropHeight)

//        // Scale 2x
        val scaled = cropped.scale(cropped.width * 2, cropped.height * 2)

//        val scaled = bitmap

        // Simple contrast & sharpen filter
        val contrast = 1.5f  // increase contrast
        val paint = android.graphics.Paint()
        val cm = android.graphics.ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, 0f,
                0f, contrast, 0f, 0f, 0f,
                0f, 0f, contrast, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val filter = android.graphics.ColorMatrixColorFilter(cm)
        paint.colorFilter = filter
        val enhanced = createBitmap(scaled.width, scaled.height)
        val canvas = android.graphics.Canvas(enhanced)
        canvas.drawBitmap(scaled, 0f, 0f, paint)

        return enhanced
    }

    /** Mild contrast boost on the full frame — no crop (fare sits on the card top edge) and no 2× scale (OOM). */
    fun increaseContrast(bitmap: Bitmap, contrast: Float = 1.6f): Bitmap {
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f

        val cm = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }

        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bitmap, 0f, 0f, paint)
        if (!bitmap.isRecycled && bitmap !== out) {
            bitmap.recycle()
        }
        return out
    }

    fun sharpen(bitmap: Bitmap, strength: Float = 1.0f): Bitmap {

        val kernel = floatArrayOf(
            0f,        -1f * strength, 0f,
            -1f * strength, 1f + 4f * strength, -1f * strength,
            0f,        -1f * strength, 0f
        )

        val width = bitmap.width
        val height = bitmap.height

        val src = IntArray(width * height)
        val dst = IntArray(width * height)

        bitmap.getPixels(src, 0, width, 0, 0, width, height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {

                var r = 0f
                var g = 0f
                var b = 0f
                var idx = 0

                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = src[(y + ky) * width + (x + kx)]
                        val weight = kernel[idx++]

                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }

                val i = y * width + x
                dst[i] = Color.rgb(
                    r.coerceIn(0f, 255f).toInt(),
                    g.coerceIn(0f, 255f).toInt(),
                    b.coerceIn(0f, 255f).toInt()
                )
            }
        }

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(dst, 0, width, 0, 0, width, height)

        if (!bitmap.isRecycled && bitmap !== out) {
            bitmap.recycle()
        }
        return out
    }



    fun saveBitmapFromService(
        bitmap: Bitmap,
        fileName: String = "image_${System.currentTimeMillis()}.jpg"
    ): Uri? {

        val resolver = applicationContext.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyServiceApp")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )

        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(it, values, null, null)
        }

        return uri
    }


    // ------------------------------------------------------------
    // OCR
    // ------------------------------------------------------------

    /**
     * Full-resolution frames + [increaseContrast] (2×) + [sharpen] (two full IntArrays) peak at tens of MB
     * per frame and can OOM-kill the process after ~1 minute. Downscale before the OCR pipeline.
     */
    private fun scaleDownForOcr(src: Bitmap, maxLongSide: Int = 720): Bitmap {
        val longSide = max(src.width, src.height)
        if (longSide <= maxLongSide) return src
        val scale = maxLongSide.toFloat() / longSide.toFloat()
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val out = Bitmap.createScaledBitmap(src, w, h, true)
        if (out !== src && !src.isRecycled) src.recycle()
        return out
    }

    /**
     * Crop to the detected Uber offer card (bottom sheet). Falls back to bottom ~2/3 if detection fails.
     * Sets [ocrCropTopPx] to the crop top used for tap mapping.
     */
    private fun cropOfferCardRegion(bitmap: Bitmap): Bitmap {
        val detected = detectOfferCardTopPx(bitmap)
        val fallback = (bitmap.height / 3).coerceIn(0, bitmap.height - 1)
        val top = if (detected >= 0) detected else fallback
        ocrCropTopPx = top
        val height = bitmap.height - top
        if (height <= 0) return bitmap
        val cropped = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
        if (cropped !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        Log.d("driverPRO-OCR", "offer-card crop top=$top/${bitmap.height} detected=$detected")
        return cropped
    }

    private fun runOCR(bitmap: Bitmap) {
        Log.d("MY-BROADCAST", "ocr real start")
        // Pause OCR while the driver is deciding on the confirm window.
        if (DriverAppAccessibilityService.isManualConfirmVisible) {
            if (!bitmap.isRecycled) {
                try {
                    bitmap.recycle()
                } catch (_: Exception) {
                }
            }
            return
        }
        screenCaptureWidth = bitmap.width
        screenCaptureHeight = bitmap.height
        val work = scaleDownForOcr(bitmap)
        if (!bitmap.isRecycled && bitmap !== work) {
            bitmap.recycle()
        }
        // Keep full-frame size for tap mapping; OCR runs on the offer-card crop only.
        ocrSourceWidth = work.width
        ocrSourceHeight = work.height
        val cardBitmap = try {
            cropOfferCardRegion(work)
        } catch (e: Exception) {
            Log.e("MY-BROADCAST", "OCR card crop failed", e)
            ocrCropTopPx = 0
            work
        }
        val preprocessed = try {
            increaseContrast(cardBitmap, contrast = 1.6f)
        } catch (e: Exception) {
            Log.e("MY-BROADCAST", "OCR preprocess failed", e)
            if (!cardBitmap.isRecycled) cardBitmap.recycle()
            return
        }
        if (!cardBitmap.isRecycled && cardBitmap !== preprocessed) cardBitmap.recycle()

        val image = InputImage.fromBitmap(preprocessed, 0)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                try {
                Log.d("MY-BROADCAST", "ocr end")
                val text = result.text
                val updatedText = fixWrongLetterToNumber(text)
                if (updatedText.isBlank()) {
                    ocrStabilityGate.reset()
                    lastText = null
                    sameRideIndex = 0
                    return@addOnSuccessListener
                }
                if (!isRideModal(updatedText)) {
                    ocrStabilityGate.reset()
                    lastText = text
                    Log.d("MY-BROADCAST", "parse error")
                    sameRideIndex = 0
                    return@addOnSuccessListener
                }
                // Do NOT reset the stability gate when raw OCR text flickers (map labels change).
                // Fingerprint matching already handles real offer changes; resetting here caused
                // permanent "Reading offer… (1/2)" loops and empty History.
                lastText = text

                Log.d("MY-BROADCAST", "parse end")
                val rideParsed = parseRideInfo(updatedText, result)
                val rideFromOcr = fillMissingTripMetrics(updatedText, rideParsed)
                // Accessibility tree fills gaps OCR missed (when Uber exposes text nodes).
                val a11yText = DriverAppAccessibilityService.snapshotOfferText().orEmpty()
                val ride = if (a11yText.isNotBlank()) {
                    mergeAccessibilityIntoRide(rideFromOcr, extractAccessibilityRideHints(a11yText))
                } else {
                    rideFromOcr
                }
                Log.d("MY-BROADCAST", ride.toString())

                if (isReserved(updatedText)) {
                    val intent = Intent("ACTION_CLICK_CONFIRM").apply {
                        putExtra("x", 0)
                        putExtra("y", 0)
                        putExtra("message", "Reserved")
                        putExtra("status", 0)
                        setPackage(applicationContext.packageName)
                    }
                    applicationContext.sendBroadcast(intent)
                    toastOnMain("Reserved")
                    return@addOnSuccessListener
                }

                if (lastRideRequest?.price == ride.price) {
                    if (ride.type == "") return@addOnSuccessListener
                    if (lastRideRequest?.type == "") return@addOnSuccessListener
                    if (lastRideRequest?.type == ride.type) return@addOnSuccessListener
                } else if (
                    lastRideRequest?.pickup_time_minutes == ride.pickup_time_minutes &&
                    lastRideRequest?.pickup_distance_value == ride.pickup_distance_value &&
                    lastRideRequest?.trip_time_minutes == ride.trip_time_minutes &&
                    lastRideRequest?.trip_distance_value == ride.trip_distance_value
                ) {
                    if (ride.type == "") return@addOnSuccessListener
                    if (lastRideRequest?.type == "") return@addOnSuccessListener
                    if (lastRideRequest?.type == ride.type) return@addOnSuccessListener
                }

                if (
                    lastRideRequest?.rating == ride.rating &&
                    lastRideRequest?.pickup_time_minutes == ride.pickup_time_minutes &&
                    lastRideRequest?.trip_time_minutes == ride.trip_time_minutes &&
                    lastRideRequest?.type == ride.type
                ) {
                    return@addOnSuccessListener
                }

                val imageUri = saveBitmapFromService(preprocessed)

                validateRideBeforeScoring(ride, updatedText)?.let { ocrError ->
                    Log.w("driverPRO-OCR", ocrError)
                    ocrStabilityGate.reset()
                    saveDebugOcrAttempt(ride, updatedText, imageUri?.toString(), ocrError)
                    val missing = listMissingRideFields(ride, updatedText)
                    reportNoScore(ocrError, missing)
                    requestManualConfirm(
                        title = "Unsure reading — decide manually",
                        detail = formatIncompleteOverlayMessage(ocrError, missing) +
                            "\n£${"%.2f".format(ride.price)} · " +
                            "${ride.pickup_address_postcode ?: "?"} → ${ride.dropoff_address_postcode ?: "?"}",
                        suggestedStatus = 0,
                        score = 0,
                    )
                    return@addOnSuccessListener
                }

                when (val gate = ocrStabilityGate.record(ride)) {
                    is StabilityGateResult.Incomplete -> return@addOnSuccessListener
                    is StabilityGateResult.Waiting -> {
                        reportReadingOffer(gate.consecutive, gate.required)
                        return@addOnSuccessListener
                    }
                    StabilityGateResult.Ready -> Unit
                }

                lastRideRequest = ride
                val currentTime = LocalTime.now()
                val pickupMinutes = ride.pickup_time_minutes!!
                val tripMinutes = ride.trip_time_minutes!!
                val workTime = pickupMinutes + tripMinutes

                ride.start_time_window = getTimeWindow(currentTime)
                ride.end_time_window = getTimeWindow(currentTime.plusMinutes(workTime.toLong()))
                ride.raw_text = updatedText
                ride.ocr_image_uri = imageUri?.toString().orEmpty()

                val jsonPayload = rideRequestHttpBody(ride)
                logRideRequestPayload(ride, jsonPayload)
                CoroutineScope(Dispatchers.IO).launch {

                    val jwtToken = getToken(applicationContext, "JWT_TOKEN")
                    val refreshToken = getToken(applicationContext, "REFRESH_TOKEN")

                    val responseResult = calculateRideRequest(
                        applicationContext,
                        jsonPayload,
                        jwtToken = jwtToken,
                        refreshToken = refreshToken,
                    )
                    val rideRequest: RideRequest? = responseResult.getOrNull()
                    if (rideRequest == null) {
                        val err = responseResult.exceptionOrNull()?.message ?: "unknown error"
                        Log.e("MY-BROADCAST", "ride scoring failed: $err")
                        reportNoScore(err)
                    } else {
                        val scored = rideRequest.copy(
                            raw_text = ride.raw_text,
                            ocr_image_uri = ride.ocr_image_uri,
                            accuracy = ride.accuracy,
                        )
                        val score = scored.final_score ?: 0
                        val lowConfidence = scored.accuracy < LOW_OCR_CONFIDENCE_THRESHOLD
                        val needsManualConfirm =
                            lowConfidence &&
                                (scored.acceptedOrRejected == 1 || scored.acceptedOrRejected == -1)

                        if (needsManualConfirm) {
                            val action = if (scored.acceptedOrRejected == 1) "Accept" else "Reject"
                            requestManualConfirm(
                                title = "Low confidence — confirm $action?",
                                detail = "Score: $score · OCR ${scored.accuracy}%\n" +
                                    "£${"%.2f".format(scored.price)} · " +
                                    "${scored.pickup_address_postcode ?: "?"} → " +
                                    "${scored.dropoff_address_postcode ?: "?"}\n" +
                                    "Suggested: $action",
                                suggestedStatus = scored.acceptedOrRejected,
                                score = score,
                            )
                            toastOnMain("Score: $score — waiting for your confirm")
                        } else if (scored.acceptedOrRejected == 1) {
                            val tapped = captureAndSendTap(
                                result,
                                acceptTapKeys,
                                score,
                                scored.acceptedOrRejected,
                            )
                            if (!tapped) {
                                sendAccessibilityFallbackTap(
                                    scored.acceptedOrRejected,
                                    score,
                                    "Score: $score — Accepted (finding button…)",
                                )
                                toastOnMain(
                                    "Score: $score — Accepted, tapping via accessibility",
                                )
                            }
                        } else if (scored.acceptedOrRejected == -1) {
                            val tapped = captureAndSendDismiss(
                                result,
                                score,
                                scored.acceptedOrRejected,
                            ) || captureAndSendTap(
                                result,
                                rejectTapKeys,
                                score,
                                scored.acceptedOrRejected,
                            )
                            if (!tapped) {
                                sendAccessibilityFallbackTap(
                                    scored.acceptedOrRejected,
                                    score,
                                    "Score: $score — Rejected (finding close…)",
                                )
                                toastOnMain(
                                    "Score: $score — Rejected, tapping via accessibility",
                                )
                            }
                        } else {
                            val intent = Intent("ACTION_CLICK_CONFIRM").apply {
                                putExtra("x", 0)
                                putExtra("y", 0)
                                putExtra("message", "Score: $score")
                                putExtra("status", scored.acceptedOrRejected)
                                setPackage(applicationContext.packageName)
                            }
                            applicationContext.sendBroadcast(intent)
                            toastOnMain("Score: $score")
                        }

                        saveNewRequest(applicationContext, "RIDE-REQUESTS", scored)
                        Log.d("MY-BROADCAST", "saved success:$scored")
                    }
                }
                } catch (e: Exception) {
                    Log.e("MY-BROADCAST", "OCR pipeline crashed", e)
                    ocrStabilityGate.reset()
                }
            }
            .addOnFailureListener {
                Log.e("OCR", "Error: ${it.message}")
            }
            .addOnCompleteListener {
                if (!preprocessed.isRecycled) {
                    try {
                        preprocessed.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
    }

    private val acceptButtonKeys = listOf("confirm", "match")
    private val closeButtonKeys = "x"
    private val timeKeys = listOf("min (", "mins (", "min(", "mins(")
    private val distanceKey = "mi)"
    private val farePattern = "[$£]\\d+(\\.\\d{1,2})?".toRegex()

    private fun isRideModal(text: String): Boolean {
        val lower = text.lowercase()
        val hasAcceptButton: Boolean = acceptButtonKeys.any { lower.contains(it) }
        if (!hasAcceptButton) {
            return false
        }
        // Batch / stacked offers often show "Match" without a visible "X" in the cropped region.
        val hasCloseButton: Boolean =
            lower.contains(closeButtonKeys) ||
                lower.contains("close") ||
                lower.contains("dismiss") ||
                lower.contains("cancel")
        // Accept "14 min (" or simply "12 min" / "12 mins" when miles appear (test apps, bad OCR).
        val hasTimeKey: Boolean = timeKeys.any { lower.contains(it) } ||
            Regex("\\b\\d{1,3}\\s*mins?\\b").containsMatchIn(lower)
        // "(4.0 miles)" uses the word "miles", not the substring "mi)".
        val hasDistanceKey: Boolean =
            lower.contains(distanceKey) ||
                Regex("\\bmiles?\\b").containsMatchIn(lower) ||
                Regex("\\d+\\.?\\d*\\s*miles?\\b").containsMatchIn(lower)
        val isPound = farePattern.containsMatchIn(lower)
        val looksLikeMatchOffer = lower.contains("match")
        val looksLikeConfirmOffer =
            lower.contains("confirm") || lower.contains("confirnm")

        // Original strict card: fare + time + distance + dismiss control.
        val strictCard =
            hasCloseButton && hasTimeKey && isPound && hasDistanceKey
        // Stacked match UI: times + miles, often no £ in the same OCR crop.
        val matchStackOffer =
            looksLikeMatchOffer && hasTimeKey && hasDistanceKey
        // Same idea for Confirm on mock/test screens (no £/X in crop).
        val confirmStackOffer =
            looksLikeConfirmOffer && hasTimeKey && hasDistanceKey

        return strictCard || matchStackOffer || confirmStackOffer
    }

    fun Rect.randomPoint(marginPercent: Double = 0.01): Pair<Int, Int> {
        val marginX = (width() * marginPercent).toInt()
        val marginY = (height() * marginPercent).toInt()

        val randX = (left + marginX) + (0..(width() - 2 * marginX)).random()
        val randY = (top + marginY) + (0..(height() - 2 * marginY)).random()

        return Pair(randX, randY)
    }

    /** Map OCR bounding-box point (preprocessed 2× crop image) to full-screen tap coordinates. */
    private fun mapOcrPointToScreen(ocrX: Int, ocrY: Int): Pair<Int, Int> {
        if (ocrSourceWidth <= 0 || ocrSourceHeight <= 0 || screenCaptureWidth <= 0) {
            return Pair(ocrX, ocrY)
        }
        val workX = ocrX
        val workY = ocrY + ocrCropTopPx
        val screenX = (workX.toLong() * screenCaptureWidth / ocrSourceWidth).toInt()
        val screenY = (workY.toLong() * screenCaptureHeight / ocrSourceHeight).toInt()
        return Pair(
            screenX.coerceIn(0, screenCaptureWidth - 1),
            screenY.coerceIn(0, screenCaptureHeight - 1),
        )
    }

    private var lastDebugSaveAt: Long = 0L
    private var lastDebugKey: String = ""

    private fun saveDebugOcrAttempt(
        ride: RideRequest,
        rawText: String,
        imageUri: String?,
        reason: String,
    ) {
        val now = System.currentTimeMillis()
        // Dedup by content, not a blanket time window: every DISTINCT failing offer is captured,
        // while the same offer lingering on screen won't create duplicate rows.
        val key = "$reason|${ride.price}|${ride.pickup_address_postcode}|" +
            "${ride.dropoff_address_postcode}|${ride.pickup_time_minutes}|${ride.trip_time_minutes}"
        if (key == lastDebugKey && now - lastDebugSaveAt < 15000L) return
        lastDebugKey = key
        lastDebugSaveAt = now
        val attempt = ride.copy(
            // Unique, always-recent id so debug entries never collapse in the storage Set and sort to the top.
            id = (now / 1000L).toInt(),
            acceptedOrRejected = 0,
            final_score = null,
            raw_text = "OCR debug: $reason\n${rawText.take(6000)}",
            ocr_image_uri = imageUri.orEmpty(),
            accuracy = 0,
            created_at = java.time.LocalDateTime.now().toString(),
        )
        saveNewRequest(applicationContext, "RIDE-REQUESTS", attempt)
        Log.d("MY-BROADCAST", "saved OCR debug attempt: $reason")
    }

    private fun reportReadingOffer(consecutive: Int, required: Int) {
        val now = System.currentTimeMillis()
        if (now - lastReadingOfferToastAt < 2500L) return
        lastReadingOfferToastAt = now
        val message = "Reading offer… ($consecutive/$required)"
        Log.d("MY-BROADCAST", message)
        applicationContext.sendBroadcast(
            Intent("ACTION_CLICK_CONFIRM").apply {
                putExtra("x", 0)
                putExtra("y", 0)
                putExtra("message", message)
                putExtra("status", 0)
                setPackage(applicationContext.packageName)
            },
        )
    }

    private fun reportNoScore(reason: String, missingFields: List<String> = emptyList()) {
        val message = formatIncompleteOverlayMessage(reason, missingFields)
        Log.d("MY-BROADCAST", message)
        // Top overlay only — Android Toast sits over the offer card and blocks drop-address OCR
        // on the next frame (self-inflicted "missing drop-off postcode" loops).
        applicationContext.sendBroadcast(
            Intent("ACTION_CLICK_CONFIRM").apply {
                putExtra("x", 0)
                putExtra("y", 0)
                putExtra("message", message)
                putExtra("status", 0)
                putExtra("top_only", true)
                putExtra("hold_ms", 4000)
                putStringArrayListExtra("missing_fields", ArrayList(missingFields))
                setPackage(applicationContext.packageName)
            },
        )
    }

    private fun requestManualConfirm(
        title: String,
        detail: String,
        suggestedStatus: Int,
        score: Int,
    ) {
        applicationContext.sendBroadcast(
            Intent(ACTION_SHOW_MANUAL_CONFIRM).apply {
                putExtra("title", title)
                putExtra("detail", detail)
                putExtra("suggested_status", suggestedStatus)
                putExtra("score", score)
                setPackage(applicationContext.packageName)
            },
        )
    }

    private fun sendTapBroadcast(screenX: Int, screenY: Int, score: Int, status: Int, message: String) {
        val intent = Intent("ACTION_CLICK_CONFIRM").apply {
            putExtra("x", screenX)
            putExtra("y", screenY)
            putExtra("score", score)
            putExtra("message", message)
            putExtra("status", status)
            setPackage(applicationContext.packageName)
        }
        applicationContext.sendBroadcast(intent)
    }

    private fun sendAccessibilityFallbackTap(status: Int, score: Int, message: String) {
        applicationContext.sendBroadcast(
            Intent(ACTION_A11Y_TAP_DECISION).apply {
                putExtra("status", status)
                putExtra("score", score)
                putExtra("message", message)
                setPackage(applicationContext.packageName)
            },
        )
        val overlay = Intent("ACTION_CLICK_CONFIRM").apply {
            putExtra("x", 0)
            putExtra("y", 0)
            putExtra("score", score)
            putExtra("message", message)
            putExtra("status", status)
            setPackage(applicationContext.packageName)
        }
        applicationContext.sendBroadcast(overlay)
    }

    private fun captureAndSendTap(
        visionText: Text,
        keys: List<String>,
        score: Int,
        status: Int,
    ): Boolean {
        for (key in keys) {
            if (captureAndSendConfirm(visionText, key, score, status)) return true
        }
        return false
    }

    private fun lineLooksLikeDismissLabel(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return false
        if (t.length == 1) {
            val ch = t[0]
            if (ch == 'x' || ch == 'X' || ch == '+' || ch == '×' || ch == '✕' || ch == '✖') {
                return true
            }
        }
        if (t.length > 8) return false
        val lower = t.lowercase()
        if (lower in setOf("x", "×", "✕", "✖", "+", "close")) return true
        return rejectTapKeys.any { lower == it || lower.contains(it) }
    }

    private data class DismissCandidate(val label: String, val rect: Rect)

    private fun collectDismissCandidates(visionText: Text): List<DismissCandidate> {
        val candidates = mutableListOf<DismissCandidate>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                if (lineLooksLikeDismissLabel(line.text)) {
                    line.boundingBox?.let { candidates.add(DismissCandidate(line.text, it)) }
                }
                for (element in line.elements) {
                    if (lineLooksLikeDismissLabel(element.text)) {
                        element.boundingBox?.let { candidates.add(DismissCandidate(element.text, it)) }
                    }
                }
            }
        }
        return candidates
    }

    /** Prefer the dismiss control near the top-right of the offer card (not the status bar). */
    private fun pickTopRightDismissCandidate(
        candidates: List<DismissCandidate>,
        card: OfferCardBounds,
    ): DismissCandidate? {
        if (candidates.isEmpty()) return null
        val upperBand = card.top + ((card.bottom - card.top).coerceAtLeast(1) * 0.28).toInt()
        val rightSide = candidates.filter { it.rect.right >= card.right * 0.55 }
        val pool = if (rightSide.isNotEmpty()) rightSide else candidates
        val inCardBand = pool.filter {
            it.rect.top >= card.top - 80 && it.rect.top <= upperBand + 40
        }
        val ranked = compareBy<DismissCandidate> { it.rect.right }.thenBy { -it.rect.top }
        return (if (inCardBand.isNotEmpty()) inCardBand else pool.filter {
            it.rect.top >= card.top - 40
        }).maxWithOrNull(ranked)
            ?: pool.maxWithOrNull(ranked)
    }

    private data class OfferCardBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * Approximate white offer-card bounds from fare / product / Confirm-Match lines.
     * Used so reject "X" taps land on the card, not the status-bar top-right.
     */
    private fun findOfferCardBounds(visionText: Text): OfferCardBounds? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = 0
        var bottom = 0
        var hits = 0
        fun consider(rect: Rect) {
            left = minOf(left, rect.left)
            top = minOf(top, rect.top)
            right = maxOf(right, rect.right)
            bottom = maxOf(bottom, rect.bottom)
            hits++
        }
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val t = line.text.trim()
                val bb = line.boundingBox ?: continue
                val lower = t.lowercase()
                val isCardAnchor =
                    t.contains('£') || t.contains('$') ||
                        lower.contains("confirm") || lower.contains("match") ||
                        lower.contains("uberx") || lower.contains("uberxl") ||
                        lower.contains("electric") || lower.contains("exclusive") ||
                        lower.contains("comfort") || lower.contains("priority") ||
                        Regex("""\d+\s*mins?\s*\(""", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
                        Regex("""\b[1-5]\.\d{1,2}\b""").containsMatchIn(t) &&
                        (t.contains('★') || t.contains('*') || lower.contains("verified"))
                if (isCardAnchor) consider(bb)
            }
        }
        if (hits < 2 || left == Int.MAX_VALUE || right <= 0) return null
        // Expand slightly upward for the X row above the fare / product chip.
        val padTop = ((bottom - top) * 0.08).toInt().coerceIn(24, 120)
        return OfferCardBounds(
            left = left,
            top = (top - padTop).coerceAtLeast(0),
            right = right,
            bottom = bottom,
        )
    }

    private fun ocrFrameBounds(visionText: Text): Pair<Int, Int> {
        var maxRight = 0
        var minTop = Int.MAX_VALUE
        for (block in visionText.textBlocks) {
            block.boundingBox?.let {
                maxRight = maxOf(maxRight, it.right)
                minTop = minOf(minTop, it.top)
            }
            for (line in block.lines) {
                line.boundingBox?.let {
                    maxRight = maxOf(maxRight, it.right)
                    minTop = minOf(minTop, it.top)
                }
            }
        }
        if (minTop == Int.MAX_VALUE) minTop = 0
        return maxRight to minTop
    }

    private fun sendDismissTapAtOcrRect(rect: Rect, label: String, score: Int, status: Int): Boolean {
        val tapX = (rect.left + rect.width() * 0.85).toInt().coerceIn(rect.left, rect.right)
        val tapY = (rect.top + rect.height() * 0.5).toInt().coerceIn(rect.top, rect.bottom)
        val (screenX, screenY) = mapOcrPointToScreen(tapX, tapY)
        Log.d("MY-BROADCAST", "Dismiss '$label' -> screen tap $screenX,$screenY (ocr $tapX,$tapY)")
        sendTapBroadcast(screenX, screenY, score, status, "Score: $score")
        toastOnMain("Score: $score")
        return true
    }

    /** Top-right close on Confirm-only cards when OCR misses the “X” glyph. */
    private fun captureAndSendTopRightFallbackDismiss(
        visionText: Text,
        score: Int,
        status: Int,
    ): Boolean {
        val card = findOfferCardBounds(visionText)
        val (frameRight, frameTop) = ocrFrameBounds(visionText)
        val right = card?.right?.takeIf { it > 0 } ?: frameRight
        if (right <= 0) return false
        // Anchor to offer-card top (not status-bar / map frameTop).
        val top = card?.top ?: (frameTop + (ocrSourceHeight * 0.35).toInt())
        val ocrX = (right * 0.93).toInt()
        val ocrY = (top + ((card?.let { it.bottom - it.top } ?: right) * 0.04).toInt())
            .coerceAtLeast(top + 8)
        val (screenX, screenY) = mapOcrPointToScreen(ocrX, ocrY)
        Log.d(
            "MY-BROADCAST",
            "Dismiss fallback card top-right -> screen tap $screenX,$screenY (ocr $ocrX,$ocrY card=$card)",
        )
        sendTapBroadcast(screenX, screenY, score, status, "Score: $score")
        toastOnMain("Score: $score")
        return true
    }

    /** Top-right close control is often a lone “X” / “×” with no word “close”. */
    private fun captureAndSendDismiss(visionText: Text, score: Int, status: Int): Boolean {
        val card = findOfferCardBounds(visionText)
            ?: OfferCardBounds(
                left = 0,
                top = (ocrSourceHeight * 0.35).toInt(),
                right = ocrFrameBounds(visionText).first,
                bottom = ocrSourceHeight,
            )
        val candidates = collectDismissCandidates(visionText)
        pickTopRightDismissCandidate(candidates, card)?.let { best ->
            return sendDismissTapAtOcrRect(best.rect, best.label, score, status)
        }
        return captureAndSendTopRightFallbackDismiss(visionText, score, status)
    }

    private fun captureAndSendConfirm(visionText: Text, key: String, score: Int, acceptOrReject: Int = -1): Boolean {
        val pattern = Regex("\\b${Regex.escape(key)}\\b", RegexOption.IGNORE_CASE)

        fun lineLooksLikeMatchLabel(raw: String): Boolean {
            if (!key.equals("match", ignoreCase = true)) return false
            val t = raw.lowercase()
            if (!t.contains("match")) return false
            if (raw.length > 32) return false
            if (Regex("matching|matched|matches").containsMatchIn(t)) return false
            return true
        }

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val text = line.text
                val hit = pattern.containsMatchIn(text) || lineLooksLikeMatchLabel(text)
                if (!hit) continue

                val rect: Rect = line.boundingBox ?: continue
                val (x0, y0) = rect.randomPoint()
                val (screenX, screenY) = mapOcrPointToScreen(x0, y0)

                Log.d(
                    "MY-BROADCAST",
                    "Match key='$key' on line='$text' -> screen tap $screenX,$screenY (ocr $x0,$y0)",
                )

                sendTapBroadcast(screenX, screenY, score, acceptOrReject, "Score: $score")
                toastOnMain("Score: $score")
                return true
            }
        }

        return false
    }


    override fun onDestroy() {
        super.onDestroy()

        try {
            sendBroadcast(
                Intent(ACTION_DRIVERPRO_CAPTURE_STOPPED).apply {
                    setPackage(packageName)
                },
            )
        } catch (_: Exception) {
        }

        releaseProjectionAndDisplay()

        handlerThread.quitSafely()
    }

    fun stopScreenCapture() {
        releaseProjectionAndDisplay()
    }


}