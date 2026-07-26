package com.driver.pro.ui.screens

/**
 * Matches server [api.postcode_regions] — map tap id → postcode district(s) to list.
 */
data class PostcodeDistrictSection(
    val district: String,
    val title: String,
)

/** Outward district from full code (EC1A → EC, NN1 → NN). */
fun outwardDistrict(fullCode: String): String {
    val trimmed = fullCode.trim().uppercase()
    return Regex("^([A-Z]{1,2})").find(trimmed)?.value.orEmpty()
}

fun sectionsForMapRegion(region: String): List<PostcodeDistrictSection> {
    return when (region.trim().uppercase()) {
        "E" -> listOf(
            PostcodeDistrictSection("E", "E East London postcode area"),
            PostcodeDistrictSection("EC", "EC East Central London postcode area"),
        )
        "W" -> listOf(
            PostcodeDistrictSection("W", "W West London postcode area"),
            PostcodeDistrictSection("WC", "WC West Central London postcode area"),
        )
        else -> {
            val key = region.trim().uppercase()
            listOf(PostcodeDistrictSection(key, "$key postcode area"))
        }
    }
}
