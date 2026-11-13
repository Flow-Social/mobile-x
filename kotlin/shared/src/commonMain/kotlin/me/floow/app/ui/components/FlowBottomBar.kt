package me.floow.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.chats_bottom_nav_label
import flow.shared.generated.resources.chats_icon
import flow.shared.generated.resources.feed_bottom_nav_back_label
import flow.shared.generated.resources.feed_bottom_nav_label
import flow.shared.generated.resources.feed_icon
import flow.shared.generated.resources.feed_icon_active
import flow.shared.generated.resources.profile_bottom_nav_label
import flow.shared.generated.resources.profile_icon
import flow.shared.generated.resources.undo_icon
import me.floow.app.navigation.MainTab
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

data class BottomNavigationItem(
    val tab: MainTab,
    val icon: DrawableResource,
    val titleRes: StringResource
)

val defaultBottomNavigationItems = listOf(
    BottomNavigationItem(
        tab = MainTab.Feed,
        icon = Res.drawable.feed_icon,
        titleRes = Res.string.feed_bottom_nav_label
    ),
    BottomNavigationItem(
        tab = MainTab.Chats,
        icon = Res.drawable.chats_icon,
        titleRes = Res.string.chats_bottom_nav_label
    ),
    BottomNavigationItem(
        tab = MainTab.Profile,
        icon = Res.drawable.profile_icon,
        titleRes = Res.string.profile_bottom_nav_label
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    navigationItems: List<BottomNavigationItem> = defaultBottomNavigationItems,
    feedUndoEnabled: Boolean = false,
    chatsUnreadCount: Int = 0,
    onFeedUndoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedColor = Color.White
    val unselectedColor = Color.White.copy(alpha = 0.3f)
    val iconSize = 24.dp

    Box(
        modifier = modifier
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                containerColor = Color.Black,
                tonalElevation = 0.dp
            ) {
                navigationItems.forEach { item ->
                    val selected = selectedTab == item.tab
                    val isFeedItem = item.tab == MainTab.Feed
                    val isChatsItem = item.tab == MainTab.Chats
                    val isProfileItem = item.tab == MainTab.Profile
                    val canUseUndo = feedUndoEnabled && onFeedUndoClick != null
                    val useUndoUi = isFeedItem && selected && canUseUndo

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (useUndoUi) {
                                onFeedUndoClick()
                            } else if (!selected) {
                                onTabSelected(item.tab)
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
                                    defaultIconRes = item.icon,
                                    activeIconRes = Res.drawable.feed_icon_active,
                                    undoIconRes = Res.drawable.undo_icon,
                                    iconSize = iconSize
                                )
                            } else if (isFeedItem) {
                                AnimatedFeedIcon(
                                    selected = selected,
                                    showUndo = false,
                                    defaultIconRes = item.icon,
                                    activeIconRes = Res.drawable.feed_icon_active,
                                    undoIconRes = Res.drawable.undo_icon,
                                    iconSize = iconSize
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
                                    AnimatedChatsIcon(
                                        selected = selected,
                                        iconRes = item.icon,
                                        iconSize = iconSize
                                    )
                                }
                            } else if (isChatsItem) {
                                AnimatedChatsIcon(
                                    selected = selected,
                                    iconRes = item.icon,
                                    iconSize = iconSize
                                )
                            } else if (isProfileItem) {
                                AnimatedProfileIcon(
                                    selected = selected,
                                    iconRes = item.icon,
                                    iconSize = iconSize
                                )
                            } else {
                                Icon(
                                    painter = painterResource(item.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(iconSize)
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
                                            if (isUndo) Res.string.feed_bottom_nav_back_label else item.titleRes
                                        )
                                    )
                                }
                            } else {
                                Text(text = stringResource(item.titleRes))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedFeedIcon(
    selected: Boolean,
    showUndo: Boolean,
    defaultIconRes: DrawableResource,
    activeIconRes: DrawableResource,
    undoIconRes: DrawableResource,
    iconSize: Dp
) {
    val popProgress = remember { Animatable(if (selected) 1f else 0f) }
    val targetState = when {
        showUndo -> FeedIconState.Undo
        selected -> FeedIconState.Active
        else -> FeedIconState.Default
    }
    val transition = updateTransition(targetState = targetState, label = "feedIconTransition")

    LaunchedEffect(selected) {
        if (selected) {
            popProgress.snapTo(0f)
            popProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = LinearOutSlowInEasing)
            )
        } else {
            popProgress.snapTo(0f)
        }
    }

    val iconRes = when (targetState) {
        FeedIconState.Default -> defaultIconRes
        FeedIconState.Active -> activeIconRes
        FeedIconState.Undo -> undoIconRes
    }

    val iconScale = transition.animateFloat(
        transitionSpec = { tween(durationMillis = 220, easing = FastOutSlowInEasing) },
        label = "iconScale"
    ) { state ->
        when (state) {
            FeedIconState.Default -> 0.96f
            FeedIconState.Active -> 1f
            FeedIconState.Undo -> 0.92f
        }
    }

    val iconRotation = transition.animateFloat(
        transitionSpec = { tween(durationMillis = 240, easing = FastOutSlowInEasing) },
        label = "iconRotation"
    ) { state ->
        when (state) {
            FeedIconState.Default -> -10f
            FeedIconState.Active -> 0f
            FeedIconState.Undo -> 16f
        }
    }

    val popPulse = sin(popProgress.value * PI).toFloat()
    val popScale = if (selected) 1f + popPulse * 0.08f else 1f
    val popLift = if (selected) -popPulse * 1.8f else 0f

    Box(modifier = Modifier.size(iconSize)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer(
                    scaleX = iconScale.value * popScale,
                    scaleY = iconScale.value * popScale,
                    rotationZ = iconRotation.value,
                    translationY = popLift
                )
        )
    }
}

