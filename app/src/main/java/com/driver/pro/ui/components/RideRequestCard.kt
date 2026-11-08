package com.driver.pro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.driver.pro.RideRequest
import com.driver.pro.utils.displayedFormulaScore
import com.driver.pro.utils.formatOfferEarningsLine

internal fun formatHistoryCreatedAt(raw: String?): String? {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return null
    var normalized = t.take(19).replace('T', ' ').replace('/', '-')
    if (normalized.length == 16) normalized = "$normalized:00"
    val parsed = runCatching {
        java.time.LocalDateTime.parse(
            normalized,
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        )
    }.getOrNull()
    return parsed?.format(
        java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", java.util.Locale.UK),
    )
        ?: t
}

fun showResult(acceptedOrRejected: Int): String {
    return when (acceptedOrRejected) {
        -1 -> "Rejected"
        1 -> "Accepted"
        else -> "No decided"
    }
}

/** One decimal so 8.4 stays 8.4 and 3.4000000000000004 is not shown. */
private fun formatHistoryMiles(miles: Double?): String {
    if (miles == null) return ""
    return String.format(java.util.Locale.US, "%.1f", miles)
}

@Composable
fun RideRequestCard(rideRequest: RideRequest) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ID-${rideRequest.id}", fontSize = 16.sp, style = MaterialTheme.typography.bodyMedium)
            formatHistoryCreatedAt(rideRequest.created_at)?.let { created ->
                Spacer(modifier = Modifier.height(2.dp))
                Text("Created: $created", fontSize = 13.sp, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Price/Rate: ${String.format(java.util.Locale.US, "%.2f", rideRequest.price)}(${String.format(java.util.Locale.US, "%.2f", rideRequest.rating)})",
                fontSize = 18.sp,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Pickup: ${rideRequest.pickup_time_minutes}(${formatHistoryMiles(rideRequest.pickup_distance_value)})(${rideRequest.pickup_address_postcode})",
                fontSize = 14.sp,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Drop: ${rideRequest.trip_time_minutes}(${formatHistoryMiles(rideRequest.trip_distance_value)})(${rideRequest.dropoff_address_postcode})",
                fontSize = 14.sp,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val shownScore = displayedFormulaScore(rideRequest) ?: rideRequest.final_score
            Text(
                "Score: ${shownScore ?: "N/A"} - ${showResult(rideRequest.acceptedOrRejected)}",
                fontSize = 14.sp,
                style = MaterialTheme.typography.bodyMedium,
            )
            formatOfferEarningsLine(rideRequest)?.let { earnings ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    earnings,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
