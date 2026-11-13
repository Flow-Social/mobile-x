package me.floow.shared.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy
import platform.posix.uint8_tVar

actual fun createTokenStorage(): TokenStorage {
    return IosKeychainTokenStorage()
}

/**
 * iOS Keychain Token Storage implementation using Apple Security framework.
 * Securely stores access and refresh tokens in the iOS Keychain.
 */
class IosKeychainTokenStorage : TokenStorage {
    private val _authState = MutableStateFlow<AuthTokenState>(AuthTokenState.Unauthenticated)
    override val authState: StateFlow<AuthTokenState> = _authState.asStateFlow()

    private companion object {
        private const val SERVICE_NAME = "me.floow.auth"
        private const val ACCOUNT_ACCESS_TOKEN = "access_token"
        private const val ACCOUNT_REFRESH_TOKEN = "refresh_token"
        private const val ACCOUNT_USER_ID = "user_id"

        private const val PREF_ACCESS_TOKEN = "flow_auth_access_token"
        private const val PREF_REFRESH_TOKEN = "flow_auth_refresh_token"
        private const val PREF_USER_ID = "flow_auth_user_id"
    }

    init {
        val initialAccess = readValue(ACCOUNT_ACCESS_TOKEN, PREF_ACCESS_TOKEN)
        val initialRefresh = readValue(ACCOUNT_REFRESH_TOKEN, PREF_REFRESH_TOKEN)
        var userId = readValue(ACCOUNT_USER_ID, PREF_USER_ID)
        if (userId.isNullOrBlank() && !initialAccess.isNullOrBlank()) {
            userId = JwtPayloadParser.extractUserId(initialAccess)
            if (!userId.isNullOrBlank()) {
                saveValue(ACCOUNT_USER_ID, PREF_USER_ID, userId)
            }
        }
        if (initialAccess != null) {
            _authState.value = AuthTokenState.Authenticated(
                AuthTokens(accessToken = initialAccess, refreshToken = initialRefresh)
            )
        } else {
            _authState.value = AuthTokenState.Unauthenticated
        }
    }

    override suspend fun getAccessToken(): String? = readValue(ACCOUNT_ACCESS_TOKEN, PREF_ACCESS_TOKEN)
    override fun getSyncAccessTokenOrNull(): String? = readValue(ACCOUNT_ACCESS_TOKEN, PREF_ACCESS_TOKEN)

    override suspend fun getRefreshToken(): String? = readValue(ACCOUNT_REFRESH_TOKEN, PREF_REFRESH_TOKEN)

    override suspend fun saveTokens(accessToken: String, refreshToken: String?) {
        saveValue(ACCOUNT_ACCESS_TOKEN, PREF_ACCESS_TOKEN, accessToken)
        if (refreshToken != null) {
            saveValue(ACCOUNT_REFRESH_TOKEN, PREF_REFRESH_TOKEN, refreshToken)
        } else {
            deleteValue(ACCOUNT_REFRESH_TOKEN, PREF_REFRESH_TOKEN)
        }
        val extractedUserId = JwtPayloadParser.extractUserId(accessToken)
        if (!extractedUserId.isNullOrBlank()) {
            saveValue(ACCOUNT_USER_ID, PREF_USER_ID, extractedUserId)
        }
        _authState.value = AuthTokenState.Authenticated(
            AuthTokens(accessToken = accessToken, refreshToken = refreshToken)
        )
    }

    override suspend fun clearTokens() {
        deleteValue(ACCOUNT_ACCESS_TOKEN, PREF_ACCESS_TOKEN)
        deleteValue(ACCOUNT_REFRESH_TOKEN, PREF_REFRESH_TOKEN)
        deleteValue(ACCOUNT_USER_ID, PREF_USER_ID)
        _authState.value = AuthTokenState.Unauthenticated
    }

    override suspend fun getUserId(): String? {
        val saved = readValue(ACCOUNT_USER_ID, PREF_USER_ID)
        if (!saved.isNullOrBlank()) return saved
        val access = readValue(ACCOUNT_ACCESS_TOKEN, PREF_ACCESS_TOKEN)
        val extracted = access?.let { JwtPayloadParser.extractUserId(it) }
        if (!extracted.isNullOrBlank()) {
            saveValue(ACCOUNT_USER_ID, PREF_USER_ID, extracted)
            return extracted
        }
        return null
    }

    override fun getSyncUserIdOrNull(): String? {
        val saved = readValue(ACCOUNT_USER_ID, PREF_USER_ID)
        if (!saved.isNullOrBlank()) return saved
        val access = readValue(ACCOUNT_ACCESS_TOKEN, PREF_ACCESS_TOKEN)
        val extracted = access?.let { JwtPayloadParser.extractUserId(it) }
        if (!extracted.isNullOrBlank()) {
            saveValue(ACCOUNT_USER_ID, PREF_USER_ID, extracted)
            return extracted
        }
        return null
    }

