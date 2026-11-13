package me.floow.shared.profile.ui.addpost

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.done_icon
import flow.shared.generated.resources.dropdown_icon
import me.floow.uikit.components.loading.FlowLoadingIndicator
import org.jetbrains.compose.resources.painterResource

@Composable
fun CategoryDropdown(
    categories: List<CreatePostCategoryItem>,
    selectedCategoryCode: String?,
    selectedCategoryTitle: String,
    placeholder: String,
    loadingText: String,
    retryText: String,
    isLoading: Boolean,
    isError: Boolean,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    colors: CategoryDropdownColors = CategoryDropdownDefaults.colors(),
    chipHeight: Dp = CreatePostOverlayTokens.categoryChipHeight,
    chipMinWidth: Dp = CreatePostOverlayTokens.categoryChipMinWidth,
    chipHorizontalPadding: Dp = CreatePostOverlayTokens.categoryChipHorizontalPadding,
    chipShape: RoundedCornerShape = RoundedCornerShape(20.dp),
    chipIconSize: Dp = CreatePostOverlayTokens.categoryChipIconSize,
    menuOffsetY: Int = 48,
) {
    val title = selectedCategoryTitle.ifBlank { placeholder }

    when {
        isLoading -> CategoryDropdownLoadingChip(
            text = loadingText,
            modifier = modifier,
            colors = colors,
            chipHeight = chipHeight,
            chipMinWidth = chipMinWidth,
            chipHorizontalPadding = chipHorizontalPadding,
            chipShape = chipShape,
        )

        categories.isEmpty() -> CategoryDropdownRetryChip(
            text = if (isError) retryText else placeholder,
            enabled = enabled,
            onClick = onRetry,
            modifier = modifier,
            colors = colors,
            chipHeight = chipHeight,
            chipMinWidth = chipMinWidth,
            chipHorizontalPadding = chipHorizontalPadding,
            chipShape = chipShape,
        )

        else -> CategoryDropdownMenuChip(
            categories = categories,
            selectedCategoryCode = selectedCategoryCode,
            selectedCategoryTitle = title,
            expanded = expanded,
            enabled = enabled,
            onExpandedChange = onExpandedChange,
            onSelect = onSelect,
            modifier = modifier,
            colors = colors,
            chipHeight = chipHeight,
            chipMinWidth = chipMinWidth,
            chipHorizontalPadding = chipHorizontalPadding,
            chipShape = chipShape,
            chipIconSize = chipIconSize,
            menuOffsetY = menuOffsetY,
        )
    }
}

@Immutable
data class CategoryDropdownColors(
    val chipContainer: Color,
    val chipBorder: Color,
    val chipContent: Color,
    val menuContainer: Color,
    val menuContent: Color,
    val menuSelectedContainer: Color,
    val menuSelectedContent: Color,
)

object CategoryDropdownDefaults {
    @Composable
    fun colors(
        chipContainer: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
        chipBorder: Color = MaterialTheme.colorScheme.outlineVariant,
        chipContent: Color = MaterialTheme.colorScheme.onSurface,
        menuContainer: Color = MaterialTheme.colorScheme.surface,
        menuContent: Color = MaterialTheme.colorScheme.onSurface,
        menuSelectedContainer: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        menuSelectedContent: Color = MaterialTheme.colorScheme.primary,
    ): CategoryDropdownColors {
        return CategoryDropdownColors(
            chipContainer = chipContainer,
            chipBorder = chipBorder,
            chipContent = chipContent,
            menuContainer = menuContainer,
            menuContent = menuContent,
            menuSelectedContainer = menuSelectedContainer,
            menuSelectedContent = menuSelectedContent,
        )
    }
}

