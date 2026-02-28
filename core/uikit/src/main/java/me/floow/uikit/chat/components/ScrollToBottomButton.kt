package me.floow.uikit.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.uikit.R
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.util.ComponentPreviewBox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun ScrollToBottomButton(
	visible: Boolean,
	badgeCount: Int,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	AnimatedVisibility(
		visible = visible,
		enter = scaleIn() + fadeIn(),
		exit = scaleOut() + fadeOut(),
		modifier = modifier
	) {
		// Add padding to container to prevent shadow clipping during animation
		Box(modifier = Modifier.padding(6.dp)) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				AnimatedVisibility(
					visible = badgeCount > 0,
					enter = scaleIn(),
					exit = scaleOut()
				) {
					Surface(
						shape = RoundedCornerShape(16.dp),
						color = MaterialTheme.colorScheme.primary,
						shadowElevation = 4.dp
					) {
						Box(
							modifier = Modifier
								.padding(horizontal = 8.dp, vertical = 4.dp)
						) {
							Text(
								text = "+$badgeCount",
								style = LocalTypography.current.labelMedium,
								color = MaterialTheme.colorScheme.onPrimary
							)
						}
					}
				}

				if (badgeCount > 0) {
					Spacer(modifier = Modifier.height(4.dp))
				}

				Surface(
					shape = CircleShape,
					color = MaterialTheme.colorScheme.surfaceContainerHigh,
					shadowElevation = 4.dp
				) {
					IconButton(
						onClick = onClick,
						modifier = Modifier.size(40.dp)
					) {
						Icon(
							painter = painterResource(R.drawable.dropdown_icon),
							contentDescription = "Scroll to bottom",
							tint = MaterialTheme.colorScheme.primary
						)
					}
				}
			}
		}
	}
}

@Preview
@Composable
private fun ScrollToBottomButtonPreview() {
	ComponentPreviewBox {
		ScrollToBottomButton(
			visible = true,
			badgeCount = 3,
			onClick = {}
		)
	}
}
