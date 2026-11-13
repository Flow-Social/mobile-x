package me.floow.shared.platform

import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext

actual fun systemShareText(text: String) {
    val context = runCatching { GlobalContext.get().get<Context>() }.getOrNull() ?: return
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }

    val shareIntent = Intent.createChooser(sendIntent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(shareIntent)
}
