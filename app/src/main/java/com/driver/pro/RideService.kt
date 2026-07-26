package com.driver.pro

//noinspection SuspiciousImport
import android.R
import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RideService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = true

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startRidePolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val channelId = "ride_monitor_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Ride Monitor", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Accept Running")
            .setContentText("Monitoring ride offers...")
            .setSmallIcon(R.drawable.ic_popup_sync)
            .build()

        startForeground(1, notification)
    }

    /** Poll backend every 5 seconds */
    private fun startRidePolling() {
        scope.launch {
            while (isRunning) {
                checkForRide()
                delay(5000)
            }
        }
    }

    /** Fetch ride request from your backend */
    private fun checkForRide() {
        try {
            val url = URL("https://YOUR_SERVER.com/api/ride-offer/")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            val hasRide = json.getBoolean("has_ride")

            if (hasRide) {
                val rideId = json.getString("ride_id")

                // AUTO ACTION
                autoAcceptRide(rideId)
                // autoRejectRide(rideId) // if you want reject
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Accept ride */
    private fun autoAcceptRide(rideId: String) {
        try {
            val url = URL("https://YOUR_SERVER.com/api/accept/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true

            val json = "{\"ride_id\":\"$rideId\"}"

            conn.outputStream.write(json.toByteArray())

            logNotification("Accepted ride $rideId")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Reject ride */
    private fun autoRejectRide(rideId: String) {
        try {
            val url = URL("https://YOUR_SERVER.com/api/reject/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true

            val json = "{\"ride_id\":\"$rideId\"}"

            conn.outputStream.write(json.toByteArray())

            logNotification("Rejected ride $rideId")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Show notification of action */
    private fun logNotification(message: String) {
        val notification = NotificationCompat.Builder(this, "ride_monitor_channel")
            .setContentTitle("Ride Action")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_input_add)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify((Math.random() * 10000).toInt(), notification)
    }
}
