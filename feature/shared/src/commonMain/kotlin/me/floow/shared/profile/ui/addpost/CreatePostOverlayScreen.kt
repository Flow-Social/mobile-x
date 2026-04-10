package me.floow.shared.profile.ui.addpost

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.create_post_categories_loading
import flow.feature.shared.generated.resources.create_post_category
import flow.feature.shared.generated.resources.create_post_cd_add_image
import flow.feature.shared.generated.resources.create_post_cd_publish
import flow.feature.shared.generated.resources.create_post_cd_remove_image
import flow.feature.shared.generated.resources.create_post_cd_selected_image
import flow.feature.shared.generated.resources.create_post_close
import flow.feature.shared.generated.resources.create_post_empty_camera_icon
import flow.feature.shared.generated.resources.create_post_retry_category
import flow.feature.shared.generated.resources.create_post_send_up_icon
import flow.feature.shared.generated.resources.create_post_whats_new
import flow.feature.shared.generated.resources.current_reply_close_icon
import flow.feature.shared.generated.resources.dropdown_icon
import flow.feature.shared.generated.resources.plus_icon
import kotlinx.coroutines.delay
import me.floow.uikit.components.loading.FlowLoadingIndicator
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun CreatePostOverlayScreen(
    uiState: CreatePostUiState,
    initialPaintersByImageId: Map<String, Painter> = emptyMap(),
    onCloseClick: () -> Unit,
    onPickImagesClick: () -> Unit,
    onRemoveImageClick: (String) -> Unit,
    onCommitImageOrder: (List<String>) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onRetryCategoriesClick: () -> Unit,
    onPublishClick: () -> Unit,
    onImageCardClick: ((String) -> Unit)? = null,
    showCategorySelector: Boolean = true,
    allowAddImageCard: Boolean = true,
    resumeSignal: Int = 0,
    blockAutoFocus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val mediaListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardCoordinator = rememberCreatePostOverlayKeyboardCoordinator(
        focusRequester = focusRequester,
        keyboardController = keyboardController,
        resumeSignal = resumeSignal,
        blockAutoFocus = blockAutoFocus,
    )
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val statusTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val uiLocked = uiState.isPublishing

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CreatePostOverlayTokens.overlayDimColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                CreatePostOverlayTokens.overlayGradientTop,
                                CreatePostOverlayTokens.overlayGradientMiddle,
                                CreatePostOverlayTokens.overlayGradientBottom,
                            )
                        )
                    )
            )

            AnimatedVisibility(
                visible = keyboardCoordinator.dialogVisible,
                enter = fadeIn(animationSpec = tween(CreatePostOverlayTokens.dialogEnterFadeMs)) +
                    scaleIn(
                        animationSpec = tween(
                            durationMillis = CreatePostOverlayTokens.dialogEnterScaleMs,
                            easing = FastOutSlowInEasing,
                        ),
                        initialScale = 0.94f,
                    ),
                exit = fadeOut(animationSpec = tween(CreatePostOverlayTokens.dialogExitFadeMs)) +
                    scaleOut(
                        animationSpec = tween(CreatePostOverlayTokens.dialogExitScaleMs),
                        targetScale = 0.96f,
                    ),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TopBar(
                        uiState = uiState,
                        onCloseClick = onCloseClick,
                        onCategoryChange = onCategoryChange,
                        onRetryCategoriesClick = onRetryCategoriesClick,
                        onRequestInputFocusRestore = keyboardCoordinator::requestInputFocusRestore,
                        categoryEnabled = !uiLocked,
                        showCategorySelector = showCategorySelector,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    AnimatedVisibility(
                        visible = !uiState.hasImages,
                        enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                            scaleIn(animationSpec = tween(220), initialScale = 0.95f),
                        exit = fadeOut(animationSpec = tween(140)) +
                            scaleOut(animationSpec = tween(160), targetScale = 0.97f),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = statusTopPadding, bottom = imeBottomPadding)
                            .wrapContentSize(Alignment.Center),
                    ) {
                        EmptyMediaUploadCard(
                            enabled = !uiLocked,
                            onClick = {
                                onPickImagesClick()
                                keyboardCoordinator.requestInputFocusRestore()
                            },
                        )
                    }

                    BottomComposer(
                        uiState = uiState,
                        initialPaintersByImageId = initialPaintersByImageId,
                        listState = mediaListState,
                        onPickImagesClick = onPickImagesClick,
                        onRemoveImageClick = onRemoveImageClick,
                        onCommitImageOrder = onCommitImageOrder,
                        onDescriptionChange = onDescriptionChange,
                        onPublishClick = onPublishClick,
                        onImageCardClick = onImageCardClick,
                        allowAddImageCard = allowAddImageCard,
                        focusRequester = focusRequester,
                        onInputLaidOut = keyboardCoordinator::markInputLaidOut,
                        onRequestInputFocusRestore = keyboardCoordinator::requestInputFocusRestore,
                        uiLocked = uiLocked,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    uiState: CreatePostUiState,
    onCloseClick: () -> Unit,
    onCategoryChange: (String) -> Unit,
    onRetryCategoriesClick: () -> Unit,
    onRequestInputFocusRestore: () -> Unit,
    categoryEnabled: Boolean,
    showCategorySelector: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(CreatePostOverlayTokens.topBarHeight)
            .padding(horizontal = CreatePostOverlayTokens.topBarHorizontalPadding),
        horizontalArrangement = if (showCategorySelector) Arrangement.SpaceBetween else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(CreatePostOverlayTokens.topBarIconTouchSize)
                .clickable(onClick = onCloseClick),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                painter = painterResource(Res.drawable.current_reply_close_icon),
                contentDescription = stringResource(Res.string.create_post_close),
                tint = Color.White,
                modifier = Modifier.size(CreatePostOverlayTokens.topBarIconSize),
            )
        }
        if (showCategorySelector) {
            CategorySelector(
                uiState = uiState,
                onCategoryChange = onCategoryChange,
                onRetryCategoriesClick = onRetryCategoriesClick,
                onRequestInputFocusRestore = onRequestInputFocusRestore,
                enabled = categoryEnabled,
            )
        }
    }
}

