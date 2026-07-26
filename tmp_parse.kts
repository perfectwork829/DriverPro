import com.driver.pro.service.*

val text = """
UberX Priority
Exclusive
£7.79
4.88 Verified
£0.56 est. holiday entitlement included
+£1.16 included for priority
7 min (2.7 mi)
Kings Langley Railway Station (KGL), London, WD4 8LF
10 mins (3.1 mi)
Confirm
""".trimIndent()

val ride = parseRideInfo(text, null)
println("price=${ride.price} rating=${ride.rating}")
println("pickup=${ride.pickup_time_minutes}mi=${ride.pickup_distance_value} pc=${ride.pickup_address_postcode}")
println("trip=${ride.trip_time_minutes}mi=${ride.trip_distance_value} pc=${ride.dropoff_address_postcode}")
println("type=${ride.type}")
