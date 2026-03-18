package me.floow.app.launch

import android.content.Intent
import android.net.Uri
import me.floow.app.navigation.AuthDestinationsCluster
import me.floow.app.navigation.EditProfileScreen
import me.floow.app.navigation.MainDestinationsCluster
import me.floow.app.navigation.NavigationRoute
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.deeplink.DeepLinkUrls

internal sealed interface LaunchBootstrapState {
	data object Loading : LaunchBootstrapState
	data class Ready(val result: LaunchBootstrapResult) : LaunchBootstrapState
}

internal data class LaunchBootstrapResult(
	val startDestination: NavigationRoute,
	val shouldDispatchInitialIntentAfterLaunch: Boolean = false
)

internal class LaunchBootstrapper(
	private val authenticationManager: AuthenticationManager
) {
	fun resolve(intent: Intent?): LaunchBootstrapResult {
		if (authenticationManager.hasPendingRegistration()) {
			val initialData = authenticationManager.getPendingRegistrationInitialDataOrNull()
			return LaunchBootstrapResult(
				startDestination = EditProfileScreen(
					name = initialData?.name.orEmpty(),
					username = initialData?.username.orEmpty(),
					description = initialData?.description.orEmpty(),
					avatarUrl = null,
					backgroundUrl = null
				)
			)
		}

		if (!authenticationManager.isSignedIn()) {
			return LaunchBootstrapResult(startDestination = AuthDestinationsCluster)
		}

		return resolveSignedInDeepLink(intent)
			?: LaunchBootstrapResult(startDestination = MainDestinationsCluster)
	}

	private fun resolveSignedInDeepLink(intent: Intent?): LaunchBootstrapResult? {
		val data = intent?.data ?: return null

		if (!data.isChatDeepLink() && !data.isFlowWebDeepLink()) return null

		return LaunchBootstrapResult(
			startDestination = MainDestinationsCluster,
			shouldDispatchInitialIntentAfterLaunch = true
		)
	}

	private fun Uri.isChatDeepLink(): Boolean {
		if (scheme != "me.floow.app" || host != "chat") return false
		return !getQueryParameter("conversation_id").isNullOrBlank()
	}

	private fun Uri.isFlowWebDeepLink(): Boolean {
		if (scheme != "https") return false
		return DeepLinkUrls.isSupportedWebUrl(toString())
	}
}
