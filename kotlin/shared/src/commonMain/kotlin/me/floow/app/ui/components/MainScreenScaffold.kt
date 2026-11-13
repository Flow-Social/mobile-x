package me.floow.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import me.floow.app.navigation.MainTab
import me.floow.uikit.components.shell.MainContentContainer
import me.floow.uikit.components.shell.MainShellBackground
import me.floow.uikit.components.shell.MainShellDefaults

enum class MainScreenScaffoldMode {
    Default,
    Immersive
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenScaffold(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    shellMode: MainScreenScaffoldMode = MainScreenScaffoldMode.Default,
    feedUndoEnabled: Boolean = false,
    chatsUnreadCount: Int = 0,
    onFeedUndoClick: (() -> Unit)? = null,
    navigationItems: List<BottomNavigationItem> = defaultBottomNavigationItems,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            FlowBottomBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                navigationItems = navigationItems,
                feedUndoEnabled = feedUndoEnabled,
                chatsUnreadCount = chatsUnreadCount,
                onFeedUndoClick = onFeedUndoClick
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val containerPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = innerPadding.calculateBottomPadding()
        )
        val contentPadding = PaddingValues(
            start = 0.dp,
            top = if (shellMode == MainScreenScaffoldMode.Default) {
                innerPadding.calculateTopPadding()
            } else {
                0.dp
            },
            end = 0.dp,
            bottom = 0.dp
        )

        MainShellBackground(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            MainContentContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(containerPadding),
                shape = if (shellMode == MainScreenScaffoldMode.Immersive) RectangleShape else MainShellDefaults.contentShape,
                containerColor = if (shellMode == MainScreenScaffoldMode.Immersive) Color.Black else MainShellDefaults.contentContainerColor
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .consumeWindowInsets(contentPadding)
                ) {
                    content(contentPadding)
                }
            }
        }
    }
}
