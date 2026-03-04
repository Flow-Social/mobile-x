package me.floow.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import me.floow.app.MainActivity
import me.floow.app.R
import java.net.HttpURLConnection
import java.net.URL

class FlowFirebaseMessagingService : FirebaseMessagingService() {
	private val dedupLock = Any()

    override fun onNewToken(token: String) {
        PushTokenSyncScheduler.enqueueNow(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushNotificationChannels.ensureCreated(applicationContext)
        if (!hasNotificationsPermission()) return

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""
        val channelId = PushNotificationChannels.resolve(message.data["channel"])
        val imageUrl = message.data["image_url"] ?: message.notification?.imageUrl?.toString()
		val notificationDedupId = message.data["notification_id"]
			?: message.data["notification_seq"]
			?: message.messageId
			?: "${message.sentTime}_${title.hashCode()}_${body.hashCode()}"
		if (!shouldDisplayNotification(notificationDedupId)) return

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)

        val bitmap = loadBitmap(imageUrl)
        if (bitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(body)
            )
        }

        NotificationManagerCompat.from(this).notify(buildNotificationId(message), builder.build())
    }

    private fun hasNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotificationId(message: RemoteMessage): Int {
        val source = message.data["notification_id"]
			?: message.data["notification_seq"]
			?: message.messageId
			?: "${System.currentTimeMillis()}_${message.sentTime}"
        return source.hashCode()
    }

	private fun shouldDisplayNotification(dedupId: String): Boolean {
		val normalizedId = dedupId.trim()
		if (normalizedId.isEmpty()) return true
		synchronized(dedupLock) {
			val prefs = getSharedPreferences(PUSH_DEDUP_PREFS, Context.MODE_PRIVATE)
			val current = prefs.getString(PUSH_DEDUP_IDS_KEY, "")
				.orEmpty()
				.split('|')
				.map(String::trim)
				.filter(String::isNotEmpty)
				.toMutableList()
			if (current.contains(normalizedId)) {
				return false
			}
			current.add(normalizedId)
			if (current.size > MAX_STORED_NOTIFICATION_IDS) {
				current.subList(0, current.size - MAX_STORED_NOTIFICATION_IDS).clear()
			}
			prefs.edit()
				.putString(PUSH_DEDUP_IDS_KEY, current.joinToString("|"))
				.apply()
			return true
		}
	}

    private fun loadBitmap(imageUrl: String?): Bitmap? {
        val urlRaw = imageUrl?.trim().orEmpty()
        if (urlRaw.isBlank()) return null

        return runCatching {
            val connection = URL(urlRaw).openConnection() as HttpURLConnection
            connection.connectTimeout = 3_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            connection.inputStream.use(BitmapFactory::decodeStream)
        }.getOrNull()
    }

	private companion object {
		const val PUSH_DEDUP_PREFS = "push_replies_dedup"
		const val PUSH_DEDUP_IDS_KEY = "recent_notification_ids"
		const val MAX_STORED_NOTIFICATION_IDS = 120
	}
}
