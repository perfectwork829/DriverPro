package com.driver.pro.viewmodels

import androidx.lifecycle.ViewModel
import com.driver.pro.repository.SettingsRepository

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.widget.Toast
import com.driver.pro.network.SettingData


class SettingsViewModel : ViewModel() {

    private val repository = SettingsRepository()

    var rejectScore by mutableStateOf(30)
    var acceptScore by mutableStateOf(70)

    var rejectPrice by mutableStateOf(5)
    var acceptPrice by mutableStateOf(20)

    var rating by mutableStateOf(4.5f)

    fun loadSettings(token: String, refresh: String) {
        viewModelScope.launch {
            val result = repository.getSettings(token, refresh)
            result.getOrNull()?.let {
                rejectScore = it.autoRejectScore.toInt()
                acceptScore = it.autoAcceptScore.toInt()
            }
        }
    }

    fun saveSettings(token: String, refresh: String, context: Context) {
        viewModelScope.launch {
            val result = repository.updateSettings(
                SettingData(acceptScore, rejectScore),
                token,
                refresh
            )

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (result.isSuccess) "Saved" else "Failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}