package me.floow.shared.feed.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
internal actual fun rememberNoMorePostsPainter(): Painter {
    return painterResource(me.floow.shared.R.drawable.myvector)
}