@Composable
private fun BottomComposer(
    uiState: CreatePostUiState,
    initialPaintersByImageId: Map<String, Painter>,
    listState: LazyListState,
    onPickImagesClick: () -> Unit,
    onRemoveImageClick: (String) -> Unit,
    onCommitImageOrder: (List<String>) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPublishClick: () -> Unit,
    onImageCardClick: ((String) -> Unit)?,
    allowAddImageCard: Boolean,
    focusRequester: FocusRequester,
    onInputLaidOut: () -> Unit,
    onRequestInputFocusRestore: () -> Unit,
    uiLocked: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = CreatePostOverlayTokens.composerBottomPadding),
        verticalArrangement = Arrangement.spacedBy(CreatePostOverlayTokens.composerItemsSpacing),
    ) {
        AnimatedVisibility(
            visible = uiState.hasImages,
            enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight / 3 },
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 120)),
            modifier = Modifier.padding(bottom = CreatePostOverlayTokens.mediaToInputGap),
        ) {
            MediaPickedContent(
                uiState = uiState,
                initialPaintersByImageId = initialPaintersByImageId,
                listState = listState,
                onAddImageClick = onPickImagesClick,
                onRemoveImageClick = onRemoveImageClick,
                onCommitImageOrder = onCommitImageOrder,
                onImageCardClick = onImageCardClick,
                onRequestInputFocusRestore = onRequestInputFocusRestore,
                allowAddImageCard = allowAddImageCard,
                uiLocked = uiLocked,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CreatePostOverlayTokens.inputRowHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(CreatePostOverlayTokens.inputRowItemsSpacing),
            verticalAlignment = Alignment.Bottom,
        ) {
            PostTextInput(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                focusRequester = focusRequester,
                onInputLaidOut = onInputLaidOut,
                enabled = !uiLocked,
                modifier = Modifier.weight(1f),
            )
            PublishFab(
                enabled = uiState.canPublish && !uiLocked,
                isPublishing = uiState.isPublishing,
                onClick = onPublishClick,
            )
        }

        if (uiState.isPublishing && !uiState.publishingStage.isNullOrBlank()) {
            Text(
                text = uiState.publishingStage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.84f),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (!uiState.errorMessage.isNullOrBlank()) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun CategorySelector(
    uiState: CreatePostUiState,
    onCategoryChange: (String) -> Unit,
    onRetryCategoriesClick: () -> Unit,
    onRequestInputFocusRestore: () -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategoryTitle = uiState.selectedCategoryTitle ?: stringResource(Res.string.create_post_category)
    val chipShape = RoundedCornerShape(20.dp)
    val chipModifier = Modifier
        .height(CreatePostOverlayTokens.categoryChipHeight)
        .widthIn(min = CreatePostOverlayTokens.categoryChipMinWidth)

    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }

    LaunchedEffect(expanded, enabled) {
        if (!expanded || !enabled) return@LaunchedEffect
        onRequestInputFocusRestore()
        delay(CreatePostOverlayTokens.refocusDelayMs)
        onRequestInputFocusRestore()
    }

    if (uiState.isCategoriesLoading) {
        Surface(
            color = CreatePostOverlayTokens.categoryChipColor,
            shape = chipShape,
            modifier = chipModifier.focusProperties { canFocus = false },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = CreatePostOverlayTokens.categoryChipHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowLoadingIndicator(color = Color.White)
                Text(
                    text = stringResource(Res.string.create_post_categories_loading),
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
        return
    }

    if (uiState.categories.isEmpty()) {
        Surface(
            color = CreatePostOverlayTokens.categoryChipColor,
            shape = chipShape,
            modifier = chipModifier
                .then(
                    if (enabled) {
                        Modifier.clickable {
                            onRetryCategoriesClick()
                            onRequestInputFocusRestore()
                        }
                    } else {
                        Modifier
                    }
                )
                .focusProperties { canFocus = false },
        ) {
            Text(
                text = if (uiState.isCategoriesError) {
                    stringResource(Res.string.create_post_retry_category)
                } else {
                    stringResource(Res.string.create_post_category)
                },
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(
                    horizontal = CreatePostOverlayTokens.categoryChipHorizontalPadding,
                    vertical = 10.dp,
                ),
            )
        }
        return
    }

    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        Surface(
            color = CreatePostOverlayTokens.categoryChipColor,
            shape = chipShape,
            modifier = chipModifier
                .then(
                    if (enabled) {
                        Modifier.clickable {
                            expanded = true
                            onRequestInputFocusRestore()
                        }
                    } else {
                        Modifier
                    }
                )
                .focusProperties { canFocus = false },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = CreatePostOverlayTokens.categoryChipHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = selectedCategoryTitle,
                    color = Color.White.copy(alpha = 0.94f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    painter = painterResource(Res.drawable.dropdown_icon),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.size(CreatePostOverlayTokens.categoryChipIconSize),
                )
            }
        }

        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = {
                expanded = false
                onRequestInputFocusRestore()
            },
            modifier = Modifier.widthIn(min = 170.dp, max = 240.dp),
        ) {
            uiState.categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = category.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onCategoryChange(category.code)
                        onRequestInputFocusRestore()
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyMediaUploadCard(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(CreatePostOverlayTokens.emptyCardSize)
            .graphicsLayer { rotationZ = CreatePostOverlayTokens.emptyCardRotation }
            .clip(RoundedCornerShape(CreatePostOverlayTokens.emptyCardCornerRadius))
            .dashedRoundedBorderOverlay(
                color = Color(0xFFE3E4EF),
                cornerRadius = CreatePostOverlayTokens.emptyCardCornerRadius,
                strokeWidth = CreatePostOverlayTokens.emptyCardBorderWidth,
            )
            .background(CreatePostOverlayTokens.mediaCardOverlayColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.create_post_empty_camera_icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(CreatePostOverlayTokens.emptyCardIconSize),
        )
    }
}

@Composable
private fun MediaPickedContent(
    uiState: CreatePostUiState,
    initialPaintersByImageId: Map<String, Painter>,
    listState: LazyListState,
    onAddImageClick: () -> Unit,
    onRemoveImageClick: (String) -> Unit,
    onCommitImageOrder: (List<String>) -> Unit,
    onImageCardClick: ((String) -> Unit)?,
    onRequestInputFocusRestore: () -> Unit,
    allowAddImageCard: Boolean,
    uiLocked: Boolean,
) {
    var draftImages by remember { mutableStateOf(uiState.selectedImages) }
    val hapticFeedback = LocalHapticFeedback.current
    val hasUncommittedOrder by remember(draftImages, uiState.selectedImages) {
        derivedStateOf { draftImages.map { it.id } != uiState.selectedImages.map { it.id } }
    }

    fun commitDraftOrderIfChanged() {
        val currentIds = uiState.selectedImages.map { it.id }
        val draftIds = draftImages.map { it.id }
        if (draftIds != currentIds) {
            onCommitImageOrder(draftIds)
        }
    }

    val reorderableMediaState = rememberReorderableLazyListState(listState) { from, to ->
        draftImages = reorderImageItems(
            items = draftImages,
            fromIndex = from.index,
            toIndex = to.index,
            fromKey = from.key,
            toKey = to.key,
        )
    }
    val isAnyItemDragging by remember(reorderableMediaState) {
        derivedStateOf { reorderableMediaState.isAnyItemDragging }
    }
    val mediaActionsEnabled = !uiLocked && !isAnyItemDragging

    LaunchedEffect(uiState.selectedImages, isAnyItemDragging, hasUncommittedOrder) {
        if (isAnyItemDragging) return@LaunchedEffect
        val uiIds = uiState.selectedImages.map { it.id }
        val draftIds = draftImages.map { it.id }
        val containsSameItems = uiIds.size == draftIds.size && uiIds.toSet() == draftIds.toSet()
        if (!containsSameItems || !hasUncommittedOrder) {
            draftImages = uiState.selectedImages
        }
    }

    LazyRow(
        state = listState,
        userScrollEnabled = !uiLocked,
        contentPadding = PaddingValues(horizontal = CreatePostOverlayTokens.mediaRowHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(CreatePostOverlayTokens.mediaRowItemSpacing),
        modifier = Modifier.fillMaxWidth().graphicsLayer { clip = false },
    ) {
        itemsIndexed(
            items = draftImages,
            key = { _, image -> image.id },
        ) { index, image ->
            val seededPainter = remember(image.id, image.isLocalReplacement, initialPaintersByImageId) {
                if (image.isLocalReplacement) null else initialPaintersByImageId[image.id]
            }
            var displayPainter by remember(image.id, image.uri, image.isLocalReplacement, seededPainter) {
                mutableStateOf(seededPainter)
            }
            ReorderableItem(
                state = reorderableMediaState,
                key = image.id,
            ) { isDragging ->
                val dragHandleModifier = if (uiLocked) {
                    Modifier
                } else {
                    with(this) {
                        Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                commitDraftOrderIfChanged()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            },
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(CreatePostOverlayTokens.mediaCardSize)
                        .graphicsLayer {
                            rotationZ = CreatePostOverlayTokens.mediaCardRotation
                            scaleX = if (isDragging) CreatePostOverlayTokens.draggingCardScale else 1f
                            scaleY = if (isDragging) CreatePostOverlayTokens.draggingCardScale else 1f
                            shadowElevation = if (isDragging) CreatePostOverlayTokens.draggingCardShadow.toPx() else 0f
                        }
                        .clip(RoundedCornerShape(CreatePostOverlayTokens.mediaCardCornerRadius))
                        .dashedRoundedBorderOverlay(
                            color = Color(0xFFE3E4EF),
                            cornerRadius = CreatePostOverlayTokens.mediaCardCornerRadius,
                            strokeWidth = CreatePostOverlayTokens.mediaCardBorderWidth,
                            dashLength = CreatePostOverlayTokens.mediaCardDashLength,
                            gapLength = CreatePostOverlayTokens.mediaCardDashGap,
                        ),
                ) {
                    val mediaImageModifier = Modifier
                        .then(
                            if (onImageCardClick != null) {
                                Modifier.clickable(enabled = mediaActionsEnabled) {
                                    commitDraftOrderIfChanged()
                                    onImageCardClick(image.id)
                                    onRequestInputFocusRestore()
                                }
                            } else {
                                Modifier
                            }
                        )
                        .then(dragHandleModifier)
                        .padding(CreatePostOverlayTokens.mediaCardImagePadding)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(CreatePostOverlayTokens.mediaCardInnerCornerRadius))

                    if (displayPainter != null) {
                        Image(
                            painter = displayPainter!!,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = mediaImageModifier,
                        )
                    }
                    AsyncImage(
                        model = image.uri,
                        contentDescription = stringResource(Res.string.create_post_cd_selected_image, index + 1),
                        contentScale = ContentScale.Crop,
                        alpha = 0f,
                        modifier = mediaImageModifier,
                        onSuccess = { state ->
                            displayPainter = state.painter
                        },
                    )
                    Surface(
                        color = CreatePostOverlayTokens.removeButtonColor,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(CreatePostOverlayTokens.removeButtonPadding)
                            .size(CreatePostOverlayTokens.removeButtonSize)
                            .clickable(enabled = mediaActionsEnabled) {
                                commitDraftOrderIfChanged()
                                onRemoveImageClick(image.id)
                                onRequestInputFocusRestore()
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(Res.drawable.current_reply_close_icon),
                                contentDescription = stringResource(Res.string.create_post_cd_remove_image),
                                tint = Color.White,
                                modifier = Modifier.size(CreatePostOverlayTokens.removeIconSize),
                            )
                        }
                    }
                }
            }
        }

        if (allowAddImageCard && uiState.canAddMoreImages) {
            item(key = "add-card") {
                Box(
                    modifier = Modifier
                        .size(CreatePostOverlayTokens.mediaCardSize)
                        .graphicsLayer { rotationZ = CreatePostOverlayTokens.mediaCardRotation }
                        .clip(RoundedCornerShape(CreatePostOverlayTokens.mediaCardCornerRadius))
                        .dashedRoundedBorderOverlay(
                            color = Color(0xFFE3E4EF),
                            cornerRadius = CreatePostOverlayTokens.mediaCardCornerRadius,
                            strokeWidth = CreatePostOverlayTokens.mediaCardBorderWidth,
                            dashLength = CreatePostOverlayTokens.mediaCardDashLength,
                            gapLength = CreatePostOverlayTokens.mediaCardDashGap,
                        )
                        .background(CreatePostOverlayTokens.mediaCardOverlayColor)
                        .clickable(enabled = mediaActionsEnabled) {
                            commitDraftOrderIfChanged()
                            onAddImageClick()
                            onRequestInputFocusRestore()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.plus_icon),
                        contentDescription = stringResource(Res.string.create_post_cd_add_image),
                        tint = Color.White.copy(alpha = 0.96f),
                        modifier = Modifier.size(CreatePostOverlayTokens.addIconSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun PostTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onInputLaidOut: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var isLaidOut by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        minLines = 1,
        maxLines = 6,
        textStyle = TextStyle(
            color = CreatePostOverlayTokens.inputTextColor.copy(alpha = 0.96f),
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            fontWeight = MaterialTheme.typography.bodyLarge.fontWeight,
        ),
        cursorBrush = SolidColor(Color.White),
        modifier = modifier
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                if (!isLaidOut) {
                    isLaidOut = true
                    onInputLaidOut()
                }
            }
            .heightIn(min = CreatePostOverlayTokens.inputHeight)
            .dashedBottomLine(color = CreatePostOverlayTokens.inputLineColor)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text(
                        text = stringResource(Res.string.create_post_whats_new),
                        style = MaterialTheme.typography.bodyLarge,
                        color = CreatePostOverlayTokens.inputPlaceholderColor,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun PublishFab(
    enabled: Boolean,
    isPublishing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (enabled) CreatePostOverlayTokens.fabEnabledColor else CreatePostOverlayTokens.fabDisabledColor,
        animationSpec = tween(durationMillis = 220),
        label = "post_fab_color",
    )

    Box(
        modifier = modifier
            .size(CreatePostOverlayTokens.fabSize)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isPublishing) {
            FlowLoadingIndicator(color = Color.White)
        } else {
            Icon(
                painter = painterResource(Res.drawable.create_post_send_up_icon),
                contentDescription = stringResource(Res.string.create_post_cd_publish),
                tint = Color.White,
                modifier = Modifier.size(CreatePostOverlayTokens.fabIconSize),
            )
        }
    }
}

private fun Modifier.dashedRoundedBorderOverlay(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 7.dp,
): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(dashLength.toPx(), gapLength.toPx()),
            ),
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
    )
}

private fun Modifier.dashedBottomLine(
    color: Color,
    strokeWidth: Dp = 1.dp,
): Modifier = drawWithContent {
    drawContent()
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(0f, size.height),
        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
        strokeWidth = strokeWidth.toPx(),
    )
}

private fun reorderImageItems(
    items: List<CreatePostImageItem>,
    fromIndex: Int,
    toIndex: Int,
    fromKey: Any? = null,
    toKey: Any? = null,
): List<CreatePostImageItem> {
    if (items.isEmpty()) return items
    val fromIndexByKey = (fromKey as? String)?.let { key ->
        items.indexOfFirst { it.id == key }.takeIf { it >= 0 }
    }
    val toIndexByKey = (toKey as? String)?.let { key ->
        items.indexOfFirst { it.id == key }.takeIf { it >= 0 }
    }
    val resolvedFromIndex = fromIndexByKey ?: fromIndex
    val resolvedToIndex = toIndexByKey ?: toIndex.coerceIn(0, items.lastIndex)
    if (resolvedFromIndex !in items.indices || resolvedToIndex !in items.indices || resolvedFromIndex == resolvedToIndex) {
        return items
    }
    val mutable = items.toMutableList()
    val moved = mutable.removeAt(resolvedFromIndex)
    mutable.add(resolvedToIndex, moved)
    return mutable
}
