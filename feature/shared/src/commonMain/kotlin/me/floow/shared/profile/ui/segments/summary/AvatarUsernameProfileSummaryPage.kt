package me.floow.shared.profile.ui.segments.summary

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.no_display_name
import me.floow.shared.profile.resolveProfileListUrls
import me.floow.shared.profile.ui.LikesLabel
import me.floow.uikit.components.avatar.InitialAvatar
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.ProgressiveImageMode
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.theme.getFlowStarShape
import org.jetbrains.compose.resources.stringResource

@Composable
fun AvatarUsernameProfileSummaryPage(
    profileAvatarUri: String?,
    displayName: String?,
    totalLikesReceived: Int,
    statusLabel: String?,
    modifier: Modifier = Modifier
) {
    val starShape = getFlowStarShape()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(bottom = 0.dp)
    ) {
        val avatarModel = profileAvatarUri?.takeIf { it.isNotBlank() }
        val (avatarLqUrl, avatarPreviewUrl) = remember(avatarModel) {
            resolveProfileListUrls(avatarModel)
        }

        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(starShape)
                .border(4.dp, Color.White, starShape)
        ) {
            InitialAvatar(
                name = displayName.orEmpty(),
                size = 150.dp,
                shape = starShape,
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

        Spacer(Modifier.height(16.dp))

        Text(
            text = displayName ?: stringResource(Res.string.no_display_name),
            style = LocalTypography.current.profileName,
            color = Color.White,
        )

        if (!statusLabel.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusLabel,
                style = LocalTypography.current.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        Spacer(Modifier.height(24.dp))

        LikesLabel(
            onClick = {},
            totalLikesReceived = totalLikesReceived,
        )
    }
}
