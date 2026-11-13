package me.floow.shared.profile.ui.addpost

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.floow.shared.profile.uilogic.ProfilePost
import me.floow.shared.profile.uilogic.addpost.CreatePostStateHolder
import me.floow.shared.profile.uilogic.addpost.EditPostStateHolder
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.compose.PlatformPickedImage
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.topbar.ModalSheetTopBar
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

@Composable
fun CreatePostSheetRoute(
    onBackClick: () -> Unit,
    onPublished: (ProfilePost?) -> Unit = {},
    onDraftChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    galleryResetKey: Any? = Unit,
    onGalleryScrollAtTopChanged: (Boolean) -> Unit = {},
    onInputBottomOffsetChanged: (Dp) -> Unit = {},
    onEmptyPreviewClick: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val koin = getKoin()
    val stateHolder: CreatePostStateHolder = remember {
        koin.get { parametersOf(InlineCreatePostImagePicker) }
    }
    val uiState by stateHolder.state.collectAsState()
    val galleryState = rememberCreatePostGalleryState()

    LaunchedEffect(stateHolder) {
        stateHolder.events.collect { event ->
            when (event) {
                is CreatePostStateHolder.Event.Created -> {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onPublished(event.post)
                }
                is CreatePostStateHolder.Event.ShowMessage -> Unit
            }
        }
    }
    LaunchedEffect(uiState.hasDraft) {
        onDraftChanged(uiState.hasDraft)
    }

    CreatePostSheetScreen(
        uiState = uiState,
        galleryState = galleryState,
        onCloseClick = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            onBackClick()
        },
        onGalleryImageClick = { image ->
            stateHolder.addPickedImages(
                listOf(
                    PlatformPickedImage(
                        id = image.uri,
                        name = image.displayName,
                        mimeType = image.mimeType,
                        sizeBytes = image.sizeBytes,
                        previewUri = image.uri,
                    )
                )
            )
        },
        onRemoveImageClick = stateHolder::removeImage,
        onDescriptionChange = stateHolder::updateDescription,
        onCategoryChange = stateHolder::updateCategory,
        onRetryCategoriesClick = stateHolder::reloadCategories,
        onPublishClick = stateHolder::publish,
        onPreviewImageClick = null,
        onRandomClick = {
            stateHolder.reorderImages(uiState.selectedImages.shuffled().map(CreatePostImageItem::id))
        },
        modifier = modifier,
        dragHandleModifier = dragHandleModifier,
        galleryResetKey = galleryResetKey,
        onGalleryScrollAtTopChanged = onGalleryScrollAtTopChanged,
        onInputBottomOffsetChanged = onInputBottomOffsetChanged,
        onEmptyPreviewClick = onEmptyPreviewClick,
    )
}

@Composable
fun EditPostSheetRoute(
    stateHolder: EditPostStateHolder,
    onBackClick: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    galleryResetKey: Any? = Unit,
    onGalleryScrollAtTopChanged: (Boolean) -> Unit = {},
    onInputBottomOffsetChanged: (Dp) -> Unit = {},
    onEmptyPreviewClick: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val uiState by stateHolder.state.collectAsState()
    val galleryState = rememberCreatePostGalleryState()

    LaunchedEffect(stateHolder) {
        stateHolder.events.collect { event ->
            when (event) {
                is EditPostStateHolder.Event.Saved,
                is EditPostStateHolder.Event.OptimisticApplied -> {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onSaved()
                }
                is EditPostStateHolder.Event.SaveFailed -> Unit
                is EditPostStateHolder.Event.ShowMessage -> Unit
            }
        }
    }

    CreatePostSheetScreen(
        uiState = uiState,
        galleryState = galleryState,
        onCloseClick = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            onBackClick()
        },
        onGalleryImageClick = { image ->
            stateHolder.addPickedImage(
                PlatformPickedImage(
                    id = image.uri,
                    name = image.displayName,
                    mimeType = image.mimeType,
                    sizeBytes = image.sizeBytes,
                    previewUri = image.uri,
                )
            )
        },
        onRemoveImageClick = stateHolder::removeImage,
        onDescriptionChange = stateHolder::updateDescription,
        onCategoryChange = stateHolder::updateCategory,
        onRetryCategoriesClick = stateHolder::reloadCategories,
        onPublishClick = stateHolder::save,
        onPreviewImageClick = null,
        onRandomClick = {
            stateHolder.reorderImages(uiState.selectedImages.shuffled().map(CreatePostImageItem::id))
        },
        modifier = modifier,
        dragHandleModifier = dragHandleModifier,
        galleryResetKey = galleryResetKey,
        onGalleryScrollAtTopChanged = onGalleryScrollAtTopChanged,
        onInputBottomOffsetChanged = onInputBottomOffsetChanged,
        onEmptyPreviewClick = onEmptyPreviewClick,
    )
}

