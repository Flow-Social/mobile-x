package me.floow.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import me.floow.app.MainActivity
import me.floow.app.R
import java.net.HttpURLConnection
import java.net.URL

class ChatNotificationRenderer(
	private val context: Context
) {
	fun render(payload: ChatNotificationPayload): RenderOutcome {
		PushNotificationChannels.ensureCreated(context)
		if (!hasNotificationsPermission()) return RenderOutcome.Failed
		if (shouldSuppressChatNotification(payload)) return RenderOutcome.Suppressed

		val notification = buildNotification(payload)
		val tag = "chat:${payload.conversationId}"
		val manager = NotificationManagerCompat.from(context)
		manager.notify(tag, MAIN_NOTIFICATION_ID, notification)
		manager.notify(tag, SUMMARY_NOTIFICATION_ID, buildSummaryNotification(payload))

		DirectChatNotificationCenter.registerConversationNotification(
			context = context,
			conversationId = payload.conversationId,
			notificationTag = tag,
			notificationId = MAIN_NOTIFICATION_ID
		)
		DirectChatNotificationCenter.registerConversationNotification(
			context = context,
			conversationId = payload.conversationId,
			notificationTag = tag,
			notificationId = SUMMARY_NOTIFICATION_ID
		)
		if (!payload.isGroup && !payload.senderId.isNullOrBlank()) {
			DirectChatNotificationCenter.registerInterlocutorNotification(
				context = context,
				interlocutorId = payload.senderId,
				notificationTag = tag,
				notificationId = MAIN_NOTIFICATION_ID
			)
		}
		return RenderOutcome.Rendered
	}

	private fun buildNotification(payload: ChatNotificationPayload): android.app.Notification {
		val contentIntent = PendingIntent.getActivity(
			context,
			buildPendingIntentRequestCode(payload.notificationId),
			Intent(context, MainActivity::class.java).apply {
				addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
				ChatPushDeepLink.buildUri(
					conversationId = payload.conversationId,
					messageId = payload.messageId.toString(),
					interlocutorId = payload.senderId,
					interlocutorName = payload.senderName
				)?.let { data = it }
			},
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		val avatarBitmap = withWakeLock(payload.senderAvatarUrl) { loadBitmap(payload.senderAvatarUrl) }
		val avatarIcon = avatarBitmap?.let { IconCompat.createWithBitmap(it) }
		val senderPerson = Person.Builder()
			.setName(payload.senderName ?: context.getString(R.string.app_name))
			.setIcon(avatarIcon)
			.build()
		val userPerson = Person.Builder()
			.setName(context.getString(R.string.notifications_you))
			.build()

		val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
			.setGroupConversation(payload.isGroup)
		if (payload.isGroup) {
			payload.conversationTitle?.takeIf(String::isNotEmpty)?.let { title ->
				messagingStyle.setConversationTitle(title)
			}
		}
		messagingStyle.addMessage(payload.messageText, payload.messageTimestampMs, senderPerson)

		val builder = NotificationCompat.Builder(context, PushNotificationChannels.CHANNEL_MESSAGES)
			.setSmallIcon(R.drawable.ic_notification_small)
			.setLargeIcon(avatarBitmap ?: resolveAppNotificationLargeIcon())
			.setContentTitle(resolveTitle(payload))
			.setContentText(payload.messageText)
			.setStyle(messagingStyle)
			.setAutoCancel(true)
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setCategory(NotificationCompat.CATEGORY_MESSAGE)
			.setContentIntent(contentIntent)
			.setGroup(buildGroupKey(payload))
			.setShortcutId(ChatConversationShortcutManager.buildShortcutId(payload.conversationId))
			.addAction(buildReplyAction(payload))

		if (payload.messageId > 0L) {
			builder.addAction(buildMarkReadAction(payload))
		}

		ChatConversationShortcutManager.ensureShortcut(
			context = context,
			conversationId = payload.conversationId,
			shortLabel = resolveTitle(payload),
			isGroup = payload.isGroup,
			person = senderPerson,
			avatarIcon = avatarIcon
		)

		return builder.build()
	}

	private fun buildSummaryNotification(payload: ChatNotificationPayload): android.app.Notification {
		return NotificationCompat.Builder(context, PushNotificationChannels.CHANNEL_MESSAGES)
			.setSmallIcon(R.drawable.ic_notification_small)
			.setContentTitle(resolveTitle(payload))
			.setContentText(payload.messageText)
			.setGroup(buildGroupKey(payload))
			.setGroupSummary(true)
			.setAutoCancel(true)
			.build()
	}

	private fun buildReplyAction(payload: ChatNotificationPayload): NotificationCompat.Action {
		val intent = Intent(context, ChatNotificationActionReceiver::class.java).apply {
			action = ChatNotificationActionReceiver.ACTION_REPLY
			putExtra(ChatNotificationActionReceiver.EXTRA_CONVERSATION_ID, payload.conversationId)
			putExtra(ChatNotificationActionReceiver.EXTRA_MESSAGE_ID, payload.messageId)
		}
		val pendingIntent = PendingIntent.getBroadcast(
			context,
			buildActionRequestCode(payload.conversationId, "reply"),
			intent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)
		val remoteInput = androidx.core.app.RemoteInput.Builder(ChatNotificationActionReceiver.KEY_TEXT_REPLY)
			.setLabel(context.getString(R.string.notifications_reply_hint))
			.build()
		return NotificationCompat.Action.Builder(
			R.drawable.ic_notification_small,
			context.getString(R.string.notifications_reply_action),
			pendingIntent
		)
			.addRemoteInput(remoteInput)
			.setAllowGeneratedReplies(true)
			.build()
	}

	private fun buildMarkReadAction(payload: ChatNotificationPayload): NotificationCompat.Action {
		val intent = Intent(context, ChatNotificationActionReceiver::class.java).apply {
			action = ChatNotificationActionReceiver.ACTION_MARK_READ
			putExtra(ChatNotificationActionReceiver.EXTRA_CONVERSATION_ID, payload.conversationId)
			putExtra(ChatNotificationActionReceiver.EXTRA_MESSAGE_ID, payload.messageId)
		}
		val pendingIntent = PendingIntent.getBroadcast(
			context,
			buildActionRequestCode(payload.conversationId, "mark_read"),
			intent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)
		return NotificationCompat.Action.Builder(
			R.drawable.ic_notification_small,
			context.getString(R.string.notifications_mark_read_action),
			pendingIntent
		).build()
	}

	private fun buildGroupKey(payload: ChatNotificationPayload): String {
		return "chat_group:${payload.conversationId}"
	}

	private fun resolveTitle(payload: ChatNotificationPayload): String {
		return if (payload.isGroup) {
			payload.conversationTitle?.takeIf(String::isNotEmpty)
				?: (payload.senderName ?: context.getString(R.string.app_name))
		} else {
			payload.senderName?.takeIf(String::isNotEmpty)
				?: context.getString(R.string.app_name)
		}
	}

	private fun resolveAppNotificationLargeIcon(): Bitmap? {
		val drawable: Drawable = runCatching { context.applicationInfo.loadIcon(context.packageManager) }.getOrNull() ?: return null
		val width = runCatching {
			context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
		}.getOrDefault(96)
		val height = runCatching {
			context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
		}.getOrDefault(96)
		return runCatching {
			drawable.toBitmap(width = width, height = height, config = Bitmap.Config.ARGB_8888)
		}.getOrNull()
	}

	private fun buildPendingIntentRequestCode(dedupId: String): Int {
		return dedupId.trim().ifBlank { "fallback_pending_intent" }.hashCode()
	}

	private fun buildActionRequestCode(conversationId: Long, action: String): Int {
		return "${conversationId}_$action".hashCode()
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

	private inline fun <T> withWakeLock(url: String?, block: () -> T): T {
		if (url.isNullOrBlank()) return block()
		val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
		val lock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "flow:chat-notification")
		return try {
			lock?.acquire(2000)
			block()
		} finally {
			if (lock?.isHeld == true) {
				lock.release()
			}
		}
	}

	private fun shouldSuppressChatNotification(payload: ChatNotificationPayload): Boolean {
		val activeConversationId = DirectChatNotificationCenter.getActiveConversationId(context)
		if (payload.conversationId == activeConversationId && payload.conversationId > 0L) return true
		val activeInterlocutorId = DirectChatNotificationCenter.getActiveInterlocutorId(context)
		if (!payload.senderId.isNullOrBlank() && payload.senderId == activeInterlocutorId) return true
		val selfUserId = getSelfUserIdOrNull()
		if (!payload.senderId.isNullOrBlank() && payload.senderId == selfUserId) return true
		return false
	}

	private fun getSelfUserIdOrNull(): String? {
		return context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
			.getString(AUTH_USER_ID_PREF_KEY, null)
			?.trim()
			?.takeIf(String::isNotEmpty)
	}

	private fun hasNotificationsPermission(): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
		return ContextCompat.checkSelfPermission(
			context,
			Manifest.permission.POST_NOTIFICATIONS
		) == PackageManager.PERMISSION_GRANTED
	}

	private companion object {
		const val AUTH_PREFS_NAME = "flowme.auth"
		const val AUTH_USER_ID_PREF_KEY = "authUserId"
		const val MAIN_NOTIFICATION_ID = 0
		const val SUMMARY_NOTIFICATION_ID = 1
	}
}

enum class RenderOutcome {
	Rendered,
	Suppressed,
	Failed
}