private enum class FeedIconState {
    Default,
    Active,
    Undo
}

@Composable
private fun AnimatedChatsIcon(
    selected: Boolean,
    iconRes: DrawableResource,
    iconSize: Dp
) {
    val progress = remember { Animatable(if (selected) 1f else 0f) }
    val contentColor = LocalContentColor.current

    LaunchedEffect(selected) {
        if (selected) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520, easing = LinearOutSlowInEasing)
            )
        } else {
            progress.snapTo(0f)
        }
    }

    val pulse = sin(progress.value * PI).toFloat()
    val wobble = sin(progress.value * PI * 2).toFloat() * (1f - progress.value)
    val bubbleScale = if (selected) 1f + pulse * 0.12f else 1f
    val bubbleRotation = if (selected) wobble * 10f else 0f
    val bubbleLift = if (selected) -pulse * 2.2f else 0f

    Box(modifier = Modifier.size(iconSize)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer(
                    scaleX = bubbleScale,
                    scaleY = bubbleScale,
                    rotationZ = bubbleRotation,
                    translationY = bubbleLift
                )
        )

        TypingDot(
            progress = progress.value,
            delay = 0f,
            color = contentColor,
            modifier = Modifier.offset(x = 6.dp, y = 11.dp)
        )
        TypingDot(
            progress = progress.value,
            delay = 0.12f,
            color = contentColor,
            modifier = Modifier.offset(x = 10.5.dp, y = 11.dp)
        )
        TypingDot(
            progress = progress.value,
            delay = 0.24f,
            color = contentColor,
            modifier = Modifier.offset(x = 15.dp, y = 11.dp)
        )
    }
}

@Composable
private fun TypingDot(
    progress: Float,
    delay: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val shiftedProgress = max(0f, (progress - delay) / (1f - delay))
    val pulse = sin(shiftedProgress * PI).toFloat()
    val scale = 1f + pulse * 0.35f
    val alpha = 0.55f + pulse * 0.45f
    val lift = -pulse * 1.8f

    Box(
        modifier = modifier
            .alpha(alpha)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationY = lift
            )
            .size(2.4.dp)
            .background(color = color, shape = CircleShape)
    )
}

@Composable
private fun AnimatedProfileIcon(
    selected: Boolean,
    iconRes: DrawableResource,
    iconSize: Dp
) {
    val progress = remember { Animatable(if (selected) 1f else 0f) }
    val contentColor = LocalContentColor.current

    LaunchedEffect(selected) {
        if (selected) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 460, easing = LinearOutSlowInEasing)
            )
        } else {
            progress.snapTo(0f)
        }
    }

    val pulse = sin(progress.value * PI).toFloat()
    val settle = sin(progress.value * PI * 1.5f).toFloat() * (1f - progress.value)
    val iconScale = if (selected) 1f + pulse * 0.1f else 1f
    val iconRotation = if (selected) settle * 7f else 0f
    val iconLift = if (selected) -pulse * 1.6f else 0f
    val haloScale = 0.82f + pulse * 0.3f
    val haloAlpha = pulse * 0.22f

    Box(modifier = Modifier.size(iconSize)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .scale(haloScale)
                .alpha(haloAlpha)
                .background(
                    color = contentColor,
                    shape = CircleShape
                )
        )

        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer(
                    scaleX = iconScale,
                    scaleY = iconScale,
                    rotationZ = iconRotation,
                    translationY = iconLift
                )
        )
    }
}
