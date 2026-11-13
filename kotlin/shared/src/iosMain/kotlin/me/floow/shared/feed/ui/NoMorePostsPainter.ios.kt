package me.floow.shared.feed.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.myvector

@Composable
internal actual fun rememberNoMorePostsPainter(): Painter {
    return painterResource(Res.drawable.myvector)
}
