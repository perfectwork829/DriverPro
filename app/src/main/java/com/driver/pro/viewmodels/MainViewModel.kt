package com.driver.pro.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driver.pro.network.ApiService
import com.driver.pro.network.SessionManager
import com.driver.pro.network.User
import com.driver.pro.repository.UserRepository
import retrofit2.HttpException
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val repository = UserRepository()

    private val _user = MutableStateFlow<User?>(null)
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading
    val user: StateFlow<User?> = _user

    fun login(email: String, password: String) {
        viewModelScope.launch {
            val result = repository.login(email, password)
            if (result.isSuccess) {
                _user.value = result.getOrNull()
            }
        }
    }

    fun setUser(user: User) {
        _user.value = user
    }

    fun clearUser() {
        _user.value = null
    }

    fun loadUser(sessionManager: SessionManager, apiService: ApiService) {
        viewModelScope.launch {
            val token = sessionManager.getAccessToken()
            if (token == null) {
                _loading.value = false
                return@launch
            }

            try {
                val result = apiService.getUser()
                _user.value = result.user
            } catch (e: HttpException) {
                Log.e(TAG, "getUser failed: HTTP ${e.code()}", e)
                if (e.code() == 401) {
                    sessionManager.clearTokens()
                }
                _user.value = null
            } catch (e: IOException) {
                Log.e(TAG, "getUser failed: network", e)
                _user.value = null
            } catch (e: Exception) {
                Log.e(TAG, "getUser failed", e)
                _user.value = null
            } finally {
                _loading.value = false
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}