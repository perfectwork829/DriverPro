package com.driver.pro.service

import com.driver.pro.RideRequest
import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.min

internal fun String.fixOcrDigits(): String = this
    .replace('i', '1').replace('l', '1')
    .replace('I', '1').replace('L', '1')
    .replace('o', '0').replace('O', '0')

/** Character class for OCR-noisy digits in fares, miles, and postcodes. */
internal const val OCR_DIGIT = """[0-9ilLoO]"""

/** One OCR text line with vertical position (top = smaller Y). */
data class OcrLine(val text: String, val top: Int, val left: Int = 0)

internal data class TripLegParse(val minutes: Int, val miles: Double)

data class StructuredRideMetrics(
    val price: Double?,
    val rating: Double?,
    val pickupMinutes: Int?,
    val tripMinutes: Int?,
    val pickupMiles: Double?,
    val tripMiles: Double?,
    val pickupPostcode: String,
    val dropPostcode: String,
)

fun collectOcrLines(visionText: Text): List<OcrLine> {
    val lines = mutableListOf<OcrLine>()
    for (block in visionText.textBlocks) {
        for (line in block.lines) {
            val box = line.boundingBox ?: continue
            lines.add(OcrLine(line.text.trim(), box.top, box.left))
        }
    }
    return lines.sortedWith(compareBy({ it.top }, { it.left }))
}

/** OCR frame width from vision bounding boxes (for margin filtering). */
internal fun ocrFrameWidthFromLines(lines: List<OcrLine>): Int {
    return lines.maxOfOrNull { it.left + 80 } ?: 0
}

/**
 * Keep lines inside the white offer card (fare header through Confirm/Match button).
 * Always retains fare lines (£) even if they sit slightly above the computed card top.
 */
internal fun filterLinesToOfferCardZone(lines: List<OcrLine>): List<OcrLine> {
    if (lines.size < 3) return lines
    val legTops = lines.mapNotNull { line ->
        if (parseTripLegFromLine(line.text) != null) line.top else null
    }
    if (legTops.isEmpty()) return lines
    val cardTop = (legTops.minOrNull()!! - 280).coerceAtLeast(0)
    val actionBottom = lines
        .filter { line ->
            val t = line.text.lowercase()
            t.contains("confirm") || t.contains("match") || t.contains("confirnm")
        }
        .maxOfOrNull { it.top }
    val cardBottom = (actionBottom ?: lines.maxOf { it.top }) + 120
    return lines.filter { line ->
        line.top in cardTop..cardBottom ||
            line.text.contains('£') ||
            line.text.contains('$') ||
            parseFareFromLine(line.text) != null
    }
}

/**
 * Drop lines in far left/right margins where map road codes (M25, A41) usually OCR.
 * Keeps fare lines, addresses (comma), and trip-leg lines anywhere on screen.
 */
internal fun filterLinesExcludingMapMargins(lines: List<OcrLine>, frameWidth: Int): List<OcrLine> {
    if (frameWidth <= 0 || lines.size < 4) return lines
    val leftMargin = (frameWidth * 0.10).toInt()
    val rightMargin = (frameWidth * 0.90).toInt()
    return lines.filter { line ->
        val t = line.text
        if (parseTripLegFromLine(t) != null) return@filter true
        if (t.contains('£') || t.contains('$')) return@filter true
        if (t.contains(',')) return@filter true
        if (t.length > 28) return@filter true
        val centerX = line.left
        centerX in leftMargin..rightMargin
    }
}

internal fun filterOcrLinesForOfferCard(lines: List<OcrLine>): List<OcrLine> {
    if (lines.isEmpty()) return lines
    val frameWidth = ocrFrameWidthFromLines(lines)
    return filterLinesExcludingMapMargins(filterLinesToOfferCardZone(lines), frameWidth)
}

