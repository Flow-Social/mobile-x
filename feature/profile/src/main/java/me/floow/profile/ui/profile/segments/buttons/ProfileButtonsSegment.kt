package me.floow.profile.ui.profile.segments.buttons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.floow.uikit.R
import me.floow.uikit.theme.LocalTypography

@Composable
internal fun ProfileButtonsSegment(
	isSelf: Boolean,
	onAddPostButtonClick: () -> Unit,
	onMessageButtonClick: () -> Unit,
	onEditButtonClick: () -> Unit,
	onShareButtonClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier
			.padding(vertical = 24.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(
			modifier = Modifier
				.padding(horizontal = 16.dp)
				.weight(1f)
				.clip(RoundedCornerShape(4.dp))
				.clickable { if (isSelf) onAddPostButtonClick() else onMessageButtonClick() },
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			if (isSelf) {
				Icon(
					painter = painterResource(R.drawable.add_post_outline_icon),
					contentDescription = null,
					modifier = Modifier.size(27.dp),
					tint = androidx.compose.ui.graphics.Color.White
				)

				Spacer(Modifier.height(8.dp))

				Text(
					text = stringResource(me.floow.profile.R.string.new_post),
					style = LocalTypography.current.labelMedium,
					color = androidx.compose.ui.graphics.Color.White
				)
			} else {
				Icon(
					painter = painterResource(me.floow.uikit.R.drawable.chats_icon),
					contentDescription = null,
					modifier = Modifier.size(27.dp),
					tint = androidx.compose.ui.graphics.Color.White
				)

				Spacer(Modifier.height(8.dp))

				Text(
					text = stringResource(me.floow.profile.R.string.message),
					style = LocalTypography.current.labelMedium,
					color = androidx.compose.ui.graphics.Color.White
				)
			}
		}

		VerticalDivider(Modifier.height(18.dp))

		Column(
			modifier = Modifier
				.padding(horizontal = 16.dp)
				.weight(1f)
				.clip(RoundedCornerShape(4.dp))
				.clickable { if (isSelf) onEditButtonClick() else onShareButtonClick() },
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Icon(
				painter = painterResource(
					if (isSelf) R.drawable.edit_icon else R.drawable.share_icon
				),
				contentDescription = null,
				modifier = Modifier.size(27.dp),
				tint = androidx.compose.ui.graphics.Color.White
			)

			Spacer(Modifier.height(8.dp))

			Text(
				text = stringResource(
					if (isSelf) me.floow.profile.R.string.edit else me.floow.profile.R.string.share
				),
				style = LocalTypography.current.labelMedium,
				color = androidx.compose.ui.graphics.Color.White
			)
		}
	}
}
