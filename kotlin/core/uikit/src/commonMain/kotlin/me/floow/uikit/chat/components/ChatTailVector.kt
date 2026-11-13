package me.floow.uikit.chat.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.chat_message_tail_incoming
import flow.core.uikit.generated.resources.chat_message_tail_outgoing
import org.jetbrains.compose.resources.painterResource

@Composable
fun ChatTailVector(
    color: Color,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(
            if (isOutgoing) Res.drawable.chat_message_tail_outgoing else Res.drawable.chat_message_tail_incoming
        ),
        contentDescription = null,
        tint = color,
        modifier = modifier.size(width = 16.dp, height = 13.dp)
    )
}
