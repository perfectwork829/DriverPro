package com.driver.pro

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import com.driver.pro.network.sendNode
import com.driver.pro.network.sendNotificationInfo
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DriverAppNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName
        val extras = sbn.notification.extras

        // Only read notifications from the driver app package (com.ubercab.driver).
        if (pkg == "com.ubercab.driver") {

            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            val intent = Intent("DRIVER_APP_NOTIFICATION")
            intent.putExtra("title", title)
            intent.putExtra("text", text)

            CoroutineScope(Dispatchers.IO).launch {
                sendNotificationInfo(text)
            }

            sendBroadcast(intent)
        }
    }
}
