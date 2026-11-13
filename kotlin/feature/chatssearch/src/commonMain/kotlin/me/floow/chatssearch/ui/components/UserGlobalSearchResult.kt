package me.floow.chatssearch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import me.floow.chatssearch.uilogic.UserSearchResult
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.theme.LocalTypography

@Composable
fun UserGlobalSearchResult(
	userSearchResult: UserSearchResult,
	onlineLabel: String,
	offlineLabel: String,
	onClick: (UserSearchResult) -> Unit,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier
			.clickable { onClick(userSearchResult) }
			.padding(horizontal = 20.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		NetworkAvatar(
			name = userSearchResult.name.value,
			avatarModel = userSearchResult.avatarUrl,
			contentDescription = null,
			size = 50.dp,
			modifier = Modifier
				.clip(CircleShape)
				.background(MaterialTheme.colorScheme.surfaceVariant),
			contentScale = ContentScale.Crop
		)

		Spacer(Modifier.width(9.dp))

		Column(
			verticalArrangement = Arrangement.Center
		) {
			Text(
				text = userSearchResult.name.value,
				style = LocalTypography.current.titleMedium,
			)

			Spacer(Modifier.height(2.dp))

			Text(
				text = if (userSearchResult.isOnline) onlineLabel else offlineLabel,
				style = LocalTypography.current.bodyMedium,
				color = MaterialTheme.colorScheme.secondary
			)
		}
	}
}
