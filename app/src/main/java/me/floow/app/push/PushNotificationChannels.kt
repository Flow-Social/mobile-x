package me.floow.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import me.floow.app.R

object PushNotificationChannels {
    const val CHANNEL_SOCIAL = "social"
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_SYSTEM = "system"

    data class ChannelSpec(
        val id: String,
        @StringRes val nameRes: Int,
        val importance: Int
    )

    private val channels = listOf(
        ChannelSpec(
            id = CHANNEL_SOCIAL,
            nameRes = R.string.notifications_channel_social,
            importance = NotificationManager.IMPORTANCE_DEFAULT
        ),
        ChannelSpec(
            id = CHANNEL_MESSAGES,
            nameRes = R.string.notifications_channel_messages,
            importance = NotificationManager.IMPORTANCE_HIGH
        ),
        ChannelSpec(
            id = CHANNEL_SYSTEM,
            nameRes = R.string.notifications_channel_system,
            importance = NotificationManager.IMPORTANCE_DEFAULT
        )
    )

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        channels.forEach { spec ->
            val channel = NotificationChannel(
                spec.id,
                context.getString(spec.nameRes),
                spec.importance
            )
            manager.createNotificationChannel(channel)
        }
    }

    fun resolve(channelRaw: String?): String {
        return when (channelRaw?.trim()?.lowercase()) {
            CHANNEL_MESSAGES -> CHANNEL_MESSAGES
            CHANNEL_SYSTEM -> CHANNEL_SYSTEM
            else -> CHANNEL_SOCIAL
        }
    }
}
