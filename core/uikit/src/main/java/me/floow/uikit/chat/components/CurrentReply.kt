package me.floow.uikit.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.R

@Composable
fun CurrentReply(
	userNameToReply: String,
	replyMessageText: String,
	titleText: String? = null,
	onClose: () -> Unit,
	onClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = modifier
			.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
			.padding(start = 16.dp, end = 0.dp, top = 4.dp, bottom = 4.dp

            )
	) {
		Column(
			modifier = Modifier
				.weight(1f)
		) {
			Text(
				text = titleText ?: "${stringResource(R.string.in_reply_to)} $userNameToReply",
				style = LocalTypography.current.bodyMedium,
				overflow = TextOverflow.Ellipsis,
				fontWeight = FontWeight.Bold,
			)

			Text(
				text = replyMessageText,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				style = LocalTypography.current.captionMedium,
				color = Color.Gray
			)
		}

		IconButton(
			onClick = onClose
		) {
			Icon(
				painter = painterResource(R.drawable.current_reply_close_icon),
				contentDescription = null
			)
		}
	}
}

@Preview
@Composable
fun CurrentReplyPreview() {
	CurrentReply(
		userNameToReply = "Bogdan",
		replyMessageText = "Компот не квасят, а культивируют. даун",
		onClose = {},
		onClick = {},
		modifier = Modifier
			.fillMaxWidth()
	)
}
