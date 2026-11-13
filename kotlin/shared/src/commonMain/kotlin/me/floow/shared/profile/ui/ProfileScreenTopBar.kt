package me.floow.shared.profile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.nav_back_icon
import flow.shared.generated.resources.no_username_topbar_title
import flow.shared.generated.resources.share
import flow.shared.generated.resources.profile_settings_icon
import flow.shared.generated.resources.settings_cd_settings
import flow.shared.generated.resources.share_icon
import me.floow.uikit.components.buttons.BlurGlassButton
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenTopBar(
    username: String?,
    isSelf: Boolean,
    onShareClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit,
    heroBackgroundPainter: Painter,
    heroBoundsInWindow: Rect?,
    modifier: Modifier = Modifier
) {
    val titleText = username ?: stringResource(Res.string.no_username_topbar_title)

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        if (isSelf) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(TopAppBarDefaults.TopAppBarExpandedHeight)
                    .padding(horizontal = 16.dp)
                    .zIndex(2f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onSettingsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.profile_settings_icon),
                        contentDescription = stringResource(Res.string.settings_cd_settings),
                        modifier = Modifier.size(22.dp)
                    )
                }

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
                            painter = painterResource(Res.drawable.share_icon),
                            contentDescription = stringResource(Res.string.share),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(TopAppBarDefaults.TopAppBarExpandedHeight)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onBackClick)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.nav_back_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(16.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Text(
                    text = titleText,
                    style = LocalTypography.current.titleLarge,
                    modifier = Modifier.weight(1f),
                    color = Color.White
                )
            }
        }
    }
}
