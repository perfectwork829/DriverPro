package com.driver.pro.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

class AuthInterceptor(
    private val session: SessionManager,
    private val client: OkHttpClient
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        // Add access token
        val token = session.getAccessToken()
        if (token != null) {
            request = request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }

        val response = chain.proceed(request)

        // If 401 → try refresh token
        if (response.code != 401) return response

        synchronized(this) {
            val refreshToken = session.getRefreshToken() ?: return response

            val refreshRequest = Request.Builder()
                .url("https://idrivesmart.co.uk/api/token/refresh/")
                .post(RequestBody.create(
                    "application/json".toMediaType(),
                    JSONObject().put("refresh", refreshToken).toString()
                ))
                .build()

            try {
                val refreshResponse = client.newCall(refreshRequest).execute()

                if (!refreshResponse.isSuccessful) {
                    session.clearTokens()
                    return response
                }

                val body = refreshResponse.body?.string() ?: return response
                val json = JSONObject(body)
                val newAccess = json.getString("access")
                val newRefresh = json.optString("refresh", refreshToken)
                session.persistAuthTokens(newAccess, newRefresh)

                // Retry original request with new token
                val newRequest = request.newBuilder()
                    .removeHeader("Authorization")
                    .addHeader("Authorization", "Bearer $newAccess")
                    .build()

                return chain.proceed(newRequest)

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return response
    }
}