/** Parse using line order (pickup above drop) — most reliable for Driver app offer cards. */
fun parseStructuredFromLines(lines: List<OcrLine>, fullText: String): StructuredRideMetrics? {
    if (lines.isEmpty()) return null

    val cardScoped = filterLinesToOfferCardZone(lines)
    val workLines = if (cardScoped.size >= 4) cardScoped else lines

    val tripLegs = mutableListOf<Pair<Int, TripLegParse>>()
    workLines.forEachIndexed { index, line ->
        parseTripLegFromLine(line.text)?.let { tripLegs.add(index to it) }
    }
    if (tripLegs.isEmpty()) return null

    // Driver app cards occasionally OCR extra "min (mi)" noise; keep first two legs in document order.
    val orderedLegs = tripLegs.sortedBy { it.first }.take(2)
    val pickupLeg = if (orderedLegs.size >= 2) orderedLegs[0].second else null
    val tripLeg = if (orderedLegs.size >= 2) orderedLegs[1].second else null

    data class PostcodeHit(val lineIndex: Int, val code: String)

    fun collectPostcodeHits(startInclusive: Int, endExclusive: Int): List<PostcodeHit> {
        if (startInclusive >= endExclusive) return emptyList()
        val hits = mutableListOf<PostcodeHit>()
        for (i in startInclusive until endExclusive) {
            for (pc in extractOuterLondonPostcodes(workLines[i].text)) {
                hits.add(PostcodeHit(i, pc))
            }
        }
        return hits
    }

    fun chooseNearestCode(
        targetLine: Int,
        hits: List<PostcodeHit>,
        requiredMinLineExclusive: Int? = null,
        requiredMaxLineExclusive: Int? = null,
        bannedCodes: Set<String> = emptySet(),
    ): String {
        return hits
            .asSequence()
            .filter { it.code !in bannedCodes }
            .filter { requiredMinLineExclusive == null || it.lineIndex >= requiredMinLineExclusive }
            .filter { requiredMaxLineExclusive == null || it.lineIndex < requiredMaxLineExclusive }
            .sortedWith(compareBy<PostcodeHit>({ abs(it.lineIndex - targetLine) }, { it.lineIndex }))
            .map { it.code }
            .firstOrNull()
            .orEmpty()
    }

    var pickupPostcode = ""
    var dropPostcode = ""
    if (tripLegs.size >= 2) {
        val pickupLegIndex = orderedLegs[0].first
        val dropLegIndex = orderedLegs[1].first
        val hitWindowEnd = min(dropLegIndex + 8, workLines.size)
        val hits = collectPostcodeHits(pickupLegIndex.coerceAtMost(workLines.size), hitWindowEnd)

        val lineTexts = workLines.map { it.text }
        val dropAddressIdx = findDropAddressLineIndex(lineTexts, dropLegIndex)
        val pickupZoneEnd = findPickupZoneEnd(lineTexts, pickupLegIndex, dropLegIndex)
        pickupPostcode = pickBestPostcodeInLineRange(
            lineTexts,
            pickupLegIndex,
            pickupZoneEnd,
            minScore = 60,
        )
        if (pickupPostcode.isBlank() &&
            pickupZoneHasAddressWithoutPostcode(lineTexts, pickupLegIndex, dropLegIndex)
        ) {
            // Postcode-only line after pickup address — usually between legs (Uber wraps it).
            val betweenOrphan = ((pickupLegIndex + 1) until dropLegIndex).firstOrNull { idx ->
                idx < workLines.size && isFullPostcodeOnlyLine(workLines[idx].text)
            }
            val afterDropOrphan = (dropLegIndex + 1).takeIf { orphanIdx ->
                orphanIdx < workLines.size && isFullPostcodeOnlyLine(workLines[orphanIdx].text)
            }
            val orphanIdx = betweenOrphan ?: afterDropOrphan
            if (orphanIdx != null) {
                val orphan = extractOuterLondonPostcodes(workLines[orphanIdx].text).firstOrNull()
                val dropOnly = pickBestPostcodeInLineRange(
                    lineTexts,
                    maxOf(dropLegIndex + 1, orphanIdx + 1),
                    workLines.size,
                    minScore = 60,
                )
                if (orphan != null && (dropOnly.isBlank() || orphan != dropOnly ||
                        betweenOrphan != null)
                ) {
                    pickupPostcode = orphan
                }
            }
        }
        var dropZoneStart = dropAddressIdx.coerceAtLeast(dropLegIndex + 1)
        if (pickupPostcode.isNotBlank() && dropLegIndex + 1 < workLines.size &&
            isPostcodeOnlyLine(workLines[dropLegIndex + 1].text) &&
            extractOuterLondonPostcodes(workLines[dropLegIndex + 1].text).contains(pickupPostcode)
        ) {
            dropZoneStart = maxOf(dropZoneStart, dropLegIndex + 2)
        }
        dropPostcode = pickBestPostcodeInLineRange(
            lineTexts,
            dropZoneStart,
            workLines.size,
            minScore = 60,
            preferEarliest = false,
        )
        if (dropPostcode.isBlank()) {
            dropPostcode = pickBestPostcodeInLineRange(
                lineTexts,
                dropZoneStart,
                workLines.size,
                minScore = 0,
                preferEarliest = false,
            )
        }
        if (dropPostcode.isBlank()) {
            dropPostcode = chooseNearestCode(
                targetLine = dropLegIndex,
                hits = hits,
                requiredMinLineExclusive = dropLegIndex,
                bannedCodes = setOf(pickupPostcode).filter { it.isNotBlank() }.toSet(),
            )
        }
        if (dropPostcode.isBlank() && pickupPostcode.isNotBlank()) {
            val metricsDiffer = pickupLeg != null && tripLeg != null &&
                (pickupLeg.minutes != tripLeg.minutes ||
                    kotlin.math.abs(pickupLeg.miles - tripLeg.miles) > 0.05)
            if (!metricsDiffer) {
                for (line in workLines.subList(dropLegIndex, hitWindowEnd.coerceAtMost(workLines.size))) {
                    if (extractOuterLondonPostcodes(line.text).contains(pickupPostcode)) {
                        dropPostcode = pickupPostcode
                        break
                    }
                }
            }
        }

        // Last-resort fallback by leg zone (not blind global order).
        if (pickupPostcode.isBlank() && dropPostcode.isBlank()) {
            val (zonePickup, zoneDrop) = resolvePostcodesFromLegZones(fullText)
            pickupPostcode = zonePickup
            dropPostcode = zoneDrop
            if (pickupPostcode.isBlank() && dropPostcode.isBlank()) {
                val global = extractOuterLondonPostcodes(fullText)
                if (global.size >= 2) {
                    pickupPostcode = global[0]
                    dropPostcode = global[1]
                }
            }
        } else if (pickupPostcode.isBlank() && dropPostcode.isNotBlank()) {
            val (zonePickup, zoneDrop) = resolvePostcodesFromLegZones(fullText)
            if (zonePickup.isNotBlank()) {
                pickupPostcode = zonePickup
                if (zoneDrop.isNotBlank() && zoneDrop != zonePickup) {
                    dropPostcode = zoneDrop
                }
                // Same-district: keep existing dropPostcode when zones only found one code.
            }
        }

        // Visual Y only when addresses were OCR-reordered after the drop leg (both below),
        // or when leg zones left blank/duplicate. Do NOT overwrite a good between-legs pickup
        // (that caused CR0↔SE14 / CR2↔SE1 / W6↔SW1A swaps from map text boxes).
        val pickupFoundBetweenLegs = ((pickupLegIndex + 1) until dropLegIndex).any { idx ->
            idx < workLines.size &&
                pickBestPostcodeInLineRange(
                    listOf(workLines[idx].text), 0, 1, minScore = 60,
                ).isNotBlank()
        }
        val legZonesAlreadyGood = pickupFoundBetweenLegs &&
            pickupPostcode.isNotBlank() &&
            dropPostcode.isNotBlank() &&
            pickupPostcode != dropPostcode
        if (!legZonesAlreadyGood && (
                !pickupFoundBetweenLegs ||
                    pickupPostcode.isBlank() ||
                    dropPostcode.isBlank() ||
                    pickupPostcode == dropPostcode
                )
        ) {
            val addressesAfterDropLeg = ((dropLegIndex + 1) until workLines.size).count { idx ->
                val t = workLines[idx].text
                extractOuterLondonPostcodes(t).any { isValidUkOutward(it) } &&
                    (t.contains(',') || t.contains("London", ignoreCase = true) ||
                        Regex(
                            """\b(Road|Street|Hotel|Inn|Walk|Lane|Close|Gardens|Station|Grove|House|Avenue)\b""",
                            RegexOption.IGNORE_CASE,
                        ).containsMatchIn(t))
            }
            assignPostcodesByVisualTop(workLines, pickupLegIndex)?.let { (visualPickup, visualDrop) ->
                if (addressesAfterDropLeg >= 2) {
                    // Document order vs visual Y: use visual only when a later document line
                    // sits higher on screen (true OCR reorder, e.g. Whitton UB6 / Harrow HA2).
                    data class Addr(val index: Int, val top: Int, val code: String)
                    val addrs = mutableListOf<Addr>()
                    for (idx in (pickupLegIndex + 1) until workLines.size) {
                        val line = workLines[idx]
                        if (parseTripLegFromLine(line.text) != null) continue
                        val t = line.text
                        val addressLike = t.contains(',') ||
                            Regex(
                                """\b(Road|Street|Hotel|Inn|Walk|Lane|Close|Gardens|Station|Grove|House|Avenue|Drive)\b""",
                                RegexOption.IGNORE_CASE,
                            ).containsMatchIn(t)
                        if (!addressLike) continue
                        val pc = pickBestPostcodeInLineRange(listOf(t), 0, 1, minScore = 60)
                        if (pc.isNotBlank()) addrs.add(Addr(idx, line.top, pc))
                    }
                    val distinctDoc = addrs.distinctBy { it.code }
                    val byDoc = distinctDoc.sortedBy { it.index }
                    val ocrReordered = byDoc.size >= 2 && byDoc[1].top < byDoc[0].top - 5
                    if (ocrReordered) {
                        pickupPostcode = visualPickup
                        dropPostcode = visualDrop
                    } else if (byDoc.size >= 2) {
                        pickupPostcode = byDoc[0].code
                        dropPostcode = byDoc[1].code
                    } else {
                        pickupPostcode = visualPickup
                        dropPostcode = visualDrop
                    }
                } else {
                    // Never overwrite a leg-zone postcode with a map-label guess.
                    if (pickupPostcode.isBlank()) pickupPostcode = visualPickup
                    if (dropPostcode.isBlank()) dropPostcode = visualDrop
                    if (pickupPostcode == dropPostcode && visualPickup.isNotBlank() &&
                        visualDrop.isNotBlank() && visualPickup != visualDrop
                    ) {
                        pickupPostcode = visualPickup
                        dropPostcode = visualDrop
                    }
                }
            }
        }
    } else {
        val singleLegIndex = tripLegs[0].first
        val hits = collectPostcodeHits(
            (singleLegIndex + 1).coerceAtMost(workLines.size),
            min(singleLegIndex + 6, workLines.size),
        )
        pickupPostcode = chooseNearestCode(
            targetLine = singleLegIndex,
            hits = hits,
            requiredMinLineExclusive = singleLegIndex,
        )
        dropPostcode = chooseNearestCode(
            targetLine = singleLegIndex + 1,
            hits = hits,
            requiredMinLineExclusive = singleLegIndex,
            bannedCodes = setOf(pickupPostcode).filter { it.isNotBlank() }.toSet(),
        )
    }

    val firstLegIdx = orderedLegs[0].first
    val firstLegTop = workLines.getOrNull(firstLegIdx)?.top ?: Int.MAX_VALUE
    val headerLines = workLines
        .filter { it.top < firstLegTop - 8 }
        .ifEmpty { workLines.take(12) }
    val price = pickFareFromHeaderLines(headerLines, fullText)
    val rating = pickBestPassengerRating(headerLines, fullText)

    return StructuredRideMetrics(
        price = price,
        rating = rating,
        pickupMinutes = pickupLeg?.minutes,
        tripMinutes = tripLeg?.minutes,
        pickupMiles = pickupLeg?.miles,
        tripMiles = tripLeg?.miles,
        pickupPostcode = pickupPostcode,
        dropPostcode = dropPostcode,
    )
}

private fun appendPostcodesInOrder(lineText: String, into: MutableList<String>) {
    for (pc in extractOuterLondonPostcodes(lineText)) {
        if (pc !in into) into.add(pc)
    }
}

/** Typical Driver app offer speeds; used to fix 7.1 mi OCR'd as 71.0. */
internal fun isPlausibleMilesForMinutes(miles: Double, minutes: Int): Boolean {
    if (minutes <= 0) return miles in 0.1..200.0
    val mph = miles / (minutes / 60.0)
    return mph in 2.0..65.0
}

/**
 * Fix miles OCR: "7l.1" -> 71.1, "71.0" / "71" instead of 7.1 when trip time implies urban speeds.
 */
