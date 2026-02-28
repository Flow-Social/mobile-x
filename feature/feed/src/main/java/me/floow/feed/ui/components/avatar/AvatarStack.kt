package me.floow.feed.ui.components.avatar

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import me.floow.uikit.components.avatar.InitialAvatar
import me.floow.uikit.components.avatar.NetworkAvatar

@Composable
internal fun AvatarStack(
	avatars: List<AvatarUiModel>,
	size: Dp,
	overlap: Dp,
	shape: Shape,
	borderWidth: Dp,
	borderColor: androidx.compose.ui.graphics.Color,
	modifier: Modifier = Modifier
) {
	if (avatars.isEmpty()) return

	val stackWidth = size + overlap * (avatars.size - 1)
	Box(modifier = modifier.width(stackWidth)) {
		avatars.forEachIndexed { index, avatar ->
			val avatarModifier = Modifier
				.offset(x = overlap * index)
				.border(
					width = borderWidth,
					color = borderColor,
					shape = shape
				)
				.zIndex((avatars.size - index).toFloat())

			when (avatar) {
				is AvatarUiModel.Remote -> {
					NetworkAvatar(
						name = avatar.fallbackName,
						avatarModel = avatar.url,
						size = size,
						shape = shape,
						modifier = avatarModifier
					)
				}
				is AvatarUiModel.Initial -> {
					InitialAvatar(
						name = avatar.name,
						size = size,
						shape = shape,
						modifier = avatarModifier
					)
				}
			}
		}
	}
}
