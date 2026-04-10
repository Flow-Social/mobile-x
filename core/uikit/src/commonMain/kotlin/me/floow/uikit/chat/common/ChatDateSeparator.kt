package me.floow.uikit.chat.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatDateSeparator(
	text: String,
	modifier: Modifier = Modifier,
) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelSmall,
		color = MaterialTheme.colorScheme.secondary,
		modifier = modifier
			.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
			.border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
			.padding(horizontal = 10.dp, vertical = 8.dp),
	)
}
