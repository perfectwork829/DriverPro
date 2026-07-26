package com.driver.pro.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {

    private val appContext = context.applicationContext

    // Create MasterKey instance (modern, non-deprecated)
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        "user_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAccessToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)

    fun saveRefreshToken(token: String) {
        prefs.edit().putString("refresh_token", token).apply()
    }

    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    /** Writes access (+ optional refresh) to encrypted prefs and legacy `app_prefs`. */
    fun persistAuthTokens(access: String, refresh: String? = null) {
        saveAccessToken(access)
        if (refresh != null) {
            saveRefreshToken(refresh)
        }
        val legacy = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
        legacy.putString("JWT_TOKEN", access)
        if (refresh != null) {
            legacy.putString("REFRESH_TOKEN", refresh)
        }
        legacy.apply()
    }

    fun clearTokens() {
        prefs.edit().remove("access_token").remove("refresh_token").apply()
        appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("JWT_TOKEN")
            .remove("REFRESH_TOKEN")
            .apply()
    }

}