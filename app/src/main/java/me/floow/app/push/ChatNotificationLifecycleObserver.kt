package me.floow.app.push

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class ChatNotificationLifecycleObserver(
	private val foregroundVisibleChatStore: ForegroundVisibleChatStore
) : DefaultLifecycleObserver {
	override fun onStart(owner: LifecycleOwner) {
		foregroundVisibleChatStore.onAppForeground()
	}

	override fun onStop(owner: LifecycleOwner) {
		foregroundVisibleChatStore.onAppBackground()
	}
}
