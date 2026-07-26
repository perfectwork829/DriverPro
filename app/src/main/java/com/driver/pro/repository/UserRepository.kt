package com.driver.pro.repository

import com.driver.pro.network.User

class UserRepository {

    suspend fun login(email: String, password: String): Result<User> {
        return login(email, password) // your existing API
    }

    suspend fun getUser(token: String): Result<User> {
        return getUser(token)
    }
}