    override suspend fun saveUserId(userId: String) {
        if (userId.isNotBlank()) {
            saveValue(ACCOUNT_USER_ID, PREF_USER_ID, userId)
        } else {
            deleteValue(ACCOUNT_USER_ID, PREF_USER_ID)
        }
    }

    private fun readValue(account: String, prefKey: String): String? {
        val fromKeychain = readKeychain(account)
        if (!fromKeychain.isNullOrBlank()) {
            return fromKeychain
        }
        val fromPrefs = platform.Foundation.NSUserDefaults.standardUserDefaults.stringForKey(prefKey)
        if (!fromPrefs.isNullOrBlank()) {
            // One-time migration from legacy unencrypted NSUserDefaults to Apple Keychain
            saveKeychain(account, fromPrefs)
            platform.Foundation.NSUserDefaults.standardUserDefaults.removeObjectForKey(prefKey)
            platform.Foundation.NSUserDefaults.standardUserDefaults.synchronize()
            return fromPrefs
        }
        return null
    }

    private fun saveValue(account: String, prefKey: String, value: String) {
        saveKeychain(account, value)
        platform.Foundation.NSUserDefaults.standardUserDefaults.removeObjectForKey(prefKey)
        platform.Foundation.NSUserDefaults.standardUserDefaults.synchronize()
    }

    private fun deleteValue(account: String, prefKey: String) {
        deleteKeychain(account)
        platform.Foundation.NSUserDefaults.standardUserDefaults.removeObjectForKey(prefKey)
        platform.Foundation.NSUserDefaults.standardUserDefaults.synchronize()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun saveKeychain(account: String, value: String): Boolean {
        deleteKeychain(account)
        val bytes = value.encodeToByteArray()

        return memScoped {
            val cfService = CFStringCreateWithCString(null, SERVICE_NAME, kCFStringEncodingUTF8)
            val cfAccount = CFStringCreateWithCString(null, account, kCFStringEncodingUTF8)
            val cfData = if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    CFDataCreate(null, pinned.addressOf(0).reinterpret<uint8_tVar>(), bytes.size.toLong())
                }
            } else {
                null
            }

            val query = CFDictionaryCreateMutable(null, 4, null, null)
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, cfService)
            CFDictionarySetValue(query, kSecAttrAccount, cfAccount)
            if (cfData != null) {
                CFDictionarySetValue(query, kSecValueData, cfData)
            }

            val status = SecItemAdd(query, null)
            if (status != errSecSuccess) {
                println("[IosKeychainTokenStorage] SecItemAdd error for $account: status=$status")
            }

            CFRelease(query)
            if (cfData != null) CFRelease(cfData)
            if (cfAccount != null) CFRelease(cfAccount)
            if (cfService != null) CFRelease(cfService)

            status == errSecSuccess
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readKeychain(account: String): String? {
        memScoped {
            val cfService = CFStringCreateWithCString(null, SERVICE_NAME, kCFStringEncodingUTF8)
            val cfAccount = CFStringCreateWithCString(null, account, kCFStringEncodingUTF8)

            val query = CFDictionaryCreateMutable(null, 5, null, null)
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, cfService)
            CFDictionarySetValue(query, kSecAttrAccount, cfAccount)
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)

            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)

            CFRelease(query)
            if (cfAccount != null) CFRelease(cfAccount)
            if (cfService != null) CFRelease(cfService)

            if (status == errSecSuccess && result.value != null) {
                val cfData: CFDataRef = result.value!!.reinterpret()
                val length = CFDataGetLength(cfData)
                val ptr = CFDataGetBytePtr(cfData)
                if (ptr != null && length > 0) {
                    val byteArray = ByteArray(length.toInt())
                    byteArray.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), ptr, length.toULong())
                    }
                    CFRelease(cfData)
                    return byteArray.decodeToString()
                }
                CFRelease(cfData)
            } else if (status != errSecSuccess) {
                // Not found (-25300) is normal when unauthenticated, other errors are logged
                if (status != platform.Security.errSecItemNotFound) {
                    println("[IosKeychainTokenStorage] SecItemCopyMatching status=$status for $account")
                }
            }
            return null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun deleteKeychain(account: String): Boolean {
        return memScoped {
            val cfService = CFStringCreateWithCString(null, SERVICE_NAME, kCFStringEncodingUTF8)
            val cfAccount = CFStringCreateWithCString(null, account, kCFStringEncodingUTF8)

            val query = CFDictionaryCreateMutable(null, 3, null, null)
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, cfService)
            CFDictionarySetValue(query, kSecAttrAccount, cfAccount)

            val status = SecItemDelete(query)

            CFRelease(query)
            if (cfAccount != null) CFRelease(cfAccount)
            if (cfService != null) CFRelease(cfService)

            status == errSecSuccess || status == platform.Security.errSecItemNotFound
        }
    }
}
