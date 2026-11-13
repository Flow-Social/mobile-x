package me.floow.shared.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

import org.koin.mp.KoinPlatformTools

object AndroidTokenContext {
    private var appContext: Context? = null

    fun set(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context? = appContext
}

actual fun createTokenStorage(): TokenStorage {
    val context = AndroidTokenContext.get()
        ?: KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<Context>()
    return if (context != null) {
        AndroidKeystoreTokenStorage(context)
    } else {
        InMemoryTokenStorage()
    }
}

class AndroidKeystoreTokenStorage(
    private val context: Context,
) : TokenStorage {

    private companion object {
        private const val PREFS_NAME = "me.floow.secure_auth_tokens"
        private const val LEGACY_AUTH_PREFS = "flowme.auth"
        private const val LEGACY_AUTH_TOKEN_KEY = "authToken"
        private const val KEY_ACCESS_TOKEN = "access_token_enc"
        private const val KEY_REFRESH_TOKEN = "refresh_token_enc"
        private const val KEY_IV_ACCESS = "access_token_iv"
        private const val KEY_IV_REFRESH = "refresh_token_iv"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ALIAS = "me.floow.auth.tokens.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthTokenState>(AuthTokenState.Unauthenticated)
    override val authState: StateFlow<AuthTokenState> = _authState.asStateFlow()

    init {
        var initialAccess = readDecrypted(KEY_ACCESS_TOKEN, KEY_IV_ACCESS)
        val initialRefresh = readDecrypted(KEY_REFRESH_TOKEN, KEY_IV_REFRESH)
        if (initialAccess == null) {
            // Check legacy unencrypted shared preferences for existing auth token
            val legacyPrefs = context.getSharedPreferences(LEGACY_AUTH_PREFS, Context.MODE_PRIVATE)
            val legacyToken = legacyPrefs.getString(LEGACY_AUTH_TOKEN_KEY, null)
            if (!legacyToken.isNullOrBlank()) {
                try {
                    val (encAccess, ivAccess) = encrypt(legacyToken)
                    prefs.edit()
                        .putString(KEY_ACCESS_TOKEN, encAccess)
                        .putString(KEY_IV_ACCESS, ivAccess)
                        .apply()
                    initialAccess = legacyToken
                } catch (_: Exception) {
                }
            }
        }

        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId.isNullOrBlank() && !initialAccess.isNullOrBlank()) {
            userId = JwtPayloadParser.extractUserId(initialAccess)
            if (!userId.isNullOrBlank()) {
                prefs.edit().putString(KEY_USER_ID, userId).apply()
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

    override suspend fun getAccessToken(): String? {
        return readDecrypted(KEY_ACCESS_TOKEN, KEY_IV_ACCESS)
    }

    override fun getSyncAccessTokenOrNull(): String? {
        return readDecrypted(KEY_ACCESS_TOKEN, KEY_IV_ACCESS)
    }

    override suspend fun getRefreshToken(): String? {
        return readDecrypted(KEY_REFRESH_TOKEN, KEY_IV_REFRESH)
    }

    override suspend fun saveTokens(accessToken: String, refreshToken: String?) {
        val (encAccess, ivAccess) = encrypt(accessToken)
        val refreshEncPair = refreshToken?.let { encrypt(it) }
        val extractedUserId = JwtPayloadParser.extractUserId(accessToken)

        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, encAccess)
            putString(KEY_IV_ACCESS, ivAccess)
            if (!extractedUserId.isNullOrBlank()) {
                putString(KEY_USER_ID, extractedUserId)
            }
            if (refreshEncPair != null) {
                putString(KEY_REFRESH_TOKEN, refreshEncPair.first)
                putString(KEY_IV_REFRESH, refreshEncPair.second)
            } else {
                remove(KEY_REFRESH_TOKEN)
                remove(KEY_IV_REFRESH)
            }
            apply()
        }

        _authState.value = AuthTokenState.Authenticated(
            AuthTokens(accessToken = accessToken, refreshToken = refreshToken)
        )

        // Also sync legacy unencrypted prefs for backward compatibility
        context.getSharedPreferences(LEGACY_AUTH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LEGACY_AUTH_TOKEN_KEY, accessToken)
            .apply()
    }

    override suspend fun clearTokens() {
        prefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_IV_ACCESS)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_IV_REFRESH)
            remove(KEY_USER_ID)
            apply()
        }
        context.getSharedPreferences(LEGACY_AUTH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(LEGACY_AUTH_TOKEN_KEY)
            .apply()
        _authState.value = AuthTokenState.Unauthenticated
    }

    override suspend fun getUserId(): String? {
        val saved = prefs.getString(KEY_USER_ID, null)
        if (!saved.isNullOrBlank()) return saved
        val access = getAccessToken()
        val extracted = access?.let { JwtPayloadParser.extractUserId(it) }
        if (!extracted.isNullOrBlank()) {
            prefs.edit().putString(KEY_USER_ID, extracted).apply()
            return extracted
        }
        return null
    }

    override fun getSyncUserIdOrNull(): String? {
        val saved = prefs.getString(KEY_USER_ID, null)
        if (!saved.isNullOrBlank()) return saved
        val access = getSyncAccessTokenOrNull()
        val extracted = access?.let { JwtPayloadParser.extractUserId(it) }
        if (!extracted.isNullOrBlank()) {
            prefs.edit().putString(KEY_USER_ID, extracted).apply()
            return extracted
        }
        return null
    }

    override suspend fun saveUserId(userId: String) {
        if (userId.isNotBlank()) {
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        } else {
            prefs.edit().remove(KEY_USER_ID).apply()
        }
    }

    private fun readDecrypted(keyData: String, keyIv: String): String? {
        val enc = prefs.getString(keyData, null) ?: return null
        val iv = prefs.getString(keyIv, null) ?: return null
        return try {
            decrypt(enc, iv)
        } catch (_: Exception) {
            // Corrupted or invalidated key -> clear and treat as unauthenticated
            prefs.edit().remove(keyData).remove(keyIv).apply()
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val encString = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
        return Pair(encString, ivString)
    }

    private fun decrypt(encryptedBase64: String, ivBase64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
