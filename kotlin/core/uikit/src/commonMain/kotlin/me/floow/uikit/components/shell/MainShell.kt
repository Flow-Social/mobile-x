package me.floow.uikit.components.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object MainShellDefaults {
    val contentShape: Shape
        @Composable get() = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)

    val appBackgroundColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainer

    val contentContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surface

    val bottomBarSelectedColor: Color
        @Composable get() = MaterialTheme.colorScheme.onBackground

    val bottomBarUnselectedColor: Color
        @Composable get() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
}

@Composable
fun MainShellBackground(
    modifier: Modifier = Modifier,
    color: Color = MainShellDefaults.appBackgroundColor,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color)
    ) {
        content()
    }
}

@Composable
fun MainContentContainer(
    modifier: Modifier = Modifier,
    shape: Shape = MainShellDefaults.contentShape,
    containerColor: Color = MainShellDefaults.contentContainerColor,
    applyBottomInsets: Boolean = false,
    content: @Composable () -> Unit
) {
    val insetModifier = if (applyBottomInsets) {
        Modifier.navigationBarsPadding()
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.then(insetModifier),
        color = containerColor,
        shape = shape
    ) {
        content()
    }
}
