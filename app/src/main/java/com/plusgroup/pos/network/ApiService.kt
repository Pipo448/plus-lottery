package com.plusgroup.pos.network

import com.plusgroup.pos.network.models.AgentBalance
import com.plusgroup.pos.network.models.AgentPrime
import com.plusgroup.pos.network.models.AgentProfile
import com.plusgroup.pos.network.models.ApiDataResponse
import com.plusgroup.pos.network.models.ApiListResponse
import com.plusgroup.pos.network.models.ApiMessageResponse
import com.plusgroup.pos.network.models.BlockedNumber
import com.plusgroup.pos.network.models.CompanySetting
import com.plusgroup.pos.network.models.Draw
import com.plusgroup.pos.network.models.LotteryGame
import com.plusgroup.pos.network.models.LoginRequest
import com.plusgroup.pos.network.models.LoginResponse
import com.plusgroup.pos.network.models.PartialReport
import com.plusgroup.pos.network.models.RegisterDeviceRequest
import com.plusgroup.pos.network.models.SellTicketRequest
import com.plusgroup.pos.network.models.SellTicketResponse
import com.plusgroup.pos.network.models.Ticket
import com.plusgroup.pos.network.models.VerifyTicketResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("agent/register-device")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest): Response<ApiMessageResponse>

    @GET("agent/profile")
    suspend fun getProfile(): Response<ApiDataResponse<AgentProfile>>

    @GET("agent/balance")
    suspend fun getBalance(): Response<ApiDataResponse<AgentBalance>>

    @GET("agent/draws")
    suspend fun getDraws(@Query("status") status: String = "open"): Response<ApiListResponse<Draw>>

    @GET("agent/games")
    suspend fun getGames(): Response<ApiListResponse<LotteryGame>>

    @POST("agent/tickets")
    suspend fun sellTicket(@Body body: SellTicketRequest): Response<SellTicketResponse>

    // "Chache Fich" — chèche yon tikè pa nimewo (via SCAN oswa antre manyèl)
    @GET("agent/tickets/verify/{ticketNumber}")
    suspend fun verifyTicket(@Path("ticketNumber") ticketNumber: String): Response<ApiDataResponse<VerifyTicketResult>>

    // "Fich Mwen Yo" — tikè ajan an, ak filtè dat opsyonèl (yyyy-MM-dd)
    @GET("agent/tickets")
    suspend fun getMyTickets(
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
    ): Response<ApiListResponse<Ticket>>

    // "Rapò" — Rapò Pasyèl pou yon jou
    @GET("agent/reports/partial")
    suspend fun getPartialReport(@Query("date") date: String? = null): Response<ApiDataResponse<PartialReport>>

    // "Boul Ki Soti" — nimewo ki soti pou tiraj ki gen rezilta deja antre
    @GET("agent/draws/results")
    suspend fun getDrawResults(@Query("date") date: String? = null): Response<ApiListResponse<com.plusgroup.pos.network.models.DrawResult>>

    // "Paramèt" — Prime pa ajan, boul bloke pa ajan, paramèt tenant antye
    @GET("agent/prime")
    suspend fun getPrime(): Response<ApiDataResponse<AgentPrime>>

    @GET("agent/blocked-numbers")
    suspend fun getBlockedNumbers(): Response<ApiListResponse<BlockedNumber>>

    @GET("agent/settings")
    suspend fun getCompanySettings(): Response<ApiListResponse<CompanySetting>>
}