package me.floow.uikit.components.topbar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.uikit.components.buttons.WideOutlinedIconButton
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.util.ComponentPreviewBox
import me.floow.uikit.R

@Composable
fun TitleTopBarWithActionButton(
    titleText: String,
    onActionButtonClick: () -> Unit,
    icon: @Composable () -> Unit,
    showActionButton: Boolean = true,
    useOutlinedActionButton: Boolean = true,
    wrapActionInIconButton: Boolean = true,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = titleText,
                overflow = TextOverflow.Ellipsis,
                style = LocalTypography.current.titleLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            if (showActionButton) {
                if (useOutlinedActionButton) {
                    WideOutlinedIconButton(
                        onClick = onActionButtonClick,
                        modifier = Modifier
                    ) {
                        icon()
                    }
                } else {
                    if (wrapActionInIconButton) {
                        IconButton(onClick = onActionButtonClick) {
                            icon()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(onClick = onActionButtonClick),
                            contentAlignment = Alignment.Center
                        ) {
                            icon()
                        }
                    }
                }
            }
        }

        if (showDivider) {
            HorizontalDivider()
        }
    }
}

@Preview
@Composable
private fun TitleIconTopBarPreview(modifier: Modifier = Modifier) {
    ComponentPreviewBox(Modifier.fillMaxSize()) {
        TitleTopBarWithActionButton(
            titleText = "Chats",
            icon = {
                Icon(
                    painterResource(R.drawable.chats_icon),
                    null
                )
            },
            onActionButtonClick = {},
            modifier = Modifier
                .fillMaxSize()
        )
    }
}

@Preview
@Composable
private fun TitleIconTopBarPreview_TextOverflow(modifier: Modifier = Modifier) {
    ComponentPreviewBox(Modifier.fillMaxSize()) {
        TitleTopBarWithActionButton(
            titleText = "Lorem ipsum dolor sit amet very very long text.. I don't know what to say",
            icon = {
                Icon(
                    painterResource(R.drawable.chats_icon),
                    null
                )
            },
            onActionButtonClick = {},
            modifier = Modifier
                .fillMaxSize()
        )
    }
}
