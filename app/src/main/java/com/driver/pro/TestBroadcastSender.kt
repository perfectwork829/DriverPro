package com.driver.pro

import android.content.Context
import android.content.Intent

object TestBroadcastSender {
    fun sendTestDriverAppNotification(context: Context) {
        val intent = Intent("DRIVER_APP_NOTIFICATION")
        intent.putExtra("title", "Driver app • New Trip Request")
        intent.putExtra("text", "Pickup: London → Heathrow")
        context.sendBroadcast(intent)
    }
}