internal fun parseOcrMiles(raw: String, legMinutes: Int? = null): Double? {
    val cleaned = raw.replace(',', '.').fixOcrDigits()
    var value = cleaned.toDoubleOrNull() ?: return null

    // "7l.1" / "7i.1" → fixOcrDigits makes "71.1"; restore decimal (letters only — not "71.0").
    val rawNorm = raw.replace(',', '.')
    Regex("""^(\d)([ilILoO])\.(\d+)$""").find(rawNorm)?.let { m ->
        "${m.groupValues[1]}.${m.groupValues[2].fixOcrDigits()}".toDoubleOrNull()?.let { value = it }
    }
    Regex("""^(\d)([ilILoO])(\d+)$""").find(rawNorm)?.let { m ->
        "${m.groupValues[1]}.${m.groupValues[2].fixOcrDigits()}".toDoubleOrNull()?.let { value = it }
    }

    if (value >= 10.0) {
        val scaled = value / 10.0
        val minutes = legMinutes ?: 0
        val scaledOk = scaled in 0.2..200.0 &&
            (minutes <= 0 || isPlausibleMilesForMinutes(scaled, minutes))
        if (minutes > 0) {
            val mph = value / (minutes / 60.0)
            // 71.0 mi for ~54 min is almost always 7.1 mi with a spurious tens digit.
            if (mph > 55.0 && scaledOk) {
                value = scaled
                return value
            }
        }
        val rawOk = minutes > 0 && isPlausibleMilesForMinutes(value, minutes)
        if (scaledOk && !rawOk) value = scaled
    }

    // Short pickup/drop legs: "0.4 mi" often OCRs as "4.0 mi" (decimal shifted).
    // Do NOT scale "1.0 mi" on 3+ min legs — that is a normal urban pickup distance.
    if (legMinutes != null && legMinutes <= 15 && value in 1.0..9.9) {
        val scaled = value / 10.0
        val mphRaw = value / (legMinutes / 60.0)
        val looksLikeOneMile = value in 0.95..1.05 && legMinutes >= 3
        if (!looksLikeOneMile && mphRaw > 45.0 && scaled in 0.05..3.0 &&
            isPlausibleMilesForMinutes(scaled, legMinutes)
        ) {
            value = scaled
        }
    }

    // "(0.0 mi)" / "0.0" is almost always "1.0 mi" misread on a short pickup leg.
    if (legMinutes != null && legMinutes in 2..10 && value in 0.0..0.15) {
        val asOne = 1.0
        if (isPlausibleMilesForMinutes(asOne, legMinutes)) value = asOne
    }

    // REMOVED: "0.8 mi" → "0.3 mi" heuristic. Short pickups like "4 min (0.3 mi)"
    // are common and were wrongly forced to 0.8.

    // Pickup "0.2 mi" often OCRs as "0.7 mi" (2 misread as 7) on very short (≤2 min) legs only.
    if (legMinutes != null && legMinutes <= 2 && value in 0.65..0.75) {
        val asTwo = 0.2 + (value - 0.7)
        val mphHi = value / (legMinutes / 60.0)
        if (asTwo in 0.15..0.35 && mphHi >= 14.0 &&
            isPlausibleMilesForMinutes(asTwo, legMinutes)
        ) {
            value = asTwo
        }
    }

    // OCR often reads 8.2 as 3.2 on long trips (8 misread as 3) — before tens-digit upscale.
    if (legMinutes != null && legMinutes >= 35 && value in 3.0..3.9) {
        val mph = value / (legMinutes / 60.0)
        val asEight = 8.0 + (value - 3.0)
        if (mph < 8.0 && isPlausibleMilesForMinutes(asEight, legMinutes)) {
            value = asEight
        }
    }

    // Long trips: "27.8 mi" / "11.8 mi" often OCRs as "2.78" / "1.1" (tens digit / decimal lost).
    if (legMinutes != null && legMinutes >= 20 && value in 1.0..2.99) {
        val mphLo = value / (legMinutes / 60.0)
        val scaled = value * 10.0
        if (mphLo < 12.0 && scaled in 10.0..120.0 && isPlausibleMilesForMinutes(scaled, legMinutes)) {
            value = scaled
        }
    }

    // Trip "3.8 mi" often OCRs as "8.8 mi" (3 misread as 8) — run after 3.x→8.x upscale.
    if (legMinutes != null && legMinutes in 20..50 && value in 8.0..8.99) {
        val firstDecimal = kotlin.math.round(value * 10.0).toInt() % 10
        if (firstDecimal >= 8) {
            val alt = value - 5.0
            if (alt in 3.0..4.5 && isPlausibleMilesForMinutes(alt, legMinutes)) {
                value = alt
            }
        }
    }

    return value
}

/** e.g. "2 min (0.3 mi)", "26 mins (5.0 mi)", "1 hr 1 min (17.7 mi)", "6min l6 m)", "29 mins (94 mi)" */
internal fun parseTripLegFromLine(line: String): TripLegParse? {
    val originalLine = line
    var normalized = line
        .replace(',', '.') // "1,2 mi" / European decimals before other normalisations
        .replace(Regex("m[l1]n", RegexOption.IGNORE_CASE), "min")
        .replace(Regex("m[l1]\\)", RegexOption.IGNORE_CASE), "mi)")
        // "50 nmins (7.4 mi)" — stray letter between the number and "mins"
        .replace(Regex("""(\d)\s*[nr]\s?(mins?)\b""", RegexOption.IGNORE_CASE), "$1 $2")
        // "(0.8 mni)" / "(3.2 nmi)" / "0.8 mni" — "mi" misread as "mni"/"nmi"
        .replace(Regex("""(\d)\s*mni\b""", RegexOption.IGNORE_CASE), "$1 mi")
        .replace(Regex("""(\d)\s*nmi\b""", RegexOption.IGNORE_CASE), "$1 mi")
        // "1.l mi" — decimal digit OCR'd as letter l/I/O
        .replace(Regex("""(\d)\.([ilIoO])\s*mi""", RegexOption.IGNORE_CASE), "$1.1 mi")
        // Garbled "(l.0 mi)" / "(lỘ mi)" / "(1Ộ mi)" → "(1.0 mi)"
        .replace(Regex("""\([lI1]Ộ\s*mi""", RegexOption.IGNORE_CASE), "(1.0 mi")
        .replace(Regex("""\([lI1]\s*[.Oo0Ộ]\s*[0oO]\s*mi""", RegexOption.IGNORE_CASE), "(1.0 mi")
        .replace(Regex("""\([lI1][^0-9a-zA-Z]{0,3}[0oO]\s*mi""", RegexOption.IGNORE_CASE), "(1.0 mi")
        .replace(Regex("""\([lI1][^0-9a-zA-Z]{1,3}mi""", RegexOption.IGNORE_CASE), "(1.0 mi")
        // "7 min (.3 mi)" — leading 0 dropped before the decimal
        .replace(
            Regex("""\b(\d{1,3})\s*(mins?)\s+\(\.(\d+)""", RegexOption.IGNORE_CASE),
            "$1 $2 (0.$3",
        )
        // "7 min .4 m)" — "(1." lost; keep the leg so scoring is not blocked
        .replace(
            Regex("""\b(\d{1,3})\s*(mins?)\s+\.(\d)\s*m""", RegexOption.IGNORE_CASE),
            "$1 $2 (0.$3 m",
        )
        .replace(Regex("""^\s*([a-zA-Z])\s+(?=\d)"""), "")
        // "6min l6 m)" → "6 min (1.6 mi)" (l/1 is tens digit of 1.6 with lost decimal/paren)
        .replace(
            Regex("""\b(\d{1,3})\s*mins?\s+[l1](\d)\s*m\)?""", RegexOption.IGNORE_CASE),
            "$1 min (1.$2 mi)",
        )
        .replace(Regex("""(\d)\s*min\s*\("""), "$1 min (")
        .replace(Regex("""\(([oO])\.?(\d)"""), "(0.$2")
        .replace(Regex("""\(([oO])(\d)\s*m"""), "(0.$2 mi")
        .replace(Regex("""\b(\d+)\s*min\s+([oO])(\d)\s*m\)"""), "$1 min (0.$3 mi)")
        // "6min(1.6 mi)" without space
        .replace(Regex("""(\d)mins?\(""", RegexOption.IGNORE_CASE), "$1 min (")
        // Last: "(0.4 m)" → "(0.4 mi)" so the paren-recovered legs above still parse
        .replace(Regex("""(\d)\s*m\)"""), "$1 mi)")

    val match = Regex(
        """(?:([0-9ilILoO]{1,2})\s*h(?:ou)?r?s?\s*)?([0-9ilILoO]{1,3})\s*mins?\s*\(\s*([0-9ilILoO]+(?:\.[0-9ilILoO]+)?)\s*mi""",
        RegexOption.IGNORE_CASE,
    ).find(normalized) ?: return null

    val hours = match.groupValues[1].fixOcrDigits().toIntOrNull() ?: 0
    val mins = match.groupValues[2].fixOcrDigits().toIntOrNull() ?: return null
    val totalMinutes = if (hours > 0) hours * 60 + mins else mins
    var miles = parseOcrMiles(match.groupValues[3], totalMinutes) ?: return null
    // Prefer explicit 1.2/1,2 on the same leg line when OCR also invented 2.0.
    if (miles in 1.85..2.15 &&
        Regex("""1[.,]2""", RegexOption.IGNORE_CASE).containsMatchIn(originalLine)
    ) {
        miles = 1.2
    }
    // Only scale down very large miles when implausible (e.g. 71.0 → 7.1), not real long trips (27.7 mi).
    if (miles > 25.0 && totalMinutes > 0 && !isPlausibleMilesForMinutes(miles, totalMinutes)) {
        val scaled = miles / 10.0
        if (scaled in 0.2..200.0 && isPlausibleMilesForMinutes(scaled, totalMinutes)) {
            miles = scaled
        }
    }
    // "29 mins (94 mi)" — missing decimal (9.4) when mph is absurd for urban trip.
    if (totalMinutes in 15..60 && miles in 30.0..99.0 && !isPlausibleMilesForMinutes(miles, totalMinutes)) {
        val scaled = miles / 10.0
        if (isPlausibleMilesForMinutes(scaled, totalMinutes)) miles = scaled
    }
    // "13 mins (19 mi)" — missing decimal (1.9) on short central London hops.
    if (totalMinutes in 8..25 && miles in 10.0..25.0 && !isPlausibleMilesForMinutes(miles, totalMinutes)) {
        val scaled = miles / 10.0
        if (isPlausibleMilesForMinutes(scaled, totalMinutes)) miles = scaled
    }

    if (totalMinutes !in 1..600 || miles !in 0.0..200.0) return null
    return TripLegParse(totalMinutes, miles)
}

