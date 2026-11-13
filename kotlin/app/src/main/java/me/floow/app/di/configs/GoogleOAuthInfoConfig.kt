package me.floow.app.di.configs

import me.floow.app.BuildConfig
import me.floow.auth.models.GoogleOAuthInfo

val googleOAuthInfoConfig = GoogleOAuthInfo(
    clientId = BuildConfig.GOOGLE_CLIENT_ID,
    redirectUri = "com.googleusercontent.apps.291755427997-hjaabnfaa435ikjlsocejeg5p9elraj8:/"
)