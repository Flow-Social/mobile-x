package me.floow.feed.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import me.floow.uikit.components.swipe.LocalSwipeItemIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.feed.ui.components.avatar.AvatarStack
import me.floow.feed.ui.components.avatar.toAvatarUiModels
import me.floow.uikit.components.buttons.BlurGlassButton
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.ProgressiveImageMode
import me.floow.uikit.components.text.LinkifiedText
import me.floow.uikit.theme.NinehedronShape
import me.floow.uikit.theme.categoryColor
import me.floow.uikit.theme.toDisplayTag
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostCategories
import me.floow.domain.models.PostContent
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun PostCard(
	post: Post,
	recommendationReason: String?,
	onProfileClick: (String) -> Unit = {},
	onProfileTagClick: (String) -> Unit = {},
	onPostLinkClick: (String, String) -> Unit = { _, _ -> },
	onViewImagesClick: (Post, OverlayLaunchData) -> Unit = { _, _ -> },
	onOverlaySourceSnapshot: (OverlayLaunchData) -> Unit = {},
	isOverlayActive: Boolean = false,
	overlayDetachedCount: Int = 0,
	onMoreClick: (Post) -> Unit = {},
	onCommentsClick: (Post) -> Unit = {},
	modifier: Modifier = Modifier
) {
	Card(
		modifier = modifier,
		shape = RoundedCornerShape(
			topStart = 14.dp,
			topEnd = 14.dp,
			bottomEnd = 14.dp,
			bottomStart = 0.dp
		),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceContainer
		)
	) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp, 12.dp, 12.dp, 6.dp)

        ) {
			// Header с автором и категорией
			PostHeader(
				post = post,
				recommendationReason = recommendationReason,
				onProfileClick = onProfileClick,
				onMoreClick = onMoreClick
			)

			Spacer(modifier = Modifier.height(16.dp))

			// Image Gallery с веерным эффектом - занимает оставшееся пространство
			ImageGallery(
				imageVariants = post.content.resolvedImageVariants(),
				onViewClick = { launchData -> onViewImagesClick(post, launchData) },
				onOverlaySourceSnapshot = onOverlaySourceSnapshot,
				isOverlayActive = isOverlayActive,
                overlayDetachedCount = overlayDetachedCount,
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f) // Растягивается, но оставляет место для нижних элементов
			)

			Spacer(modifier = Modifier.height(16.dp))

			// Text Content с заголовком и описанием
			TextContent(
				description = post.content.description,
				timestamp = post.createdAt,
				onProfileTagClick = onProfileTagClick,
				onPostLinkClick = onPostLinkClick,
				linkColor = post.category.categoryColor()
			)

			Spacer(modifier = Modifier.height(16.dp))

			// Footer с аватарами и комментариями
			PostFooter(
				commentsCount = post.commentsCount,
				commentersPreview = post.commentersPreview,
				onCommentsClick = { onCommentsClick(post) }
			)
		}
	}
}

