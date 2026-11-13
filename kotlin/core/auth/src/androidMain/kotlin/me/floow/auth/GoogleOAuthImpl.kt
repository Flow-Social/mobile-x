package me.floow.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.api.config.AppConfig
import me.floow.auth.models.GoogleOAuthInfo
import me.floow.domain.auth.GoogleOAuth
import me.floow.domain.auth.models.GoogleOAuthResult
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

@Serializable
internal data class IdTokenResponse(
    @SerialName("id_token")
    val idToken: String
)

class GoogleOAuthImpl(
    private val context: Context,
    private val googleOAuthInfo: GoogleOAuthInfo
) : GoogleOAuth {
    companion object {
        private const val STRING_LENGTH = 43
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    }

    private val httpClient = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) { json() }
    }
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val prefs by lazy {
        context.getSharedPreferences("flow_google_oauth_pkce", Context.MODE_PRIVATE)
    }

    private fun generateCodeVerifier(): String {
        val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val secureRandom = SecureRandom()
        val str = (1..STRING_LENGTH).map {
            secureRandom.nextInt(charPool.size).let { charPool[it] }
        }.joinToString("")

        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(str.toByteArray())
    }

    override suspend fun startSignIn() {
        val verifier = generateCodeVerifier()
        prefs.edit().putString("code_verifier", verifier).commit()

        val md = MessageDigest.getInstance("SHA-256")
        val codeChallenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(md.digest(verifier.toByteArray()))

        val authUrl = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", googleOAuthInfo.clientId)
            .appendQueryParameter("redirect_uri", googleOAuthInfo.redirectUri)
            .appendQueryParameter("scope", "openid profile email")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("response_type", "code")
            .build()

        val builder = CustomTabsIntent.Builder().apply {
            setShowTitle(true)
            setInstantAppsEnabled(true)
        }

        val customBuilder = builder.build()
        if (context !is Activity) {
            customBuilder.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        customBuilder.launchUrl(context, authUrl)
    }

    override suspend fun handleGoogleOAuthCode(code: String): GoogleOAuthResult {
        val savedVerifier = prefs.getString("code_verifier", null)
        val hasSavedVerifier = savedVerifier != null
        val verifier = savedVerifier ?: generateCodeVerifier()

        return try {
            val response: HttpResponse = httpClient.post(TOKEN_URL) {
                contentType(ContentType.Application.FormUrlEncoded)

                setBody(
                    listOf(
                        "code" to code,
                        "client_id" to googleOAuthInfo.clientId,
                        "code_verifier" to verifier,
                        "grant_type" to "authorization_code",
                        "redirect_uri" to googleOAuthInfo.redirectUri
                    ).formUrlEncode()
                )
            }

            val body = response.bodyAsText()
            android.util.Log.d("GoogleOAuthImpl", "Google token response status=${response.status}")

            if (response.status.isSuccess()) {
                val idTokenResponse = json.decodeFromString<IdTokenResponse>(body)
                GoogleOAuthResult.Success(idToken = idTokenResponse.idToken)
            } else {
                android.util.Log.e("GoogleOAuthImpl", "Google OAuth exchange failed: ${response.status}")
                GoogleOAuthResult.Failure
            }
        } catch (e: Throwable) {
            android.util.Log.e("GoogleOAuthImpl", "Google OAuth exception: ${e.message}", e)
            runCatching {
                httpClient.post("${AppConfig.BASE_URL}/debug/log") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"event":"google_oauth_exception","hasSavedVerifier":$hasSavedVerifier,"error":"${e.message}"}""")
                }
            }
            GoogleOAuthResult.Failure
        }
    }
}
