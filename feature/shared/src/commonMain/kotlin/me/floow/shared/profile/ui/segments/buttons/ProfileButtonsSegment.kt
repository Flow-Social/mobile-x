package me.floow.shared.profile.ui.segments.buttons

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.add_post_outline_icon
import flow.feature.shared.generated.resources.chats_icon
import flow.feature.shared.generated.resources.edit
import flow.feature.shared.generated.resources.edit_icon
import flow.feature.shared.generated.resources.message
import flow.feature.shared.generated.resources.new_post
import flow.feature.shared.generated.resources.share
import flow.feature.shared.generated.resources.share_icon
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileButtonsSegment(
    isSelf: Boolean,
    onAddPostButtonClick: () -> Unit,
    onMessageButtonClick: () -> Unit,
    onEditButtonClick: () -> Unit,
    onShareButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 24.dp),
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
            Icon(
                painter = painterResource(
                    if (isSelf) Res.drawable.add_post_outline_icon else Res.drawable.chats_icon
                ),
                contentDescription = null,
                modifier = Modifier.size(27.dp),
                tint = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(
                    if (isSelf) Res.string.new_post else Res.string.message
                ),
                style = LocalTypography.current.labelMedium,
                color = Color.White
            )
        }

        VerticalDivider(
            modifier = Modifier.height(18.dp),
            thickness = 2.dp,
            color = Color.White.copy(alpha = 0.72f)
        )

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
                    if (isSelf) Res.drawable.edit_icon else Res.drawable.share_icon
                ),
                contentDescription = null,
                modifier = Modifier.size(27.dp),
                tint = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(
                    if (isSelf) Res.string.edit else Res.string.share
                ),
                style = LocalTypography.current.labelMedium,
                color = Color.White
            )
        }
    }
}
