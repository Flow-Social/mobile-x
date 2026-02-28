package me.floow.profile.ui.addpost

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object CreatePostOverlayTokens {
	val overlayDimColor = Color(0x5B0F1015)
	val overlayGradientTop = Color(0x48FFFFFF)
	val overlayGradientMiddle = Color(0x4E121213)
	val overlayGradientBottom = Color(0x7A121213)

	const val dialogEnterFadeMs = 190
	const val dialogEnterScaleMs = 220
	const val dialogExitFadeMs = 140
	const val dialogExitScaleMs = 140

	const val initialFocusDelayMs = 240L
	const val refocusDelayMs = 120L

	val topBarHeight = 80.dp
	val topBarHorizontalPadding = 24.dp
	val topBarIconTouchSize = 48.dp
	val topBarIconSize = 24.dp

	val categoryChipHeight = 40.dp
	val categoryChipMinWidth = 72.dp
	val categoryChipHorizontalPadding = 14.dp
	val categoryChipIconSize = 18.dp
	val categoryChipColor = Color(0x48121213)

	val emptyCardSize = 206.dp
	val emptyCardCornerRadius = 20.dp
	const val emptyCardRotation = -5.2f
	val emptyCardBorderWidth = 1.4.dp
	val emptyCardIconSize = 48.dp

	val composerBottomPadding = 10.dp
	val composerItemsSpacing = 10.dp
	val mediaToInputGap = 8.dp
	val inputRowHorizontalPadding = 12.dp
	val inputRowItemsSpacing = 12.dp

	const val mediaCardRotation = -4.5f
	val mediaCardSize = 104.dp
	val mediaCardCornerRadius = 16.dp
	val mediaCardInnerCornerRadius = 14.dp
	val mediaCardImagePadding = 3.dp
	val mediaCardBorderWidth = 1.7.dp
	val mediaCardDashLength = 10.dp
	val mediaCardDashGap = 7.dp
	val mediaCardOverlayColor = Color(0x12000000)
	const val draggingCardScale = 1.1f
	val draggingCardShadow = 18.dp

	val mediaRowHorizontalPadding = 16.dp
	val mediaRowItemSpacing = 16.dp

	val removeButtonSize = 28.dp
	val removeButtonPadding = 4.dp
	val removeIconSize = 16.dp
	val removeButtonColor = Color.Black.copy(alpha = 0.42f)

	val addIconSize = 48.dp

	val inputHeight = 56.dp
	val inputTextColor = Color.White
	val inputPlaceholderColor = Color.White.copy(alpha = 0.44f)
	val inputLineColor = Color.White.copy(alpha = 0.62f)

	val fabSize = 56.dp
	val fabIconSize = 24.dp
	val fabProgressSize = 22.dp
	val fabEnabledColor = Color(0xFF7C71F7)
	val fabDisabledColor = Color(0xFF6D6D73)
}
