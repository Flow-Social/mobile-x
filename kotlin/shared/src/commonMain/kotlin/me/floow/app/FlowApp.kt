package me.floow.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import me.floow.app.navigation.FlowRootNavHost
import me.floow.uikit.theme.FlowTheme
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import org.koin.compose.koinInject

@Composable
fun FlowApp(
    darkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
) {
    koinInject<ChatThreadRepository>() // Start pending direct-chat receipts on app start.
    FlowTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        Surface(
            modifier = modifier.fillMaxSize()
        ) {
            FlowRootNavHost(navController = navController)
        }
    }
}
