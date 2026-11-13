package me.floow.shared.push

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushNotificationContractTest {

    private class FakePushContract : PushNotificationContract {
        val registeredTokens = mutableListOf<String>()
        val revokedTokens = mutableListOf<String>()
        var shouldSucceed = true

        override suspend fun registerDeviceToken(
            token: String,
            platform: String,
            appVersion: String,
            locale: String?,
            timezone: String?
        ): PushTokenRegistrationResult {
            return if (shouldSucceed) {
                registeredTokens.add(token)
                PushTokenRegistrationResult.Success
            } else {
                PushTokenRegistrationResult.Failure("Network error")
            }
        }

        override suspend fun revokeDeviceToken(token: String): PushTokenRegistrationResult {
            return if (shouldSucceed) {
                revokedTokens.add(token)
                PushTokenRegistrationResult.Success
            } else {
                PushTokenRegistrationResult.Failure("Network error")
            }
        }
    }

    @Test
    fun testPushRegistrationLifecycle() = runTest {
        val fakeContract = FakePushContract()
        val manager = PushNotificationManager(contract = fakeContract, platform = "android")

        assertNull(manager.deviceToken.value)
        assertFalse(manager.isRegistered.value)

        val registerResult = manager.onNewTokenReceived("fcm_token_xyz")
        assertIs<PushTokenRegistrationResult.Success>(registerResult)
        assertEquals("fcm_token_xyz", manager.deviceToken.value)
        assertTrue(manager.isRegistered.value)
        assertEquals(listOf("fcm_token_xyz"), fakeContract.registeredTokens)

        val revokeResult = manager.revokeToken()
        assertIs<PushTokenRegistrationResult.Success>(revokeResult)
        assertNull(manager.deviceToken.value)
        assertFalse(manager.isRegistered.value)
        assertEquals(listOf("fcm_token_xyz"), fakeContract.revokedTokens)
    }

    @Test
    fun testEmptyTokenRegistrationFails() = runTest {
        val fakeContract = FakePushContract()
        val manager = PushNotificationManager(contract = fakeContract, platform = "ios")

        val result = manager.onNewTokenReceived("   ")
        assertIs<PushTokenRegistrationResult.Failure>(result)
        assertNull(manager.deviceToken.value)
        assertFalse(manager.isRegistered.value)
        assertTrue(fakeContract.registeredTokens.isEmpty())
    }
}
