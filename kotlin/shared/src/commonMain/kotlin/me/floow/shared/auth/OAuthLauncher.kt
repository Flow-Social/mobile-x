package me.floow.shared.auth

interface OAuthLauncher {
    fun launch(
        authUrl: String,
        callbackScheme: String,
        onResult: (callbackUrl: String?, error: Throwable?) -> Unit
    )
}

expect fun createOAuthLauncher(): OAuthLauncher

expect fun createPlatformGoogleOAuth(): me.floow.domain.auth.GoogleOAuth?
