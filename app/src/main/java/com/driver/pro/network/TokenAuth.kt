package com.driver.pro.network

import android.content.Context
import com.driver.pro.getToken
import com.driver.pro.saveToken
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** True when the API body indicates an expired or invalid JWT access token. */
fun isTokenExpiredResponseBody(body: String): Boolean {
    if (body.isBlank()) return false
    return try {
        val detail = kotlinx.serialization.json.Json
            .parseToJsonElement(body)
            .jsonObject["detail"]
            ?.jsonPrimitive
            ?.content
            .orEmpty()
        detail.contains("token", ignoreCase = true) ||
            detail.contains("not valid", ignoreCase = true) ||
            detail.contains("expired", ignoreCase = true)
    } catch (_: Exception) {
        false
    }
}

fun isTokenAuthFailure(result: Result<*>): Boolean {
    val msg = result.exceptionOrNull()?.message.orEmpty()
    return msg.contains("token", ignoreCase = true) ||
        msg.contains("not valid", ignoreCase = true) ||
        msg.contains("expired", ignoreCase = true)
}

/**
 * Refresh JWT using the stored refresh token and persist access + refresh to
 * [SessionManager] and legacy `app_prefs` (JWT_TOKEN / REFRESH_TOKEN).
 */
suspend fun refreshAndPersistTokens(
    context: Context,
    sessionManager: SessionManager? = null,
): Result<Pair<String, String>> {
    val refresh = getToken(context, "REFRESH_TOKEN")
        ?: sessionManager?.getRefreshToken()
        ?: return Result.failure(Exception("No refresh token — please log in again"))

    return updateToken(null, refresh).mapCatching { login ->
        if (sessionManager != null) {
            sessionManager.persistAuthTokens(login.access, login.refresh)
        } else {
            persistLegacyTokens(context, login.access, login.refresh)
        }
        login.access to login.refresh
    }
}

private fun persistLegacyTokens(context: Context, access: String, refresh: String) {
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("JWT_TOKEN", access)
        .putString("REFRESH_TOKEN", refresh)
        .apply()
}

/** After login or refresh — keep encrypted session + legacy prefs in sync. */
fun persistAuthAfterLogin(
    context: Context,
    sessionManager: SessionManager,
    access: String,
    refresh: String,
    user: com.driver.pro.network.User,
) {
    sessionManager.persistAuthTokens(access, refresh)
    saveToken(context, access, refresh, user)
}
