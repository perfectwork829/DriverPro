package com.driver.pro.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.driver.pro.network.ApiService

class StartScreenViewModelFactory(
    private val api: ApiService
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StartScreenViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StartScreenViewModel(api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}