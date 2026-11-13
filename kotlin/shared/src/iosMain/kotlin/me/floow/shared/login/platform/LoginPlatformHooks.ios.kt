package me.floow.shared.login.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.floow.domain.auth.AuthenticationManager
import org.koin.mp.KoinPlatformTools
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun showToast(message: String) {
    println("Toast: $message")
}

actual fun startGoogleAuth() {
    val koin = KoinPlatformTools.defaultContext().getOrNull()
    val authManager = koin?.getOrNull<AuthenticationManager>()
    if (authManager != null) {
        CoroutineScope(Dispatchers.Main).launch {
            authManager.startGoogleAuthentication()
        }
    } else {
        println("startGoogleAuth(): AuthenticationManager not found on iOS")
    }
}

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}
