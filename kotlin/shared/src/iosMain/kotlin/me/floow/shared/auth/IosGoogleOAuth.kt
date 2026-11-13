package me.floow.shared.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.GoogleOAuth
import me.floow.domain.auth.models.GoogleOAuthResult
import org.koin.mp.KoinPlatformTools
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.dataWithBytes

@Serializable
private data class GoogleIdTokenResponse(
    @SerialName("id_token")
    val idToken: String
)

class IosGoogleOAuth(
    private val launcher: OAuthLauncher,
    private val clientId: String = "291755427997-hjaabnfaa435ikjlsocejeg5p9elraj8.apps.googleusercontent.com",
    private val redirectUri: String = "com.googleusercontent.apps.291755427997-hjaabnfaa435ikjlsocejeg5p9elraj8:/",
) : GoogleOAuth {
    companion object {
        private const val PREFS_KEY_VERIFIER = "flow_google_oauth_pkce_verifier"
        private var activeOAuthInstance: IosGoogleOAuth? = null

        fun handleCallbackUrl(url: String): Boolean {
            return activeOAuthInstance?.onCallbackReceived(url) ?: false
        }
    }

    private val httpClient = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) { json() }
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val signInMutex = Mutex()
    private var isSigningIn = false

    init {
        activeOAuthInstance = this
    }

    fun onCallbackReceived(callbackUrl: String): Boolean {
        if (!callbackUrl.contains("code=")) return false
        val code = callbackUrl.substringAfter("code=").substringBefore("&")
        if (code.isBlank() || code == callbackUrl) return false
        println("[IosGoogleOAuth] onCallbackReceived code=$code")
        isSigningIn = false
        val koin = KoinPlatformTools.defaultContext().getOrNull()
        val authManager = koin?.getOrNull<AuthenticationManager>()
        CoroutineScope(Dispatchers.Main).launch {
            authManager?.handleGoogleOAuthCode(code)
        }
        return true
    }

    private fun generateCodeVerifier(): String {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..43).map { chars.random() }.joinToString("")
    }

    private fun saveVerifier(verifier: String) {
        NSUserDefaults.standardUserDefaults.setObject(verifier, forKey = PREFS_KEY_VERIFIER)
        NSUserDefaults.standardUserDefaults.synchronize()
    }

    private fun getSavedVerifier(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey(PREFS_KEY_VERIFIER)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun sha256Base64Url(input: String): String {
        val inputData = input.encodeToByteArray()
        val hash = ByteArray(32)
        hash.usePinned { pinnedHash ->
            inputData.usePinned { pinnedData ->
                CC_SHA256(pinnedData.addressOf(0), inputData.size.toUInt(), pinnedHash.addressOf(0).reinterpret())
            }
        }
        val nsData = hash.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), 32.toULong())
        }
        return nsData.base64EncodedStringWithOptions(0UL)
            .replace("+", "-")
            .replace("/", "_")
            .trimEnd('=')
    }

    override suspend fun startSignIn() {
        if (!signInMutex.tryLock()) {
            println("[IosGoogleOAuth] Sign-in already in progress, ignoring duplicate call")
            return
        }

        val verifier: String
        val challenge: String
        try {
            if (isSigningIn) {
                println("[IosGoogleOAuth] Already isSigningIn, skipping")
                return
            }
            isSigningIn = true

            verifier = generateCodeVerifier()
            saveVerifier(verifier)
            challenge = sha256Base64Url(verifier)
        } finally {
            signInMutex.unlock()
        }

        val callbackScheme = "com.googleusercontent.apps.291755427997-hjaabnfaa435ikjlsocejeg5p9elraj8"
        val encodedRedirectUri = redirectUri.encodeURLParameter()
        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
            "client_id=$clientId&" +
            "redirect_uri=$encodedRedirectUri&" +
            "scope=openid%20profile%20email&" +
            "code_challenge=$challenge&" +
            "code_challenge_method=S256&" +
            "response_type=code"

        println("[IosGoogleOAuth] Starting sign-in. Challenge: $challenge, verifier: $verifier")
        launcher.launch(
            authUrl = authUrl,
            callbackScheme = callbackScheme,
        ) { callbackUrl, error ->
            isSigningIn = false
            println("[IosGoogleOAuth] Launcher finished: callbackUrl=$callbackUrl, error=${error?.message}")
            if (callbackUrl != null) {
                val code = callbackUrl.substringAfter("code=").substringBefore("&")
                println("[IosGoogleOAuth] Extracted code: $code")
                if (code.isNotBlank() && code != callbackUrl) {
                    val koin = KoinPlatformTools.defaultContext().getOrNull()
                    val authManager = koin?.getOrNull<AuthenticationManager>()
                    println("[IosGoogleOAuth] authManager found: ${authManager != null}")
                    CoroutineScope(Dispatchers.Main).launch {
                        authManager?.handleGoogleOAuthCode(code)
                    }
                }
            } else if (error != null) {
                val koin = KoinPlatformTools.defaultContext().getOrNull()
                val authManager = koin?.getOrNull<AuthenticationManager>()
                CoroutineScope(Dispatchers.Main).launch {
                    authManager?.clearPendingRegistration()
                }
            }
        }
    }

    override suspend fun handleGoogleOAuthCode(code: String): GoogleOAuthResult {
        val verifier = getSavedVerifier() ?: generateCodeVerifier()
        println("[IosGoogleOAuth] Exchanging code for token with verifier=$verifier")
        return try {
            val response = httpClient.post("https://oauth2.googleapis.com/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("code", code)
                            append("client_id", clientId)
                            append("code_verifier", verifier)
                            append("grant_type", "authorization_code")
                            append("redirect_uri", redirectUri)
                        }
                    )
                )
            }
            val body = response.bodyAsText()
            println("[IosGoogleOAuth] Token exchange status: ${response.status}")
            if (response.status.isSuccess()) {
                val tokenResponse = json.decodeFromString<GoogleIdTokenResponse>(body)
                println("[IosGoogleOAuth] Received ID token successfully")
                GoogleOAuthResult.Success(idToken = tokenResponse.idToken)
            } else {
                println("[IosGoogleOAuth] Token exchange failed: ${response.status} -> $body")
                GoogleOAuthResult.Failure
            }
        } catch (e: Throwable) {
            println("[IosGoogleOAuth] Token exchange error: ${e.message}")
            e.printStackTrace()
            GoogleOAuthResult.Failure
        }
    }
}
