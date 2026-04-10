package me.floow.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import me.floow.app.deeplink.DeepLinkDispatcher
import me.floow.app.launch.LaunchBootstrapState
import me.floow.app.launch.LaunchBootstrapper
import kotlinx.coroutines.launch
import me.floow.app.ui.App
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.deeplink.DeepLinkUrls
import me.floow.uikit.theme.FlowTheme
import org.koin.android.ext.android.getKoin
import java.util.Locale

class MainActivity : ComponentActivity() {
	private var launchBootstrapState: LaunchBootstrapState by mutableStateOf(LaunchBootstrapState.Loading)

	override fun onCreate(savedInstanceState: Bundle?) {
		val splashScreen = installSplashScreen()

		super.onCreate(savedInstanceState)

		val authenticationManager: AuthenticationManager = getKoin().get()
		handleGoogleOAuthIntent(intent, authenticationManager)
		val launchBootstrapper = LaunchBootstrapper(authenticationManager)

		splashScreen.setKeepOnScreenCondition {
			launchBootstrapState is LaunchBootstrapState.Loading
		}
		splashScreen.setOnExitAnimationListener { splashProvider ->
			val translationY = TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP,
				-6f,
				resources.displayMetrics
			)
			splashProvider.view.animate()
				.alpha(0f)
				.scaleX(0.78f)
				.scaleY(0.78f)
				.translationY(translationY)
				.setDuration(180L)
				.withEndAction { splashProvider.remove() }
				.start()
		}

		enableEdgeToEdge()

		setContent {
			FlowTheme {
				val currentState = launchBootstrapState
				if (currentState is LaunchBootstrapState.Ready) {
					App(
						startDestination = currentState.result.startDestination,
						Modifier.fillMaxSize()
					)
				}
			}
		}

		lifecycleScope.launch {
			val launchResult = launchBootstrapper.resolve(intent)
			launchBootstrapState = LaunchBootstrapState.Ready(launchResult)
			if (launchResult.shouldDispatchInitialIntentAfterLaunch) {
				pushDeepLinkIntent(intent)
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		val authenticationManager: AuthenticationManager = getKoin().get()
		setIntent(intent)

		handleGoogleOAuthIntent(intent, authenticationManager)

		pushDeepLinkIntent(intent)
		super.onNewIntent(intent)
	}

	private fun pushDeepLinkIntent(intent: Intent) {
		val data = intent.data ?: return
		val isSupportedWebDeepLink = DeepLinkUrls.isSupportedWebUrl(data.toString())
		val isCustomScheme = data.scheme == "me.floow.app"
		if (!isSupportedWebDeepLink && !isCustomScheme) return

		val dispatcher: DeepLinkDispatcher = getKoin().get()
		dispatcher.push(intent)
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
