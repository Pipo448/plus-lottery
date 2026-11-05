package com.plusgroup.pos.printer

/**
 * Yon seksyon tiraj sou resi fich la. Fich plizyè tiraj = plizyè seksyon
 * (egz. "Georgia-Soir: 20.00" + boul yo, apre "Florida Soir: 20.00" + boul yo).
 * lines = (kòd jwèt, boul, pri fòmate)
 */
data class ReceiptSection(
    val drawName: String,
    val drawTotal: String,
    val lines: List<Triple<String, String, String>>,
)