/** Priority/holiday add-on lines are not the main trip fare (e.g. "+£2.55 included for priority"). */
internal fun isAddonFareLine(line: String): Boolean {
    val lower = line.lowercase().trim()
    if (
        (lower.contains("included") && lower.contains("priority")) ||
        (lower.contains("est") && (lower.contains("holiday") || lower.contains("entitlement")))
    ) {
        return true
    }
    if (lower.contains("priority") && lower.contains('+')) return true
    if (Regex("""\+\s*[£$]""").containsMatchIn(line)) return true
    return false
}

/** ML Kit often misreads £ as E, F, or € on phone photos of the offer card. */
internal fun normalizeOcrCurrencyLine(line: String): String {
    var out = line.replace('€', '£')
    out = out.replace(Regex("""(^|[^A-Za-z])([EeFf])(?=\s*\d)""")) { "${it.groupValues[1]}£" }
    // "£l.83" / "£I.83" — leading digit of holiday/rating lines misread as £.
    out = out.replace(Regex("""£\s*[ilIL]\.""")) { "£1." }
    return out
}

internal fun lineHasCurrencySymbol(line: String): Boolean {
    val n = normalizeOcrCurrencyLine(line)
    return n.contains('£') || n.contains('$')
}

/**
 * Driver app offer card: fare is the large £ amount; "5.00" below it is passenger rating, not price.
 * Prefer £ lines, then reject rating-shaped decimals when a lower fare exists.
 */
internal fun pickFareFromHeaderLines(headerLines: List<OcrLine>, fullText: String): Double? {
    val headerText = headerLines.joinToString("\n") { it.text }
    val fromPoundLines = headerLines
        .filterNot { isAddonFareLine(it.text) }
        .mapNotNull { line ->
            val t = normalizeOcrCurrencyLine(line.text)
            if (lineHasCurrencySymbol(t)) parseFareFromLine(t) else null
        }
        .filter { it >= 3.0 }
    if (fromPoundLines.isNotEmpty()) return fromPoundLines.maxOrNull()

    val fares = headerLines
        .filterNot { isAddonFareLine(it.text) }
        .mapNotNull { parseFareFromLine(it.text) }
        .toMutableList()
    collectBareTripFares(headerText).forEach { fare ->
        if (fare !in fares) fares.add(fare)
    }
    if (fares.isEmpty()) return extractOfferFareFromText(fullText) ?: parseFareFromLine(fullText)

    val likelyTripFare = fares.filter { it >= 5.0 }
    if (likelyTripFare.isNotEmpty()) return likelyTripFare.maxOrNull()

    val fullTextFare = extractOfferFareFromText(fullText)
    if (fullTextFare != null && fullTextFare >= 5.0) return fullTextFare

    val likelyRatingBand = fares.filter { it in 4.0..5.05 }
    val likelyFare = fares.filter { it !in 4.0..5.05 }
    return when {
        likelyFare.isNotEmpty() -> likelyFare.maxOrNull()
        likelyRatingBand.size == 1 && fares.size == 1 -> null
        else -> fares.maxOrNull()
    }
}

/** Fare-sized bare decimals (e.g. 11.91) on Electric/Exclusive cards without a £ prefix. */
internal fun collectBareTripFares(text: String): List<Double> {
    val fares = mutableListOf<Double>()
    Regex("""(?m)(?<![\d.])(\d{1,2}\.\d{2})(?![\d.])""")
        .findAll(text)
        .forEach { match ->
            val lineStart = text.lastIndexOf('\n', match.range.first).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', match.range.first).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            if (isAddonFareLine(line)) return@forEach
            if (line.contains("est", ignoreCase = true) && line.contains("holiday", ignoreCase = true)) {
                return@forEach
            }
            // "★ 4.48" is the passenger rating, never a bare fare.
            if (line.contains('★') || line.contains('*') ||
                line.contains("Verified", ignoreCase = true)
            ) {
                return@forEach
            }
            match.groupValues[1].toDoubleOrNull()?.let { v ->
                if (v >= 5.0 && v !in 4.0..5.05) fares.add(v)
            }
        }
    return fares
}

internal fun extractOfferFareFromText(text: String): Double? {
    val amounts = extractPrice(text).toMutableList()
    collectBareTripFares(text).forEach { amounts.add(it) }
    Regex("""[£$]\s*(\d{2,3})(?!\d|[.,])""").findAll(text).forEach { m ->
        val raw = m.groupValues[1].fixOcrDigits().toIntOrNull() ?: return@forEach
        normalizeFareWithoutDecimal(raw.toDouble())?.let { amounts.add(it) }
    }
    val tripFares = amounts.filter { it >= 5.0 }
    if (tripFares.isNotEmpty()) return tripFares.maxOrNull()
    // Valid sub-£5 trip fares (e.g. £4.67 Electric) must not lose to the rating-band filter.
    val subFive = amounts.filter { it in 3.0..4.99 }
    if (subFive.isNotEmpty()) return subFive.maxOrNull()
    return amounts.filter { it !in 4.0..5.05 }.maxOrNull()
}

fun parseFareFromLine(line: String): Double? {
    if (line.contains('\n')) {
        val perLine = line.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseFareFromSingleLine(it) }
            .toList()
        if (perLine.isEmpty()) return null
        val fares = perLine.filter { it !in 4.0..5.05 }
        return fares.maxOrNull() ?: perLine.maxOrNull()
    }
    return parseFareFromSingleLine(line)
}

