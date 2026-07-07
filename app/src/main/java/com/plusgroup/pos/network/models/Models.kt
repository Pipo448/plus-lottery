package com.plusgroup.pos.network.models

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val accessToken: String? = null,
    val token: String? = null,
    val data: LoginResponseData? = null,
) {
    fun resolveToken(): String? =
        accessToken ?: token ?: data?.accessToken ?: data?.token
}

data class LoginResponseData(
    val accessToken: String? = null,
    val token: String? = null,
)

data class RegisterDeviceRequest(
    val deviceId: String
)

data class ApiMessageResponse(
    val success: Boolean? = null,
    val message: String? = null
)