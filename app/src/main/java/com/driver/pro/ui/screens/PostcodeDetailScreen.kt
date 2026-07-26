package com.driver.pro.ui.screens

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.driver.pro.network.ApiService
import com.driver.pro.network.PostcodeResponse
import com.driver.pro.utils.defaultOutwardCodesForRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

data class UserPostcode(
    val full_code: String,
    var status: String,
)

private val sectionDotColors = listOf(
    Color(0xFF7B1FA2),
    Color(0xFFE65100),
    Color(0xFF2E7D32),
    Color(0xFF1565C0),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostcodeDetailScreen(
    postcode: String,
    navController: NavController,
    userId: Int,
    apiService: ApiService,
) {
    val coroutineScope = rememberCoroutineScope()
    val mapRegion = remember(postcode) { postcode.trim().uppercase() }
    val sections = remember(mapRegion) { sectionsForMapRegion(mapRegion) }

    val postcodes = remember { mutableStateListOf<PostcodeResponse>() }
    val userPostcodes = remember { mutableStateListOf<UserPostcode>() }
    var isLoading by remember { mutableStateOf(true) }
    var loadMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mapRegion) {
        isLoading = true
        loadMessage = null
        try {
            val fromApi = withContext(Dispatchers.IO) {
                apiService.getPostcodes(mapRegion)
            }
            val list = fromApi.ifEmpty {
                Log.w("POSTCODE_API", "API returned 0 codes for $mapRegion — using defaults")
                defaultOutwardCodesForRegion(mapRegion)
            }
            postcodes.clear()
            postcodes.addAll(list)

            val userData = withContext(Dispatchers.IO) {
                try {
                    apiService.getUserPostcodes(userId, mapRegion)
                } catch (e: Exception) {
                    Log.w("POSTCODE_API", "User postcodes load failed: ${e.message}")
                    emptyList()
                }
            }
            userPostcodes.clear()
            userPostcodes.addAll(
                userData.map { UserPostcode(full_code = it.full_code, status = it.status) },
            )
            if (fromApi.isEmpty() && list.isNotEmpty()) {
                loadMessage = "Showing default codes (server list was empty)."
            }
            Log.d("POSTCODE_API", "Showing ${postcodes.size} codes for $mapRegion (api=${fromApi.size})")
        } catch (e: HttpException) {
            Log.e("POSTCODE_API", "HTTP ${e.code()} for $mapRegion", e)
            val fallback = defaultOutwardCodesForRegion(mapRegion)
            postcodes.clear()
            postcodes.addAll(fallback)
            loadMessage = if (fallback.isEmpty()) {
                "Could not load postcodes (HTTP ${e.code()})."
            } else {
                "Server error — showing default codes offline."
            }
        } catch (e: Exception) {
            Log.e("POSTCODE_API", "Error ${e.message}", e)
            val fallback = defaultOutwardCodesForRegion(mapRegion)
            postcodes.clear()
            postcodes.addAll(fallback)
            loadMessage = if (fallback.isEmpty()) {
                "Could not load postcodes."
            } else {
                "Offline — showing default codes."
            }
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Postcode $mapRegion") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                loadMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                sections.forEachIndexed { sectionIndex, section ->
                    if (sectionIndex > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    val allDistinct = postcodes.distinctBy { it.full_code }
                    val codesInSection = allDistinct.filter { pc ->
                        val district = pc.area.ifBlank {
                            outwardDistrict(pc.full_code)
                        }
                        district == section.district
                    }
                    val displayCodes = codesInSection.ifEmpty {
                        if (sections.size == 1) allDistinct else emptyList()
                    }

                    if (displayCodes.isEmpty() && sections.size == 1) {
                        Text(
                            "No postcodes found for this area.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        return@forEachIndexed
                    }
                    if (displayCodes.isEmpty()) {
                        return@forEachIndexed
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    sectionDotColors[sectionIndex % sectionDotColors.size],
                                    CircleShape,
                                ),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = section.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }

                    displayCodes.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            pair.forEach { item ->
                                PostcodeStatusRow(
                                    code = item.full_code,
                                    initialStatus = userPostcodes
                                        .find { it.full_code == item.full_code }
                                        ?.status ?: "decide",
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    onStatusChange = { code, newStatus ->
                                        val existing = userPostcodes.indexOfFirst { it.full_code == code }
                                        if (existing >= 0) {
                                            userPostcodes[existing].status = newStatus
                                        } else {
                                            userPostcodes.add(UserPostcode(code, newStatus))
                                        }
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                apiService.updateUserPostcode(
                                                    userId,
                                                    mapOf(
                                                        "user" to userId.toString(),
                                                        "postcode" to code,
                                                        "status" to newStatus,
                                                    ),
                                                )
                                            } catch (e: Exception) {
                                                Log.e("POSTCODE_API", "Update failed ${e.message}")
                                            }
                                        }
                                    },
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostcodeStatusRow(
    code: String,
    initialStatus: String,
    modifier: Modifier = Modifier,
    onStatusChange: (String, String) -> Unit,
) {
    var status by remember(code) { mutableStateOf(initialStatus) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = code,
            color = Color(0xFF1565C0),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                status = when (status) {
                    "decide" -> "accept"
                    "accept" -> "reject"
                    "reject" -> "decide"
                    else -> "decide"
                }
                onStatusChange(code, status)
            },
        ) {
            Text(
                when (status) {
                    "accept" -> "✔️"
                    "reject" -> "❌"
                    else -> "➖"
                },
                fontSize = 18.sp,
            )
        }
    }
}
