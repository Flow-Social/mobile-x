package me.floow.chatssearch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.floow.chatssearch.uilogic.MessageResult
import me.floow.uikit.theme.LocalTypography

@Composable
fun MessageResult(
	result: MessageResult,
	onClick: (MessageResult) -> Unit,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier
			.clickable { onClick(result) }
			.padding(horizontal = 20.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Box(
			Modifier
				.size(56.dp)
				.clip(CircleShape)
				.background(Color.LightGray)
		)

		Spacer(Modifier.width(9.dp))

		Column(
			verticalArrangement = Arrangement.Center
		) {
			Text(
				text = result.name.value,
				style = LocalTypography.current.titleMedium,
			)

			Spacer(Modifier.height(2.dp))

			Text(
				text = result.messageText,
				style = LocalTypography.current.bodyMedium,
				color = MaterialTheme.colorScheme.secondary,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}
