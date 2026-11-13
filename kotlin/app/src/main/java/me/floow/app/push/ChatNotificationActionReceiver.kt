package me.floow.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.floow.app.notifications.DirectMessagesReadSyncScheduler
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import org.koin.core.context.GlobalContext
import java.util.UUID

class ChatNotificationActionReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		val pendingResult = goAsync()
		val appContext = context.applicationContext
		val koin = GlobalContext.getKoinApplicationOrNull()?.koin
		if (koin == null) {
			pendingResult.finish()
			return
		}

		val conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, 0L)
		val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, 0L)

		val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
		when (intent.action) {
			ACTION_REPLY -> {
				val replyText = RemoteInput.getResultsFromIntent(intent)
					?.getCharSequence(KEY_TEXT_REPLY)
					?.toString()
					?.trim()
				if (replyText.isNullOrEmpty() || conversationId <= 0L) {
					pendingResult.finish()
					return
				}
				scope.launch {
					val chatsRepository = koin.get<ChatsRepository>()
					chatsRepository.sendMessage(
						conversationId = conversationId,
						text = replyText,
						clientMessageId = UUID.randomUUID().toString(),
						replyToMessageId = messageId.takeIf { it > 0L }
					)
					DirectChatNotificationCenter.cancelConversationNotifications(appContext, conversationId)
					pendingResult.finish()
				}
			}
			ACTION_MARK_READ -> {
				if (conversationId <= 0L || messageId <= 0L) {
					pendingResult.finish()
					return
				}
				scope.launch {
					val cursorStore = koin.get<DirectMessagesReadCursorStore>()
					val chatsRepository = koin.get<ChatsRepository>()
					cursorStore.enqueueReadUpTo(conversationId, messageId)
					chatsRepository.applyLocalReadUpTo(conversationId, messageId)
					cursorStore.setLocalLastReadMessageId(conversationId, messageId)
					DirectMessagesReadSyncScheduler.enqueueNow(appContext, conversationId)
					DirectChatNotificationCenter.cancelConversationNotifications(appContext, conversationId)
					pendingResult.finish()
				}
			}
			else -> pendingResult.finish()
		}
	}

	companion object {
		const val ACTION_REPLY = "me.floow.app.push.ACTION_REPLY"
		const val ACTION_MARK_READ = "me.floow.app.push.ACTION_MARK_READ"

		const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
		const val EXTRA_MESSAGE_ID = "extra_message_id"
		const val KEY_TEXT_REPLY = "key_text_reply"
	}
}