@Composable
private fun CategoryDropdownLoadingChip(
    text: String,
    modifier: Modifier = Modifier,
    colors: CategoryDropdownColors,
    chipHeight: Dp,
    chipMinWidth: Dp,
    chipHorizontalPadding: Dp,
    chipShape: RoundedCornerShape,
) {
    CategoryChipContainer(
        modifier = modifier,
        colors = colors,
        chipHeight = chipHeight,
        chipMinWidth = chipMinWidth,
        chipShape = chipShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = chipHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowLoadingIndicator(color = colors.chipContent)
            Text(
                text = text,
                color = colors.chipContent,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryDropdownRetryChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: CategoryDropdownColors,
    chipHeight: Dp,
    chipMinWidth: Dp,
    chipHorizontalPadding: Dp,
    chipShape: RoundedCornerShape,
) {
    CategoryChipContainer(
        modifier = modifier.then(
            if (enabled) {
                Modifier
                    .clip(chipShape)
                    .clickable(onClick = onClick)
            } else {
                Modifier
            },
        ),
        colors = colors,
        chipHeight = chipHeight,
        chipMinWidth = chipMinWidth,
        chipShape = chipShape,
    ) {
        Text(
            text = text,
            color = colors.chipContent,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = chipHorizontalPadding,
                vertical = 10.dp,
            ),
        )
    }
}

@Composable
private fun CategoryDropdownMenuChip(
    categories: List<CreatePostCategoryItem>,
    selectedCategoryCode: String?,
    selectedCategoryTitle: String,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: CategoryDropdownColors,
    chipHeight: Dp,
    chipMinWidth: Dp,
    chipHorizontalPadding: Dp,
    chipShape: RoundedCornerShape,
    chipIconSize: Dp,
    menuOffsetY: Int,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "CategoryDropdownArrowRotation",
    )
    var keepPopupComposed by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) keepPopupComposed = true
    }

    Box(modifier = modifier.wrapContentSize(Alignment.TopEnd)) {
        CategoryChipContainer(
            modifier = Modifier.then(
                if (enabled) {
                    Modifier
                        .clip(chipShape)
                        .clickable { onExpandedChange(true) }
                } else {
                    Modifier
                },
            ),
            colors = colors,
            chipHeight = chipHeight,
            chipMinWidth = chipMinWidth,
            chipShape = chipShape,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = chipHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = selectedCategoryTitle,
                    color = colors.chipContent,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    painter = painterResource(Res.drawable.dropdown_icon),
                    contentDescription = null,
                    tint = colors.chipContent,
                    modifier = Modifier
                        .size(chipIconSize)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
        }

        if ((expanded || keepPopupComposed) && enabled) {
            CategoryPopupMenu(
                categories = categories,
                selectedCategoryCode = selectedCategoryCode,
                visible = expanded,
                onDismiss = { onExpandedChange(false) },
                onHidden = { keepPopupComposed = false },
                onSelect = onSelect,
                colors = colors,
                menuOffsetY = menuOffsetY,
            )
        }
    }
}

@Composable
private fun CategoryPopupMenu(
    categories: List<CreatePostCategoryItem>,
    selectedCategoryCode: String?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onHidden: () -> Unit,
    onSelect: (String) -> Unit,
    colors: CategoryDropdownColors,
    menuOffsetY: Int,
) {
    val shadowPadding = 14.dp
    val shadowPaddingPx = with(LocalDensity.current) { shadowPadding.roundToPx() }
    val transparentInteractionSource = remember { MutableInteractionSource() }
    val visibleState = remember {
        MutableTransitionState(false)
    }

    LaunchedEffect(visible) {
        visibleState.targetState = visible
    }

    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (visibleState.isIdle && !visibleState.currentState) {
            onHidden()
        }
    }
    val transition = rememberTransition(
        transitionState = visibleState,
        label = "CategoryPopupVisibility",
    )
    val popupAlpha by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = 120,
                easing = FastOutSlowInEasing,
            )
        },
        label = "CategoryPopupAlpha",
    ) { isVisible ->
        if (isVisible) 1f else 0f
    }
    val popupScaleY by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = 190,
                easing = FastOutSlowInEasing,
            )
        },
        label = "CategoryPopupScaleY",
    ) { isVisible ->
        if (isVisible) 1f else 0.72f
    }
    val popupTranslationY by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = 190,
                easing = FastOutSlowInEasing,
            )
        },
        label = "CategoryPopupTranslationY",
    ) { isVisible ->
        if (isVisible) 0f else -8f
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(x = shadowPaddingPx, y = menuOffsetY - shadowPaddingPx),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = transparentInteractionSource,
                indication = null,
                onClick = onDismiss,
            ),
        ) {
            Surface(
                color = colors.menuContainer,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 18.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .padding(shadowPadding)
                    .graphicsLayer {
                        alpha = popupAlpha
                        scaleY = popupScaleY
                        translationY = popupTranslationY
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .widthIn(min = 184.dp, max = 248.dp)
                    .heightIn(max = 320.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    categories.forEach { category ->
                        CategoryPopupMenuRow(
                            category = category,
                            selected = category.code == selectedCategoryCode,
                            onClick = { onSelect(category.code) },
                            colors = colors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPopupMenuRow(
    category: CreatePostCategoryItem,
    selected: Boolean,
    onClick: () -> Unit,
    colors: CategoryDropdownColors,
) {
    val rowShape = RoundedCornerShape(10.dp)
    val rowBackground = if (selected) {
        colors.menuSelectedContainer
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(rowShape)
            .background(rowBackground, rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = category.title,
            color = if (selected) colors.menuSelectedContent else colors.menuContent,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(Res.drawable.done_icon),
                contentDescription = null,
                tint = colors.menuSelectedContent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CategoryChipContainer(
    modifier: Modifier = Modifier,
    colors: CategoryDropdownColors,
    chipHeight: Dp,
    chipMinWidth: Dp,
    chipShape: RoundedCornerShape,
    content: @Composable () -> Unit,
) {
    Surface(
        color = colors.chipContainer,
        shape = chipShape,
        border = BorderStroke(1.dp, colors.chipBorder),
        modifier = modifier
            .height(chipHeight)
            .widthIn(min = chipMinWidth)
            .focusProperties { canFocus = false },
        content = content,
    )
}
