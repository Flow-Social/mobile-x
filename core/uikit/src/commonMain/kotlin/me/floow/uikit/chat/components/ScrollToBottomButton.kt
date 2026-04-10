package me.floow.uikit.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.floow.uikit.theme.LocalTypography

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
		Box(modifier = Modifier.padding(6.dp)) {
			Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
						Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
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
						ChatScrollToBottomIcon(
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(20.dp)
						)
					}
				}
			}
		}
	}
}
