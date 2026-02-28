package me.floow.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import me.floow.app.navigation.FlowNavHost
import me.floow.app.navigation.NavigationRoute

@Composable
fun App(
	startDestination: NavigationRoute,
	modifier: Modifier = Modifier
) {
	val navController = rememberNavController()
	Surface(
		modifier = modifier.fillMaxSize(),
		color = MaterialTheme.colorScheme.surfaceContainer
	) {
		FlowNavHost(
			navController = navController,
			startDestination = startDestination,
			modifier = Modifier.fillMaxSize()
		)
	}
}
