package me.floow.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.floow.app.deeplink.DeepLinkDispatcher
import me.floow.app.launch.shouldDispatchInitialIntent
import me.floow.app.settings.ThemeMode
import me.floow.app.settings.ThemePreferenceManager
import me.floow.domain.auth.AuthTokenState
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.TokenStorage
import me.floow.domain.deeplink.DeepLinkUrls
import org.koin.android.ext.android.getKoin
import java.util.Locale

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		val splashScreen = installSplashScreen()

		super.onCreate(savedInstanceState)

		val authenticationManager: AuthenticationManager = getKoin().get()
		val tokenStorage: TokenStorage = getKoin().get()
		val syncCoordinator: me.floow.app.notifications.DirectChatsSyncCoordinator = getKoin().get()
		syncCoordinator.start()
		getKoin().getOrNull<me.floow.shared.chats.uilogic.ChatsListRepository>()
		getKoin().getOrNull<me.floow.shared.profile.uilogic.ProfileRepository>()
		handleGoogleOAuthIntent(intent, authenticationManager)

		splashScreen.setKeepOnScreenCondition {
			tokenStorage.authState.value is AuthTokenState.Loading
		}

		enableEdgeToEdge()

		ThemePreferenceManager.init(applicationContext)

		setContent {
			val themeMode by ThemePreferenceManager.themeMode.collectAsState()
			val isDark = when (themeMode) {
				ThemeMode.SYSTEM -> isSystemInDarkTheme()
				ThemeMode.LIGHT -> false
				ThemeMode.DARK -> true
			}
			FlowApp(darkTheme = isDark)
		}

		if (shouldDispatchInitialIntent(intent)) {
			pushDeepLinkIntent(intent)
		}
	}

	override fun onNewIntent(intent: Intent) {
		val authenticationManager: AuthenticationManager = getKoin().get()
		setIntent(intent)

		handleGoogleOAuthIntent(intent, authenticationManager)

		pushDeepLinkIntent(intent)
		super.onNewIntent(intent)
	}

	override fun onResume() {
		super.onResume()
		// Failsafe: Ensure MainActivity window never retains FLAG_ALT_FOCUSABLE_IM
		window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
	}

	private fun pushDeepLinkIntent(intent: Intent) {
		val data = intent.data ?: return
		val uriString = data.toString()
		val isSupportedWebDeepLink = DeepLinkUrls.isSupportedWebUrl(uriString)
		val isCustomScheme = data.scheme == "me.floow.app" || data.scheme == "floow"
		if (!isSupportedWebDeepLink && !isCustomScheme) return

		val dispatcher: DeepLinkDispatcher = getKoin().get()
		dispatcher.push(intent)
		me.floow.app.navigation.FlowDeepLinkHandler.openUri(uriString)
	}

	private fun extractGoogleOAuthCode(intent: Intent): String? {
		val data = intent.data ?: return null
		if (data.scheme != GOOGLE_OAUTH_SCHEME) return null
		return runCatching { data.getQueryParameter("code") }.getOrNull()
	}

	private fun handleGoogleOAuthIntent(
		intent: Intent?,
		authenticationManager: AuthenticationManager,
	) {
		val code = intent?.let(::extractGoogleOAuthCode) ?: return
		lifecycleScope.launch {
			authenticationManager.handleGoogleOAuthCode(code)
		}
	}

	override fun attachBaseContext(newBase: Context?) {
		// TODO: remove at production
		val newConfiguration = Configuration(newBase?.resources?.configuration).apply {
			setLocale(Locale("ru"))
		}

		super.attachBaseContext(newBase?.createConfigurationContext(newConfiguration))
	}

	private companion object {
		const val GOOGLE_OAUTH_SCHEME =
			"com.googleusercontent.apps.291755427997-hjaabnfaa435ikjlsocejeg5p9elraj8"
	}
}
