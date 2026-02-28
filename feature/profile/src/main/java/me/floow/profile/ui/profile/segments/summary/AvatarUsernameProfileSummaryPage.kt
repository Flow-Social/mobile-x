package me.floow.profile.ui.profile.segments.summary

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.floow.profile.R
import me.floow.profile.ui.profile.LikesLabel
import me.floow.profile.ui.profile.resolveProfileListUrls
import me.floow.uikit.components.avatar.InitialAvatar
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.ProgressiveImageMode
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.theme.NinehedronShape

@Composable
internal fun AvatarUsernameProfileSummaryPage(
	profileAvatarUri: Uri?,
	displayName: String?,
	totalLikesReceived: Int,
	modifier: Modifier = Modifier
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
		modifier = modifier.padding(bottom = 0.dp),
	) {
        val avatarModel = profileAvatarUri?.toString()?.takeIf { it.isNotBlank() }
        val (avatarLqUrl, avatarPreviewUrl) = remember(avatarModel) {
            resolveProfileListUrls(avatarModel)
        }

        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(NinehedronShape)
                .border(2.dp, Color.White, NinehedronShape)
        ) {
            InitialAvatar(
                name = displayName.orEmpty(),
                size = 150.dp,
                shape = NinehedronShape,
                textColor = Color.White,
                textStyle = LocalTypography.current.profileName,
                modifier = Modifier.fillMaxSize()
            )

            if (!avatarLqUrl.isNullOrBlank() || !avatarPreviewUrl.isNullOrBlank()) {
                ProgressiveImage(
                    lqUrl = avatarLqUrl,
                    previewUrl = avatarPreviewUrl,
                    fullUrl = null,
                    mode = ProgressiveImageMode.LIST,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholderColor = Color.Transparent
                )
            }
        }

		Spacer(Modifier.height(9.dp))

        Text(
            text = displayName ?: stringResource(R.string.no_display_name),
            style = LocalTypography.current.profileName,
            color = Color.White,
        )

		Spacer(Modifier.height(17.dp))

		LikesLabel(
			onClick = {},
			totalLikesReceived = totalLikesReceived,
			modifier = Modifier
		)
	}
}
