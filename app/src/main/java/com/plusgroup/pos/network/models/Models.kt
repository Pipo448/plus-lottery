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

// Anvlòp jenerik backend lan itilize pou lis yo: { success: true, data: [...] }
data class ApiListResponse<T>(
    val success: Boolean? = null,
    val data: List<T>? = null,
)

// Anvlòp jenerik pou repons ki gen yon sèl objè: { success: true, data: {...} }
data class ApiDataResponse<T>(
    val success: Boolean? = null,
    val data: T? = null,
    val message: String? = null,
)

data class Draw(
    val id: String,
    val name: String? = null,
    val status: String? = null,
    // Lòt chan yo (heure tiraj, dat, elatriye) ka ajoute apre si backend
    // retounen yo — mete kòm nullable pou pa kraze si yo pa la.
)

data class LotteryGame(
    val id: String,
    val name: String? = null,
    @com.google.gson.annotations.SerializedName("min_bet")
    val minBet: Double? = null,
    @com.google.gson.annotations.SerializedName("max_bet")
    val maxBet: Double? = null,
    @com.google.gson.annotations.SerializedName("is_active")
    val isActive: Boolean? = null,
)

data class SellTicketRequest(
    val drawId: String,
    val gameId: String,
    val numbers: List<String>,
    val betAmount: Double,
    val posDeviceId: String? = null,
    val isOfflineSale: Boolean = false,
)

data class SellTicketResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val data: TicketData? = null,
)

data class TicketData(
    @com.google.gson.annotations.SerializedName("ticket_number")
    val ticketNumber: String? = null,
    @com.google.gson.annotations.SerializedName("bet_amount")
    val betAmount: Double? = null,
)

data class AgentProfile(
    val id: String? = null,
    val username: String? = null,
    @com.google.gson.annotations.SerializedName("full_name")
    val fullName: String? = null,
    // Chan sa yo ka pa egziste ankò nan `users` — rete nullable espre.
    // Si yo manke, Dashboard la montre "Illimité" pa default.
    @com.google.gson.annotations.SerializedName("credit_vente")
    val creditVente: Double? = null,
    @com.google.gson.annotations.SerializedName("limite_gain")
    val limiteGain: Double? = null,
)

data class AgentBalance(
    val available: Double? = null,
    @com.google.gson.annotations.SerializedName("lifetime_sales")
    val lifetimeSales: Double? = null,
)
data class Ticket(
    val id: String? = null,
    @com.google.gson.annotations.SerializedName("ticket_number")
    val ticketNumber: String? = null,
    val status: String? = null,
    @com.google.gson.annotations.SerializedName("bet_amount")
    val betAmount: Double? = null,
    val numbers: String? = null,
    @com.google.gson.annotations.SerializedName("sold_at")
    val soldAt: String? = null,
    @com.google.gson.annotations.SerializedName("is_winner")
    val isWinner: Boolean? = null,
    @com.google.gson.annotations.SerializedName("prize_amount")
    val prizeAmount: Double? = null,
)

data class VerifyTicketResult(
    val ticketNumber: String? = null,
    val status: String? = null,
    val isWinner: Boolean? = null,
    val prizeAmount: Double? = null,
    val numbers: String? = null,
    val betAmount: Double? = null,
    val soldAt: String? = null,
)

data class PartialReport(
    val tirage: String? = null,
    val date: String? = null,
    val ficheVendu: Int? = null,
    val vente: Double? = null,
    val commission: Double? = null,
)
