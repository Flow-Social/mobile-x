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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import me.floow.app.R as AppR
import me.floow.app.navigation.BottomNavigationItem
import me.floow.app.navigation.FeedScreen
import me.floow.app.navigation.NavigationRoute

@Composable
fun FlowBottomBar(
	currentDestination: NavDestination?,
	navigationItems: List<BottomNavigationItem>,
	feedUndoEnabled: Boolean = false,
	onFeedUndoClick: (() -> Unit)? = null,
	onClick: (route: NavigationRoute) -> Unit,
	modifier: Modifier = Modifier
) {
	NavigationBar(
		modifier = modifier,
		containerColor = NavigationBarDefaults.containerColor,
		tonalElevation = 0.dp
	) {
		navigationItems.forEach { item ->
			val selected =
				currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } ?: false
			val isFeedItem = item.route == FeedScreen
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
				icon = {
					if (isFeedItem && selected) {
						AnimatedFeedIcon(
							showUndo = canUseUndo,
							defaultIconRes = item.drawableIconId,
							undoIconRes = me.floow.uikit.R.drawable.undo_icon
						)
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
	showUndo: Boolean,
	defaultIconRes: Int,
	undoIconRes: Int
) {
	val transition = updateTransition(targetState = showUndo, label = "feedUndoIconTransition")

	val baseAlpha = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 170, easing = FastOutSlowInEasing) },
		label = "baseAlpha"
	) { isUndo -> if (isUndo) 0f else 1f }

	val undoAlpha = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 220, easing = FastOutSlowInEasing) },
		label = "undoAlpha"
	) { isUndo -> if (isUndo) 1f else 0f }

	val undoScale = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 220, easing = FastOutSlowInEasing) },
		label = "undoScale"
	) { isUndo -> if (isUndo) 1f else 0.84f }

	val undoRotation = transition.animateFloat(
		transitionSpec = { tween(durationMillis = 240, easing = FastOutSlowInEasing) },
		label = "undoRotation"
	) { isUndo -> if (isUndo) 0f else -95f }

	Box(modifier = Modifier.size(24.dp)) {
		Icon(
			painter = painterResource(defaultIconRes),
			contentDescription = null,
			modifier = Modifier
				.size(24.dp)
				.graphicsLayer(alpha = baseAlpha.value)
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
