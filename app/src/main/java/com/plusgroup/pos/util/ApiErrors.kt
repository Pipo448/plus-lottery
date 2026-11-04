package com.plusgroup.pos.util

import com.google.gson.JsonParser
import retrofit2.Response

/**
 * Li mesaj erè backend lan voye ({ "message": "...", "code": "..." }) pou
 * montre ajan an yon fraz klè olye yon gwo JSON.
 */
object ApiErrors {

    fun message(response: Response<*>, fallback: String = "Erè sèvè. Eseye ankò."): String =
        parse(runCatching { response.errorBody()?.string() }.getOrNull()) ?: fallback

    fun code(raw: String?): String? = runCatching {
        JsonParser.parseString(raw ?: return null).asJsonObject.get("code")?.asString
    }.getOrNull()

    fun parse(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JsonParser.parseString(raw).asJsonObject
            val msg = json.get("message") ?: return null
            when {
                msg.isJsonArray -> msg.asJsonArray.joinToString("\n") { it.asString }
                msg.isJsonPrimitive -> msg.asString
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
