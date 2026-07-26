package com.driver.pro.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.driver.pro.RideRequest

fun showResult(acceptedOrRejected: Int): String {
    return when (acceptedOrRejected) {
        -1 -> "Rejected"
        1 -> "Accepted"
        else -> "No decided"
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied raw OCR text", Toast.LENGTH_SHORT).show()
}

@Composable
fun RideRequestCard(rideRequest: RideRequest) {
    val context = LocalContext.current
    val isDebug = rideRequest.raw_text.startsWith("OCR debug", ignoreCase = true)
    var expanded by remember { mutableStateOf(isDebug) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            if (isDebug) {
                Text(
                    "⚠ OCR DEBUG — tap to view raw text",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text("ID-${rideRequest.id} : ${rideRequest.created_at}", fontSize = 16.sp, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Price/Rate: ${rideRequest.price}(${String.format(java.util.Locale.US, "%.2f", rideRequest.rating)})",
                fontSize = 18.sp,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Pickup: ${rideRequest.pickup_time_minutes}(${rideRequest.pickup_distance_value})(${rideRequest.pickup_address_postcode})", fontSize = 14.sp, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Drop: ${rideRequest.trip_time_minutes}(${rideRequest.trip_distance_value})(${rideRequest.dropoff_address_postcode})", fontSize = 14.sp, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Score: ${rideRequest.final_score ?: "N/A"} - ${showResult(rideRequest.acceptedOrRejected)}", fontSize = 14.sp, style = MaterialTheme.typography.bodyMedium)

            if (rideRequest.raw_text.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (expanded) "Hide raw OCR" else "Show raw OCR",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                    TextButton(onClick = {
                        copyToClipboard(context, "DriverPro OCR", rideRequest.raw_text)
                    }) {
                        Text("Copy raw OCR", fontSize = 13.sp)
                    }
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rideRequest.raw_text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