@Composable
internal fun CreatePostSheetScreen(
    uiState: CreatePostUiState,
    galleryState: CreatePostGalleryState,
    onCloseClick: () -> Unit,
    onGalleryImageClick: (CreatePostGalleryImage) -> Unit,
    onRemoveImageClick: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onRetryCategoriesClick: () -> Unit,
    onPublishClick: () -> Unit,
    onPreviewImageClick: ((String) -> Unit)?,
    onRandomClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    title: String = "Cоздать Пост",
    showCategorySelector: Boolean = true,
    allowGalleryImageClickWhenFull: Boolean = false,
    pinnedGalleryImages: List<CreatePostGalleryImage> = emptyList(),
    galleryResetKey: Any? = Unit,
    onGalleryScrollAtTopChanged: (Boolean) -> Unit = {},
    onInputBottomOffsetChanged: (Dp) -> Unit = {},
    onEmptyPreviewClick: () -> Unit = {},
) {
    var shuffleTick by remember { mutableStateOf(0) }
    val colors = createPostSheetColors()
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.galleryBackground)
            .imePadding(),
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(colors.panelBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                CreatePostSheetTopBar(
                    uiState = uiState,
                    colors = colors,
                    title = title,
                    showCategorySelector = showCategorySelector,
                    onCloseClick = onCloseClick,
                    onCategoryChange = onCategoryChange,
                    onRetryCategoriesClick = onRetryCategoriesClick,
                    modifier = dragHandleModifier,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.panelBackground)
                        .then(dragHandleModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    CreatePostPreviewStack(
                        images = uiState.selectedImages,
                        shuffleTick = shuffleTick,
                        colors = colors,
                        onImageClick = { clickedId ->
                            if (onPreviewImageClick != null) {
                                onPreviewImageClick(clickedId)
                            } else {
                                val current = uiState.selectedImages.map(CreatePostImageItem::id)
                                val rest = current.filterNot { it == clickedId }
                                onRandomClick() // triggers rotation & reorders
                            }
                        },
                        onRemoveImageClick = onRemoveImageClick,
                        onEmptyClick = onEmptyPreviewClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .onGloballyPositioned { coordinates ->
                            val bottomPx = coordinates.positionInParent().y + coordinates.size.height
                            onInputBottomOffsetChanged(with(density) { bottomPx.toDp() })
                        }
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CreatePostSheetInput(
                        value = uiState.description,
                        colors = colors,
                        onValueChange = onDescriptionChange,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    )
                    CreatePostPublishButton(
                        enabled = uiState.canPublish && !uiState.isPublishing,
                        isPublishing = uiState.isPublishing,
                        colors = colors,
                        onClick = onPublishClick,
                    )
                }
            }
            if (uiState.selectedImages.size > 1) {
                CreatePostRandomButton(
                    shuffleTick = shuffleTick,
                    onClick = {
                        shuffleTick += 1
                        onRandomClick()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 58.dp, end = 16.dp),
                )
            }
        }

        CreatePostGalleryGrid(
            uiState = uiState,
            galleryState = galleryState,
            colors = colors,
            onGalleryImageClick = onGalleryImageClick,
            onRemoveImageClick = onRemoveImageClick,
            allowImageClickWhenFull = allowGalleryImageClickWhenFull,
            pinnedImages = pinnedGalleryImages,
            resetKey = galleryResetKey,
            onScrollAtTopChanged = onGalleryScrollAtTopChanged,
            dragHandleModifier = dragHandleModifier,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CreatePostSheetTopBar(
    uiState: CreatePostUiState,
    colors: CreatePostSheetColors,
    title: String,
    showCategorySelector: Boolean,
    onCloseClick: () -> Unit,
    onCategoryChange: (String) -> Unit,
    onRetryCategoriesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ModalSheetTopBar(
        onCloseClick = onCloseClick,
        titleString = title,
        modifier = modifier,
        action = {
            if (!showCategorySelector) return@ModalSheetTopBar
            CategoryDropdown(
                categories = uiState.categories,
                selectedCategoryCode = uiState.selectedCategoryCode,
                selectedCategoryTitle = uiState.selectedCategoryTitle ?: "Категория",
                placeholder = "Категория",
                loadingText = "Загрузка",
                retryText = "Повторить",
                isLoading = uiState.isCategoriesLoading,
                isError = uiState.isCategoriesError,
                expanded = expanded,
                enabled = true,
                onExpandedChange = { expanded = it },
                onSelect = { code ->
                    expanded = false
                    onCategoryChange(code)
                },
                onRetry = onRetryCategoriesClick,
                colors = CategoryDropdownDefaults.colors(
                    chipContainer = colors.categoryChipBackground,
                    chipBorder = colors.categoryChipBorder,
                    chipContent = colors.primaryText,
                ),
                chipHeight = 32.dp,
                chipShape = RoundedCornerShape(100.dp),
                chipHorizontalPadding = 12.dp,
                chipIconSize = 18.dp,
                menuOffsetY = 40,
            )
        },
    )
}

@Composable
private fun CreatePostPreviewStack(
    images: List<CreatePostImageItem>,
    shuffleTick: Int,
    colors: CreatePostSheetColors,
    onImageClick: ((String) -> Unit)?,
    onRemoveImageClick: (String) -> Unit,
    onEmptyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shufflePulse = remember { Animatable(0f) }
    LaunchedEffect(shuffleTick) {
        if (shuffleTick <= 0) return@LaunchedEffect
        shufflePulse.snapTo(0f)
        shufflePulse.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 560
                0f at 0
                1f at 150
                0.58f at 300
                0f at 560
            },
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (images.isEmpty()) {
            CreatePostEmptyPlaceholder(
                colors = colors,
                onClick = onEmptyClick,
                modifier = Modifier
                    .size(width = 138.dp, height = 174.dp)
                    .graphicsLayer {
                        rotationZ = -5.2f
                    }
                    .clip(RoundedCornerShape(18.dp)),
            )
            return@Box
        }

        val orderedItems = images.take(4)
        val drawOrder = listOf(3, 1, 2, 0)
        val slots = remember(orderedItems.size) {
            previewSlotsForCount(orderedItems.size)
        }
        val currentOnImageClick by androidx.compose.runtime.rememberUpdatedState(onImageClick)

        drawOrder.forEach { index ->
            val image = orderedItems.getOrNull(index) ?: return@forEach
            val slot = slots[index]
            key(image.id) {
                val pulse = shufflePulse.value
                val animatedWidth by animateDpAsState(
                    targetValue = slot.width,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    label = "create_post_preview_width_${image.id}",
                )
                val animatedHeight by animateDpAsState(
                    targetValue = slot.height,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    label = "create_post_preview_height_${image.id}",
                )
                val offsetX by animateDpAsState(
                    targetValue = slot.x,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    label = "create_post_preview_offset_x_${image.id}",
                )
                val offsetY by animateDpAsState(
                    targetValue = slot.y,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    label = "create_post_preview_offset_y_${image.id}",
                )
                val rotation by animateFloatAsState(
                    targetValue = slot.rotation,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    label = "create_post_preview_rotation_${image.id}",
                )
                val alpha by animateFloatAsState(
                    targetValue = slot.alpha,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    label = "create_post_preview_alpha_${image.id}",
                )
                val scale by animateFloatAsState(
                    targetValue = slot.scale,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    label = "create_post_preview_scale_${image.id}",
                )
                CreatePostPreviewImage(
                    image = image,
                    alpha = alpha,
                    modifier = Modifier
                        .size(animatedWidth, animatedHeight)
                        .zIndex(index.toFloat())
                        .then(
                            if (currentOnImageClick != null) {
                                Modifier.pointerInput(image.id) {
                                    detectTapGestures(onTap = { currentOnImageClick?.invoke(image.id) })
                                }
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer {
                            translationX = offsetX.toPx() + slot.shuffleX.toPx() * pulse
                            translationY = offsetY.toPx() + slot.shuffleY.toPx() * pulse
                            rotationZ = rotation + slot.shuffleRotation * pulse
                            scaleX = scale + 0.05f * pulse
                            scaleY = scale + 0.05f * pulse
                            shadowElevation = 10.dp.toPx()
                            shape = RoundedCornerShape(14.dp)
                            clip = false
                        },
                )
            }
        }
    }
}

@Composable
private fun CreatePostEmptyPlaceholder(
    colors: CreatePostSheetColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    Box(
        modifier = modifier
            .dashedBorder(
                color = colors.placeholderBorder,
                cornerRadius = 18.dp,
                strokeWidth = 1.dp,
            )
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        scale.animateTo(
                            targetValue = 0.94f,
                            animationSpec = keyframes {
                                durationMillis = 140
                                0.98f at 30
                                0.94f at 140
                            },
                        )
                        tryAwaitRelease()
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = keyframes {
                                durationMillis = 220
                                1.02f at 120
                                1f at 220
                            },
                        )
                    },
                    onTap = { onClick() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = colors.placeholderIcon,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Добавить фото",
                color = colors.placeholderText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun CreatePostRandomButton(
    shuffleTick: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonScale = remember { Animatable(1f) }
    LaunchedEffect(shuffleTick) {
        if (shuffleTick <= 0) return@LaunchedEffect
        buttonScale.snapTo(0.94f)
        buttonScale.animateTo(1.14f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
        buttonScale.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
    }
    val rotation by animateFloatAsState(
        targetValue = shuffleTick * 360f,
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "create_post_random_rotation",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation
                scaleX = buttonScale.value
                scaleY = buttonScale.value
            }
            .size(35.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFA259FF), Color(0xFF613599)),
                ),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "🎲",
            fontSize = 17.sp,
        )
    }
}

