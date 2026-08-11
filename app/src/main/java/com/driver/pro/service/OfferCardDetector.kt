package com.driver.pro.service

import android.graphics.Bitmap
import kotlin.math.sqrt

/**
 * Detect the top edge of Uber's floating offer card (light bottom sheet) on a screenshot.
 *
 * Scans rows from the bottom for a contiguous bright, low-variance band (Material sheet),
 * then returns the Y just above that band. Returns -1 when detection is unreliable so callers
 * can fall back to the fixed bottom-~2/3 crop.
 */
fun detectOfferCardTopPx(bitmap: Bitmap): Int {
    val width = bitmap.width
    val height = bitmap.height
    if (width < 40 || height < 80) return -1
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    return detectOfferCardTopPx(pixels, width, height)
}

/**
 * Pure pixel-buffer detector (unit-testable without Robolectric).
 */
fun detectOfferCardTopPx(pixels: IntArray, width: Int, height: Int): Int {
    if (width < 40 || height < 80 || pixels.size < width * height) return -1

    val minScanY = (height * 0.12f).toInt()
    val maxScanY = height - 1
    val rowStep = 2
    val colStep = (width / 48).coerceIn(4, 12)
    val xStart = (width * 0.10f).toInt()
    val xEnd = (width * 0.90f).toInt().coerceAtLeast(xStart + 8)

    var y = maxScanY
    var sheetBottom = -1
    var sheetTop = -1
    while (y >= minScanY) {
        if (isLightSheetRow(pixels, width, y, xStart, xEnd, colStep)) {
            if (sheetBottom < 0) sheetBottom = y
            sheetTop = y
            y -= rowStep
        } else if (sheetBottom >= 0) {
            var gapOk = false
            var probe = y - rowStep
            var gap = 0
            while (probe >= minScanY && gap < 6) {
                if (isLightSheetRow(pixels, width, probe, xStart, xEnd, colStep)) {
                    gapOk = true
                    sheetTop = probe
                    y = probe - rowStep
                    break
                }
                probe -= rowStep
                gap++
            }
            if (!gapOk) break
        } else {
            y -= rowStep
        }
    }

    if (sheetBottom < 0 || sheetTop < 0) return -1

    val sheetHeight = sheetBottom - sheetTop + 1
    if (sheetHeight < (height * 0.28f).toInt()) return -1
    if (height - 1 - sheetBottom > (height * 0.08f).toInt()) return -1

    val pad = (height * 0.03f).toInt().coerceIn(8, 36)
    val top = (sheetTop - pad).coerceIn(
        (height * 0.12f).toInt(),
        (height * 0.55f).toInt(),
    )
    if (height - top < (height * 0.35f).toInt()) return -1
    return top
}

private fun channelR(c: Int): Int = (c ushr 16) and 0xFF
private fun channelG(c: Int): Int = (c ushr 8) and 0xFF
private fun channelB(c: Int): Int = c and 0xFF

private fun isLightSheetRow(
    pixels: IntArray,
    width: Int,
    y: Int,
    xStart: Int,
    xEnd: Int,
    colStep: Int,
): Boolean {
    var sum = 0.0
    var sumSq = 0.0
    var n = 0
    var x = xStart
    val rowOff = y * width
    while (x < xEnd) {
        val c = pixels[rowOff + x]
        val lum = 0.299 * channelR(c) + 0.587 * channelG(c) + 0.114 * channelB(c)
        sum += lum
        sumSq += lum * lum
        n++
        x += colStep
    }
    if (n < 6) return false
    val mean = sum / n
    val variance = (sumSq / n) - mean * mean
    val std = if (variance > 0) sqrt(variance) else 0.0
    return mean >= 188.0 && std <= 48.0
}
