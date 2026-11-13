package me.floow.shared.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.koin.mp.KoinPlatformTools

class AndroidOAuthLauncher(
    private val context: Context,
) : OAuthLauncher {
    override fun launch(
        authUrl: String,
        callbackScheme: String,
        onResult: (callbackUrl: String?, error: Throwable?) -> Unit
    ) {
        try {
            val uri = Uri.parse(authUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onResult(null, null)
        } catch (e: Throwable) {
            onResult(null, e)
        }
    }
}

actual fun createOAuthLauncher(): OAuthLauncher {
    val koin = KoinPlatformTools.defaultContext().getOrNull()
    val context = AndroidTokenContext.get() ?: koin?.getOrNull<Context>()
    return if (context != null) {
        AndroidOAuthLauncher(context)
    } else {
        object : OAuthLauncher {
            override fun launch(authUrl: String, callbackScheme: String, onResult: (String?, Throwable?) -> Unit) {}
        }
    }
}

actual fun createPlatformGoogleOAuth(): me.floow.domain.auth.GoogleOAuth? {
    val koin = KoinPlatformTools.defaultContext().getOrNull()
    return koin?.getOrNull<me.floow.domain.auth.GoogleOAuth>()
}
