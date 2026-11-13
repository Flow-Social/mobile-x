package me.floow.app.presence

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import me.floow.domain.data.repos.PresenceRepository

class PresenceLifecycleObserver(
	private val presenceRepository: PresenceRepository
) : DefaultLifecycleObserver {
	override fun onStart(owner: LifecycleOwner) {
		presenceRepository.onAppForeground()
	}

	override fun onStop(owner: LifecycleOwner) {
		presenceRepository.onAppBackground()
	}
}
