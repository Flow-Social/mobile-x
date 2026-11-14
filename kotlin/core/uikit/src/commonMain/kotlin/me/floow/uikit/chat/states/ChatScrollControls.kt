package me.floow.uikit.chat.states

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.components.ScrollToBottomButton

/**
 * Floating fast-scroll to bottom controls with unread messages count badge.
 */
@Composable
internal fun BoxScope.ChatScrollControls(
	visible: Boolean,
	badgeCount: Int,
	isLoading: Boolean,
	bottomPadding: Dp,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	ScrollToBottomButton(
		visible = visible,
		badgeCount = badgeCount.coerceAtLeast(0),
		isLoading = isLoading,
		onClick = onClick,
		modifier = modifier
			.align(Alignment.BottomEnd)
			.padding(end = 16.dp, bottom = bottomPadding)
	)
}
