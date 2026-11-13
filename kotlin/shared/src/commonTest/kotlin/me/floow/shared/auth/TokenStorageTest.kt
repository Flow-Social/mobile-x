package me.floow.shared.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStorageTest {

    @Test
    fun testInitialUnauthenticatedState() = runTest {
        val storage = InMemoryTokenStorage()
        assertIs<AuthTokenState.Unauthenticated>(storage.authState.value)
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
    }

    @Test
    fun testSaveTokensUpdatesStateAndGetters() = runTest {
        val storage = InMemoryTokenStorage()
        storage.saveTokens(accessToken = "access_123", refreshToken = "refresh_456")

        val state = storage.authState.value
        assertIs<AuthTokenState.Authenticated>(state)
        assertEquals("access_123", state.tokens.accessToken)
        assertEquals("refresh_456", state.tokens.refreshToken)
        assertEquals("access_123", storage.getAccessToken())
        assertEquals("refresh_456", storage.getRefreshToken())
    }

    @Test
    fun testClearTokensResetsToUnauthenticated() = runTest {
        val storage = InMemoryTokenStorage(
            initialTokens = AuthTokens(accessToken = "access_initial")
        )
        assertIs<AuthTokenState.Authenticated>(storage.authState.value)

        storage.clearTokens()
        assertIs<AuthTokenState.Unauthenticated>(storage.authState.value)
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
    }
}