private fun parseFareFromSingleLine(rawLine: String): Double? {
    if (isAddonFareLine(rawLine)) return null
    val line = normalizeOcrCurrencyLine(rawLine)
    val lower = line.lowercase()

    val candidates = mutableListOf<Double>()

    // normalizeOcrCurrencyLine already turned genuine boundary E/F/€ into £, so match only £/$ here.
    // OCR_DIGIT allows £1l.98 → £11.98 (1 misread as lowercase L).
    Regex("""[£$]\s*($OCR_DIGIT{1,3})\.($OCR_DIGIT{1,2})\b""").findAll(line).forEach { m ->
        val whole = m.groupValues[1].fixOcrDigits()
        val frac = m.groupValues[2].fixOcrDigits()
        "${whole}.${frac}".toDoubleOrNull()?.let { candidates.add(it) }
    }

    Regex("""[£$]\s*($OCR_DIGIT{2,3})(?!\d|[.,])""").findAll(line).forEach { m ->
        val raw = m.groupValues[1].fixOcrDigits().toIntOrNull() ?: return@forEach
        normalizeFareWithoutDecimal(raw.toDouble())?.let { candidates.add(it) }
    }

    Regex("""(?<![\d.])(\d{1,2})\.(\d{2})(?![\d.])""").findAll(line).forEach { m ->
        if (line.contains("est", ignoreCase = true) && line.contains("holiday", ignoreCase = true)) {
            return@forEach
        }
        // Bare "5.00" without £ on offer cards is almost always passenger rating.
        if (!lineHasCurrencySymbol(line)) {
            val v = "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull() ?: return@forEach
            if (v in 4.5..5.05) return@forEach
        }
        val whole = m.groupValues[1]
        val frac = m.groupValues[2]
        "${whole}.${frac}".toDoubleOrNull()?.let { candidates.add(it) }
    }

    return candidates.filter { it in 3.0..500.0 }.maxOrNull()
}

/** £743 / 743 without decimal → £7.43 when in typical trip-fare range. */
fun normalizeFareWithoutDecimal(raw: Double): Double? {
    return when {
        raw in 100.0..999.0 -> raw / 100.0
        raw in 3.0..500.0 -> raw
        else -> null
    }
}

fun parseRatingFromLine(line: String): Double? {
    // ML Kit uses several star glyphs; normalise before pattern matching.
    var normalized = line.trim().replace(',', '.')
        .replace('☆', '*').replace('✦', '*').replace('✩', '*')
        .replace('⭐', '*').replace('★', '*')
    // Star/Verified-anchored rating wins even when OCR merges "★ 4.80 Verified" with the
    // "£0.43 est. holiday" line — otherwise the £/holiday rejection below loses the rating.
    Regex("""(?:★|\*)\s*([1-5])\.(\d{1,2})\b""").find(normalized)?.let { m ->
        val r = "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()
        if (r != null && r in 3.0..5.05) return r
    }
    Regex("""\b([1-5])\.(\d{1,2})\s*[^\d\n]{0,6}Verified""", RegexOption.IGNORE_CASE).find(normalized)?.let { m ->
        val r = "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()
        if (r != null && r in 3.0..5.05) return r
    }
    // "£9.82 4.44" / "£6.84 ★ 4.63" — fare and rating merged onto one OCR line.
    Regex("""[£$]\s*\d+[.,]\d{2}\s+(?:★|\*)?\s*([1-5])\.(\d{1,2})\b""").find(normalized)?.let { m ->
        val r = "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()
        if (r != null && r in 3.0..5.05) return r
    }
    // "4.44 £0.57 est. holiday" — rating leads the merged line.
    Regex("""^([1-5])\.(\d{1,2})\b""").find(normalized)?.let { m ->
        val r = "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()
        if (r != null && r in 3.0..5.05) return r
    }
    // OCR sometimes drops the decimal: "4 44" / "* 4 63".
    Regex("""(?:★|\*)?\s*([1-5])\s+(\d{2})\b""").find(normalized)?.let { m ->
        val r = "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()
        if (r != null && r in 3.0..5.05) return r
    }
    val lower = normalized.lowercase()
    if (lower.contains("min") || lower.contains(" mi") || lower.contains("mile")) return null
    if (lower.contains("£") || lower.contains('$') || lower.contains("est. holiday") ||
        lower.contains("entitlement") || lower.contains("payment")
    ) {
        return null
    }

    val patterns = listOf(
        Regex("""(?:★|\*|star)?\s*([1-5])[.,](\d{2})\b"""),
        Regex("""(?:★|\*|star)?\s*([1-5])[.,](\d)\b"""),
        Regex("""\b([1-5])[.,](\d{2})\s*(?:★|\*|verified)?""", RegexOption.IGNORE_CASE),
        Regex("""\b([1-5])[.,](\d)\s*(?:★|\*|verified)?""", RegexOption.IGNORE_CASE),
    )
    for (pattern in patterns) {
        pattern.find(normalized)?.let { m ->
            val whole = m.groupValues[1]
            val frac = m.groupValues[2]
            val r = "$whole.$frac".toDoubleOrNull()
            // Prefer two-decimal passenger ratings (4.65 / 4.66); still accept 4.5–5.0.
            if (r != null && r in 3.0..5.05) return r
        }
    }
    return null
}

fun pickBestPassengerRating(lines: List<OcrLine>, fullText: String): Double? {
    val candidates = mutableListOf<Double>()
    val verifiedCandidates = mutableListOf<Double>()

    fun collectFromLine(text: String) {
        parseRatingFromLine(text)?.let { r ->
            candidates.add(r)
            if (text.contains("Verified", ignoreCase = true) || text.contains('★') || text.contains('*')) {
                verifiedCandidates.add(r)
            }
        }
    }

    lines.forEach { line ->
        collectFromLine(line.text)
    }
    if (candidates.isEmpty()) {
        fullText.lineSequence().forEach { collectFromLine(it.trim()) }
    }
    if (candidates.isEmpty()) {
        // Rating line sits between the £ fare and the first trip leg on most cards.
        val fareThenRating = Regex(
            """[£$]\s*\d+[.,]\d{2}\s*\n\s*(?:★|\*|☆)?\s*([1-5])[.,\s](\d{1,2})""",
            RegexOption.MULTILINE,
        ).find(fullText)?.let { m ->
            "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()
        }
        if (fareThenRating != null && fareThenRating in 3.0..5.05) {
            candidates.add(fareThenRating)
        }
    }
    if (candidates.isEmpty()) {
        // "Cash payment ★ 5.00" / "Cash payment t 5.00" — payment line with rating.
        Regex(
            """(?:payment|Verified)[^\n]{0,20}(?:★|\*|☆|t)?\s*([4-5])[.,](\d{2})\b""",
            RegexOption.IGNORE_CASE,
        ).find(fullText)?.let { m ->
            "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()?.let {
                if (it in 4.0..5.05) candidates.add(it)
            }
        }
    }
    if (candidates.isEmpty()) {
        Regex("""\b([4-5])[.,](\d{2})\b""").findAll(fullText).forEach { m ->
            val lineStart = fullText.lastIndexOf('\n', m.range.first).let { if (it < 0) 0 else it + 1 }
            val lineEnd = fullText.indexOf('\n', m.range.first).let { if (it < 0) fullText.length else it }
            val line = fullText.substring(lineStart, lineEnd)
            if (line.contains('£') || line.contains('$') || line.contains("mi", ignoreCase = true)) {
                return@forEach
            }
            // Trip distance "3.50" / "3.5" must not become the passenger rating.
            if (line.contains("min", ignoreCase = true)) return@forEach
            "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()?.let { candidates.add(it) }
        }
    }
    if (candidates.isEmpty()) return null

    val prefer = if (verifiedCandidates.isNotEmpty()) verifiedCandidates else candidates
    val ratingBand = prefer.filter { it in 4.0..5.05 }
    // Prefer two-decimal ratings that actually appear in OCR (4.71 over 4.7 / 4.70).
    // Avoid float traps like (4.71 * 100).toInt() == 470.
    val twoDecFromText = mutableListOf<Double>()
    // Use [ \t]* not \s* — \s matches newlines and made match.range start on '\n' (crash).
    Regex("""(?:★|\*|☆)?[ \t]*([4-5])[.,](\d{2})\b""").findAll(fullText).forEach { m ->
        val at = m.range.first.coerceIn(0, (fullText.length - 1).coerceAtLeast(0))
        val lineStart = fullText.lastIndexOf('\n', at).let { if (it < 0) 0 else it + 1 }
        val lineEnd = fullText.indexOf('\n', at).let { if (it < 0) fullText.length else it }
        if (lineEnd <= lineStart) return@forEach
        val line = fullText.substring(lineStart, lineEnd)
        if (isAddonFareLine(line)) return@forEach
        if (line.contains('£') || line.contains('$')) return@forEach
        if (line.contains("mi", ignoreCase = true) || line.contains("min", ignoreCase = true)) return@forEach
        val r = "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull() ?: return@forEach
        if (r in 4.0..5.05) twoDecFromText.add(r)
    }
    // Whole-pound fare cards (£37): rating often sits alone under the fare.
    if (twoDecFromText.isEmpty()) {
        Regex("""[£$]\s*\d{1,3}(?![.,\d])\s*\n\s*(?:★|\*|☆)?\s*([4-5])[.,](\d{2})\b""")
            .find(fullText)?.let { m ->
                "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull()?.let {
                    if (it in 4.0..5.05) twoDecFromText.add(it)
                }
            }
    }
    val twoDec = when {
        twoDecFromText.isNotEmpty() -> twoDecFromText
        else -> ratingBand.filter { r ->
            val cents = kotlin.math.round(r * 100.0).toInt()
            cents % 10 != 0
        }
    }
    val pool = when {
        twoDec.isNotEmpty() -> twoDec
        ratingBand.isNotEmpty() -> ratingBand
        // Never promote trip-distance "3.50" to a passenger rating — require 4.0+.
        else -> prefer.filter { it in 4.0..5.05 }
    }
    var best = pool.maxOrNull() ?: return null
    // OCR often drops the last digit ("4.7" for 4.71). Upgrade when the full form exists.
    best = upgradeTruncatedRating(best, fullText)
    return best
}

