package me.floow.shared.chats.media.attachment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.floow.shared.profile.ui.addpost.rememberCreatePostGalleryState
import me.floow.uikit.components.sheet.FlowModalSheetOverlay

@Composable
fun ChatAttachmentPickerSheet(
    onDismissRequest: () -> Unit,
    onImagesSelected: (images: List<String>, caption: String?) -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedUris: List<String> = emptyList(),
    maxSelection: Int = 10,
    entryId: Long = 105L,
) {
    val galleryState = rememberCreatePostGalleryState()

    val selectedUris = remember {
        mutableStateListOf<String>().apply { addAll(initialSelectedUris) }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val galleryBackground = if (isDark) Color(0xFF050506) else Color.White
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    FlowModalSheetOverlay(
        entryId = entryId,
        onClose = onDismissRequest,
        onRevealChanged = {},
        canDragToDismiss = true,
        shouldClose = { true },
        scrimMaxAlpha = 0.32f,
        containerColor = Color.Transparent,
        sheetShape = RectangleShape,
        bottomPadding = 0.dp,
        expandable = true,
        expandedSheetHeightFraction = 1f,
        respectStatusBar = false,
        avoidIme = false,
        observeIme = false,
        minSheetHeight = 440.dp,
        sheetHeightFraction = 0.58f,
        modifier = modifier.fillMaxSize(),
    ) {
        val isExpanded = expansionProgress > 0.45f
        val topCornerRadius = (28f * (1f - expansionProgress)).coerceIn(0f, 28f).dp
        val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topInset = statusBarTopDp * expansionProgress
        val gridState = rememberLazyGridState()
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
                .background(galleryBackground),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset)
                    .height(44.dp)
                    .then(dragHandleModifier)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isExpanded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(handleColor)
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = textColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = "Галерея",
                            color = textColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (!galleryState.hasPermission) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 24.dp, end = 24.dp, bottom = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Разреши доступ к фото",
                            color = secondaryTextColor,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                galleryState.requestPermission()
                            },
                        ) {
                            Text(
                                text = "Разрешить",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                        }
                    }
                } else if (galleryState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(dragHandleModifier)
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (galleryState.images.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(dragHandleModifier)
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Нет доступных фото",
                            color = secondaryTextColor,
                            fontSize = 15.sp,
                        )
                    }
                } else {
                    val density = LocalDensity.current
                    val navBarBottomDp = with(density) {
                        WindowInsets.navigationBars.getBottom(density).toDp()
                    }
                    val bottomContentPadding = if (selectedUris.isNotEmpty()) navBarBottomDp + 76.dp else navBarBottomDp + 16.dp
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(top = 0.dp, bottom = bottomContentPadding),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                            .background(galleryBackground),
                    ) {
                        items(
                            items = galleryState.images,
                            key = { it.uri },
                            contentType = { "gallery_image_item" }
                        ) { item ->
                            val uri = item.uri
                            val isSelected = selectedUris.contains(uri)
                            val selectionIndex = if (isSelected) selectedUris.indexOf(uri) + 1 else null

                            val tileAnimScale = remember { Animatable(1f) }

                            val selectInsetDp by animateDpAsState(
                                targetValue = if (isSelected) 5.dp else 0.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                label = "selectInset",
                            )
                            val cornerRadiusDp by animateDpAsState(
                                targetValue = if (isSelected) 10.dp else 0.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                label = "cornerRadius",
                            )

                            val safeSelectInsetDp = selectInsetDp.coerceAtLeast(0.dp)
                            val safeCornerRadiusDp = cornerRadiusDp.coerceAtLeast(0.dp)

                            val badgeScale by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0.8f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                label = "badgeScale",
                            )

                            Box(
                                modifier = Modifier
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
                                        scope.launch {
                                            tileAnimScale.animateTo(0.92f, tween(65, easing = FastOutLinearInEasing))
                                            tileAnimScale.animateTo(
                                                1f,
                                                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                                            )
                                        }
                                        if (isSelected) {
                                            selectedUris.remove(uri)
                                        } else if (selectedUris.size < maxSelection) {
                                            selectedUris.add(uri)
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(safeSelectInsetDp)
                                        .clip(RoundedCornerShape(safeCornerRadiusDp))
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
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
                                            .background(if (isSelected) Color.White else Color.Transparent)
                                            .border(1.6.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isSelected && selectionIndex != null) {
                                            Text(
                                                text = selectionIndex.toString(),
                                                color = Color.Black,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 11.sp,
                                                textAlign = TextAlign.Center,
                                                style = TextStyle(
                                                    textAlign = TextAlign.Center,
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedUris.isNotEmpty(),
                    enter = slideInVertically(
                        animationSpec = tween(140, easing = FastOutSlowInEasing),
                    ) { it } + fadeIn(
                        animationSpec = tween(140),
                    ),
                    exit = slideOutVertically(
                        animationSpec = tween(100, easing = FastOutLinearInEasing),
                    ) { it } + fadeOut(
                        animationSpec = tween(100),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = {
                                if (selectedUris.isNotEmpty()) {
                                    onImagesSelected(selectedUris.toList(), null)
                                }
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                text = "Прикрепить (${selectedUris.size})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
