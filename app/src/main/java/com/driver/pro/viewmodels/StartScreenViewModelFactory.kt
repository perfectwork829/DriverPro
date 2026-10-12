package com.driver.pro.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.driver.pro.network.ApiService

class StartScreenViewModelFactory(
    private val api: ApiService,
    private val appContext: Context,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StartScreenViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StartScreenViewModel(api, appContext.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