@Composable
private fun PostHeader(
	post: Post,
	recommendationReason: String?,
	onProfileClick: (String) -> Unit,
    onMoreClick: (Post) -> Unit,
	modifier: Modifier = Modifier
) {
    val reasonLabel = recommendationReason?.takeIf { it.isNotBlank() }

	Row(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		// Левая часть: @ + username + категория
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier
                .weight(1f)
                .clickable {
				onProfileClick(post.author.id)
			}
		) {
			// @ символ + username
			Text(
				text = "@${post.author.username?.value ?: "unknown"}",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.onSurface
			)
			
			// Категория в фиолетовом бейдже
			Surface(
				color = post.category.categoryColor(),
				shape = RoundedCornerShape(8.dp)
			) {
				Text(
					text = post.category.toDisplayTag(),
					color = Color.White,
					style = MaterialTheme.typography.labelMedium,
					modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
				)
			}

            if (reasonLabel != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.widthIn(max = 170.dp)
                ) {
                    Text(
                        text = reasonLabel,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
		}
		
		// Правая часть: троеточие
		IconButton(
			onClick = { onMoreClick(post) },
			modifier = Modifier.size(24.dp)
		) {
			Icon(
				imageVector = Icons.Default.MoreVert,
				contentDescription = "Меню",
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

@Composable
private fun ImageGallery(
	imageVariants: List<me.floow.domain.models.PostImageVariant>,
	onViewClick: (OverlayLaunchData) -> Unit,
	onOverlaySourceSnapshot: (OverlayLaunchData) -> Unit,
	isOverlayActive: Boolean,
    overlayDetachedCount: Int,
	modifier: Modifier = Modifier
) {
    val index = LocalSwipeItemIndex.current
    val isTopCard = index == 0
    val attachFadeMs = 36
    
    val expansion = remember { Animatable(0f) }
    val cleanedVariants = imageVariants.take(4)
    if (cleanedVariants.isEmpty()) return

    val front = cleanedVariants.first()
    var frontPainter by remember(front) { mutableStateOf<androidx.compose.ui.graphics.painter.Painter>(ColorPainter(Color(0xFF1A1A1A))) }
    val left = cleanedVariants.getOrNull(1)
    val right = cleanedVariants.getOrNull(2)
    val back = cleanedVariants.getOrNull(3)
    val extraCount = (cleanedVariants.size - 3).coerceAtLeast(0)
    val frontShadeAlpha by animateFloatAsState(
        targetValue = if (isOverlayActive && overlayDetachedCount >= 1) 0f else 0.3f,
        animationSpec = tween(durationMillis = attachFadeMs),
        label = "frontShadeAlpha"
    )
    val leftShadeAlpha by animateFloatAsState(
        targetValue = if (isOverlayActive && overlayDetachedCount >= 2) 0f else 0.5f,
        animationSpec = tween(durationMillis = attachFadeMs),
        label = "leftShadeAlpha"
    )
    val rightShadeAlpha by animateFloatAsState(
        targetValue = if (isOverlayActive && overlayDetachedCount >= 3) 0f else 0.5f,
        animationSpec = tween(durationMillis = attachFadeMs),
        label = "rightShadeAlpha"
    )
    val backShadeAlpha by animateFloatAsState(
        targetValue = if (isOverlayActive && overlayDetachedCount >= 4) 0f else 0.55f,
        animationSpec = tween(durationMillis = attachFadeMs),
        label = "backShadeAlpha"
    )
    val frontCardAlpha by animateFloatAsState(
        targetValue = if (isOverlayActive && overlayDetachedCount >= 1) 0f else 1f,
        animationSpec = tween(durationMillis = attachFadeMs),
        label = "frontCardAlpha"
    )
    val leftCardAlpha by animateFloatAsState(
        targetValue = if (isOverlayActive && overlayDetachedCount >= 2) 0f else 1f,
        animationSpec = tween(durationMillis = attachFadeMs),
        label = "leftCardAlpha"
    )
    val rightCardAlpha by animateFloatAsState(
        targetValue = if (isOverlayActive && overlayDetachedCount >= 3) 0f else 1f,
        animationSpec = tween(durationMillis = attachFadeMs),
        label = "rightCardAlpha"
    )
    val backCardAlpha by animateFloatAsState(
        targetValue = if (isOverlayActive && overlayDetachedCount >= 4) 0f else 1f,
        animationSpec = tween(durationMillis = attachFadeMs),
        label = "backCardAlpha"
    )
    val watchButtonAlpha by animateFloatAsState(
        targetValue = if (isOverlayActive) 0f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "watchButtonAlpha"
    )
    val showFrontBadge = extraCount > 0 && !(isOverlayActive && overlayDetachedCount >= 1)
    val density = LocalDensity.current
    val frontWidthPx = with(density) { 180.dp.toPx() }
    val frontHeightPx = with(density) { 230.dp.toPx() }
    val sideWidthPx = with(density) { 170.dp.toPx() }
    val sideHeightPx = with(density) { 200.dp.toPx() }
    val backWidthPx = with(density) { (176.dp * 0.96f).toPx() }
    val backHeightPx = with(density) { (210.dp * 0.96f).toPx() }
    val sideShiftPx = with(density) { 50.dp.toPx() }
    val backShiftYPx = with(density) { 16.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    
    LaunchedEffect(isTopCard) {
        if (isTopCard) {
            delay(150)
            expansion.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            expansion.snapTo(0f)
        }
    }

	val lastRect = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
	val buttonRect = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
	val frontImageRect = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val leftImageRect = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val rightImageRect = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val backImageRect = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    val frontContent = remember(front) {
        movableContentOf {
            ProgressiveImage(
                lqUrl = front.lqUrl,
                previewUrl = front.previewUrl,
                fullUrl = front.fullUrl,
                mode = ProgressiveImageMode.LIST,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onPainterChanged = { painter ->
                    if (painter != null) {
                        frontPainter = painter
                    }
                }
            )
        }
    }
    val leftContent = remember(left) {
        left?.let { variant ->
            movableContentOf {
                ProgressiveImage(
                    lqUrl = variant.lqUrl,
                    previewUrl = variant.previewUrl,
                    fullUrl = variant.fullUrl,
                    mode = ProgressiveImageMode.LIST,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
    val rightContent = remember(right) {
        right?.let { variant ->
            movableContentOf {
                ProgressiveImage(
                    lqUrl = variant.lqUrl,
                    previewUrl = variant.previewUrl,
                    fullUrl = variant.fullUrl,
                    mode = ProgressiveImageMode.LIST,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
    val backContent = remember(back) {
        back?.let { variant ->
            movableContentOf {
                ProgressiveImage(
                    lqUrl = variant.lqUrl,
                    previewUrl = variant.previewUrl,
                    fullUrl = variant.fullUrl,
                    mode = ProgressiveImageMode.LIST,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }

    val buildLaunchData = remember(
        frontImageRect.value,
        leftImageRect.value,
        rightImageRect.value,
        backImageRect.value,
        buttonRect.value,
        lastRect.value,
        expansion.value,
        frontWidthPx,
        frontHeightPx,
        sideWidthPx,
        sideHeightPx,
        backWidthPx,
        backHeightPx,
        sideShiftPx,
        backShiftYPx,
        frontContent,
        leftContent,
        rightContent,
        backContent
    ) {
        {
            val cardRects = listOf(
                frontImageRect.value,
                leftImageRect.value,
                rightImageRect.value,
                backImageRect.value
            )
            val anchorCenter = frontImageRect.value?.center ?: lastRect.value?.center
            fun poseOrFallback(
                rect: androidx.compose.ui.geometry.Rect?,
                width: Float,
                height: Float,
                rotation: Float,
                shiftX: Float = 0f,
                shiftY: Float = 0f
            ): CardPose? {
                return if (rect != null) {
                    CardPose(
                        centerX = rect.center.x,
                        centerY = rect.center.y,
                        width = width,
                        height = height,
                        rotation = rotation
                    )
                } else {
                    anchorCenter?.let { center ->
                        CardPose(
                            centerX = center.x + shiftX,
                            centerY = center.y + shiftY,
                            width = width,
                            height = height,
                            rotation = rotation
                        )
                    }
                }
            }
            OverlayLaunchData(
                buttonRect = buttonRect.value ?: lastRect.value,
                cardRects = cardRects,
                cardPoses = listOf(
                    poseOrFallback(
                        rect = frontImageRect.value,
                        width = frontWidthPx,
                        height = frontHeightPx,
                        rotation = 0f
                    ),
                    poseOrFallback(
                        rect = leftImageRect.value,
                        width = sideWidthPx,
                        height = sideHeightPx,
                        rotation = -10f * expansion.value,
                        shiftX = -sideShiftPx * expansion.value
                    ),
                    poseOrFallback(
                        rect = rightImageRect.value,
                        width = sideWidthPx,
                        height = sideHeightPx,
                        rotation = 10f * expansion.value,
                        shiftX = sideShiftPx * expansion.value
                    ),
                    poseOrFallback(
                        rect = backImageRect.value,
                        width = backWidthPx,
                        height = backHeightPx,
                        rotation = 0f,
                        shiftY = backShiftYPx * expansion.value
                    )
                ),
                cardContents = listOf(
                    frontContent,
                    leftContent,
                    rightContent,
                    backContent
                )
            )
        }
    }

    LaunchedEffect(
        isOverlayActive,
        frontImageRect.value,
        leftImageRect.value,
        rightImageRect.value,
        backImageRect.value,
        expansion.value
    ) {
        if (isOverlayActive) {
            onOverlaySourceSnapshot(buildLaunchData())
        }
    }

    val openOverlay: () -> Unit = {
        if (!isOverlayActive) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onViewClick(buildLaunchData())
        }
    }

	Box(
		modifier = modifier
			.onGloballyPositioned { coords ->
				lastRect.value = coords.boundsInWindow()
			},
		contentAlignment = Alignment.Center
	) {
        if (back != null) {
            Box(
                modifier = Modifier
                    .size(width = 176.dp, height = 210.dp)
                    .graphicsLayer {
                        val progress = expansion.value
                        translationY = 16.dp.toPx() * progress
                        scaleX = 0.96f
                        scaleY = 0.96f
                        alpha = 0.95f * backCardAlpha
                    }
                    .onGloballyPositioned { coords ->
                        backImageRect.value = coords.boundsInWindow()
                    }
                    .clip(RoundedCornerShape(14.dp))
            ) {
                backContent?.invoke()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = backShadeAlpha))
                )
            }
        }

		if (left != null) {
			Box(
				modifier = Modifier
					.size(width = 170.dp, height = 200.dp)
					.graphicsLayer {
						val progress = expansion.value
						rotationZ = -10f * progress
						translationX = -50.dp.toPx() * progress
                        alpha = leftCardAlpha
					}
                    .onGloballyPositioned { coords ->
                        leftImageRect.value = coords.boundsInWindow()
                    }
					.clip(RoundedCornerShape(14.dp))
			) {
                leftContent?.invoke()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = leftShadeAlpha))
                )
			}
		}
		
		if (right != null) {
			Box(
				modifier = Modifier
					.size(width = 170.dp, height = 200.dp)
					.graphicsLayer {
						val progress = expansion.value
						rotationZ = 10f * progress
						translationX = 50.dp.toPx() * progress
                        alpha = rightCardAlpha
					}
                    .onGloballyPositioned { coords ->
                        rightImageRect.value = coords.boundsInWindow()
                    }
					.clip(RoundedCornerShape(14.dp))
			) {
                rightContent?.invoke()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = rightShadeAlpha))
                )
			}
		}
		
			Box(
				contentAlignment = Alignment.Center
			) {
		            Box(
		                modifier = Modifier
		                    .size(width = 180.dp, height = 230.dp)
	                        .graphicsLayer { alpha = frontCardAlpha }
                            .clickable(
                                enabled = !isOverlayActive,
                                onClick = openOverlay
                            )
		                    .onGloballyPositioned { coords ->
		                        frontImageRect.value = coords.boundsInWindow()
		                    }
		                    .clip(RoundedCornerShape(14.dp))
		            ) {
                    frontContent()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = frontShadeAlpha))
                    )
                    if (showFrontBadge) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "+$extraCount",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
            }
			
				BlurGlassButton(
					painter = frontPainter,
					backgroundBoundsInWindow = frontImageRect.value,
					backgroundContentScale = ContentScale.Crop,
                    refractionScale = 1.14f, // слабее "линза"
                    blurRadius = 2.5.dp,       // меньше blur
					modifier = Modifier.onGloballyPositioned { coords ->
						buttonRect.value = coords.boundsInWindow()
					}.graphicsLayer {
	                        alpha = watchButtonAlpha
	                    },
				onClick = if (isOverlayActive) null else openOverlay
			) {
                Text(
                    text = "смотреть",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
		}
	}
}

@Composable
private fun TextContent(
	description: String?,
	timestamp: Long,
	onProfileTagClick: (String) -> Unit,
	onPostLinkClick: (String, String) -> Unit,
	linkColor: Color,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		if (!description.isNullOrBlank()) {
			val (title, body) = extractTitleAndBody(description)
			
			// Заголовок (первые 3 слова)
			if (title.isNotBlank()) {
				LinkifiedText(
					text = title,
					style = MaterialTheme.typography.titleLarge.copy(
						fontWeight = FontWeight.Bold,
						fontSize = 20.sp,
						color = MaterialTheme.colorScheme.onSurface
					),
					linkColor = linkColor,
					onProfileTagClick = onProfileTagClick,
					onPostLinkClick = onPostLinkClick
				)
			}
			
			// Основной текст
			if (body.isNotBlank()) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.Bottom
				) {
					LinkifiedText(
						text = body,
						style = MaterialTheme.typography.bodyMedium.copy(
							fontSize = 14.sp,
							color = MaterialTheme.colorScheme.onSurface
						),
						linkColor = linkColor,
						modifier = Modifier.weight(1f),
						onProfileTagClick = onProfileTagClick,
						onPostLinkClick = onPostLinkClick
					)
					
					// Время справа внизу
					Text(
						text = formatTime(timestamp),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(start = 8.dp)
					)
				}
			}
		}
	}
}

@Composable
private fun PostFooter(
	commentsCount: Int,
	commentersPreview: List<String>,
	onCommentsClick: () -> Unit = {},
	modifier: Modifier = Modifier
) {
	val previewItems = commentersPreview.toAvatarUiModels(limit = 3)
	val avatarSize = 28.dp
	val overlapStep = 12.dp

	Column(modifier = modifier.fillMaxWidth()) {
		HorizontalDivider(
			color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
			thickness = 1.dp
		)
		
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(52.dp)
				.clickable { onCommentsClick() },
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			// Левая часть: аватары + счетчик комментариев
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					if (previewItems.isNotEmpty()) {
						AvatarStack(
							avatars = previewItems,
							size = avatarSize,
							overlap = overlapStep,
							shape = NinehedronShape,
							borderWidth = 1.2.dp,
							borderColor = MaterialTheme.colorScheme.surfaceContainer
						)
					}
				
				// Счетчик комментариев
				Text(
					text = "$commentsCount комментариев",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			
			// Правая часть: стрелка
			Icon(
				imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
				contentDescription = "Открыть комментарии",
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

private fun extractTitleAndBody(description: String): Pair<String, String> {
	val words = description.trim().split("\\s+".toRegex())
	val title = words.take(3).joinToString(" ")
	val body = words.drop(3).joinToString(" ")
	return title to body
}

private fun formatTime(timestamp: Long): String {
	val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
	return formatter.format(Date(timestamp))
}

@OptIn(RawValueObjectCreate::class)
@Preview
@Composable
private fun PostCardPreview() {
	PostCard(
		post = Post(
			id = "preview_1",
			author = PostAuthor(
				id = "preview_user_1",
				name = ProfileName.createRaw("Анна Котова"),
				username = ProfileUsername.createRaw("vatarase"),
				avatarUrl = "https://picsum.photos/100/100?random=1"
			),
			content = PostContent(
				imageUrls = listOf(
					"https://picsum.photos/400/600?nature=1",
					"https://picsum.photos/400/600?nature=2",
					"https://picsum.photos/400/600?nature=3",
					"https://picsum.photos/400/600?nature=4"
				),
				description = "Новый учебный год начался с переезда в Санкт-Петербург. Я рада, что мы смогли пойти на такой серьёзный шаг!!!!"
			),
			category = PostCategories.LIFESTYLE,
			createdAt = System.currentTimeMillis() - 3600000
		),
		recommendationReason = null
	)
}

@OptIn(RawValueObjectCreate::class)
@Preview
@Composable
private fun PostCardPreview_ShortText() {
	PostCard(
		post = Post(
			id = "preview_2",
			author = PostAuthor(
				id = "preview_user_2",
				name = ProfileName.createRaw("Петр Путешественник"),
				username = ProfileUsername.createRaw("petr_travel"),
				avatarUrl = "https://picsum.photos/100/100?random=2"
			),
			content = PostContent(
				imageUrls = listOf(
					"https://picsum.photos/400/600?travel=1",
					"https://picsum.photos/400/600?travel=2"
				),
				description = "Париж весной"
			),
			category = PostCategories.TRAVEL,
			createdAt = System.currentTimeMillis() - 7200000
		),
		recommendationReason = null
	)
}
