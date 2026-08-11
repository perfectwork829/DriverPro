package com.driver.pro.service

import org.junit.Assert.assertTrue
import org.junit.Test

class OfferCardDetectorTest {

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    @Test
    fun detectsWhiteBottomSheetTop() {
        val w = 360
        val h = 720
        val sheetTop = 280
        val pixels = IntArray(w * h)
        val mapColor = rgb(90, 110, 95)
        val sheetColor = rgb(255, 255, 255)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (y >= sheetTop) sheetColor else mapColor
            }
        }
        for (i in 0 until 80) {
            val x = (i * 17) % w
            val y = (i * 23) % sheetTop
            pixels[y * w + x] = rgb(50, 70, 60)
        }

        val detected = detectOfferCardTopPx(pixels, w, h)
        assertTrue("expected detection near $sheetTop, got $detected", detected >= 0)
        assertTrue(
            "detected top $detected should be near sheetTop $sheetTop",
            detected in (sheetTop - 50)..(sheetTop + 20),
        )
    }

    @Test
    fun returnsFallbackSignalWhenNoSheet() {
        val w = 360
        val h = 720
        val pixels = IntArray(w * h) { rgb(70, 90, 80) }
        val detected = detectOfferCardTopPx(pixels, w, h)
        assertTrue("expected -1 without a sheet, got $detected", detected < 0)
    }
}
