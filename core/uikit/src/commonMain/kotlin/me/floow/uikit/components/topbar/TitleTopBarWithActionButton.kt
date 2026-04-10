package me.floow.uikit.components.topbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.floow.uikit.components.buttons.WideOutlinedIconButton
import me.floow.uikit.theme.LocalTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleTopBarWithActionButton(
    titleText: String,
    onActionButtonClick: () -> Unit,
    icon: @Composable () -> Unit,
    titleTextStyle: TextStyle? = null,
    showActionButton: Boolean = true,
    useOutlinedActionButton: Boolean = true,
    wrapActionInIconButton: Boolean = true,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TopAppBarDefaults.TopAppBarExpandedHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titleText,
                overflow = TextOverflow.Ellipsis,
                style = titleTextStyle ?: LocalTypography.current.titleLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            TopBarAction(
                showActionButton = showActionButton,
                useOutlinedActionButton = useOutlinedActionButton,
                wrapActionInIconButton = wrapActionInIconButton,
                onActionButtonClick = onActionButtonClick,
                icon = icon
            )
        }

        if (showDivider) {
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarAction(
    showActionButton: Boolean,
    useOutlinedActionButton: Boolean,
    wrapActionInIconButton: Boolean,
    onActionButtonClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    if (!showActionButton) return

    if (useOutlinedActionButton) {
        WideOutlinedIconButton(
            onClick = onActionButtonClick,
            buttonWidth = 60.dp
        ) {
            icon()
        }
    } else if (wrapActionInIconButton) {
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
