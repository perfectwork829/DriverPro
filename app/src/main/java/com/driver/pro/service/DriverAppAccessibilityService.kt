package com.driver.pro.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import java.io.File
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

@SuppressLint("AccessibilityPolicy")
open class DriverAppAccessibilityService : AccessibilityService() {

    data class NodeData(
        val text: String?,
        val contentDescription: String?,
        val className: String?,
        val viewId: String?,
        val clickable: Boolean,
        val enabled: Boolean
    )

    fun collectNodeData(node: AccessibilityNodeInfo, result: MutableList<NodeData>) {
        val item = NodeData(
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            className = node.className?.toString(),
            viewId = node.viewIdResourceName,
            clickable = node.isClickable,
            enabled = node.isEnabled
        )

        result.add(item)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectNodeData(child, result)
            }
        }
    }

    /** Visible text from the active window — used to fill OCR gaps. */
    fun captureVisibleOfferText(): String {
        val root = rootInActiveWindow ?: return ""
        return try {
            val nodes = mutableListOf<NodeData>()
            collectNodeData(root, nodes)
            val lines = nodes.flatMap { n ->
                listOfNotNull(n.text, n.contentDescription).map { it.trim() }.filter { it.isNotEmpty() }
            }
            accessibilityNodesToText(lines)
        } catch (e: Exception) {
            Log.e("DriverAppA11y", "captureVisibleOfferText failed", e)
            ""
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
    }

    companion object {
        @Volatile
        private var instance: DriverAppAccessibilityService? = null

        /** True while the manual Accept/Decline/Skip overlay is on screen (pauses OCR). */
        @Volatile
        var isManualConfirmVisible: Boolean = false

        /** Latest Accessibility snapshot of on-screen text (empty if service off). */
        fun snapshotOfferText(): String? {
            return try {
                instance?.captureVisibleOfferText()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    private var manualConfirmView: View? = null
    private var manualConfirmDismissRunnable: Runnable? = null

    private val confirmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val x = intent?.getIntExtra("x", 0) ?: return
            val y = intent.getIntExtra("y", 0)
            val message = intent.getStringExtra("message")
            val score = intent.getIntExtra("score", 0)
            val status = intent.getIntExtra("status", 0)
            val topOnly = intent.getBooleanExtra("top_only", false)
            val holdMs = intent.getLongExtra("hold_ms", 1500L).coerceIn(800L, 8000L)

            if (x > 0 || y > 0) {
                showCursor(x, y)
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    clickPositionWithShizuku(x, y)
                } else {
                    clickAt(x, y, score)
                }
            } else if (status == 1 || status == -1) {
                tryPerformDecisionTap(status, score)
            }
            if (message != null) {
                // Keep status text at the very top so it never covers offer-card drop addresses.
                showLogOverlay(
                    message,
                    x = 40,
                    y = if (topOnly || x == 0) 24 else 100,
                    holdMs = holdMs,
                )
            }
        }
    }

    private val a11yDecisionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getIntExtra("status", 0) ?: return
            if (status != 1 && status != -1) return
            val score = intent.getIntExtra("score", 0)
            val message = intent.getStringExtra("message")
            Handler(mainLooper).postDelayed({
                if (tryPerformDecisionTap(status, score)) {
                    showLogOverlay(message ?: "Score: $score")
                } else {
                    showLogOverlay(
                        message ?: "Score: $score — enable accessibility on Driver app and try again",
                    )
                }
            }, 400L)
        }
    }

    private val manualConfirmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val title = intent.getStringExtra("title") ?: "Confirm decision"
            val detail = intent.getStringExtra("detail").orEmpty()
            val suggested = intent.getIntExtra("suggested_status", 0)
            val score = intent.getIntExtra("score", 0)
            showManualConfirmOverlay(title, detail, suggested, score)
        }
    }

    fun clickPositionWithShizuku(x: Int, y: Int) {
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) return

        Log.d("MY-BROADCAST", "CLICKing... $x $y")
        try {
            // Use reflection to access the private newProcess method
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true

            // Execute "input tap x y"
            val command = arrayOf("input", "tap", x.toString(), y.toString())
            val remoteProcess = method.invoke(null, command, null, null) as ShizukuRemoteProcess

            remoteProcess.waitFor()
            remoteProcess.destroy()

            Log.d("MY-BROADCAST", "CLICK SUCCESS")
        } catch (e: Exception) {
            Log.d("MY-BROADCAST", "CLICK fAIL $e")
            e.printStackTrace()
        }
    }



    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                confirmReceiver,
                IntentFilter("ACTION_CLICK_CONFIRM"),
                Context.RECEIVER_NOT_EXPORTED,
            )
            registerReceiver(
                a11yDecisionReceiver,
                IntentFilter(ACTION_A11Y_TAP_DECISION),
                Context.RECEIVER_NOT_EXPORTED,
            )
            registerReceiver(
                manualConfirmReceiver,
                IntentFilter(ACTION_SHOW_MANUAL_CONFIRM),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            registerReceiver(confirmReceiver, IntentFilter("ACTION_CLICK_CONFIRM"))
            registerReceiver(a11yDecisionReceiver, IntentFilter(ACTION_A11Y_TAP_DECISION))
            registerReceiver(manualConfirmReceiver, IntentFilter(ACTION_SHOW_MANUAL_CONFIRM))
        }
        Log.d("MY-BROADCAST", "Connected")
    }

    private fun tryPerformDecisionTap(status: Int, score: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val labels = if (status == 1) {
            listOf("Confirm", "Match", "Accept", "ACCEPT")
        } else {
            listOf("Close", "Dismiss", "Decline", "No thanks", "Pass", "Not now")
        }
        for (label in labels) {
            if (clickNodeByLabel(root, label)) {
                Log.d("DriverAppA11y", "Tapped '$label' for status=$status score=$score")
                return true
            }
        }
        if (status == -1 && clickTopRightDismissControl(root)) {
            Log.d("DriverAppA11y", "Tapped top-right dismiss for reject score=$score")
            return true
        }
        return false
    }

    private fun clickNodeByLabel(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val matches = text.contains(label, ignoreCase = true) ||
            desc.contains(label, ignoreCase = true)
        if (matches) {
            if (performClickOnNodeOrParent(node)) return true
        }
        for (i in 0 until node.childCount) {
            if (clickNodeByLabel(node.getChild(i), label)) return true
        }
        return false
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
            depth++
        }
        return false
    }

    /** Driver app often uses an icon-only X near the top-right of the offer card. */
    private fun clickTopRightDismissControl(root: AccessibilityNodeInfo): Boolean {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectClickableNodes(root, candidates)
        // Offer card sits in the lower ~65% of the screen; X is near the card's top edge.
        val cardBandTop = (screenH * 0.28f).toInt()
        val cardBandBottom = (screenH * 0.72f).toInt()
        val topRight = candidates
            .mapNotNull { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.isEmpty) return@mapNotNull null
                if (bounds.centerY() < cardBandTop || bounds.centerY() > cardBandBottom) {
                    return@mapNotNull null
                }
                if (bounds.centerX() < screenW * 0.72f) return@mapNotNull null
                // Prefer small icon-sized controls (the X), not huge Confirm buttons.
                val area = bounds.width() * bounds.height()
                if (area > screenW * screenH * 0.08f) return@mapNotNull null
                Triple(node, area, bounds.centerX() to bounds.centerY())
            }
            .sortedWith(
                compareBy<Triple<AccessibilityNodeInfo, Int, Pair<Int, Int>>> { it.second }
                    .thenByDescending { it.third.first }
                    .thenBy { it.third.second },
            )
        for ((node, _, _) in topRight.take(6)) {
            if (performClickOnNodeOrParent(node)) return true
        }
        return false
    }

    private fun collectClickableNodes(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable && node.isEnabled) out.add(node)
        for (i in 0 until node.childCount) {
            collectClickableNodes(node.getChild(i), out)
        }
    }

    fun toOverlayCoords(context: Context, rawX: Int, rawY: Int): Pair<Int, Int> {
        val resources = context.resources

        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else 0

        val navBarId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navBarHeight = if (navBarId > 0) resources.getDimensionPixelSize(navBarId) else 0

        val usableHeight = resources.displayMetrics.heightPixels - navBarHeight

        // FIXED overlay Y = screenY - statusBarHeight
        val overlayX = rawX
        val overlayY = rawY - statusBarHeight

        return Pair(overlayX, overlayY)
    }


    // ================= CORE MATCHING =================

    private fun findNodeByOcrRect(
        node: AccessibilityNodeInfo,
        ocrRect: Rect
    ): AccessibilityNodeInfo? {

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (node.isClickable && bounds.overlaps(ocrRect)) {
            return node
        }

        if (bounds.overlaps(ocrRect)) {
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) return parent
                parent = parent.parent
            }
        }

        for (i in 0 until node.childCount) {
            val found = findNodeByOcrRect(node.getChild(i), ocrRect)
            if (found != null) return found
        }
        return null
    }

    private fun Rect.overlaps(other: Rect): Boolean {
        return left < other.right &&
                right > other.left &&
                top < other.bottom &&
                bottom > other.top
    }

    private fun highlightNode(node: AccessibilityNodeInfo, color: Int = Color.GREEN) {


        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val resources = this.resources

        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else 0



        if (bounds.isEmpty) return
        if (!Settings.canDrawOverlays(this)) return

        val overlay = ImageView(this)

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(4, color)
            setColor(Color.TRANSPARENT)
            cornerRadius = 16f
        }

        overlay.background = drawable

        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top - statusBarHeight
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.addView(overlay, params)

        Handler(mainLooper).postDelayed({
            try { wm.removeView(overlay) } catch (_: Exception) {}
        }, 500)
    }


    private fun showCursor(x: Int, y: Int) {
        val (ox, oy) = toOverlayCoords(this, x, y)
        Log.d("MY-BROADCAST", "Show Cursor at x=$x y=$y")

        if (!Settings.canDrawOverlays(this)) {
            Log.e("Cursor", "Overlay permission not granted")
            return
        }

        val cursor = ImageView(this)

        val size = 80   // diameter of circle
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.RED)
            alpha = (0.8f * 255).toInt()  // 0.6 transparency
        }


        cursor.background = shape

        val params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = ox - size / 2
            this.y = oy - size / 2
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.addView(cursor, params)

        Handler(mainLooper).postDelayed({
            try { wm.removeView(cursor) } catch (_: Exception) {}
        }, 200)
    }

    private fun showLogOverlay(message: String, x: Int = 50, y: Int = 100, holdMs: Long = 1500L) {
        Handler(mainLooper).post {
            try {
                val textView = TextView(this).apply {
                    text = message
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.argb(200, 20, 20, 20))
                    textSize = 15f
                    setPadding(16, 10, 16, 10)
                    maxWidth = resources.displayMetrics.widthPixels - 48
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    this.x = x
                    this.y = y
                }

                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    return@post
                }

                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.addView(textView, params)

                Handler(mainLooper).postDelayed({
                    try { wm.removeView(textView) } catch (_: Exception) {}
                }, holdMs.coerceIn(800L, 8000L))
            } catch (e: Exception) {
                Log.e("DriverAppA11y", "Score overlay failed", e)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Touchable overlay when OCR is incomplete or low-confidence.
     * Accept / Decline taps Uber via accessibility; Skip leaves the offer alone.
     */
    private fun showManualConfirmOverlay(
        title: String,
        detail: String,
        suggestedStatus: Int,
        score: Int,
    ) {
        Handler(mainLooper).post {
            try {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(
                        this,
                        "$title — enable Display over other apps for confirm buttons",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@post
                }
                dismissManualConfirmOverlay()

                val density = resources.displayMetrics.density
                fun dp(v: Int) = (v * density).toInt()

                val panel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    background = GradientDrawable().apply {
                        setColor(Color.argb(230, 18, 18, 22))
                        cornerRadius = dp(14).toFloat()
                    }
                }

                panel.addView(
                    TextView(this).apply {
                        text = title
                        setTextColor(Color.WHITE)
                        textSize = 16f
                        setPadding(0, 0, 0, dp(6))
                    },
                )
                panel.addView(
                    TextView(this).apply {
                        text = detail
                        setTextColor(Color.argb(230, 220, 220, 220))
                        textSize = 13f
                        setPadding(0, 0, 0, dp(12))
                    },
                )

                val buttons = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    weightSum = 3f
                }

                fun actionButton(label: String, bg: Int, onClick: () -> Unit): Button {
                    return Button(this).apply {
                        text = label
                        textSize = 13f
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            setColor(bg)
                            cornerRadius = dp(10).toFloat()
                        }
                        setOnClickListener { onClick() }
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(6)
                        }
                        isAllCaps = false
                        minimumHeight = dp(44)
                    }
                }

                val acceptBg = if (suggestedStatus == 1) Color.parseColor("#1B7F4E") else Color.parseColor("#2E7D32")
                val declineBg = if (suggestedStatus == -1) Color.parseColor("#B71C1C") else Color.parseColor("#C62828")

                buttons.addView(
                    actionButton("Accept", acceptBg) {
                        dismissManualConfirmOverlay()
                        val ok = tryPerformDecisionTap(1, score)
                        showLogOverlay(
                            if (ok) "Accepted (manual)" else "Accept failed — tap Confirm/Match yourself",
                            holdMs = 2500L,
                        )
                    },
                )
                buttons.addView(
                    actionButton("Decline", declineBg) {
                        dismissManualConfirmOverlay()
                        val ok = tryPerformDecisionTap(-1, score)
                        showLogOverlay(
                            if (ok) "Declined (manual)" else "Decline failed — tap X yourself",
                            holdMs = 2500L,
                        )
                    },
                )
                buttons.addView(
                    actionButton("Skip", Color.parseColor("#455A64")) {
                        dismissManualConfirmOverlay()
                        showLogOverlay("Skipped — no auto tap", holdMs = 1800L)
                    }.also {
                        (it.layoutParams as LinearLayout.LayoutParams).marginEnd = 0
                    },
                )
                panel.addView(buttons)

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    // Touchable (no FLAG_NOT_TOUCHABLE) so Accept/Decline/Skip work.
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = dp(72)
                    width = resources.displayMetrics.widthPixels - dp(24)
                }

                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.addView(panel, params)
                manualConfirmView = panel
                isManualConfirmVisible = true

                val dismiss = Runnable { dismissManualConfirmOverlay() }
                manualConfirmDismissRunnable = dismiss
                Handler(mainLooper).postDelayed(dismiss, 20_000L)
            } catch (e: Exception) {
                Log.e("DriverAppA11y", "Manual confirm overlay failed", e)
                isManualConfirmVisible = false
                Toast.makeText(this, title, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dismissManualConfirmOverlay() {
        val view = manualConfirmView
        manualConfirmView = null
        isManualConfirmVisible = false
        manualConfirmDismissRunnable?.let { Handler(mainLooper).removeCallbacks(it) }
        manualConfirmDismissRunnable = null
        if (view == null) return
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun clickAt(rawX: Int, rawY: Int, score: Int) {
        Handler(mainLooper).postDelayed({
            performGestureTap(rawX, rawY, attempt = 1, score)
        }, 1000L) // delay lets UI finish animations 250
    }

    private fun performGestureTap(x: Int, y: Int, attempt: Int, score: Int) {

        val MAX_ATTEMPTS = 3

        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    80L,   // 80ms - most reliable
                    false
                )
            )
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                showLogOverlay("Tap completed $attempt. Score is $score. If button didn't work, Click manually ")
                if (attempt < MAX_ATTEMPTS) {
                    // Retry after a tiny delay (very effective)
                    Handler(mainLooper).postDelayed({
                        performGestureTap(x, y, attempt + 1, score)
                    }, 1000L)
                } else {
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)

                showLogOverlay("Tap cancelled ❌")


                if (attempt < MAX_ATTEMPTS) {
                    // Retry after a tiny delay (very effective)
                    Handler(mainLooper).postDelayed({
                        performGestureTap(x, y, attempt + 1, score)
                    }, 120)
                } else {
                }
            }
        }, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        dismissManualConfirmOverlay()
        try { unregisterReceiver(confirmReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(a11yDecisionReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(manualConfirmReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
