package me.floow.profile.ui.common

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

private const val STATUS_BAR_LUMINANCE_THRESHOLD = 0.42f
private const val SHEET_SCRIM_ALPHA = 0.32f

internal data class StatusBarAppearance(
    val color: Color,
    val darkIcons: Boolean,
)

internal fun profileRouteStatusBarAppearance(
    colors: ColorScheme,
    isEditProfileSheetVisible: Boolean,
    isBumpSheetVisible: Boolean,
): StatusBarAppearance {
    val color = when {
        isEditProfileSheetVisible -> profileSheetBackdrop(colors = colors, imeVisible = false)
        isBumpSheetVisible -> bumpSheetBackdrop(colors)
        else -> colors.background
    }
    return color.toStatusBarAppearance()
}

internal fun editProfileSheetStatusBarAppearance(
    colors: ColorScheme,
    imeVisible: Boolean,
): StatusBarAppearance {
    return profileSheetBackdrop(colors = colors, imeVisible = imeVisible).toStatusBarAppearance()
}

internal fun bumpSheetStatusBarAppearance(colors: ColorScheme): StatusBarAppearance {
    return bumpSheetBackdrop(colors).toStatusBarAppearance()
}

private fun profileSheetBackdrop(colors: ColorScheme, imeVisible: Boolean): Color {
    return if (imeVisible) {
        colors.surface
    } else {
        colors.scrim.copy(alpha = SHEET_SCRIM_ALPHA).compositeOver(colors.background)
    }
}

private fun bumpSheetBackdrop(colors: ColorScheme): Color {
    return colors.scrim.copy(alpha = SHEET_SCRIM_ALPHA).compositeOver(colors.background)
}

private fun Color.toStatusBarAppearance(): StatusBarAppearance {
    return StatusBarAppearance(
        color = this,
        darkIcons = luminance() > STATUS_BAR_LUMINANCE_THRESHOLD,
    )
}
