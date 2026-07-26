package com.driver.pro.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList

object ShizukuHelper {

    /** Shizuku permission request code (not used with Activity.onActivityResult). */
    private const val REQ = 9001

    /** Official Shizuku manager on Play Store / GitHub (rikka.shizuku). */
    private const val SHIZUKU_PLAY_STORE_ID = "rikka.shizuku"

    private val SHIZUKU_LAUNCH_PACKAGES = listOf("rikka.shizuku")

    private var attached = false
    private val permissionListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQ) return@OnRequestPermissionResultListener
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            notifyPermissionChanged(granted)
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        notifyPermissionChanged(hasPermission())
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        notifyPermissionChanged(false)
    }

    enum class RequestResult {
        ALREADY_GRANTED,
        REQUESTED,
        DENIED_PERMANENTLY,
        NOT_RUNNING,
        UNSUPPORTED,
        UNAVAILABLE,
    }

    /** Register Shizuku listeners — call from [android.app.Activity.onCreate]. */
    fun attach() {
        if (attached) return
        if (Shizuku.isPreV11()) return
        try {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            attached = true
        } catch (_: Throwable) {
            attached = false
        }
    }

    /** Unregister listeners — call from [android.app.Activity.onDestroy]. */
    fun detach() {
        if (!attached) return
        try {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (_: Throwable) {
        }
        attached = false
    }

    fun addOnPermissionChangedListener(listener: (Boolean) -> Unit) {
        permissionListeners.add(listener)
        listener(hasPermission())
    }

    fun removeOnPermissionChangedListener(listener: (Boolean) -> Unit) {
        permissionListeners.remove(listener)
    }

    private fun notifyPermissionChanged(granted: Boolean) {
        permissionListeners.forEach { it(granted) }
    }

    fun hasPermission(): Boolean = runCatching {
        if (Shizuku.isPreV11()) return@runCatching false
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun isServiceRunning(): Boolean = runCatching {
        !Shizuku.isPreV11() && Shizuku.pingBinder()
    }.getOrDefault(false)

    fun openShizukuApp(context: Context) {
        for (pkg in SHIZUKU_LAUNCH_PACKAGES) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        }
        Toast.makeText(context, "Install Shizuku from Play Store", Toast.LENGTH_LONG).show()
        openShizukuInPlayStore(context)
    }

    private fun openShizukuInPlayStore(context: Context) {
        val market = Uri.parse("market://details?id=$SHIZUKU_PLAY_STORE_ID")
        val https =
            Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PLAY_STORE_ID")
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, market).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, https).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Request Shizuku API permission. Result is delivered asynchronously via
     * [addOnPermissionChangedListener] when [RequestResult.REQUESTED].
     */
    fun requestPermission(context: Context): RequestResult {
        if (Shizuku.isPreV11()) {
            Toast.makeText(context, "Shizuku v11+ required", Toast.LENGTH_SHORT).show()
            return RequestResult.UNSUPPORTED
        }
        return try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(context, "Shizuku is not running", Toast.LENGTH_SHORT).show()
                return RequestResult.NOT_RUNNING
            }
            when (Shizuku.checkSelfPermission()) {
                PackageManager.PERMISSION_GRANTED -> {
                    notifyPermissionChanged(true)
                    RequestResult.ALREADY_GRANTED
                }
                PackageManager.PERMISSION_DENIED -> {
                    if (Shizuku.shouldShowRequestPermissionRationale()) {
                        Toast.makeText(
                            context,
                            "Open Shizuku and allow driverPRO under authorized apps",
                            Toast.LENGTH_LONG,
                        ).show()
                        RequestResult.DENIED_PERMANENTLY
                    } else {
                        Shizuku.requestPermission(REQ)
                        RequestResult.REQUESTED
                    }
                }
                else -> RequestResult.UNAVAILABLE
            }
        } catch (_: Throwable) {
            Toast.makeText(context, "Shizuku not installed or unavailable", Toast.LENGTH_SHORT).show()
            RequestResult.UNAVAILABLE
        }
    }
}
