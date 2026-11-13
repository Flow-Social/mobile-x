package me.floow.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtPayloadParserTest {

    @Test
    fun extractUserId_fromStandardTokenWithUserIdString() {
        // payload: {"user_id":"42","email":"test@floow.me"}
        // base64url of payload: eyJ1c2VyX2lkIjoiNDIiLCJlbWFpbCI6InRlc3RAZmxvow5tZSJ9 (demo base64)
        // Let's create an actual valid base64url payload:
        // {"user_id":"42","email":"test@floow.me"} -> eyJ1c2VyX2lkIjoiNDIiLCJlbWFpbCI6InRlc3RAZmxvdy5tZSJ9
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiNDIiLCJlbWFpbCI6InRlc3RAZmxvdy5tZSJ9.signature"
        val userId = JwtPayloadParser.extractUserId(token)
        assertEquals("42", userId)
    }

    @Test
    fun extractUserId_fromTokenWithNumericUserId() {
        // payload: {"user_id":100,"email":"num@floow.me"}
        // {"user_id":100,"email":"num@floow.me"} -> eyJ1c2VyX2lkIjoxMDAsImVtYWlsIjoibnVtQGZsb3cubWUifQ
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxMDAsImVtYWlsIjoibnVtQGZsb3cubWUifQ.signature"
        val userId = JwtPayloadParser.extractUserId(token)
        assertEquals("100", userId)
    }

    @Test
    fun extractUserId_fallbackToSubClaim() {
        // payload: {"sub":"user_999","email":"sub@floow.me"}
        // {"sub":"user_999","email":"sub@floow.me"} -> eyJzdWIiOiJ1c2VyXzk5OSIsImVtYWlsIjoic3ViQGZsb3cubWUifQ
        val token = "header.eyJzdWIiOiJ1c2VyXzk5OSIsImVtYWlsIjoic3ViQGZsb3cubWUifQ.signature"
        val userId = JwtPayloadParser.extractUserId(token)
        assertEquals("user_999", userId)
    }

    @Test
    fun extractUserId_handlesBearerPrefix() {
        val rawToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiNDIiLCJlbWFpbCI6InRlc3RAZmxvdy5tZSJ9.sig"
        val tokenWithBearer = "Bearer $rawToken"
        val userId = JwtPayloadParser.extractUserId(tokenWithBearer)
        assertEquals("42", userId)
    }

    @Test
    fun extractUserId_returnsNullForInvalidOrEmptyTokens() {
        assertNull(JwtPayloadParser.extractUserId(null))
        assertNull(JwtPayloadParser.extractUserId(""))
        assertNull(JwtPayloadParser.extractUserId("   "))
        assertNull(JwtPayloadParser.extractUserId("not_a_jwt_token"))
        assertNull(JwtPayloadParser.extractUserId("only.two"))
        assertNull(JwtPayloadParser.extractUserId("bad.base64!!.token"))
    }
}
