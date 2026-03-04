package me.floow.uikit.chat.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.R
import me.floow.uikit.theme.ElevanagonShape
import me.floow.uikit.util.ComponentPreviewBox

@Composable
fun ChatScreenTopBar(
	profileName: String,
	isOnline: Boolean,
	typingUsers: List<String>,
	showSubtitle: Boolean = true,
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
				.height(TopAppBarDefaults.TopAppBarExpandedHeight)
				.padding(horizontal = 16.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(
				modifier = Modifier
					.size(24.dp)
					.clickable { onBackClick() }
			) {
				Icon(
					painter = painterResource(R.drawable.nav_back_icon),
					contentDescription = null,
					modifier = Modifier
						.align(Alignment.Center)
						.size(16.dp)
				)
			}

				Spacer(Modifier.width(16.dp))

			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.clickable { onProfileClick() }
					.weight(1f),
			) {
				profileAvatar(Modifier.size(42.dp))

				Spacer(Modifier.width(8.dp))

				Column {
					Text(
						text = profileName,
						style = MaterialTheme.typography.titleMedium.copy(
								fontSize = 18.sp,
							fontWeight = FontWeight.Medium
						)
					)

					if (showSubtitle) {
						if (typingUsers.isNotEmpty()) {
							Row(
								verticalAlignment = Alignment.CenterVertically
							) {
								Text(
									text = stringResource(R.string.chat_typing),
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									style = MaterialTheme.typography.bodyMedium.copy(
										fontSize = 13.sp,
										fontWeight = FontWeight.Medium
									)
								)
								Spacer(Modifier.width(6.dp))
								TypingDots()
							}
						} else {
							Text(
								text = stringResource(if (isOnline) R.string.online else R.string.offline),
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								style = MaterialTheme.typography.bodyMedium.copy(
									fontSize = 13.sp,
									fontWeight = FontWeight.Medium
								)
							)
						}
					}
				}
			}

			Icon(
				painter = painterResource(R.drawable.dropdown_icon),
				contentDescription = null,
				modifier = Modifier
					.size(24.dp)
					.clickable { onDropdownClick() }
			)
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
				.height(TopAppBarDefaults.TopAppBarExpandedHeight)
				.padding(horizontal = 16.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(
				modifier = Modifier
					.size(24.dp)
					.clickable { onBackClick() }
			) {
				Icon(
					painter = painterResource(R.drawable.nav_back_icon),
					contentDescription = null,
					modifier = Modifier
						.align(Alignment.Center)
						.size(16.dp)
				)
			}

			Spacer(Modifier.width(10.dp))

			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium.copy(
					fontSize = 18.sp,
					fontWeight = FontWeight.Medium
				),
				modifier = Modifier.weight(1f)
			)

			if (showDropdown) {
				Icon(
					painter = painterResource(R.drawable.dropdown_icon),
					contentDescription = null,
					modifier = Modifier
						.size(24.dp)
						.clickable { onDropdownClick() }
				)
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
