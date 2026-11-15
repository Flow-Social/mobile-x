package me.floow.shared.feed.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.theme.NinehedronShape

@Composable
internal fun SharedPostFooter(
    commentsCount: Int,
    commentersPreview: List<String>,
    onCommentsClick: () -> Unit,
    onHeightChanged: ((Int) -> Unit)? = null,
    colors: SharedFeedTopCardColors,
) {
    val previewAvatars = remember(commentersPreview) {
        commentersPreview.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .take(3)
            .toList()
    }
    val avatarSize = 28.dp
    val overlapStep = 12.dp
    val avatarsGroupWidth = if (previewAvatars.isEmpty()) 0.dp else avatarSize + overlapStep * (previewAvatars.size - 1)
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates -> onHeightChanged?.invoke(coordinates.size.height) },
    ) {
        HorizontalDivider(color = colors.border, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable { onCommentsClick() }
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (previewAvatars.isNotEmpty()) {
                    Box(Modifier.width(avatarsGroupWidth)) {
                        previewAvatars.forEachIndexed { index, raw ->
                            val isRemote = raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)
                            val avatarModifier = Modifier
                                .offset(x = overlapStep * index)
                                .border(1.2.dp, MaterialTheme.colorScheme.surfaceContainer, NinehedronShape)
                                .size(avatarSize)
                            NetworkAvatar(
                                name = if (isRemote) "?" else raw,
                                avatarModel = if (isRemote) raw else null,
                                modifier = avatarModifier,
                                size = avatarSize,
                                shape = NinehedronShape,
                            )
                        }
                    }
                }
                Text(
                    "$commentsCount комментариев",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    color = colors.content,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Открыть комментарии", tint = colors.content)
        }
    }
}