/** If we only have 4.7 / 4.70 but OCR also has 4.71, prefer the two-decimal form. */
internal fun upgradeTruncatedRating(rating: Double, fullText: String): Double {
    val cents = kotlin.math.round(rating * 100.0).toInt()
    if (cents % 10 != 0) return rating
    val whole = cents / 100
    val tenths = (cents / 10) % 10
    if (whole !in 4..5) return rating
    Regex("""\b$whole\.$tenths([1-9])\b""").find(fullText)?.let { m ->
        val upgraded = "$whole.$tenths${m.groupValues[1]}".toDoubleOrNull()
        if (upgraded != null && upgraded in 4.0..5.05) return upgraded
    }
    return rating
}

/** Ordered (minutes, miles) pairs from "N min (X mi)" lines in document order. */
internal fun extractTripLegPairsInOrder(ocr: String): List<Pair<Int, Double>> {
    val legs = mutableListOf<Pair<Int, Double>>()
    ocr.lineSequence().forEach { line ->
        parseTripLegFromLine(line.trim())?.let { leg ->
            legs.add(leg.minutes to leg.miles)
        }
    }
    return legs
}

/** UK outward code shape: 1–2 letters + 1–2 digits + optional letter (e.g. GU1, GU21, KT5, SW1A). */
/** Real UK postcode areas — map words like "Hill"→HI11 or "NOR"→N0R must never become outwards. */
internal val UK_POSTCODE_AREAS = setOf(
    "AB", "AL", "B", "BA", "BB", "BD", "BH", "BL", "BN", "BR", "BS", "BT",
    "CA", "CB", "CF", "CH", "CM", "CO", "CR", "CT", "CV", "CW",
    "DA", "DD", "DE", "DG", "DH", "DL", "DN", "DT", "DY",
    "E", "EC", "EH", "EN", "EX", "FK", "FY", "G", "GL", "GU",
    "HA", "HD", "HG", "HP", "HR", "HS", "HU", "HX", "IG", "IP", "IV",
    "KA", "KT", "KW", "KY", "L", "LA", "LD", "LE", "LL", "LN", "LS", "LU",
    "M", "ME", "MK", "ML", "N", "NE", "NG", "NN", "NP", "NR", "NW",
    "OL", "OX", "PA", "PE", "PH", "PL", "PO", "PR",
    "RG", "RH", "RM",
    "S", "SE", "SG", "SK", "SL", "SM", "SN", "SO", "SP", "SR", "SS", "ST", "SW", "SY",
    "TA", "TD", "TF", "TN", "TQ", "TR", "TS", "TW", "UB",
    "W", "WA", "WC", "WD", "WF", "WN", "WR", "WS", "WV", "YO", "ZE",
)

internal fun isValidUkOutward(code: String): Boolean {
    val upper = code.uppercase()
    val m = Regex("""^([A-Z]{1,2})(\d{1,2})([A-Z]?)$""").find(upper) ?: return false
    val letters = m.groupValues[1]
    val digits = m.groupValues[2]
    val trailing = m.groupValues[3]
    // "HI11" from map label "Hill" — the area must be a real UK postcode area.
    if (letters !in UK_POSTCODE_AREAS) return false
    // District 0 only exists in Croydon (CR0) — "NOR" text must not become N0R.
    if (digits.toIntOrNull() == 0 && letters != "CR") return false
    // CR0S / BR1A-style fakes — trailing letter only for central sectors.
    if (trailing.isNotEmpty() && letters !in setOf("E", "EC", "N", "NW", "SE", "SW", "W", "WC")) return false
    return true
}

/** True when [outward] appears as its own district token — GU2 must not match inside GU21.
 *  Also accepts OCR digit letters: SEl→SE1, El→E1, ECIN→EC1N.
 */
