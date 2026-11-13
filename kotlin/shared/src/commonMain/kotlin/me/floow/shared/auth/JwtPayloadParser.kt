package me.floow.shared.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object JwtPayloadParser {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /**
     * Extracts user ID from JWT access token payload without verifying the cryptographic signature.
     * Looks for "user_id" claim (string or number), falling back to "sub".
     *
     * Works purely in common Kotlin Multiplatform (zero platform dependencies).
     */
    fun extractUserId(token: String?): String? {
        if (token.isNullOrBlank()) return null
        val cleanToken = token.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()
        val parts = cleanToken.split('.')
        if (parts.size < 2) return null

        val payloadBase64 = parts[1]
        val payloadJson = decodeBase64UrlToString(payloadBase64) ?: return null

        return runCatching {
            val root = json.parseToJsonElement(payloadJson).jsonObject
            val userIdPrimitive = root["user_id"]?.jsonPrimitive
            val fromUserId = userIdPrimitive?.content?.takeIf(String::isNotBlank)
                ?: userIdPrimitive?.intOrNull?.toString()

            if (!fromUserId.isNullOrBlank()) {
                return@runCatching fromUserId
            }

            val subPrimitive = root["sub"]?.jsonPrimitive
            subPrimitive?.content?.takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun decodeBase64UrlToString(base64Url: String): String? {
        return runCatching {
            var normalized = base64Url.replace('-', '+').replace('_', '/')
            val remainder = normalized.length % 4
            if (remainder > 0) {
                normalized += "=".repeat(4 - remainder)
            }
            val bytes = decodeBase64(normalized) ?: return null
            bytes.decodeToString()
        }.getOrNull()
    }

    private fun decodeBase64(input: String): ByteArray? {
        val clean = input.trim()
        if (clean.isEmpty()) return ByteArray(0)

        val table = IntArray(256) { -1 }
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        for (i in chars.indices) {
            table[chars[i].code] = i
        }

        var padCount = 0
        for (i in clean.length - 1 downTo 0) {
            if (clean[i] == '=') padCount++ else break
        }

        val cleanLen = clean.length
        val rawByteCount = (cleanLen * 6) / 8 - padCount
        if (rawByteCount < 0) return null
        val out = ByteArray(rawByteCount)

        var buffer = 0
        var bitsCollected = 0
        var outIndex = 0

        for (i in 0 until cleanLen) {
            val c = clean[i]
            if (c == '=') break
            val v = table[c.code]
            if (v < 0) return null // invalid character

            buffer = (buffer shl 6) or v
            bitsCollected += 6
            if (bitsCollected >= 8) {
                bitsCollected -= 8
                if (outIndex < rawByteCount) {
                    out[outIndex++] = ((buffer shr bitsCollected) and 0xFF).toByte()
                }
            }
        }

        return out
    }
}
