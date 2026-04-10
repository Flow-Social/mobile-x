package me.floow.uikit.theme

import androidx.compose.ui.graphics.Shape

expect fun getFlowStarShape(): Shape

val NinehedronShape: Shape
    get() = getFlowStarShape()
