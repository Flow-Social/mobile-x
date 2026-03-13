package me.floow.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import me.floow.app.MainActivity
import me.floow.app.R
import org.koin.core.context.GlobalContext
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

		val fallbackBody = message.notification?.body
			?: message.data["body"]
			?: ""
		val chatPayload = parseChatNotificationPayload(message.data, fallbackBody)
		if (chatPayload != null) {
			val koin = GlobalContext.getKoinApplicationOrNull()?.koin ?: return
			val pipeline = koin.get<NotificationPipeline>()
			pipeline.process(chatPayload, chatPayload.source)
			return
		}

        val defaultTitle = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val defaultBody = fallbackBody
        val title = defaultTitle
        val body = defaultBody
        val channelId = PushNotificationChannels.resolve(message.data["channel"])
        val imageUrl = message.data["image_url"] ?: message.notification?.imageUrl?.toString()
		val notificationDedupId = message.data["notification_id"]
			?: message.data["notification_seq"]
			?: message.messageId
			?: "${message.sentTime}_${title.hashCode()}_${body.hashCode()}"
		if (!shouldDisplayNotification(notificationDedupId)) return
		val notificationId = buildNotificationId(message)

        val contentIntent = PendingIntent.getActivity(
            this,
			buildPendingIntentRequestCode(notificationDedupId),
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
				buildPushDeepLinkUri(message)?.let { uri ->
					data = uri
				}
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(resolveAppNotificationLargeIcon())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(contentIntent)

		val bitmap = loadBitmap(imageUrl)
		if (bitmap != null) {
			builder.setStyle(
				NotificationCompat.BigPictureStyle()
					.bigPicture(bitmap)
					.setSummaryText(body)
			)
		}

		NotificationManagerCompat.from(this).notify(notificationId, builder.build())
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
			?: message.data["message_id"]
			?: message.data["notification_seq"]
			?: message.messageId
			?: "${System.currentTimeMillis()}_${message.sentTime}"
        return source.hashCode()
    }

	private fun buildPendingIntentRequestCode(dedupId: String): Int {
		return dedupId.trim().ifBlank { "fallback_pending_intent" }.hashCode()
	}

	private fun buildPushDeepLinkUri(message: RemoteMessage): Uri? {
		val conversationId = message.data["conversation_id"]
			?.trim()
			?.toLongOrNull()
			?.takeIf { it > 0L }
			?: return null
		return ChatPushDeepLink.buildUri(
			conversationId = conversationId,
			messageId = message.data["message_id"],
			interlocutorId = message.data["sender_id"],
			interlocutorName = message.data["sender_name"]
		)
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

	private fun resolveAppNotificationLargeIcon(): Bitmap? {
		val drawable: Drawable = runCatching { applicationInfo.loadIcon(packageManager) }.getOrNull() ?: return null
		val width = runCatching {
			resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
		}.getOrDefault(96)
		val height = runCatching {
			resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
		}.getOrDefault(96)
		return runCatching {
			drawable.toBitmap(width = width, height = height, config = Bitmap.Config.ARGB_8888)
		}.getOrNull()
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

	private fun parseChatNotificationPayload(
		data: Map<String, String>,
		fallbackBody: String
	): ChatNotificationPayload? {
		val type = data["type"]?.trim()?.lowercase().orEmpty()
		if (type.isNotEmpty() && type != "chat_message") return null
		val conversationId = data["conversation_id"]
			?.trim()
			?.toLongOrNull()
			?.takeIf { it > 0L }
			?: return null
		val messageId = data["message_id"]
			?.trim()
			?.toLongOrNull()
			?.takeIf { it > 0L }
			?: return null
		val isGroup = data["is_group"]
			?.trim()
			?.lowercase()
			?.let { value -> value == "1" || value == "true" || value == "yes" }
			?: false
		val timestampRaw = data["message_timestamp"]
			?.trim()
			?.toLongOrNull()
		val timestampMs = timestampRaw?.let { raw ->
			if (raw in 1L..9_999_999_999L) raw * 1000L else raw
		} ?: System.currentTimeMillis()
		val messageText = data["message_text"]
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?: fallbackBody.trim().ifBlank { "Новое сообщение" }
		val isFallback = data["is_fallback"]
			?.trim()
			?.lowercase()
			?.let { value -> value == "1" || value == "true" || value == "yes" }
			?: false
		val notificationId = data["notification_id"]
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?: "chat:$conversationId:$messageId"

		return ChatNotificationPayload(
			type = if (type.isNotEmpty()) type else "chat_message",
			notificationId = notificationId,
			conversationId = conversationId,
			messageId = messageId,
			senderId = data["sender_id"],
			senderName = data["sender_name"],
			senderAvatarUrl = data["sender_avatar_url"],
			messageText = messageText,
			messageTimestampMs = timestampMs,
			isGroup = isGroup,
			conversationTitle = data["conversation_title"],
			isFallback = isFallback,
			source = if (isFallback) ChatNotificationSource.FALLBACK else ChatNotificationSource.FCM
		)
	}
}
