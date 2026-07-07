package com.plusgroup.pos.network

import com.plusgroup.pos.network.models.ApiMessageResponse
import com.plusgroup.pos.network.models.LoginRequest
import com.plusgroup.pos.network.models.LoginResponse
import com.plusgroup.pos.network.models.RegisterDeviceRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("agent/register-device")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest): Response<ApiMessageResponse>
}
