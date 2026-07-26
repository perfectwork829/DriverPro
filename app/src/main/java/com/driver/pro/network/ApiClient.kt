package com.driver.pro.network

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.driver.pro.getToken
import com.driver.pro.RideRequest
import com.driver.pro.getToken
import com.driver.pro.saveToken
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// Create a client with the necessary configuration for content negotiation
val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true }) // Ignore unknown keys during deserialization
    }
    /** Read 4xx/5xx bodies instead of throwing before `bodyAsText()`. */
    expectSuccess = false
}

/** Lenient decoding for API `user` objects (extra fields, minor type quirks). */
private val jsonLenient = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

private fun parseAuthErrorBody(body: String): String = parseApiErrorBody(body)

/** Extract a human-readable error from JSON API bodies (4xx, success:false, validation errors). */
internal fun parseApiErrorBody(body: String, httpStatus: Int? = null): String {
    val t = body.trim()
    if (t.isEmpty()) {
        return httpStatus?.let { "Empty response from server (HTTP $it)" }
            ?: "Empty response from server"
    }
    if (t.startsWith("<!DOCTYPE") || t.startsWith("<html", ignoreCase = true)) {
        return "Server returned HTML instead of JSON (wrong URL or proxy error)"
    }
    return try {
        val obj = Json.parseToJsonElement(t).jsonObject
        obj["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { return it }
        obj["error"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { return it }
        obj["detail"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { return it }
        obj["non_field_errors"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?.joinToString(" ")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val parts = mutableListOf<String>()
        obj.entries.forEach { (key, el) ->
            when (el) {
                is JsonArray -> el.forEach { item ->
                    runCatching { parts.add("$key: ${item.jsonPrimitive.content}") }
                }
                else -> runCatching {
                    val msg = el.jsonPrimitive.content
                    if (msg.isNotBlank()) parts.add("$key: $msg")
                }
            }
        }
        val joined = parts.joinToString("; ")
        if (joined.isNotBlank()) return joined
        httpStatus?.let { return "Request failed (HTTP $it)" }
        "Request failed (${t.take(160)})"
    } catch (_: Exception) {
        httpStatus?.let { return "Request failed (HTTP $it): ${t.take(200)}" }
        t.take(280)
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LoginRequest(
    /** Many backends use `email` for login; omitting it yields “To pole jest wymagane.” (field required). */
    val email: String,
    /** SimpleJWT default + backends that still expect `username` (often same value as email). */
    @SerialName("username") val username: String,
    val password: String,
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LoginResponse(
    val access: String,
    val refresh: String,
    val user: User
)



@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class User(
    val id: Int,
    val username: String = "",
    val email: String = "",
    /** Backend may omit or name differently; default keeps decode resilient. */
    val subscribed: Boolean = false,
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SettingData(
    val autoAcceptScore: Int,
    val autoRejectScore: Int,
)


suspend fun login(email: String, password: String): Result<LoginResponse> {
    return try {
        val id = email.trim()
        val httpResponse = client.post("https://idrivesmart.co.uk/api/token/") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = id, username = id, password = password))
        }
        val response = httpResponse.bodyAsText()
        Log.d("API Response", "status=${httpResponse.status.value} body=$response")

        if (httpResponse.status.value !in 200..299) {
            return Result.failure(Exception(parseAuthErrorBody(response)))
        }

        // Try to parse the response as JSON
        val json = Json.parseToJsonElement(response).jsonObject

        // Check if the response contains non_field_errors (error case)
        val errorMessages: List<String>? = json["non_field_errors"]?.jsonArray?.map { it.jsonPrimitive.content }

        if (errorMessages != null && errorMessages.isNotEmpty()) {
            return Result.failure(Exception(errorMessages.first()))
        }

        json["detail"]?.jsonPrimitive?.content?.let { detail ->
            return Result.failure(Exception(detail))
        }

        val access = json["access"]?.jsonPrimitive?.content
            ?: return Result.failure(Exception("Login response missing access token"))
        val refresh = json["refresh"]?.jsonPrimitive?.content
            ?: return Result.failure(Exception("Login response missing refresh token"))

        val user: User = if (json["user"] != null) {
            try {
                jsonLenient.decodeFromJsonElement<User>(json["user"]!!)
            } catch (e: Exception) {
                return Result.failure(Exception("Invalid user in login response: ${e.message}"))
            }
        } else {
            getUser(access).fold(
                onSuccess = { it },
                onFailure = { err ->
                    return Result.failure(Exception("Got tokens but could not load profile: ${err.message}"))
                },
            )
        }

        Result.success(LoginResponse(access = access, refresh = refresh, user = user))
    } catch (e: Exception) {
        Log.e("API Error", "Login failed: ${e.message}", e)
        return Result.failure(e)
    }
}

suspend fun updateToken(jwtToken: String?, refreshToken: String?): Result<LoginResponse> {
    return try {
        // Make the POST request to the API
        val httpResponse = client.post("https://idrivesmart.co.uk/api/token/refresh/") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "refresh": "$refreshToken"
                }
            """.trimIndent())
        }
        val response = httpResponse.bodyAsText()

        Log.d("API Response", "refresh status=${httpResponse.status.value} body=$response")

        if (httpResponse.status.value !in 200..299) {
            return Result.failure(Exception(parseAuthErrorBody(response)))
        }

        // Try to parse the response as JSON
        val json = Json.parseToJsonElement(response).jsonObject

        // Check if the response contains non_field_errors (error case)
        val errorMessages: List<String>? = json["non_field_errors"]?.jsonArray?.map { it.jsonPrimitive.content }

        if (errorMessages != null && errorMessages.isNotEmpty()) {
            // If the error message is present, return it as failure
            return Result.failure(Exception(errorMessages.first()))
        }

        json["detail"]?.jsonPrimitive?.content?.let { detail ->
            return Result.failure(Exception(detail))
        }

        val access = json["access"]?.jsonPrimitive?.content
            ?: return Result.failure(Exception("Refresh response missing access token"))
        val refresh = json["refresh"]?.jsonPrimitive?.content ?: refreshToken
            ?: return Result.failure(Exception("Refresh response missing refresh token"))

        val user: User = if (json["user"] != null) {
            try {
                jsonLenient.decodeFromJsonElement<User>(json["user"]!!)
            } catch (e: Exception) {
                return Result.failure(Exception("Invalid user in refresh response: ${e.message}"))
            }
        } else {
            getUser(access).fold(
                onSuccess = { it },
                onFailure = { err ->
                    return Result.failure(Exception("Could not load profile after refresh: ${err.message}"))
                },
            )
        }

        // Return the successful response
        Result.success(LoginResponse(access = access, refresh = refresh, user = user))


    } catch (e: Exception) {
        // Log the error details
        Log.e("API Error", "Login failed: ${e.message}")
        return Result.failure(e)
    }
}


suspend fun sendNode(jsonPayload: String) {
    try {
        val responseText = client.post("https://idrivesmart.co.uk/api/saveNode/") {
            contentType(ContentType.Application.Json)
            setBody(jsonPayload)
        }.bodyAsText()

        Log.d("API Response", responseText)

        // Parse JSON safely
        val jsonObj = try {
            Json.parseToJsonElement(responseText).jsonObject
        } catch (parseErr: Exception) {
            Log.e("API Error", "JSON parse error: ${parseErr.message}")
            return
        }

        // Example: if backend returns {"success":true}
        val success = jsonObj["success"]?.toString()
        Log.d("API Success", "$success")

    } catch (e: Exception) {
        Log.e("API Error", "Send node failed: ${e.message}")
    }
}



suspend fun sendNotificationInfo(jsonPayload: String) {
    try {
        val responseText = client.post("https://idrivesmart.co.uk/api/saveNotificationInfo/") {
            contentType(ContentType.Application.Json)
            setBody(jsonPayload)
        }.bodyAsText()

        Log.d("API Response", responseText)

        // Parse JSON safely
        val jsonObj = try {
            Json.parseToJsonElement(responseText).jsonObject
        } catch (parseErr: Exception) {
            Log.e("API Error", "JSON parse error: ${parseErr.message}")
            return
        }

        // Example: if backend returns {"success":true}
        val success = jsonObj["success"]?.toString()
        Log.d("API Success", "$success")

    } catch (e: Exception) {
        Log.e("API Error", "Send node failed: ${e.message}")
    }
}

suspend fun getUser(jwtToken: String?): Result<User> {
    return try {
        if (jwtToken.isNullOrBlank()) {
            return Result.failure(Exception("JWT token is null or empty"))
        }

        val httpResponse = client.get("https://idrivesmart.co.uk/api/user/") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $jwtToken")
        }
        val responseText = httpResponse.bodyAsText()

        Log.d("API Response", "getUser status=${httpResponse.status.value} body=$responseText")

        if (httpResponse.status.value !in 200..299) {
            return Result.failure(Exception(parseAuthErrorBody(responseText)))
        }

        val jsonObj = try {
            Json.parseToJsonElement(responseText).jsonObject
        } catch (parseErr: Exception) {
            Log.e("API Error", "JSON parse error: ${parseErr.message}")
            return Result.failure(parseErr)
        }

        jsonObj["detail"]?.jsonPrimitive?.content?.let { detail ->
            return Result.failure(Exception(detail))
        }

        val userElement = jsonObj["user"]
        if (userElement != null) {
            return try {
                val user = jsonLenient.decodeFromJsonElement<User>(userElement)
                Log.d("API Success", "User ID: ${user.id}")
                Result.success(user)
            } catch (e: Exception) {
                Log.e("API Error", "User decode failed: ${e.message}")
                Result.failure(Exception("Profile parse error: ${e.message}"))
            }
        }

        if (jsonObj["success"]?.jsonPrimitive?.boolean == false) {
            return Result.failure(Exception("User request unsuccessful"))
        }

        Log.e("API Error", "Unexpected response format: $responseText")
        Result.failure(Exception("Unexpected response (no user object)"))
    } catch (e: Exception) {
        Log.e("API Error", "API call failed: ${e.message}")
        Result.failure(Exception("API call failed: ${e.message}"))
    }
}

suspend fun calculateRideRequest(
    context: Context,
    jsonPayload: String,
    jwtToken: String?,
    @Suppress("UNUSED_PARAMETER") refreshToken: String?,
): Result<RideRequest> {
    val first = calculateRideRequestOnce(jsonPayload, jwtToken)
    if (!isTokenAuthFailure(first)) {
        return first
    }
    Log.d("API", "Access token expired — refreshing for ride scoring")
    val refreshed = refreshAndPersistTokens(context)
    if (refreshed.isFailure) {
        return first
    }
    return calculateRideRequestOnce(jsonPayload, refreshed.getOrNull()?.first)
}

private suspend fun calculateRideRequestOnce(jsonPayload: String, jwtToken: String?): Result<RideRequest> {
    return try {
        if (jwtToken.isNullOrBlank()) {
            return Result.failure(Exception("JWT token is null or empty"))
        }

        val trimmed = jsonPayload.trim()
        if (!trimmed.startsWith("\"")) {
            Log.w(
                "API",
                "ride-request body is a raw JSON object; server expects a JSON-encoded string. " +
                    "Use rideRequestHttpBody().",
            )
        }

        val firstAttempt = executeRideRequest(jwtToken, trimmed, "primary")
        if (firstAttempt.isSuccess) return firstAttempt
        val firstError = firstAttempt.exceptionOrNull()?.message.orEmpty()
        if (!isPayloadFormatError(firstError)) return firstAttempt

        val fallbackBody = chooseFallbackRideBody(trimmed, firstError)
        if (fallbackBody == trimmed) return firstAttempt
        Log.w("API", "Retrying ride-request with fallback body format")
        executeRideRequest(jwtToken, fallbackBody, "fallback")
    } catch (e: Exception) {
        Log.e("API Error", "API call failed: ${e.message}", e)
        Result.failure(Exception("API call failed: ${e.message ?: e.javaClass.simpleName}"))
    }
}

private suspend fun executeRideRequest(
    jwtToken: String,
    body: String,
    attemptLabel: String,
): Result<RideRequest> {
    Log.d("API Request", "ride-request $attemptLabel body length=${body.length}")
    logLongPayload("ride-request-payload", "apiPostBody[$attemptLabel]=", body)

    val httpResponse = client.post("https://idrivesmart.co.uk/api/ride-request/") {
        contentType(ContentType.Application.Json)
        header("Authorization", "Bearer $jwtToken")
        setBody(body)
    }
    val responseText = httpResponse.bodyAsText()
    val status = httpResponse.status.value

    Log.d("API Response", "ride-request $attemptLabel status=$status body=$responseText")

    if (status == 401 || isTokenExpiredResponseBody(responseText)) {
        return Result.failure(Exception("Token expired or invalid"))
    }

    if (status !in 200..299) {
        return Result.failure(Exception(parseApiErrorBody(responseText, status)))
    }

    val jsonObj = try {
        Json.parseToJsonElement(responseText).jsonObject
    } catch (parseErr: Exception) {
        Log.e("API Error", "JSON parse error: ${parseErr.message}")
        return Result.failure(
            Exception("Server returned invalid JSON: ${parseErr.message ?: "parse error"}"),
        )
    }

    if (jsonObj["success"]?.jsonPrimitive?.boolean == true) {
        val dataObj = jsonObj["data"]?.jsonObject
        val rideRequestResponse = dataObj?.get("ride_request")?.jsonObject
            ?: return Result.failure(Exception("Server success but ride_request missing in data"))

        return try {
            val rideRequestObj = jsonLenient.decodeFromJsonElement<RideRequest>(rideRequestResponse)
            Log.d("API Success", "Ride Request ID: ${rideRequestObj.id}")
            Result.success(rideRequestObj)
        } catch (e: Exception) {
            Log.e("API Error", "Ride request decode failed: ${e.message}")
            Result.failure(
                Exception("Could not read scored ride from server: ${e.message ?: "decode error"}"),
            )
        }
    }

    if (isTokenExpiredResponseBody(responseText)) {
        return Result.failure(Exception("Token expired or invalid"))
    }

    return Result.failure(Exception(parseApiErrorBody(responseText, status)))
}

private fun isPayloadFormatError(message: String): Boolean {
    val m = message.lowercase()
    return m.contains("json object must be str") ||
        m.contains("no attribute 'droplast'") ||
        m.contains("no attribute \"droplast\"") ||
        m.contains("dropLast")
}

private fun chooseFallbackRideBody(currentBody: String, errorMessage: String): String {
    val normalized = errorMessage.lowercase()
    val rawObject = decodeJsonStringBody(currentBody)
    return when {
        normalized.contains("no attribute 'droplast'") || normalized.contains("dropLast") -> rawObject
        normalized.contains("json object must be str") -> encodeAsJsonStringBody(rawObject)
        currentBody.trim().startsWith("\"") -> rawObject
        else -> encodeAsJsonStringBody(rawObject)
    }
}

private fun decodeJsonStringBody(body: String): String {
    val trimmed = body.trim()
    if (!trimmed.startsWith("\"")) return trimmed
    return runCatching { Json.parseToJsonElement(trimmed).jsonPrimitive.content }
        .getOrDefault(trimmed)
}

private fun encodeAsJsonStringBody(rawObject: String): String = org.json.JSONObject.quote(rawObject)

suspend fun loadRecentRideRequest(
    context: Context,
    jwtToken: String?,
): Result<List<RideRequest>> {
    val first = loadRecentRideRequestOnce(jwtToken)
    if (!isTokenAuthFailure(first)) {
        return first
    }
    Log.d("API", "Access token expired — refreshing for history")
    val refreshed = refreshAndPersistTokens(context)
    if (refreshed.isFailure) {
        return first
    }
    return loadRecentRideRequestOnce(refreshed.getOrNull()?.first)
}

private suspend fun loadRecentRideRequestOnce(jwtToken: String?): Result<List<RideRequest>> {
    return try {
        if (jwtToken.isNullOrBlank()) {
            return Result.failure(Exception("JWT token is null or empty"))
        }

        val httpResponse = client.get("https://idrivesmart.co.uk/api/recent-ride-request/") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $jwtToken")
        }
        val responseText = httpResponse.bodyAsText()

        Log.d("API Response", "recent-ride status=${httpResponse.status.value} body=$responseText")

        if (httpResponse.status.value == 401 || isTokenExpiredResponseBody(responseText)) {
            return Result.failure(Exception("Token expired or invalid"))
        }

        if (httpResponse.status.value !in 200..299) {
            return Result.failure(Exception(parseAuthErrorBody(responseText)))
        }

        val jsonObj = try {
            Json.parseToJsonElement(responseText).jsonObject
        } catch (parseErr: Exception) {
            Log.e("API Error", "JSON parse error: ${parseErr.message}")
            return Result.failure(parseErr)
        }

        if (jsonObj["success"]?.jsonPrimitive?.boolean == true) {
            val dataArray = jsonObj["data"]?.jsonArray
            if (dataArray != null) {
                val rideRequestList = jsonLenient.decodeFromJsonElement<List<RideRequest>>(dataArray)
                Log.d("API Success", "Ride Requests Count: ${rideRequestList.size}")
                Result.success(rideRequestList)
            } else {
                Log.e("API Error", "Ride request data missing in response")
                Result.failure(Exception("Ride request data missing"))
            }
        } else if (isTokenExpiredResponseBody(responseText)) {
            Result.failure(Exception("Token expired or invalid"))
        } else {
            Log.e("API Error", "Unexpected response format: $responseText")
            Result.failure(Exception("Unexpected response"))
        }
    } catch (e: Exception) {
        Log.e("API Error", "API call failed: ${e.message}")
        Result.failure(Exception("API call failed: ${e.message}"))
    }
}



suspend fun updateSetting(jsonPayload: String, jwtToken: String?, refreshToken: String?): Result<SettingData> {
    return try {
        if (jwtToken.isNullOrBlank()) {
            return Result.failure(Exception("JWT token is null or empty"))
        }

        // Make the POST request with JWT token in the Authorization header
        val responseText = client.post("https://idrivesmart.co.uk/api/update-setting/") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $jwtToken") // Add Authorization header
            setBody(jsonPayload)
        }.bodyAsText()

        Log.d("API Response", responseText)

        // Parse JSON safely
        val jsonObj = try {
            Json.parseToJsonElement(responseText).jsonObject
        } catch (parseErr: Exception) {
            Log.e("API Error", "JSON parse error: ${parseErr.message}")
            return Result.failure(parseErr)  // Return specific error if JSON parsing fails
        }

        // Check if the success flag is true
        if (jsonObj["success"]?.jsonPrimitive?.boolean == true) {
            val dataObj = jsonObj["data"]?.jsonObject
            if (dataObj != null) {
                // Extract and map to User object
                val jsonParser = Json { ignoreUnknownKeys = true }
                val settingDataObj = jsonParser.decodeFromJsonElement<SettingData>(dataObj)
                Log.d("API Success", "Ride Request ID: ${settingDataObj.autoAcceptScore}")
                Result.success(settingDataObj)  // Return user data as success
            } else {
                // Handle missing user data in the response
                Log.e("API Error", "Ride request data missing in response")
                Result.failure(Exception("Ride request data missing"))
            }

        } else if (jsonObj["detail"]?.jsonPrimitive?.content == "Given token not valid for any token type") {
            // Token expired or invalid
            Log.e("API Error", "Token expired or invalid: ${jsonObj["detail"]?.jsonPrimitive?.content}")

            // Update access token

            Result.failure(Exception("Token expired or invalid"))

        } else {
            // Handle other cases (unexpected success response format)
            Log.e("API Error", "Unexpected response format: $responseText")
            Result.failure(Exception("Unexpected response"))
        }

    } catch (e: Exception) {
        // Handle any other exceptions (network issues, etc.)
        Log.e("API Error", "API call failed: ${e.message}")
        Result.failure(Exception("API call failed: ${e.message}"))
    }


}


suspend fun getSetting(jwtToken: String?, refreshToken: String?): Result<SettingData> {
    return try {
        if (jwtToken.isNullOrBlank()) {
            return Result.failure(Exception("JWT token is null or empty"))
        }

        // Make the POST request with JWT token in the Authorization header
        val responseText = client.get("https://idrivesmart.co.uk/api/get-setting/") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $jwtToken") // Add Authorization header
        }.bodyAsText()

        Log.d("API Response", responseText)

        // Parse JSON safely
        val jsonObj = try {
            Json.parseToJsonElement(responseText).jsonObject
        } catch (parseErr: Exception) {
            Log.e("API Error", "JSON parse error: ${parseErr.message}")
            return Result.failure(parseErr)  // Return specific error if JSON parsing fails
        }

        // Check if the success flag is true
        if (jsonObj["success"]?.jsonPrimitive?.boolean == true) {
            val dataObj = jsonObj["data"]?.jsonObject
            if (dataObj != null) {
                // Extract and map to User object
                val jsonParser = Json { ignoreUnknownKeys = true }
                val settingDataObj = jsonParser.decodeFromJsonElement<SettingData>(dataObj)
                Log.d("API Success", "Ride Request ID: ${settingDataObj.autoAcceptScore}")
                Result.success(settingDataObj)  // Return user data as success
            } else {
                // Handle missing user data in the response
                Log.e("API Error", "Ride request data missing in response")
                Result.failure(Exception("Ride request data missing"))
            }

        } else if (jsonObj["detail"]?.jsonPrimitive?.content == "Given token not valid for any token type") {
            // Token expired or invalid
            Log.e("API Error", "Token expired or invalid: ${jsonObj["detail"]?.jsonPrimitive?.content}")

            // Update access token

            Result.failure(Exception("Token expired or invalid"))

        } else {
            // Handle other cases (unexpected success response format)
            Log.e("API Error", "Unexpected response format: $responseText")
            Result.failure(Exception("Unexpected response"))
        }

    } catch (e: Exception) {
        // Handle any other exceptions (network issues, etc.)
        Log.e("API Error", "API call failed: ${e.message}")
        Result.failure(Exception("API call failed: ${e.message}"))
    }


}


object ApiClient {

    fun create(sessionManager: SessionManager): ApiService {

        // 1️⃣ Logging interceptor for debugging
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // logs request & response body, headers, URL
        }

        // 2️⃣ OkHttp client with both Auth + Logging
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionManager, OkHttpClient())) // adds token
            .addInterceptor(logging) // logs all requests/responses
            .build()

        // 3️⃣ Retrofit instance
        return Retrofit.Builder()
            .baseUrl("https://idrivesmart.co.uk/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

interface ApiService {

    @GET("api/users/{userId}/postcode-enable/")
    suspend fun getPostcodeSetting(@Path("userId") userId: Int): PostcodeSettingResponse

    @PUT("api/users/{userId}/postcode-enable/")
    suspend fun updatePostcodeSetting(
        @Path("userId") userId: Int,
        @Body body: Map<String, Boolean>
    ): PostcodeSettingResponse

    @GET("api/postcodes/")
    suspend fun getPostcodes(
        @Query("area") area: String
    ): List<PostcodeResponse>

    @GET("api/users/{userId}/postcodes/")
    suspend fun getUserPostcodes(
        @Path("userId") userId: Int,
        @Query("area") area: String
    ): List<UserPostcodeResponse>

    @POST("api/users/{userId}/postcodes/")
    suspend fun updateUserPostcode(
        @Path("userId") userId: Int,
        @Body body: Map<String, String>
    )

    // GET user settings
    @GET("api/users/{user_id}/settings/")
    suspend fun getUserSettings(
        @Path("user_id") userId: Int
    ): Response<UserSettingsResponse>

    // PUT user settings
    @PUT("api/users/{user_id}/settings/")
    suspend fun updateUserSettings(
        @Path("user_id") userId: Int,
        @Body payload: UserSettingsResponse
    ): Response<UserSettingsResponse>

    // Start scren

    @GET("api/cities/")
    suspend fun getCities(): List<City>

    @GET("api/users/{userId}/cities/")
    suspend fun getUserCities(
        @Path("userId") userId: Int
    ): List<UserCityResponse>

    @POST("api/users/{userId}/cities/")
    suspend fun updateUserCity(
        @Path("userId") userId: Int,
        @Body body: Map<String, Int>
    )

    @GET("api/users/{userId}/price-per-mile/")
    suspend fun getPricePerMile(
        @Path("userId") userId: Int
    ): List<PricePerMileResponse>

    @POST("api/users/{userId}/price-per-mile/")
    suspend fun updatePricePerMile(
        @Path("userId") userId: Int,
        @Body body: Map<String, String>
    )

    @POST("api/token/")
    suspend fun login(@Body body: Map<String, String>): TokenResponse

    @GET("api/user/")
    suspend fun getUser(): UserResponse
}

/* Response data classes */
data class PostcodeSettingResponse(val id: Int, val user: Int, val enable: Boolean)
data class TokenResponse(val access: String, val refresh: String)


data class UserResponse(val success: Boolean, val user: User)

data class PostcodeResponse(
    val full_code: String,
    val area: String = "",
)

data class UserPostcodeResponse(
    val full_code: String,
    val status: String,
    val area: String = "",
)

data class SettingField(
    val accept: Float? = null,
    val reject: Float? = null,
    val enabled: Boolean = true
)

data class UserSettingsResponse(
    val score: SettingField? = null,
    val price: SettingField? = null,
    val rate: SettingField? = null,
    val price_per_mile: SettingField? = null
)

data class City(
    val id: Int,
    val name: String,
    val type: String
)

data class UserCityResponse(
    val id: Int,
    val user: Int,
    val city: City
)

data class PricePerMileResponse(
    val id: Int,
    val user: Int,
    /** API may return JSON number (0.5) or string ("0.50"). */
    val price_per_mile: Double,
)