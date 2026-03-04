package me.floow.profile.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.floow.uikit.components.buttons.BlurGlassButton
import me.floow.uikit.components.topbar.TitleTopBarWithActionButtonWithNavBack

@Composable
internal fun ProfileScreenTopBar(
    username: String?,
    isSelf: Boolean,
    onShareClick: () -> Unit,
    onBackClick: () -> Unit,
    heroBackgroundPainter: Painter,
    heroBoundsInWindow: Rect?,
    modifier: Modifier = Modifier
) {
    val titleText = username ?: stringResource(me.floow.profile.R.string.no_username_topbar_title)

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        if (isSelf) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 24.dp)
                    .zIndex(2f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 60.dp, minHeight = 56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(onClick = onShareClick),
                    contentAlignment = Alignment.Center
                ) {
                    BlurGlassButton(
                        painter = heroBackgroundPainter,
                        backgroundBoundsInWindow = heroBoundsInWindow,
                        shape = RoundedCornerShape(20.dp),
                        blurRadius = 2.dp,
                        refractionScale = 1.01f,
                        modifier = Modifier
                            .width(60.dp)
                            .height(40.dp),
                        onClick = null
                    ) {
                        Icon(
                            painter = painterResource(me.floow.uikit.R.drawable.share_icon),
                            contentDescription = stringResource(me.floow.profile.R.string.share),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        } else {
            TitleTopBarWithActionButtonWithNavBack(
                titleText = titleText,
                onBackClick = onBackClick,
                onActionButtonClick = {},
                icon = {
                    Icon(
                        painter = painterResource(me.floow.uikit.R.drawable.share_icon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                },
                showActionButton = false,
                useOutlinedActionButton = false,
                wrapActionInIconButton = true,
                showDivider = false,
                modifier = modifier
            )
        }
    }
}
