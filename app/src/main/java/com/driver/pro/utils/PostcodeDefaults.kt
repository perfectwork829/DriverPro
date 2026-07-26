package com.driver.pro.utils

import com.driver.pro.network.PostcodeResponse

/** Mirrors server [outward_codes_for_area] when API returns no rows. */
fun defaultOutwardCodesForRegion(region: String): List<PostcodeResponse> {
    val key = region.trim().uppercase()
    if (!Regex("^[A-Z]{1,2}$").matches(key)) return emptyList()

    val upper = if (key.length >= 2) 40 else 25
    return (1..upper).map { n ->
        PostcodeResponse(full_code = "$key$n", area = key)
    }
}

fun isUkMapRegionId(region: String): Boolean =
    Regex("^[A-Z]{1,2}$").matches(region.trim().uppercase())