internal fun lineContainsOutwardStrict(line: String, outward: String): Boolean {
    if (!isValidUkOutward(outward)) return false
    val letters = outward.takeWhile { it.isLetter() }
    val district = outward.drop(letters.length)
    if (letters.isEmpty() || district.isEmpty()) return false
    if (Regex(
            """\b${Regex.escape(letters)}${Regex.escape(district)}(?!\d)""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(line)
    ) {
        return true
    }
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
    ).containsMatchIn(line)
}

internal fun lineHasFullInwardStrict(line: String, outward: String): Boolean {
    if (!lineContainsOutwardStrict(line, outward)) return false
    val letters = outward.takeWhile { it.isLetter() }
    val district = outward.drop(letters.length)
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
        """\b${Regex.escape(letters)}(?:${Regex.escape(district)}|$districtPat)(?!\d)\s+[0-9oO][A-Za-z]{2}\b""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(line)
}

internal fun lineHasTruncatedInwardStrict(line: String, outward: String): Boolean {
    if (!lineContainsOutwardStrict(line, outward)) return false
    val letters = outward.takeWhile { it.isLetter() }
    val district = outward.drop(letters.length)
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
        """\b${Regex.escape(letters)}(?:${Regex.escape(district)}|$districtPat)(?!\d)\s+[0-9oO](?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(line)
}

/** Bare map tokens like "E15" floating near City cards — not offer-card addresses. */
internal fun lineLooksLikeBareEastMapPostcode(line: String): Boolean {
    val t = line.trim()
    if (t.contains(',') || t.length > 8) return false
    if (t.contains("London", ignoreCase = true)) return false
    return Regex("""^E1[4-9]\s*$|^E20\s*$""", RegexOption.IGNORE_CASE).matches(t)
}

/** Prefer earliest line for pickup, latest for drop-off when scores tie. */
internal fun pickBestPostcodeInLineRange(
    lines: List<String>,
    startInclusive: Int,
    endExclusive: Int,
    minScore: Int = 0,
    preferEarliest: Boolean = true,
): String {
    data class Candidate(val lineIndex: Int, val code: String, val score: Int)

    val candidates = mutableListOf<Candidate>()
    val inwardOnly = Regex("""^\s*[0-9oO][A-Za-z]{2}\s*$""", RegexOption.IGNORE_CASE)
    val wrappedDistrict = Regex("""^\s*[0-9]\s*[0-9oO][A-Za-z]{2}\s*$""", RegexOption.IGNORE_CASE)
    for (i in startInclusive until endExclusive.coerceAtMost(lines.size)) {
        val line = lines[i]
        val next = lines.getOrNull(i + 1)?.trim().orEmpty()
        // Include next-line inward so "London, NW4"+"4XW" / "SW1E"+"6LB" / "NW1"+"1 8AT" score.
        val combined = when {
            inwardOnly.matches(next) || wrappedDistrict.matches(next) ->
                joinSplitPostcodeLines("$line\n$next").lineSequence().firstOrNull()?.trim() ?: line
            else -> line
        }
        for (pc in extractOuterLondonPostcodes(combined)) {
            if (!isValidUkOutward(pc)) continue
            val hasFullInward = lineHasFullInwardStrict(combined, pc)
            val hasTruncatedInward = !hasFullInward && lineHasTruncatedInwardStrict(combined, pc)
            val looksLikeAddress = combined.contains(',') || combined.length > 25
            val recoveredFullOnAddress = lineHasRecoveredFullPostcodeOnAddress(combined, pc)
            val score = when {
                hasFullInward || recoveredFullOnAddress -> 100
                hasTruncatedInward -> 80
                isFullPostcodeOnlyLine(combined) -> 95
                looksLikeAddress && lineContainsOutwardStrict(combined, pc) -> 60
                // Truncated "SW7 2" / "W8 4" / "W4 1" on short address lines without comma
                Regex(
                    """\b${Regex.escape(pc)}\s+\d(?![A-Za-z0-9])""",
                    RegexOption.IGNORE_CASE,
                ).containsMatchIn(combined) -> 80
                // Postcode-only truncated line: "W4 1"
                Regex(
                    """^\s*${Regex.escape(pc)}\s+\d\s*$""",
                    RegexOption.IGNORE_CASE,
                ).matches(combined) -> 85
                else -> 10
            }
            if (score >= minScore) {
                candidates.add(Candidate(i, pc, score))
            }
        }
    }
    // Prefer longer outwards when both NW1 and NW11 scored (NW11 wins).
    val filtered = candidates.filter { c ->
        candidates.none { o ->
            o.code != c.code && o.code.startsWith(c.code) && o.code.length > c.code.length &&
                o.score >= c.score
        }
    }
    val comparator = if (preferEarliest) {
        compareBy<Candidate> { it.score }.thenBy { it.code.length }.thenBy { it.lineIndex }
    } else {
        compareBy<Candidate> { it.score }.thenBy { it.code.length }.thenByDescending { it.lineIndex }
    }
    val best = filtered.maxWithOrNull(comparator) ?: return ""

    val fullInZone = filtered.filter { it.score >= 80 }
    if (best.score < 80 && fullInZone.isNotEmpty()) {
        return fullInZone.maxWithOrNull(comparator)!!.code
    }
    return best.code
}

internal fun lineHasRecoveredFullPostcodeOnAddress(line: String, outward: String): Boolean {
    val looksLikeAddress = line.contains(',') || line.length > 25
    return looksLikeAddress &&
        Regex("""\b[A-Za-z]{1,2}\d[A-Za-z0-9]?\s+\d[A-Za-z]{2}\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(line) &&
        extractOuterLondonPostcodes(line).contains(outward)
}

/** M25 / A41 map labels on the Driver app map — not drop-off postcodes. WD25 is often M25 misread. */
internal fun lineLooksLikeMotorwayMapLabel(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty()) return false
    if (Regex("""^[AM]\d{1,3}\s*$""", RegexOption.IGNORE_CASE).matches(t)) return true
    val compact = t.replace(" ", "").uppercase()
    if (compact == "WD25" || compact == "M25") return true
    if (compact.contains("M25") && !t.contains(',')) return true
    if (Regex("""\b[AM]\d{1,3}\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) &&
        !t.contains(',') && t.length <= 14 &&
        !Regex(
            """\b(Road|Street|St|Ave|Lane|Way|Drive|London|Station)\b""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(t)
    ) {
        return true
    }
    return false
}

internal fun isMotorwayDistrictOutwardToken(prefix: String, districtNum: Int, sourceLine: String): Boolean {
    if (prefix.equals("M", ignoreCase = true)) return true
    if (prefix.equals("A", ignoreCase = true) && prefix.length == 1) return true
    if (prefix.equals("WD", ignoreCase = true) && districtNum == 25 &&
        lineLooksLikeMotorwayMapLabel(sourceLine)
    ) {
        return true
    }
    return false
}

internal fun lineQualifiesAsDropAddressLine(line: String): Boolean {
    if (lineLooksLikeMotorwayMapLabel(line)) return false
    val pcs = extractOuterLondonPostcodes(line).filter { isValidUkOutward(it) }
    if (pcs.isEmpty()) return false
    val hasFullInward = pcs.any { lineHasFullInwardStrict(line, it) }
    val hasTruncatedInward = pcs.any { lineHasTruncatedInwardStrict(line, it) }
    val looksLikeAddress = line.contains(',') || line.length > 25
    val recoveredOnAddress = pcs.any { lineHasRecoveredFullPostcodeOnAddress(line, it) }
    val outwardOnAddress = looksLikeAddress && pcs.any { lineContainsOutwardStrict(line, it) }
    return hasFullInward || hasTruncatedInward || recoveredOnAddress || outwardOnAddress
}

/**
 * True when OCR shows a postcode/address line after the second trip-leg time line.
 * Uber sometimes shows only "10 mins (3.1 mi)" with no drop address — then this is false.
 */
fun ocrHasDropAddressAfterTripLeg(ocrText: String): Boolean {
    val lines = ocrText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val legIndices = lines.mapIndexedNotNull { index, line ->
        if (parseTripLegFromLine(line) != null) index else null
    }
    if (legIndices.size < 2) return false
    for (i in (legIndices[1] + 1) until lines.size) {
        if (lineQualifiesAsDropAddressLine(lines[i])) return true
    }
    return false
}

/** Uber sometimes shows pickup as "Alma Rd, London" with no postcode on the offer card. */
fun ocrHasPickupAddressWithoutPostcode(ocrText: String): Boolean {
    val lines = ocrText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val legIndices = lines.mapIndexedNotNull { index, line ->
        if (parseTripLegFromLine(line) != null) index else null
    }
    if (legIndices.size < 2) return false
    return pickupZoneHasAddressWithoutPostcode(lines, legIndices[0], legIndices[1])
}

/**
 * Drop shows a street ("Keats Cl. Enfield.") but OCR missed/split the postcode.
 * Used to avoid blocking scoring when the address is clearly on the card.
 */
fun ocrHasDropAddressStreetWithoutPostcode(ocrText: String): Boolean {
    val lines = ocrText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val legIndices = lines.mapIndexedNotNull { index, line ->
        if (parseTripLegFromLine(line) != null) index else null
    }
    if (legIndices.size < 2) return false

    fun lineIsStreetWithoutPostcode(line: String): Boolean {
        if (lineLooksLikeMotorwayMapLabel(line)) return false
        if (line.equals("Match", ignoreCase = true) || line.equals("Confirm", ignoreCase = true)) {
            return false
        }
        if (extractOuterLondonPostcodes(line).isNotEmpty()) return false
        if (line.equals("London", ignoreCase = true) || line.equals("London.", ignoreCase = true)) {
            return true
        }
        val looksLikeAddress = line.contains(',') ||
            Regex(
                """\b(St|Street|Road|Rd|Ave|Cl|Close|Lane|Way|Drive|Dr|London|Enfield|Lodge|Hotel|Square|Palace|Garden)\b""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(line)
        return looksLikeAddress && line.length >= 8
    }

    // After the drop leg (normal card layout).
    for (i in (legIndices[1] + 1) until lines.size) {
        if (lineIsStreetWithoutPostcode(lines[i])) return true
    }
    // OCR sometimes puts the drop street *between* legs (before pickup address with a PC).
    val between = ((legIndices[0] + 1) until legIndices[1]).map { lines[it] }
    val streetNoPc = between.any { lineIsStreetWithoutPostcode(it) }
    val streetWithPc = between.any {
        extractOuterLondonPostcodes(it).isNotEmpty() &&
            (it.contains(',') || it.contains("London", ignoreCase = true))
    }
    val dropAfterHasPc = ((legIndices[1] + 1) until lines.size).any { idx ->
        lineQualifiesAsDropAddressLine(lines[idx])
    }
    if (streetNoPc && streetWithPc && !dropAfterHasPc) return true
    return false
}

/** Full-postcode lines (address or postcode-only) after the drop leg, in document order. */
internal fun fullPostcodeLineIndicesAfter(lines: List<String>, dropLegIdx: Int): List<Int> {
    val indices = mutableListOf<Int>()
    for (i in (dropLegIdx + 1) until lines.size) {
        val line = lines[i]
        if (lineLooksLikeMotorwayMapLabel(line)) continue
        val pcs = extractOuterLondonPostcodes(line).filter { isValidUkOutward(it) }
        if (pcs.isEmpty()) continue
        val hasFullInward = pcs.any { lineHasFullInwardStrict(line, it) }
        val hasTruncatedInward = pcs.any { lineHasTruncatedInwardStrict(line, it) }
        val looksLikeAddress = line.contains(',') || line.length > 25
        val recoveredOnAddress = pcs.any { lineHasRecoveredFullPostcodeOnAddress(line, it) }
        val outwardOnAddress = looksLikeAddress && pcs.any { lineContainsOutwardStrict(line, it) }
        if (hasFullInward || hasTruncatedInward || recoveredOnAddress || outwardOnAddress ||
            isPostcodeOnlyLine(line)
        ) {
            indices.add(i)
        }
    }
    return indices
}

/**
 * Pickup/drop postcodes by on-screen Y position (top). Fixes swapped assignments when
 * OCR line order differs from the Driver app layout.
 */
internal fun assignPostcodesByVisualTop(lines: List<OcrLine>, pickupLegIdx: Int): Pair<String, String>? {
    data class Hit(val top: Int, val code: String, val addressLike: Boolean)
    val hits = mutableListOf<Hit>()
    for (i in (pickupLegIdx + 1) until lines.size) {
        val line = lines[i]
        if (parseTripLegFromLine(line.text) != null) continue
        // Map / notification tokens (bare "SW1A", "Pol") must not beat real address lines.
        val addressLike = line.text.contains(',') ||
            line.text.contains("London", ignoreCase = true) ||
            Regex(
                """\b(Road|Street|St|Ave|Lane|Way|Drive|Hotel|Inn|Walk|Close|Cl|Gardens|Station)\b""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(line.text)
        val pc = pickBestPostcodeInLineRange(listOf(line.text), 0, 1, minScore = 60)
        if (pc.isNotBlank()) {
            hits.add(Hit(line.top, pc, addressLike))
        }
    }
    val prefer = hits.filter { it.addressLike }.ifEmpty { hits }
    val distinct = prefer.distinctBy { it.code }.sortedBy { it.top }
    if (distinct.size < 2) return null
    if (distinct.last().top - distinct.first().top < 5) return null
    return distinct[0].code to distinct[1].code
}

/** Drop-off address line — last full postcode after the drop leg when pickup address OCRs in between. */
internal fun findDropAddressLineIndex(lines: List<String>, dropLegIdx: Int): Int {
    return fullPostcodeLineIndicesAfter(lines, dropLegIdx).lastOrNull() ?: lines.size
}

/** Pickup zone ends at drop leg when pickup address is above drop leg; else extends for reordered OCR. */
internal fun findPickupZoneEnd(lines: List<String>, pickupLegIdx: Int, dropLegIdx: Int): Int {
    for (i in (pickupLegIdx + 1) until dropLegIdx.coerceAtMost(lines.size)) {
        val line = lines[i]
        val pcs = extractOuterLondonPostcodes(line).filter { isValidUkOutward(it) }
        if (pcs.any { pc ->
                lineHasFullInwardStrict(line, pc) ||
                    lineHasTruncatedInwardStrict(line, pc) ||
                    lineHasRecoveredFullPostcodeOnAddress(line, pc) ||
                    isFullPostcodeOnlyLine(line) ||
                    (line.contains(',') && lineContainsOutwardStrict(line, pc))
            }
        ) {
            return dropLegIdx
        }
    }
    return findDropAddressLineIndex(lines, dropLegIdx).coerceAtLeast(dropLegIdx + 1)
}

internal fun isPostcodeOnlyLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.contains(',')) return false
    if (Regex("""\b(St|Street|Road|Rd|Ave|Avenue|London|Lane|Way|Drive|Dr)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(trimmed)
    ) {
        return false
    }
    val pcs = extractOuterLondonPostcodes(trimmed)
    return pcs.size == 1 && trimmed.length <= 18
}

/** Postcode-only line that includes an inward (e.g. "SEl 7EH", "SL41LH") — not bare "W1K"/"SW1W". */
internal fun isFullPostcodeOnlyLine(line: String): Boolean {
    if (!isPostcodeOnlyLine(line)) return false
    val trimmed = line.trim()
    return Regex(
        """^[A-Za-z]{1,2}[0-9iIlLoO]{1,2}\s*[0-9oO][A-Za-z]{2}$""",
        RegexOption.IGNORE_CASE,
    ).matches(trimmed)
}

internal fun pickupZoneHasAddressWithoutPostcode(
    lines: List<String>,
    pickupLegIdx: Int,
    dropLegIdx: Int,
): Boolean {
    for (i in (pickupLegIdx + 1) until dropLegIdx.coerceAtMost(lines.size)) {
        val line = lines[i]
        val looksLikeAddress = line.contains(',') ||
            Regex("""\b(St|Street|Road|Rd|Ave|London|Lane|Way|Drive|Dr|Baker|Bayswater|Hospital|Hotel|Academy|Studios)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(line)
        // "Pick-up point" / hotel name lines often have no postcode; next line is the outward.
        val pickUpPointLine = line.contains("pick-up", ignoreCase = true) ||
            line.contains("pickup", ignoreCase = true)
        if ((looksLikeAddress || pickUpPointLine) && extractOuterLondonPostcodes(line).isEmpty()) {
            return true
        }
    }
    return false
}

/** Map postcodes to pickup/drop using trip-leg line zones in plain OCR text. */
fun resolvePostcodesFromLegZones(ocrText: String): Pair<String, String> {
    val lines = ocrText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val legIndices = mutableListOf<Int>()
    lines.forEachIndexed { index, line ->
        if (parseTripLegFromLine(line) != null) legIndices.add(index)
    }
    if (legIndices.size < 2) return "" to ""

    val pickupLegIdx = legIndices[0]
    val dropLegIdx = legIndices[1]
    val dropAddressIdx = findDropAddressLineIndex(lines, dropLegIdx)
    val pickupZoneEnd = findPickupZoneEnd(lines, pickupLegIdx, dropLegIdx)
    var pickup = pickBestPostcodeInLineRange(lines, pickupLegIdx, pickupZoneEnd, minScore = 60)
    var dropZoneStart = dropAddressIdx.coerceAtLeast(dropLegIdx + 1)

    if (pickup.isBlank() && pickupZoneHasAddressWithoutPostcode(lines, pickupLegIdx, dropLegIdx)) {
        val betweenOrphan = ((pickupLegIdx + 1) until dropLegIdx).firstOrNull { idx ->
            isFullPostcodeOnlyLine(lines[idx])
        }
        val afterDropOrphan = (dropLegIdx + 1).takeIf { orphanIdx ->
            orphanIdx < lines.size && isFullPostcodeOnlyLine(lines[orphanIdx])
        }
        val orphanIdx = betweenOrphan ?: afterDropOrphan
        if (orphanIdx != null) {
            val orphan = extractOuterLondonPostcodes(lines[orphanIdx]).firstOrNull()
            val dropOnly = pickBestPostcodeInLineRange(
                lines,
                maxOf(dropLegIdx + 1, orphanIdx + 1),
                lines.size,
                minScore = 60,
            )
            if (orphan != null && (dropOnly.isBlank() || orphan != dropOnly || betweenOrphan != null)) {
                pickup = orphan
                dropZoneStart = maxOf(dropZoneStart, orphanIdx + 1)
            }
        }
    }

    var drop = pickBestPostcodeInLineRange(lines, dropZoneStart, lines.size, minScore = 60, preferEarliest = false)
    if (drop.isBlank()) {
        drop = pickBestPostcodeInLineRange(lines, dropZoneStart, lines.size, minScore = 0, preferEarliest = false)
    }

    if (pickup.isBlank() && drop.isBlank()) {
        val global = extractOuterLondonPostcodes(ocrText)
        if (global.size == 1) {
            val pc = global[0]
            val lineIdx = lines.indexOfFirst { line ->
                extractOuterLondonPostcodes(line).contains(pc)
            }
            when {
                lineIdx in pickupLegIdx until dropLegIdx -> pickup = pc
                lineIdx >= dropZoneStart -> drop = pc
            }
        }
    }
    return pickup to drop
}

/**
 * When pickup and drop metrics were duplicated (drop copied into pickup), restore from
 * first two leg lines in OCR order.
 */
fun reconcileDuplicatedPickupDrop(ride: RideRequest, ocr: String): RideRequest {
    val legs = extractTripLegPairsInOrder(ocr)
    if (legs.size < 2) return ride

    val sameTime = ride.pickup_time_minutes != null &&
        ride.pickup_time_minutes == ride.trip_time_minutes
    val sameDist = ride.pickup_distance_value != null &&
        ride.pickup_distance_value == ride.trip_distance_value
    if (!sameTime && !sameDist) return ride

    val (pickupLeg, tripLeg) = legs[0] to legs[1]
    if (pickupLeg == tripLeg) return ride

    return ride.copy(
        pickup_time_minutes = pickupLeg.first,
        trip_time_minutes = tripLeg.first,
        pickup_distance_value = pickupLeg.second,
        trip_distance_value = tripLeg.second,
    )
}
