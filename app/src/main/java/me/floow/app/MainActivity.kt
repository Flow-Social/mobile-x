package me.floow.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.floow.app.deeplink.DeepLinkDispatcher
import me.floow.app.navigation.AuthDestinationsCluster
import me.floow.app.navigation.MainDestinationsCluster
import me.floow.app.push.PushTokenSyncScheduler
import me.floow.app.ui.App
import me.floow.domain.auth.AuthenticationManager
import me.floow.uikit.theme.FlowTheme
import org.koin.android.ext.android.getKoin
import java.util.Locale

class MainActivity : ComponentActivity() {
	private lateinit var coroutineScope: CoroutineScope
	private val requestNotificationsPermissionLauncher =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
			if (isGranted) {
				PushTokenSyncScheduler.enqueueNow(this)
			}
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		installSplashScreen()

		super.onCreate(savedInstanceState)

		coroutineScope = CoroutineScope(CoroutineName("MainActivity"))
		val authenticationManager: AuthenticationManager = getKoin().get()

		enableEdgeToEdge()
		requestNotificationsPermissionIfNeeded()

		val startDestination = when (authenticationManager.isSignedIn()) {
			false -> AuthDestinationsCluster
			true -> MainDestinationsCluster
		}

		setContent {
			FlowTheme {
				App(
					startDestination = startDestination,
					Modifier.fillMaxSize()
				)
			}
		}

		pushDeepLinkIntent(intent)
	}

	override fun onNewIntent(intent: Intent) {
		val authenticationManager: AuthenticationManager = getKoin().get()

		intent.data?.getQueryParameter("code")?.let { code ->
			coroutineScope.launch {
				authenticationManager.handleGoogleOAuthCode(code)
			}
		}

		pushDeepLinkIntent(intent)
		super.onNewIntent(intent)
	}

	private fun pushDeepLinkIntent(intent: Intent) {
		val data = intent.data ?: return
		val isHttpsFloow = data.scheme == "https" && data.host == "floow.me"
		val isHttpsFlowSocial = data.scheme == "https" && data.host == "flow-social.github.io"
		val isCustomScheme = data.scheme == "me.floow.app"
		if (!isHttpsFloow && !isHttpsFlowSocial && !isCustomScheme) return

		val dispatcher: DeepLinkDispatcher = getKoin().get()
		dispatcher.push(intent)
	}

	override fun attachBaseContext(newBase: Context?) {
		// TODO: remove at production
		val newConfiguration = Configuration(newBase?.resources?.configuration).apply {
			setLocale(Locale("ru"))
		}

		super.attachBaseContext(newBase?.createConfigurationContext(newConfiguration))
	}

	private fun requestNotificationsPermissionIfNeeded() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			PushTokenSyncScheduler.enqueueNow(this)
			return
		}

		if (ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.POST_NOTIFICATIONS
			) == PackageManager.PERMISSION_GRANTED
		) {
			PushTokenSyncScheduler.enqueueNow(this)
			return
		}

		requestNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
	}
}