@Composable
private fun CreatePostSheetInput(
    value: String,
    colors: CreatePostSheetColors,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        minLines = 1,
        maxLines = 4,
        textStyle = TextStyle(
            color = colors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.04).sp,
        ),
        cursorBrush = SolidColor(colors.primaryText),
        modifier = modifier.heightIn(min = 52.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(text = "Что нового?", color = colors.placeholderText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun CreatePostPublishButton(
    enabled: Boolean,
    isPublishing: Boolean,
    colors: CreatePostSheetColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.publishEnabled else colors.publishDisabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isPublishing) {
            FlowLoadingIndicator(color = Color.White)
        } else {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CreatePostGalleryGrid(
    uiState: CreatePostUiState,
    galleryState: CreatePostGalleryState,
    colors: CreatePostSheetColors,
    onGalleryImageClick: (CreatePostGalleryImage) -> Unit,
    onRemoveImageClick: (String) -> Unit,
    allowImageClickWhenFull: Boolean,
    pinnedImages: List<CreatePostGalleryImage>,
    resetKey: Any? = Unit,
    onScrollAtTopChanged: (Boolean) -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val galleryImages = remember(galleryState.images, pinnedImages) {
        val pinnedUris = pinnedImages.mapTo(hashSetOf(), CreatePostGalleryImage::uri)
        pinnedImages + galleryState.images.filterNot { it.uri in pinnedUris }
    }
    fun selectedIndexFor(imageUri: String): Int {
        return uiState.selectedImages.indexOfFirst { selected ->
            selected.id == imageUri || selected.uri == imageUri
        }
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(resetKey) {
        gridState.scrollToItem(0)
    }
    val isGridAtTop by remember(gridState) {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 &&
                gridState.firstVisibleItemScrollOffset == 0 &&
                !gridState.isScrollInProgress
        }
    }
    LaunchedEffect(isGridAtTop) {
        onScrollAtTopChanged(isGridAtTop)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.galleryBackground)
            .padding(top = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(colors.galleryHandle),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(colors.galleryBackground),
        ) {
            when {
                !galleryState.hasPermission && galleryImages.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(text = "Разреши доступ к фото", color = colors.galleryText)
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = colors.permissionButtonBackground,
                            modifier = Modifier.clickable { galleryState.requestPermission() },
                        ) {
                            Text(text = "Разрешить", color = colors.permissionButtonText, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                        }
                    }
                }
                galleryState.isLoading && galleryImages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        FlowLoadingIndicator(color = colors.galleryText)
                    }
                }
                galleryImages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Фото не найдены", color = colors.galleryText.copy(alpha = 0.75f))
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(top = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(galleryImages, key = { it.uri }) { image ->
                            val selectionIndex = selectedIndexFor(image.uri)
                            val selectedImageId = uiState.selectedImages.getOrNull(selectionIndex)?.id
                            val isSelected = selectionIndex >= 0
                            CreatePostGalleryTile(
                                image = image,
                                isSelected = isSelected,
                                selectionIndex = selectionIndex + 1,
                                colors = colors,
                                onClick = {
                                    if (isSelected) {
                                        onRemoveImageClick(selectedImageId ?: image.uri)
                                    } else if (uiState.canAddMoreImages || allowImageClickWhenFull) {
                                        onGalleryImageClick(image)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePostGalleryTile(
    image: CreatePostGalleryImage,
    isSelected: Boolean,
    selectionIndex: Int,
    colors: CreatePostSheetColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val tileAnimScale = remember { Animatable(1f) }

    val selectInsetDp by animateDpAsState(
        targetValue = if (isSelected) 5.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "create_post_gallery_select_inset",
    )
    val cornerRadiusDp by animateDpAsState(
        targetValue = if (isSelected) 10.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "create_post_gallery_corner_radius",
    )
    val badgeScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "create_post_gallery_badge_scale",
    )

    val safeSelectInsetDp = selectInsetDp.coerceAtLeast(0.dp)
    val safeCornerRadiusDp = cornerRadiusDp.coerceAtLeast(0.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = tileAnimScale.value
                scaleY = tileAnimScale.value
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                coroutineScope.launch {
                    tileAnimScale.animateTo(0.92f, tween(65, easing = FastOutLinearInEasing))
                    tileAnimScale.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    )
                }
                onClick()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(safeSelectInsetDp)
                .clip(RoundedCornerShape(safeCornerRadiusDp)),
        ) {
            AsyncImage(
                model = image.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (isSelected) 0.18f else 0.08f)))
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(44.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, end = 8.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                    .clip(CircleShape)
                    .background(if (isSelected) colors.selectionBadgeBackground else Color.Transparent)
                    .border(1.6.dp, colors.selectionBadgeBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected && selectionIndex > 0) {
                    Text(
                        text = selectionIndex.toString(),
                        color = colors.selectionBadgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun previewSlotsForCount(count: Int): List<PreviewSlot> {
    val front = PreviewSlot(
        width = 138.dp,
        height = 174.dp,
        x = 12.dp,
        y = 20.dp,
        rotation = -3.8f,
        alpha = 0.25f,
        scale = 1f,
        shuffleX = 10.dp,
        shuffleY = -12.dp,
        shuffleRotation = 8f,
    )
    if (count <= 1) {
        return listOf(
            front.copy(
                width = 148.dp,
                height = 188.dp,
                x = 0.dp,
                y = 0.dp,
                rotation = -5f,
            )
        )
    }

    val left = PreviewSlot(
        width = 128.dp,
        height = 166.dp,
        x = (-48).dp,
        y = 2.dp,
        rotation = -4.5f,
        alpha = 0.34f,
        scale = 0.94f,
        shuffleX = (-16).dp,
        shuffleY = 10.dp,
        shuffleRotation = -10f,
    )
    if (count == 2) {
        return listOf(
            front.copy(x = 32.dp, y = 18.dp),
            left.copy(x = (-32).dp, y = (-2).dp),
        )
    }

    val right = PreviewSlot(
        width = 128.dp,
        height = 166.dp,
        x = 48.dp,
        y = (-18).dp,
        rotation = 4.4f,
        alpha = 0.50f,
        scale = 0.94f,
        shuffleX = 18.dp,
        shuffleY = 6.dp,
        shuffleRotation = 12f,
    )
    if (count == 3) {
        return listOf(
            front.copy(x = 0.dp, y = 28.dp),
            left.copy(x = (-54).dp, y = 0.dp),
            right.copy(x = 54.dp, y = (-14).dp),
        )
    }

    return listOf(
        front,
        left,
        right,
        PreviewSlot(
            width = 128.dp,
            height = 166.dp,
            x = (-8).dp,
            y = (-40).dp,
            rotation = -1.2f,
            alpha = 0.50f,
            scale = 0.9f,
            shuffleX = (-8).dp,
            shuffleY = (-16).dp,
            shuffleRotation = -7f,
        ),
    )
}

@Composable
private fun CreatePostPreviewImage(
    image: CreatePostImageItem,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
    ) {
        me.floow.uikit.components.media.ProgressiveImage(
            thumbUrl = null,
            targetUrl = image.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (alpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = alpha))
            )
        }
    }
}

private data class CreatePostSheetColors(
    val panelBackground: Color,
    val galleryBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val placeholderText: Color,
    val placeholderBorder: Color,
    val placeholderIcon: Color,
    val categoryChipBackground: Color,
    val categoryChipBorder: Color,
    val publishEnabled: Color,
    val publishDisabled: Color,
    val galleryHandle: Color,
    val galleryText: Color,
    val permissionButtonBackground: Color,
    val permissionButtonText: Color,
    val selectionBadgeBackground: Color,
    val selectionBadgeBorder: Color,
    val selectionBadgeText: Color,
)

@Composable
private fun createPostSheetColors(): CreatePostSheetColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    return CreatePostSheetColors(
        panelBackground = scheme.surface,
        galleryBackground = if (isDark) Color(0xFF050506) else Color(0xFF111217),
        primaryText = scheme.onSurface,
        secondaryText = scheme.onSurfaceVariant,
        placeholderText = scheme.onSurfaceVariant.copy(alpha = if (isDark) 0.78f else 0.70f),
        placeholderBorder = if (isDark) Color(0xFFA7AACC) else Color(0xFF646A84),
        placeholderIcon = if (isDark) Color(0xFFC4C7DE) else Color(0xFF5E647D),
        categoryChipBackground = scheme.surfaceVariant.copy(alpha = if (isDark) 0.50f else 0.42f),
        categoryChipBorder = scheme.outlineVariant,
        publishEnabled = Color(0xFF6576FF),
        publishDisabled = scheme.outline.copy(alpha = 0.42f),
        galleryHandle = Color.White.copy(alpha = if (isDark) 0.86f else 0.78f),
        galleryText = Color.White.copy(alpha = if (isDark) 0.92f else 0.88f),
        permissionButtonBackground = Color.White,
        permissionButtonText = Color.Black,
        selectionBadgeBackground = Color.White,
        selectionBadgeBorder = Color.White,
        selectionBadgeText = Color.Black,
    )
}

private data class PreviewSlot(
    val width: Dp,
    val height: Dp,
    val x: Dp,
    val y: Dp,
    val rotation: Float,
    val alpha: Float,
    val scale: Float,
    val shuffleX: Dp,
    val shuffleY: Dp,
    val shuffleRotation: Float,
)

private object InlineCreatePostImagePicker : PlatformImagePicker {
    override suspend fun pickPostImages(maxItems: Int): List<PlatformPickedImage> = emptyList()
    override suspend fun pickSingleImage(): PlatformPickedImage? = null
}

private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.dp,
): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx())),
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
    )
}

