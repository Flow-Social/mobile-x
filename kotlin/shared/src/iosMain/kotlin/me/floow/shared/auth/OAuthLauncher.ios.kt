package me.floow.shared.auth

import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

class IosOAuthLauncher : OAuthLauncher {
    private var currentSession: ASWebAuthenticationSession? = null

    override fun launch(
        authUrl: String,
        callbackScheme: String,
        onResult: (callbackUrl: String?, error: Throwable?) -> Unit
    ) {
        val nsUrl = NSURL.URLWithString(authUrl)
        if (nsUrl == null) {
            onResult(null, IllegalArgumentException("Invalid URL: $authUrl"))
            return
        }

        val contextProvider = ContextProvider()
        val session = ASWebAuthenticationSession(
            uRL = nsUrl,
            callbackURLScheme = callbackScheme,
            completionHandler = { callbackURL: NSURL?, error: NSError? ->
                currentSession = null
                if (error != null) {
                    onResult(null, Exception(error.localizedDescription))
                } else {
                    onResult(callbackURL?.absoluteString, null)
                }
            }
        )
        session.presentationContextProvider = contextProvider
        session.prefersEphemeralWebBrowserSession = false
        currentSession = session
        session.start()
    }

    private class ContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
        override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
            val keyWindow = UIApplication.sharedApplication.keyWindow
                ?: UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
            return keyWindow ?: ASPresentationAnchor()
        }
    }
}

actual fun createOAuthLauncher(): OAuthLauncher = IosOAuthLauncher()

actual fun createPlatformGoogleOAuth(): me.floow.domain.auth.GoogleOAuth? = IosGoogleOAuth(launcher = createOAuthLauncher())
