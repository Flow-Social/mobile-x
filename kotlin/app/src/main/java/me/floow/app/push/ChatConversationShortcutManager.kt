package me.floow.app.push

import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.drawable.IconCompat
import me.floow.app.MainActivity
import me.floow.app.R

object ChatConversationShortcutManager {
	fun ensureShortcut(
		context: Context,
		conversationId: Long,
		shortLabel: String,
		isGroup: Boolean,
		person: Person,
		avatarIcon: IconCompat?
	) {
		if (conversationId <= 0L) return
		val normalizedLabel = shortLabel.trim().ifEmpty { context.getString(R.string.app_name) }
		val shortcutId = buildShortcutId(conversationId)
		val intent = Intent(context, MainActivity::class.java).apply {
			action = Intent.ACTION_VIEW
			addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
			data = ChatPushDeepLink.buildUri(
				conversationId = conversationId,
				messageId = null,
				interlocutorId = null,
				interlocutorName = null
			)
		}
		val icon = avatarIcon ?: IconCompat.createWithResource(context, R.drawable.ic_notification_small)
		val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
			.setShortLabel(normalizedLabel)
			.setLongLabel(normalizedLabel)
			.setIntent(intent)
			.setPerson(person)
			.setIcon(icon)
			.setLongLived(true)
			.setIsConversation()
			.setLocusId(LocusIdCompat(shortcutId))
			.build()

		ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
		if (!isGroup) {
			ShortcutManagerCompat.reportShortcutUsed(context, shortcutId)
		}
	}

	fun buildShortcutId(conversationId: Long): String {
		return "conversation:$conversationId"
	}
}
