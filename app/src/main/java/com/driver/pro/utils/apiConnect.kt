package com.driver.pro.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL


suspend fun fetchApi(urlString: String, token: String): String = withContext(Dispatchers.IO) {
    val url = URL(urlString)
    val conn = url.openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

suspend fun postApi(urlString: String, payload: String, token: String, method: String): String = withContext(Dispatchers.IO) {
    val url = URL(urlString)
    val conn = url.openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.outputStream.use { it.write(payload.toByteArray()) }
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
