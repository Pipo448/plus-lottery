package com.plusgroup.pos.network

import com.plusgroup.pos.network.models.AgentBalance
import com.plusgroup.pos.network.models.AgentProfile
import com.plusgroup.pos.network.models.ApiDataResponse
import com.plusgroup.pos.network.models.ApiListResponse
import com.plusgroup.pos.network.models.ApiMessageResponse
import com.plusgroup.pos.network.models.Draw
import com.plusgroup.pos.network.models.LotteryGame
import com.plusgroup.pos.network.models.LoginRequest
import com.plusgroup.pos.network.models.LoginResponse
import com.plusgroup.pos.network.models.RegisterDeviceRequest
import com.plusgroup.pos.network.models.SellTicketRequest
import com.plusgroup.pos.network.models.SellTicketResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("agent/register-device")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest): Response<ApiMessageResponse>

    // Pwofil ajan konekte a (non konplè, elatriye) — pou tèt Dashboard la.
    @GET("agent/profile")
    suspend fun getProfile(): Response<ApiDataResponse<AgentProfile>>

    // Balans/kredi/limit ajan an — pou kat Dashboard la.
    @GET("agent/balance")
    suspend fun getBalance(): Response<ApiDataResponse<AgentBalance>>

    @GET("agent/draws")
    suspend fun getDraws(@Query("status") status: String = "open"): Response<ApiListResponse<Draw>>

    @GET("agent/games")
    suspend fun getGames(): Response<ApiListResponse<LotteryGame>>

    @POST("agent/tickets")
    suspend fun sellTicket(@Body body: SellTicketRequest): Response<SellTicketResponse>
}
