package me.floow.app.launch

import android.content.Intent
import me.floow.domain.deeplink.DeepLinkUrls

internal sealed interface LaunchBootstrapState {
	data object Loading : LaunchBootstrapState
	data object Ready : LaunchBootstrapState
}

internal class LaunchBootstrapper {
	fun shouldDispatchInitialIntent(intent: Intent?): Boolean {
		val data = intent?.data ?: return false
		if (data.scheme == "me.floow.app" || data.scheme == "floow") return true
		if (data.scheme == "https" && DeepLinkUrls.isSupportedWebUrl(data.toString())) return true
		return false
	}
}
