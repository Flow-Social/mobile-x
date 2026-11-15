package me.floow.shared.profile.ui.bump

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.floow.shared.profile.ui.qr.ProfileQrCode
import me.floow.uikit.theme.LocalTypography

@Composable
fun ProfileBumpSheetContent(
    profileQrData: String?,
    onShareLinkClick: () -> Unit,
    onImpactDetected: (Float) -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    var isQrMode by remember { mutableStateOf(false) }

    BumpDetectorEffect(
        enabled = !isQrMode,
        onImpactDetected = onImpactDetected,
    )

    val bumpTransition = rememberInfiniteTransition(label = "bump_collide_transition")
    val cycleMs = 4000
    val restOffset = 0.24f
    val leftOffsetFraction = bumpTransition.animateFloat(
        initialValue = -restOffset,
        targetValue = -restOffset,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = cycleMs
                -restOffset at 0
                0f at 500 using FastOutSlowInEasing
                0.05f at 620
                0f at 740
                0f at 2740
                -restOffset at 3300 using FastOutSlowInEasing
                -restOffset at cycleMs
            },
        ),
        label = "bump_left_offset",
    )
    val rightOffsetFraction = bumpTransition.animateFloat(
        initialValue = restOffset,
        targetValue = restOffset,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = cycleMs
                restOffset at 0
                0f at 500 using FastOutSlowInEasing
                -0.05f at 620
                0f at 740
                0f at 2740
                restOffset at 3300 using FastOutSlowInEasing
                restOffset at cycleMs
            },
        ),
        label = "bump_right_offset",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isQrMode) {
                Text(
                    text = "QR-код профиля",
                    style = LocalTypography.current.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Отсканируйте камерой для перехода",
                    style = LocalTypography.current.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(
                    text = "Flow Bump",
                    style = LocalTypography.current.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Соударите телефоны",
                    style = LocalTypography.current.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = "Поднесите устройства близко друг к другу",
                    style = LocalTypography.current.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        AnimatedContent(
            targetState = isQrMode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(durationMillis = 260)) +
                    scaleIn(
                        initialScale = 0.6f,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    )) togetherWith
                    (fadeOut(animationSpec = tween(durationMillis = 180)) +
                        scaleOut(
                            targetScale = 0.85f,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        ))
            },
            modifier = Modifier
                .padding(vertical = 20.dp)
                .fillMaxWidth()
                .height(240.dp),
            label = "bump_qr_swap",
        ) { showQr ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (showQr) {
                    ProfileQrCode(
                        url = profileQrData,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .sizeIn(maxWidth = 220.dp, maxHeight = 220.dp)
                            .padding(8.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Left device pill
                        Box(
                            modifier = Modifier
                                .size(width = 68.dp, height = 130.dp)
                                .graphicsLayer {
                                    translationX = leftOffsetFraction.value * 280.dp.toPx()
                                }
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                            )
                        }

                        // Right device pill
                        Box(
                            modifier = Modifier
                                .size(width = 68.dp, height = 130.dp)
                                .graphicsLayer {
                                    translationX = rightOffsetFraction.value * 280.dp.toPx()
                                }
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF7A73F8)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { isQrMode = !isQrMode },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = if (isQrMode) "Скрыть QR-код" else "Показать QR-код",
                style = LocalTypography.current.labelMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Button(
            onClick = onShareLinkClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Text(
                text = "Поделиться ссылкой",
                style = LocalTypography.current.labelMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}
