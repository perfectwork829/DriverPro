package com.driver.pro.repository

import com.driver.pro.network.SettingData
import com.driver.pro.network.getSetting
import com.driver.pro.network.updateSetting
import com.google.gson.Gson

class SettingsRepository {

    suspend fun getSettings(token: String?, refresh: String?) =
        getSetting(token, refresh)

    suspend fun updateSettings(data: SettingData, token: String?, refresh: String?) =
        updateSetting(Gson().toJson(data), token, refresh)
}