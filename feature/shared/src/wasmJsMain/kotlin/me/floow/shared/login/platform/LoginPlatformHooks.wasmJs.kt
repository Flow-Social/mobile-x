package me.floow.shared.login.platform

import kotlinx.browser.window

actual fun showToast(message: String) {
    window.alert(message)
}

actual fun startGoogleAuth() {
    println("startGoogleAuth() is not implemented for Wasm")
}

actual fun openUrl(url: String) {
    window.open(url, "_blank")
}
