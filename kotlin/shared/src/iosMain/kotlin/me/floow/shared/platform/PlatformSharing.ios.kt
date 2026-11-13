package me.floow.shared.platform

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun systemShareText(text: String) {
    runCatching {
        val window = UIApplication.sharedApplication.keyWindow
        val rootViewController = window?.rootViewController
        val activityViewController = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null
        )
        rootViewController?.presentViewController(activityViewController, animated = true, completion = null)
    }.onFailure {
        println("Share failed: ${it.message}")
    }
}
