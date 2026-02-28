package me.floow.uikit.chat.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.uikit.R
import me.floow.uikit.components.buttons.WideOutlinedIconButton
import me.floow.uikit.theme.ElevanagonShape
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.util.ComponentPreviewBox

@Composable
fun ChatScreenTopBar(
	profileName: String,
	isOnline: Boolean,
	typingUsers: List<String>,
	profileAvatar: @Composable (Modifier) -> Unit,
	onBackClick: () -> Unit,
	onProfileClick: () -> Unit,
	onDropdownClick: () -> Unit,
	dividerColor: Color? = null,
	modifier: Modifier = Modifier
) {
	Column(modifier) {
		Row(
			Modifier
				.fillMaxWidth()
				.height(80.dp)
				.padding(horizontal = 24.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(
				onClick = onBackClick,
				modifier = Modifier.size(24.dp)
			) {
				Icon(
					painter = painterResource(R.drawable.nav_back_icon),
					contentDescription = null,
					modifier = Modifier.size(16.dp)
				)
			}

			Spacer(Modifier.width(10.dp))

			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.clickable { onProfileClick() }
					.weight(1f),
			) {
				profileAvatar(Modifier.size(50.dp))

				Spacer(Modifier.width(8.dp))

				Column() {
					Text(
						text = profileName,
						style = LocalTypography.current.titleLarge
					)

					if (typingUsers.isNotEmpty()) {
						Row(
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								text = "Typing...",
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								style = LocalTypography.current.bodyMedium
							)
							Spacer(Modifier.width(6.dp))
							TypingDots()
						}
					} else {
						Text(
							text = stringResource(if (isOnline) R.string.online else R.string.offline),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							style = LocalTypography.current.bodyMedium
						)
					}
				}
			}

			WideOutlinedIconButton(
				onClick = onDropdownClick,
				modifier = Modifier
			) {
				Icon(
					painterResource(R.drawable.dropdown_icon),
					null
				)
			}
		}

		HorizontalDivider(color = dividerColor ?: MaterialTheme.colorScheme.outlineVariant)
	}
}

@Composable
fun ChatScreenTitleTopBar(
	title: String,
	onBackClick: () -> Unit,
	onDropdownClick: () -> Unit,
	showDropdown: Boolean,
	dividerColor: Color? = null,
	modifier: Modifier = Modifier
) {
	Column(modifier) {
		Row(
			Modifier
				.fillMaxWidth()
				.height(80.dp)
				.padding(horizontal = 24.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(
				onClick = onBackClick,
				modifier = Modifier.size(24.dp)
			) {
				Icon(
					painter = painterResource(R.drawable.nav_back_icon),
					contentDescription = null,
					modifier = Modifier.size(16.dp)
				)
			}

			Spacer(Modifier.width(10.dp))

			Text(
				text = title,
				style = LocalTypography.current.titleLarge,
				modifier = Modifier.weight(1f)
			)

			if (showDropdown) {
				WideOutlinedIconButton(
					onClick = onDropdownClick,
					modifier = Modifier
				) {
					Icon(
						painterResource(R.drawable.dropdown_icon),
						null
					)
				}
			}
		}

		HorizontalDivider(color = dividerColor ?: MaterialTheme.colorScheme.outlineVariant)
	}
}

@Composable
private fun TypingDots() {
	val infiniteTransition = rememberInfiniteTransition(label = "TypingDots")
	Row(
		verticalAlignment = Alignment.CenterVertically
	) {
		repeat(3) { index ->
			val alpha by infiniteTransition.animateFloat(
				initialValue = 0.3f,
				targetValue = 1f,
				animationSpec = infiniteRepeatable(
					animation = tween(600, delayMillis = index * 200, easing = LinearEasing),
					repeatMode = RepeatMode.Reverse
				),
				label = "DotAlpha"
			)

			Spacer(
				Modifier
					.size(6.dp)
					.graphicsLayer { this.alpha = alpha }
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.onSurfaceVariant)
			)

			if (index != 2) {
				Spacer(Modifier.width(4.dp))
			}
		}
	}
}

@Preview
@Composable
private fun ChatScreenTopBarPreview() {
	ComponentPreviewBox(Modifier.fillMaxSize()) {
		ChatScreenTopBar(
			profileName = "Alina",
			isOnline = false,
			typingUsers = emptyList(),
			profileAvatar = { modifier ->
				Image(
					painterResource(R.drawable.cute_girl),
					null,
					modifier
						.clip(ElevanagonShape),
				)
			},
			onBackClick = {},
			onDropdownClick = {},
			onProfileClick = {},
			modifier = Modifier
				.fillMaxSize()
		)
	}
}
