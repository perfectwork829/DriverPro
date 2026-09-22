package com.driver.pro.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.driver.pro.BuildConfig
import com.driver.pro.RideRequest
import com.driver.pro.clearAllRequests
import com.driver.pro.getHistoryClearedAtMs
import com.driver.pro.getRideRequestArray
import com.driver.pro.getToken
import com.driver.pro.isRideAfterHistoryClear
import com.driver.pro.markHistoryCleared
import com.driver.pro.mergeRideHistory
import com.driver.pro.parseRideCreatedAtMs
import com.driver.pro.network.loadRecentRideRequest
import com.driver.pro.ui.components.RideRequestCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


fun showFilter(filterIndex: Int): String {
    val filterList = listOf("All", "Accepted Only")
    return filterList.getOrElse(filterIndex) { "Unknown" }
}


@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val rideRequestsState = remember { mutableStateOf<List<RideRequest>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val loadError = remember { mutableStateOf<String?>(null) }
    val showClearConfirm = remember { mutableStateOf(false) }

    fun applyClearedFilter(rides: List<RideRequest>): List<RideRequest> {
        val clearedAt = getHistoryClearedAtMs(context)
        return rides.filter { isRideAfterHistoryClear(it, clearedAt) }
    }

    fun loadHistory() {
        scope.launch {
            isLoading.value = true
            loadError.value = null
            val local = withContext(Dispatchers.IO) {
                getRideRequestArray(context, "RIDE-REQUESTS")?.toList().orEmpty()
            }
            val jwt = getToken(context, "JWT_TOKEN")
            if (jwt.isNullOrBlank()) {
                // Local scored rides are always shown (do not apply clear-filter here).
                rideRequestsState.value = local
                if (local.isEmpty()) {
                    loadError.value = "Not signed in — log in to load server history."
                }
                isLoading.value = false
                return@launch
            }
            val apiResult = withContext(Dispatchers.IO) {
                loadRecentRideRequest(context, jwt)
            }
            apiResult.fold(
                onSuccess = { fromApi ->
                    // Clear History must only hide old *server* rows. Local scored saves always
                    // stay visible — otherwise slash-format / unparseable API timestamps make
                    // History look empty while scores still overlay on Uber.
                    val visibleApi = applyClearedFilter(fromApi)
                    rideRequestsState.value = mergeRideHistory(local, visibleApi)
                },
                onFailure = { e ->
                    // Never hide local rows on API failure.
                    rideRequestsState.value = local
                    if (local.isEmpty()) {
                        loadError.value = e.message ?: "Could not load history"
                    }
                },
            )
            isLoading.value = false
        }
    }

    fun clearLocalHistory() {
        scope.launch {
            withContext(Dispatchers.IO) {
                clearAllRequests(context, "RIDE-REQUESTS")
                markHistoryCleared(context)
            }
            rideRequestsState.value = emptyList()
            loadError.value = null
            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
            // Reload applies the cleared-at filter so old server rows stay hidden.
            loadHistory()
        }
    }

    LaunchedEffect(Unit) {
        loadHistory()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mFilter = remember {
        mutableIntStateOf(0)
    }

    val filteredRideRequest = if (mFilter.value == 1) {
        rideRequestsState.value
            .filter { it.acceptedOrRejected == 1 }
            .sortedWith(
                compareByDescending<RideRequest> { parseRideCreatedAtMs(it.created_at) ?: 0L }
                    .thenByDescending { it.id },
            )
    } else {
        // Newest first by time (local stamps + server). Debug OCR rows stay on top.
        val byTime = compareByDescending<RideRequest> { parseRideCreatedAtMs(it.created_at) ?: 0L }
            .thenByDescending { it.id }
        val debug = rideRequestsState.value
            .filter { it.raw_text.startsWith("OCR debug", ignoreCase = true) }
            .sortedWith(byTime)
        val rest = rideRequestsState.value
            .filter { !it.raw_text.startsWith("OCR debug", ignoreCase = true) }
            .sortedWith(byTime)
        debug + rest
    }

    if (showClearConfirm.value) {
        AlertDialog(
            onDismissRequest = { showClearConfirm.value = false },
            title = { Text("Clear history?") },
            text = {
                Text(
                    "Removes all history shown on this phone (including synced server rides). " +
                        "New offers after this will still appear.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm.value = false
                        clearLocalHistory()
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm.value = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "History (v${BuildConfig.VERSION_NAME})",
                style = MaterialTheme.typography.headlineLarge,
            )

            Button(
                onClick = {
                    mFilter.intValue = 1 - mFilter.intValue
                },
                modifier = Modifier.padding(2.dp),
            ) {
                Text(showFilter(mFilter.intValue))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = { showClearConfirm.value = true },
                enabled = !isLoading.value,
            ) {
                Text("Clear History")
            }
        }

        when {
            isLoading.value -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            loadError.value != null && filteredRideRequest.isEmpty() -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = loadError.value.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { loadHistory() }) {
                        Text("Retry")
                    }
                }
            }
            filteredRideRequest.isEmpty() -> {
                val cleared = getHistoryClearedAtMs(context) > 0L
                Text(
                    text = if (cleared) {
                        "No ride history yet. Clear History is on — new scored offers will appear here after the next job."
                    } else {
                        "No ride history yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(filteredRideRequest) { rideRequest ->
                        RideRequestCard(rideRequest)
                    }
                }
            }
        }
    }
}
