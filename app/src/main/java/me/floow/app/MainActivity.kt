package me.floow.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import me.floow.chatssearch.di.usersearchModule
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.floow.app.BuildConfig.*
import me.floow.app.deeplink.DeepLinkDispatcher
import me.floow.app.di.apiModule
import me.floow.app.di.appModule
import me.floow.app.di.authModule
import me.floow.app.di.createAppControllerModule
import me.floow.app.di.dataModule
import me.floow.app.di.databaseModule
import me.floow.app.di.domainModule
import me.floow.app.di.mockAuthModule
import me.floow.app.di.mockDataModule
import me.floow.app.di.mockModule
import me.floow.app.navigation.AuthDestinationsCluster
import me.floow.app.navigation.MainDestinationsCluster
import me.floow.app.ui.App
import me.floow.chats.di.chatsModule
import me.floow.comments.di.commentsModule
import me.floow.domain.auth.AuthenticationManager
import me.floow.feed.di.feedModule
import me.floow.login.di.loginModule
import me.floow.profile.di.profileModule
import me.floow.uikit.theme.FlowTheme
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.util.Locale

class MainActivity : ComponentActivity() {
	private lateinit var coroutineScope: CoroutineScope

	override fun onCreate(savedInstanceState: Bundle?) {
		installSplashScreen()

		super.onCreate(savedInstanceState)

		if (GlobalContext.getKoinApplicationOrNull() == null) {
			startKoin {
				androidContext(this@MainActivity)

				@Suppress("KotlinConstantConditions")
				if (USE_MOCK_DATA) {
					modules(
						appModule,
						createAppControllerModule(this@MainActivity),
						apiModule,
						mockAuthModule,
						databaseModule,
						mockDataModule,
						domainModule,
						mockModule,
						loginModule,
						profileModule,
						usersearchModule,
						chatsModule,
						commentsModule,
						feedModule
					)
				} else {
					modules(
						appModule,
						createAppControllerModule(this@MainActivity),
						apiModule,
						authModule,
						databaseModule,
						dataModule,
						domainModule,
						mockModule,
						loginModule,
						profileModule,
						usersearchModule,
						chatsModule,
						commentsModule,
						feedModule
					)
				}
			}
		}

		coroutineScope = CoroutineScope(CoroutineName("MainActivity"))
		val authenticationManager: AuthenticationManager = getKoin().get()

		enableEdgeToEdge()

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
}
