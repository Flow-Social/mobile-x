package me.floow.uikit.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.status_checked
import flow.core.uikit.generated.resources.status_nochecked
import me.floow.uikit.chat.model.ChatMessageDeliveryStatus
import me.floow.uikit.chat.model.formatChatClockTime
import org.jetbrains.compose.resources.painterResource

val FlowStatusIconColor = Color(0xFF89898D)
val FlowStatusErrorColor = Color(0xFFCF1939)

@Composable
fun MessageDeliveryStatusIcon(
    status: ChatMessageDeliveryStatus,
    isRead: Boolean,
    onRetryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    tint: Color = FlowStatusIconColor,
) {
    Box(
        modifier = modifier.size(14.dp),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            ChatMessageDeliveryStatus.SENDING -> {
                val transition = rememberInfiniteTransition(label = "sending_transition")
                val rotation by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = LinearEasing)
                    ),
                    label = "sending_rotation"
                )
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = "Sending",
                    tint = tint,
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
            ChatMessageDeliveryStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Failed",
                    tint = FlowStatusErrorColor,
                    modifier = Modifier
                        .size(12.dp)
                        .then(if (onRetryClick != null) Modifier.clickable(onClick = onRetryClick) else Modifier)
                )
            }
            ChatMessageDeliveryStatus.SENT -> {
                if (isRead) {
                    Icon(
                        painter = painterResource(Res.drawable.status_checked),
                        contentDescription = "Read",
                        tint = tint,
                        modifier = Modifier.size(width = 13.dp, height = 8.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.status_nochecked),
                        contentDescription = "Sent",
                        tint = tint,
                        modifier = Modifier.size(width = 11.dp, height = 9.dp)
                    )
                }
            }
        }
    }
}

@Deprecated(
    message = "Use MessageTimestampRow with isOverlay = true instead",
    replaceWith = ReplaceWith("MessageTimestampRow(formattedTime = formatChatClockTime(createdAtMillis), isOutgoing = isOutgoing, deliveryStatus = deliveryStatus, isOverlay = true, modifier = modifier)")
)
@Composable
fun ChatMediaClockOverlay(
    createdAtMillis: Long,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT,
    isRead: Boolean = true,
) {
    MessageTimestampRow(
        formattedTime = formatChatClockTime(createdAtMillis),
        isOutgoing = isOutgoing,
        deliveryStatus = deliveryStatus,
        showUnreadDot = !isRead,
        isOverlay = true,
        modifier = modifier,
    )
}
