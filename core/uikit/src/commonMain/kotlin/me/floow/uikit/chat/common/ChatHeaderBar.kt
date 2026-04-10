package me.floow.uikit.chat.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.dropdown_icon
import flow.core.uikit.generated.resources.nav_back_icon
import me.floow.uikit.components.avatar.NetworkAvatar
import org.jetbrains.compose.resources.painterResource

@Composable
fun ChatHeaderBar(
	title: String,
	subtitle: String?,
	avatarUrl: String?,
	isSavedMessages: Boolean,
	onBackClick: () -> Unit,
	onHeaderClick: (() -> Unit)? = null,
	onTrailingClick: (() -> Unit)? = null,
	avatarPainter: Painter? = null,
	dividerColor: Color? = null,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(64.dp)
				.padding(horizontal = 16.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(
				modifier = Modifier
					.size(24.dp)
					.clickable(onClick = onBackClick),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					painter = painterResource(Res.drawable.nav_back_icon),
					contentDescription = null,
					modifier = Modifier.size(16.dp),
				)
			}
			Spacer(Modifier.width(16.dp))
			Row(
				modifier = Modifier
					.weight(1f)
					.then(if (onHeaderClick != null) Modifier.clickable(onClick = onHeaderClick) else Modifier),
				verticalAlignment = Alignment.CenterVertically,
			) {
				if (!isSavedMessages || avatarPainter != null) {
					if (avatarPainter != null) {
						if (isSavedMessages) {
							Box(
								modifier = Modifier
									.size(42.dp)
									.clip(CircleShape)
									.background(MaterialTheme.colorScheme.primary),
								contentAlignment = Alignment.Center,
							) {
								Icon(
									painter = avatarPainter,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onPrimary,
									modifier = Modifier.size(21.dp),
								)
							}
						} else {
							Image(
								painter = avatarPainter,
								contentDescription = null,
								modifier = Modifier
									.size(42.dp)
									.clip(CircleShape),
							)
						}
					} else {
						NetworkAvatar(
							name = title,
							avatarModel = avatarUrl,
							size = 42.dp,
							shape = CircleShape,
						)
					}
					Spacer(Modifier.width(8.dp))
				}
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = title,
						style = MaterialTheme.typography.titleMedium.copy(
							fontWeight = FontWeight.Medium,
							fontSize = 18.sp,
						),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					subtitle?.takeIf(String::isNotBlank)?.let {
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text(
								text = it,
								style = MaterialTheme.typography.bodyMedium.copy(
									fontWeight = FontWeight.Medium,
									fontSize = 13.sp,
								),
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							if ("печатает" in it.lowercase()) {
								Spacer(Modifier.width(6.dp))
								ChatTypingDots()
							}
						}
					}
				}
			}
			if (onTrailingClick != null) {
				Box(
					modifier = Modifier
						.size(24.dp)
						.clickable(onClick = onTrailingClick),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						painter = painterResource(Res.drawable.dropdown_icon),
						contentDescription = null,
					)
				}
			}
		}
		HorizontalDivider(color = dividerColor ?: MaterialTheme.colorScheme.outlineVariant)
	}
}
