package com.driver.pro.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

fun isAccessibilityServiceEnabled(
    context: Context,
    serviceClass: Class<out AccessibilityService>,
): Boolean {
    val expected = ComponentName(context, serviceClass).flattenToString()
    val enabled =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) return true
    }
    return false
}

/** True when either the current or legacy accessibility component is enabled. */
fun isDriverAccessibilityEnabled(context: Context): Boolean =
    isAccessibilityServiceEnabled(context, DriverAppAccessibilityService::class.java) ||
        isAccessibilityServiceEnabled(context, UberAccessibilityService::class.java)
