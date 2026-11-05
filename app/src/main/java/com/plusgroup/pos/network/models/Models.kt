package com.plusgroup.pos.network.models

data class LoginRequest(
    val username: String,
    val password: String,
    // Kòd sèvè (bank) ajan an chwazi a — koneksyon an fèt sèlman sou bank sa a
    val tenantSlug: String? = null,
)

/** Yon bank nan lis sèvè APK a (GET /public/servers). */
data class PublicServer(
    val code: String? = null,
    val name: String? = null,
    val displayName: String? = null,
    val logo: String? = null,
    val active: Boolean? = null,
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
    val refreshToken: String? = null,
)

// POST auth/refresh
data class RefreshRequest(
    val refreshToken: String,
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
    @com.google.gson.annotations.SerializedName("open_time")
    val openTime: String? = null,
    @com.google.gson.annotations.SerializedName("close_time")
    val closeTime: String? = null,
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
    // Menm valè a dwe pase pou TOUT liy ki fè pati YON SÈL fich, pou
    // backend lan ka gwoupe yo ansanm nan "Mes fiches".
    val ficheNumber: String? = null,
)

// ==================== VANN YON FICH KONPLÈ (POST agent/fiches) ====================
// Tout liy yo pati ansanm; si youn pa pase, backend lan pa vann ayen.
// `requestId` pèmèt re-eseye san risk: menm requestId = menm fich, pa doub vant.
data class SellFicheRequest(
    val requestId: String,
    val ficheNumber: String,
    val drawId: String,
    val gameId: String,
    val deviceCode: String?,
    val lines: List<SellFicheLine>,
)

data class SellFicheLine(
    val numero: String,
    val gameType: String?,   // borlette, loto3, mariage, loto4, loto5
    val option: String?,     // "01" / "02" / "03" pou loto4/loto5
    val amount: Double,
)

data class SellFicheResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val data: SellFicheResult? = null,
)

data class SellFicheResult(
    val replay: Boolean? = null,
    val ficheNumber: String? = null,
    val drawName: String? = null,
    val soldAt: String? = null,
    val total: Double? = null,
    val lines: List<SoldLine>? = null,
)

data class SoldLine(
    val lineNo: Int? = null,
    val ticketNumber: String? = null,
    val numero: String? = null,
    val gameType: String? = null,
    val option: String? = null,
    val amount: Double? = null,
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
    @com.google.gson.annotations.SerializedName("credit_vente")
    val creditVente: Double? = null,
    @com.google.gson.annotations.SerializedName("limite_gain")
    val limiteGain: Double? = null,
    val status: String? = null,
    val deviceId: String? = null,
    val branchName: String? = null,
    val branchStatus: String? = null,
    val tenantName: String? = null,
    @com.google.gson.annotations.SerializedName("zone_address")
    val zoneAddress: String? = null,
    @com.google.gson.annotations.SerializedName("commission_rate")
    val commissionRate: Double? = null,
)

data class AgentBalance(
    val available: Double? = null,
    @com.google.gson.annotations.SerializedName("lifetime_sales")
    val lifetimeSales: Double? = null,
)

// Ansyen modèl "yon liy = yon tikè" — rete la pou verifyTicket ("Chache
// Fich") ki toujou retounen yon sèl tikè apa.
data class Ticket(
    val id: String? = null,
    @com.google.gson.annotations.SerializedName("ticket_number")
    val ticketNumber: String? = null,
    val status: String? = null,
    @com.google.gson.annotations.SerializedName("bet_amount")
    val betAmount: Double? = null,
    val numbers: List<String>? = null,
    @com.google.gson.annotations.SerializedName("sold_at")
    val soldAt: String? = null,
    @com.google.gson.annotations.SerializedName("is_winner")
    val isWinner: Boolean? = null,
    @com.google.gson.annotations.SerializedName("prize_amount")
    val prizeAmount: Double? = null,
)

// ==================== FICH MWEN YO (gwoupe pa fiche_number) ====================
// Backend lan (getMyTickets) kounye a retounen YON REZIME pa fich — pa
// yon liy pa boul. `lines` gen tout boul yo (kòd/nimewo/pri) ki fè pati
// menm fich la.
data class FicheLineSummary(
    val id: String? = null,
    val numero: String? = null,
    val betAmount: Double? = null,
    val status: String? = null,
    val gameType: String? = null,
    val option: String? = null,
    val prize: Double? = null,
)

data class FicheSummary(
    val ficheNumber: String? = null,
    val ticketIds: List<String>? = null,
    val ticketNumber: String? = null,
    val drawName: String? = null,
    val soldAt: String? = null,
    val totalMise: Double? = null,
    val totalGain: Double? = null,
    val status: String? = null,
    val lines: List<FicheLineSummary>? = null,
)

data class VerifyTicketResult(
    val ticketNumber: String? = null,
    val ficheNumber: String? = null,
    val status: String? = null,
    val isWinner: Boolean? = null,
    val prizeAmount: Double? = null,
    val numbers: List<String>? = null,
    val betAmount: Double? = null,
    val soldAt: String? = null,
    val drawName: String? = null,
    val drawDate: String? = null,
    val results: List<String?>? = null,
    val agent: String? = null,
    val lines: List<VerifyLine>? = null,
)

data class VerifyLine(
    val ticketNumber: String? = null,
    val numero: String? = null,
    val gameType: String? = null,
    val option: String? = null,
    val amount: Double? = null,
    val status: String? = null,
    val prize: Double? = null,
    val winningPosition: Int? = null,
)

data class PayFicheResult(
    val paidLines: Int? = null,
    val totalPaid: Double? = null,
)

data class PartialReport(
    val tirage: String? = null,
    val date: String? = null,
    val ficheVendu: Int? = null,
    val vente: Double? = null,
    val commission: Double? = null,
)

data class AgentPrime(
    val borlette: String? = null,
    val loto3: String? = null,
    val mariage: String? = null,
    val l4o1: String? = null,
    val l4o2: String? = null,
    val l4o3: String? = null,
    val l5o1: String? = null,
    val l5o2: String? = null,
    val l5o3: String? = null,
)

data class BlockedNumber(
    val id: String? = null,
    val numero: String? = null,
)

data class CompanySetting(
    val key: String? = null,
    val value: String? = null,
)

data class DrawResult(
    val name: String? = null,
    val drawDate: String? = null,
    val closeTime: String? = null,
    val drawnAt: String? = null,
    val winningNumber1: String? = null,
    val winningNumber2: String? = null,
    val winningNumber3: String? = null,
)
