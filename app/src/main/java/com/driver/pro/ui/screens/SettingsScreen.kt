package com.driver.pro.ui.screens

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.driver.pro.network.ApiService
import com.driver.pro.network.SessionManager
import com.driver.pro.network.SettingField
import com.driver.pro.network.UserSettingsResponse
import com.driver.pro.service.ACTION_DRIVERPRO_CAPTURE_STARTED
import com.driver.pro.service.ACTION_DRIVERPRO_CAPTURE_STOPPED
import com.driver.pro.service.ScreenCaptureService
import com.driver.pro.service.isDriverAccessibilityEnabled
import com.driver.pro.utils.ShizukuHelper
import com.driver.pro.ui.components.ScoreRangeSlider
import com.driver.pro.ui.components.ScoreSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.round

@Composable
fun SettingsScreen(
    sessionManager: SessionManager,
    apiService: ApiService,
    userId: Int,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pendingCaptureAfterNotification by remember { mutableStateOf(false) }
    var captureRunning by remember { mutableStateOf(false) }
    var showAccessibilityGate by remember { mutableStateOf(false) }
    var showShizukuGate by remember { mutableStateOf(false) }
    var pendingStartAfterShizuku by remember { mutableStateOf(false) }
    var showStopCaptureConfirmDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_DRIVERPRO_CAPTURE_STARTED -> captureRunning = true
                    ACTION_DRIVERPRO_CAPTURE_STOPPED -> captureRunning = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_DRIVERPRO_CAPTURE_STARTED)
            addAction(ACTION_DRIVERPRO_CAPTURE_STOPPED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    val activity = LocalContext.current as? ComponentActivity
    val syncGatesAfterResume = rememberUpdatedState {
        if (isDriverAccessibilityEnabled(context)) {
            showAccessibilityGate = false
        }
        if (ShizukuHelper.hasPermission()) {
            showShizukuGate = false
        }
    }
    DisposableEffect(activity) {
        val act = activity ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncGatesAfterResume.value()
            }
        }
        act.lifecycle.addObserver(observer)
        onDispose { act.lifecycle.removeObserver(observer) }
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            Toast.makeText(context, "Screen capture cancelled", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
            putExtra("code", result.resultCode)
            putExtra("data", result.data)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            captureRunning = true
            Toast.makeText(context, "Capture started — switch to Driver app", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("SettingsScreen", "startForegroundService failed", e)
            Toast.makeText(context, "Could not start capture: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "Notification permission helps keep capture running in the background",
                Toast.LENGTH_LONG,
            ).show()
        }
        if (pendingCaptureAfterNotification) {
            pendingCaptureAfterNotification = false
            val pm =
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(pm.createScreenCaptureIntent())
        }
    }

    fun beginScreenCapture() {
        val pm =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = screenCapturePermissionIntent(pm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED -> {
                    screenCaptureLauncher.launch(captureIntent)
                }
                else -> {
                    pendingCaptureAfterNotification = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            screenCaptureLauncher.launch(captureIntent)
        }
    }

    val beginCaptureRef = rememberUpdatedState { beginScreenCapture() }

    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { granted ->
            if (granted) {
                showShizukuGate = false
                if (pendingStartAfterShizuku) {
                    pendingStartAfterShizuku = false
                    beginCaptureRef.value()
                }
            }
        }
        ShizukuHelper.addOnPermissionChangedListener(listener)
        onDispose { ShizukuHelper.removeOnPermissionChangedListener(listener) }
    }

    // --- Score ---
    var scoreReject by remember { mutableStateOf(0f) }
    var scoreAccept by remember { mutableStateOf(0f) }
    var scoreEnabled by remember { mutableStateOf(true) }

    // --- Price ---
    var priceReject by remember { mutableStateOf(0f) }
    var priceAccept by remember { mutableStateOf(0f) }
    var priceEnabled by remember { mutableStateOf(true) }

    // --- Rate ---
    var rateAccept by remember { mutableStateOf(3f) }
    var rateEnabled by remember { mutableStateOf(true) }

    // --- Price per Mile ---
    var priceMileReject by remember { mutableStateOf(0f) }
    var priceMileAccept by remember { mutableStateOf(0f) }
    var priceMileEnabled by remember { mutableStateOf(true) }

    // Load current settings
    LaunchedEffect(Unit) {
        try {
            val response = apiService.getUserSettings(userId)
            if (response.isSuccessful) {
                val body = response.body()
                body?.let { data ->
                    Log.d("settting resposne", data.toString())
                    scoreReject = data.score?.reject?.toFloat() ?: 0f
                    scoreAccept = data.score?.accept?.toFloat() ?: 0f
                    scoreEnabled = data.score?.enabled ?: true

                    Log.d("settting resposne", scoreReject.toString())
                    Log.d("settting resposne", scoreAccept.toString())
                    Log.d("settting resposne", scoreEnabled.toString())

                    priceReject = data.price?.reject?.toFloat() ?: 0f
                    priceAccept = data.price?.accept?.toFloat() ?: 0f
                    priceEnabled = data.price?.enabled ?: true

                    rateAccept = data.rate?.accept?.toFloat() ?: 0f
                    rateEnabled = data.rate?.enabled ?: true

                    priceMileReject = (data.price_per_mile?.reject?.toFloat() ?: 0f)
                        .coerceIn(PRICE_PER_MILE_MIN, PRICE_PER_MILE_MAX)
                    priceMileAccept = (data.price_per_mile?.accept?.toFloat() ?: 0f)
                        .coerceIn(PRICE_PER_MILE_MIN, PRICE_PER_MILE_MAX)
                    priceMileEnabled = data.price_per_mile?.enabled ?: true
                }
            } else {
                Toast.makeText(context, "Failed to load settings", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error fetching settings", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Helper to update API ---
    fun updateSettings() {
        coroutineScope.launch {
            try {
                val payload = UserSettingsResponse(
                    score = SettingField(
                        accept = scoreAccept,
                        reject = scoreReject,
                        enabled = scoreEnabled
                    ),
                    price =  SettingField(
                        accept = priceAccept,
                        reject = priceReject,
                        enabled = priceEnabled
                    ),
                    rate = SettingField(
                        accept = rateAccept,
                        enabled = rateEnabled
                    ),
                    price_per_mile = SettingField(
                        accept = priceMileAccept,
                        reject = priceMileReject,
                        enabled = priceMileEnabled
                    )
                )
                Log.d("hacking", payload.toString())
                val response = apiService.updateUserSettings(userId, payload)
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to update settings", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error updating settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onLogout) {
                Text("Log out")
            }
        }

        // Score Slider
        ScoreRangeSlider(
            title = "SCORE",
            unit = "",
            min = 0f,
            max = 150f,
            initialStart = scoreReject,
            initialEnd = scoreAccept,
            enabled = scoreEnabled,
            onEnabledChange = { scoreEnabled = it; updateSettings() },
            onValueChange = { start, end -> scoreReject = start; scoreAccept = end; },
            onChangeFinished = {updateSettings()}
        )

        // Price Slider
        ScoreRangeSlider(
            title = "PRICE",
            unit = "£",
            min = 0f,
            max = 100f,
            initialStart = priceReject,
            initialEnd = priceAccept,
            enabled = priceEnabled,
            onEnabledChange = { priceEnabled = it; updateSettings() },
            onValueChange = { start, end -> priceReject = start; priceAccept = end; },
            onChangeFinished = {updateSettings()}
        )

        // Rate Slider
        ScoreSlider(
            title = "CUSTOMER RATING",
            value = rateAccept,
            valueRange = 3f..5f,
            enabled = rateEnabled,
            onEnabledChange = { rateEnabled = it; updateSettings() },
            onValueChange = { rateAccept = it.toFixed(1); },
            onChangeFinished = {updateSettings()}
        )

        // Price per mile
        ScoreRangeSlider(
            title = "PRICE PER MILE",
            unit = "£",
            min = PRICE_PER_MILE_MIN,
            max = PRICE_PER_MILE_MAX,
            initialStart = priceMileReject,
            initialEnd = priceMileAccept,
            enabled = priceMileEnabled,
            onEnabledChange = { priceMileEnabled = it; updateSettings() },
            onValueChange = { start, end -> priceMileReject = start; priceMileAccept = end; },
            onChangeFinished = {updateSettings()}
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    if (captureRunning) {
                        showStopCaptureConfirmDialog = true
                        return@Button
                    }
                    if (!isDriverAccessibilityEnabled(context)) {
                        showAccessibilityGate = true
                        return@Button
                    }
                    if (!ShizukuHelper.hasPermission()) {
                        pendingStartAfterShizuku = true
                        showShizukuGate = true
                        return@Button
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !Settings.canDrawOverlays(context)
                    ) {
                        Toast.makeText(
                            context,
                            "Optional: allow display over other apps for score overlays",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    beginScreenCapture()
                },
                modifier = Modifier
                    .padding(20.dp)   // 20dp padding around the button
            ) {
                Text(if (captureRunning) "CAPTURING…" else "START")
            }
        }


    }

        if (showAccessibilityGate) {
            RequirementGateOverlay(
                title = "Accessibility Service Required",
                body = "To use all features, please enable the Accessibility service for this app.",
                primaryLabel = "Enable",
                onBack = { showAccessibilityGate = false },
                onPrimary = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
        }

        if (showShizukuGate) {
            val shizukuRunning = ShizukuHelper.isServiceRunning()
            RequirementGateOverlay(
                title = if (shizukuRunning) "Shizuku permission" else "Run Shizuku",
                body = if (shizukuRunning) {
                    "Shizuku is running. Tap Grant permission and allow driverPRO in the Shizuku dialog."
                } else {
                    "Start the Shizuku app (wireless debugging or ADB), then return here and grant permission."
                },
                primaryLabel = if (shizukuRunning) "Grant permission" else "Open Shizuku",
                onBack = {
                    pendingStartAfterShizuku = false
                    showShizukuGate = false
                },
                onPrimary = {
                    when {
                        ShizukuHelper.hasPermission() -> {
                            showShizukuGate = false
                            if (pendingStartAfterShizuku) {
                                pendingStartAfterShizuku = false
                                beginScreenCapture()
                            }
                        }
                        !shizukuRunning -> ShizukuHelper.openShizukuApp(context)
                        else -> when (ShizukuHelper.requestPermission(context)) {
                            ShizukuHelper.RequestResult.ALREADY_GRANTED -> {
                                showShizukuGate = false
                                if (pendingStartAfterShizuku) {
                                    pendingStartAfterShizuku = false
                                    beginScreenCapture()
                                }
                            }
                            ShizukuHelper.RequestResult.DENIED_PERMANENTLY,
                            ShizukuHelper.RequestResult.NOT_RUNNING,
                            -> ShizukuHelper.openShizukuApp(context)
                            ShizukuHelper.RequestResult.REQUESTED -> {
                                // Shizuku system dialog — result via permission listener
                            }
                            else -> ShizukuHelper.openShizukuApp(context)
                        }
                    }
                },
            )
        }

        if (showStopCaptureConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showStopCaptureConfirmDialog = false },
                title = { Text("Stop capturing?") },
                text = { Text("Are you sure you want to stop capturing now?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showStopCaptureConfirmDialog = false
                            context.stopService(Intent(context, ScreenCaptureService::class.java))
                        },
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStopCaptureConfirmDialog = false }) {
                        Text("No")
                    }
                },
            )
        }
    }
}

@Composable
private fun RequirementGateOverlay(
    title: String,
    body: String,
    primaryLabel: String,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            TextButton(onClick = onBack) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(primaryLabel)
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * On Android 14+ (API 34), use the system flow that lets the user pick **one app** vs **entire screen**
 * before granting capture. Older versions use the legacy single-step consent.
 */
private fun screenCapturePermissionIntent(pm: MediaProjectionManager): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        pm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
    } else {
        pm.createScreenCaptureIntent()
    }

private const val PRICE_PER_MILE_MIN = 0f
private const val PRICE_PER_MILE_MAX = 15f

fun Float.toFixed(digits: Int): Float {
    val factor = 10.0.pow(digits).toFloat()
    return (round(this * factor) / factor)
}