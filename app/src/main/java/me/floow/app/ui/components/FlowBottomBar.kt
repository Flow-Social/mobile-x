package me.floow.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import me.floow.app.R as AppR
import me.floow.app.navigation.BottomNavigationItem
import me.floow.app.navigation.ChatsScreen
import me.floow.app.navigation.FeedScreen
import me.floow.app.navigation.NavigationRoute
import me.floow.uikit.components.shell.MainShellDefaults

@Composable
fun FlowBottomBar(
	currentDestination: NavDestination?,
	navigationItems: List<BottomNavigationItem>,
	feedUndoEnabled: Boolean = false,
	chatsUnreadCount: Int = 0,
	onFeedUndoClick: (() -> Unit)? = null,
	onClick: (route: NavigationRoute) -> Unit,
	modifier: Modifier = Modifier
) {
	val selectedColor = MainShellDefaults.bottomBarSelectedColor
	val unselectedColor = MainShellDefaults.bottomBarUnselectedColor

	NavigationBar(
		modifier = modifier,
		containerColor = MainShellDefaults.appBackgroundColor,
		tonalElevation = 0.dp
	) {
		navigationItems.forEach { item ->
			val selected =
				currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } ?: false
			val isFeedItem = item.route == FeedScreen
			val isChatsItem = item.route == ChatsScreen
			val canUseUndo = feedUndoEnabled && onFeedUndoClick != null
			val useUndoUi = isFeedItem && selected && canUseUndo

			NavigationBarItem(
				selected = selected,
				onClick = {
					if (useUndoUi) {
						onFeedUndoClick?.invoke()
					} else if (!selected) {
						onClick(item.route)
					}
				},
				colors = NavigationBarItemDefaults.colors(
					selectedIconColor = selectedColor,
					unselectedIconColor = unselectedColor,
					selectedTextColor = selectedColor,
					unselectedTextColor = unselectedColor,
					indicatorColor = Color.Transparent
				),
				icon = {
					if (isFeedItem && selected) {
						AnimatedFeedIcon(
							selected = selected,
							showUndo = canUseUndo,
							defaultIconRes = item.drawableIconId,
							activeIconRes = me.floow.uikit.R.drawable.feed_icon_active,
							undoIconRes = me.floow.uikit.R.drawable.undo_icon
						)
					} else if (isFeedItem) {
						AnimatedFeedIcon(
							selected = selected,
							showUndo = false,
							defaultIconRes = item.drawableIconId,
							activeIconRes = me.floow.uikit.R.drawable.feed_icon_active,
							undoIconRes = me.floow.uikit.R.drawable.undo_icon
						)
					} else if (isChatsItem && chatsUnreadCount > 0) {
						BadgedBox(
							badge = {
								Badge {
									Text(
										text = if (chatsUnreadCount > 99) "99+" else chatsUnreadCount.toString()
									)
								}
							}
						) {
							Icon(
								painter = painterResource(item.drawableIconId),
								contentDescription = null,
								modifier = Modifier.size(24.dp)
							)
						}
					} else {
						Icon(
							painter = painterResource(item.drawableIconId),
							contentDescription = null,
							modifier = Modifier.size(24.dp)
						)
					}
				},
				label = {
					if (isFeedItem && selected) {
						AnimatedContent(
							targetState = canUseUndo,
							transitionSpec = {
								(fadeIn(animationSpec = tween(170, easing = FastOutSlowInEasing)) togetherWith
									fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)))
									.using(SizeTransform(clip = false))
							},
							label = "feedUndoLabel"
						) { isUndo ->
							Text(
								text = stringResource(
									if (isUndo) AppR.string.feed_bottom_nav_back_label else item.titleId
								)
							)
						}
					} else {
						Text(text = stringResource(item.titleId))
					}
				}
			)
		}
	}
}

@Composable
private fun AnimatedFeedIcon(
	selected: Boolean,
	showUndo: Boolean,
	defaultIconRes: Int,
	activeIconRes: Int,
	undoIconRes: Int
) {
	val targetState = when {
		showUndo -> FeedIconState.Undo
		selected -> FeedIconState.Active
		else -> FeedIconState.Default
	}
	val transition = updateTransition(targetState = targetState, label = "feedIconTransition")

	val baseAlpha = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 170, easing = FastOutSlowInEasing) },
		label = "baseAlpha"
	) { state -> if (state == FeedIconState.Default) 1f else 0f }

	val activeAlpha = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 190, easing = FastOutSlowInEasing) },
		label = "activeAlpha"
	) { state -> if (state == FeedIconState.Active) 1f else 0f }

	val activeScale = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 220, easing = FastOutSlowInEasing) },
		label = "activeScale"
	) { state -> if (state == FeedIconState.Active) 1f else 0.88f }

	val activeRotation = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 240, easing = FastOutSlowInEasing) },
		label = "activeRotation"
	) { state ->
		when (state) {
			FeedIconState.Default -> -20f
			FeedIconState.Active -> 0f
			FeedIconState.Undo -> 18f
		}
	}

	val undoAlpha = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 220, easing = FastOutSlowInEasing) },
		label = "undoAlpha"
	) { state -> if (state == FeedIconState.Undo) 1f else 0f }

	val undoScale = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 220, easing = FastOutSlowInEasing) },
		label = "undoScale"
	) { state -> if (state == FeedIconState.Undo) 1f else 0.84f }

	val undoRotation = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 240, easing = FastOutSlowInEasing) },
		label = "undoRotation"
	) { state -> if (state == FeedIconState.Undo) 0f else -95f }

	Box(modifier = Modifier.size(24.dp)) {
		Icon(
			painter = painterResource(defaultIconRes),
			contentDescription = null,
			modifier = Modifier
				.size(24.dp)
				.graphicsLayer(alpha = baseAlpha.value)
		)
		Icon(
			painter = painterResource(activeIconRes),
			contentDescription = null,
			modifier = Modifier
				.size(24.dp)
				.graphicsLayer(
					alpha = activeAlpha.value,
					rotationZ = activeRotation.value,
					scaleX = activeScale.value,
					scaleY = activeScale.value
				)
		)
		Icon(
			painter = painterResource(undoIconRes),
			contentDescription = null,
			modifier = Modifier
				.size(24.dp)
				.graphicsLayer(
					alpha = undoAlpha.value,
					rotationZ = undoRotation.value,
					scaleX = undoScale.value,
					scaleY = undoScale.value
				)
		)
	}
}

private enum class FeedIconState {
	Default,
	Active,
	Undo
}
