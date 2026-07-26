package com.driver.pro.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsCompat
import com.driver.pro.BottomNavItem
import com.driver.pro.R
import com.driver.pro.network.User
import com.driver.pro.service.ACTION_DRIVERPRO_CAPTURE_STARTED
import com.driver.pro.service.ACTION_DRIVERPRO_CAPTURE_STOPPED
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import com.driver.pro.network.ApiClient
import com.driver.pro.network.SessionManager

private val BottomNavIconInnerSize = 30.dp
private val BottomNavHitCircleSize = 54.dp
private val BottomNavSelectedCircleColor = Color(0xFFE0E0E0)

private const val NAV_TAB_BITMAP_DECODE_VERSION = 1

private fun decodeNavTabPng(resources: android.content.res.Resources, resId: Int): androidx.compose.ui.graphics.ImageBitmap? {
    val decoded = BitmapFactory.decodeResource(resources, resId) ?: return null
    val argb = decoded.copy(Bitmap.Config.ARGB_8888, true)
    decoded.recycle()
    return argb.asImageBitmap()
}

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    user: User,
    navController: NavController,
    onLogout: () -> Unit,
) {

    val context = LocalContext.current

    val sessionManager = remember { SessionManager(context) }
    val apiService = remember { ApiClient.create(sessionManager) }

    val tabs = listOf(
        BottomNavItem.Licence,
        BottomNavItem.AcceptReject,
        BottomNavItem.Filters,
        BottomNavItem.PostcodeMap,
    )

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    val insets = WindowInsetsCompat.toWindowInsetsCompat(LocalView.current.rootWindowInsets)
        .getInsets(WindowInsetsCompat.Type.systemBars())
    val density = LocalDensity.current

    var capturingOverlayVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_DRIVERPRO_CAPTURE_STARTED -> capturingOverlayVisible = true
                    ACTION_DRIVERPRO_CAPTURE_STOPPED -> capturingOverlayVisible = false
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

    Scaffold(
        modifier = Modifier
            .padding(top = with(density) { insets.top.toDp() })
            .fillMaxSize(),
        bottomBar = {
            Surface(
                color = Color(0xFFFFFFFF),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = 12.dp,
                            bottom = with(density) { insets.bottom.toDp() } + 8.dp,
                        ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = pagerState.currentPage == index

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                            ) {
                                BottomTabNavImage(
                                    tabIndex = index,
                                    contentDescription = tab.label,
                                    selected = isSelected,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = pagerState.currentPage != 3,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> StartScreen(user, apiService)
                    1 -> HistoryScreen()
                    2 -> SettingsScreen(
                        sessionManager = sessionManager,
                        apiService = apiService,
                        userId = user.id,
                        onLogout = onLogout,
                    )
                    3 -> PostcodeScreen(
                        navController = navController,
                        sessionManager = sessionManager,
                        apiService = apiService,
                        userId = user.id,
                    )
                }
            }

            if (capturingOverlayVisible) {
                Text(
                    text = "Capturing...",
                    color = Color.Red,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 4.dp)
                        .zIndex(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomTabNavImage(
    tabIndex: Int,
    contentDescription: String,
    selected: Boolean,
) {
    val context = LocalContext.current
    val resId = when (tabIndex) {
        0 -> R.drawable.nav_tab_gear
        1 -> R.drawable.nav_tab_folder
        2 -> R.drawable.nav_tab_sliders
        else -> R.drawable.nav_tab_map
    }
    val imageBitmap = remember(resId, NAV_TAB_BITMAP_DECODE_VERSION) {
        decodeNavTabPng(context.resources, resId)
    }
    Box(
        modifier = Modifier
            .size(BottomNavHitCircleSize)
            .then(
                if (selected) {
                    Modifier.background(color = BottomNavSelectedCircleColor, shape = CircleShape)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                modifier = Modifier.size(BottomNavIconInnerSize),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
            )
        }
    }
}
