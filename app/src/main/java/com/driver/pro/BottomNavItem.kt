package com.driver.pro

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune

/**
 * Bottom tabs (left → right): Licence / rules / filter settings / postcode map.
 * Icons: vector drawables `nav_tab_gear`, `nav_tab_folder`, `nav_tab_sliders`, `nav_tab_map`.
 */
sealed class BottomNavItem(
    val label: String,
    val icon: ImageVector,
) {
    object Licence : BottomNavItem(
        label = "Licence",
        icon = Icons.Filled.Settings,
    )

    object AcceptReject : BottomNavItem(
        label = "Rules",
        icon = Icons.Filled.Settings,
    )

    object Filters : BottomNavItem(
        label = "Settings",
        icon = Icons.Filled.Tune,
    )

    object PostcodeMap : BottomNavItem(
        label = "Postcode",
        icon = Icons.Filled.Map,
    )
}
