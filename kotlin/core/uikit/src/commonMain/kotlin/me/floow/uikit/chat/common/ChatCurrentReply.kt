package me.floow.uikit.chat.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.current_reply_close_icon
import flow.core.uikit.generated.resources.edit_icon
import flow.core.uikit.generated.resources.reply_out_icon
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.painterResource

@Composable
fun ChatCurrentReply(
	title: String,
	subtitle: String,
	onClose: () -> Unit,
	onClick: (() -> Unit)? = null,
	isEditMode: Boolean = false,
	modifier: Modifier = Modifier,
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = modifier
			.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
			.padding(start = 14.dp, top = 3.dp),
	) {
		val accent = MaterialTheme.colorScheme.primary
		Icon(
			painter = painterResource(if (isEditMode) Res.drawable.edit_icon else Res.drawable.reply_out_icon),
			contentDescription = null,
			tint = accent,
			modifier = Modifier.size(22.dp).graphicsLayer { if (!isEditMode) scaleX = -1f },
		)
		Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
			Text(
				text = title,
				style = LocalTypography.current.bodyMedium,
				maxLines = 1,
				color = accent,
				overflow = TextOverflow.Ellipsis,
				fontWeight = FontWeight.Medium,
			)
			Text(
				text = subtitle,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				style = LocalTypography.current.captionMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		IconButton(onClick = onClose) {
			Icon(
				painter = painterResource(Res.drawable.current_reply_close_icon),
				contentDescription = null,
			)
		}
	}
}
