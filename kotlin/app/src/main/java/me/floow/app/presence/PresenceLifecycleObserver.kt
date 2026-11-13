package me.floow.app.presence

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.lifecycle.AppLifecycleBridge

class PresenceLifecycleObserver(
	private val presenceRepository: PresenceRepository
) : DefaultLifecycleObserver {
	override fun onStart(owner: LifecycleOwner) {
		AppLifecycleBridge.onAppForeground()
		presenceRepository.onAppForeground()
	}

	override fun onStop(owner: LifecycleOwner) {
		AppLifecycleBridge.onAppBackground()
		presenceRepository.onAppBackground()
	}
}
