package com.plusgroup.pos.network.models

data class FinTirageReport(
    val tirage: String?,
    val dateFrom: String?,
    val dateTo: String?,
    val datePrinted: String?,
    val vendeur: String?,
    val succursal: String?,
    val ficheVendu: Int?,
    val ficheGagnant: Int?,
    val vente: Double?,
    val commission: Double?,
    val aPaye: Double?,
    val dejaPeye: Double? = null,
    val pokoPeye: Double? = null,
    val profitPerte: Double?,
    val depot: Any?,
    val retrait: Any?,
    val balance: Double?,
)

data class GagnantLine(
    val ticketNumber: String?,
    val ficheNumber: String?,
    val drawName: String?,
    val soldAt: String?,
    val vente: Double?,
    val prizeAmount: Double?,
    val paid: Boolean?,
    val numero: String? = null,
    val linesWon: Int? = null,
)

data class GagnantReport(
    val dateFrom: String?,
    val dateTo: String?,
    val ficheGagnant: Int?,
    val totalPrize: Double?,
    val pokoPeye: Double? = null,
    val lines: List<GagnantLine>?,
)

data class TransactionLine(
    val date: String?,
    val type: String?,
    val montant: Double?,
)

/** Non yon tiraj pou filtè rapò yo (GET agent/draws/names) */
data class DrawName(
    val name: String? = null,
    val closeTime: String? = null,
)

/** Bwat mesaj ajan an (GET agent/messages) */
data class AgentInbox(
    val unread: Int? = null,
    val messages: List<AgentMessage>? = null,
)

data class AgentMessage(
    val id: String? = null,
    val title: String? = null,
    val body: String? = null,
    val important: Boolean? = null,
    val createdAt: String? = null,
    val read: Boolean? = null,
)

data class MarkReadRequest(val id: String? = null)
