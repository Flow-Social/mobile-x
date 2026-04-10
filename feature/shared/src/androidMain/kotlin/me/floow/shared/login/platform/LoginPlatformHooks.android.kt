package me.floow.shared.login.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.koin.core.context.GlobalContext

actual fun showToast(message: String) {
    val context = runCatching { GlobalContext.get().get<Context>() }.getOrNull() ?: return
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

actual fun startGoogleAuth() {
    println("startGoogleAuth() is not implemented in shared Android UI")
}

actual fun openUrl(url: String) {
    val context = runCatching { GlobalContext.get().get<Context>() }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
