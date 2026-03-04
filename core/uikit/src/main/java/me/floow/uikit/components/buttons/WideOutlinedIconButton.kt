package me.floow.uikit.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.floow.uikit.util.ComponentPreviewBox
import me.floow.uikit.R

@Composable
fun WideOutlinedIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    buttonWidth: Dp = 72.dp,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    OutlinedButton(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        enabled = enabled,
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .width(buttonWidth)
            .height(40.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
    }
}

@Preview
@Composable
private fun WideOutlinedIconButtonPreview() {
    ComponentPreviewBox {
        WideOutlinedIconButton(
            onClick = {},
        ) {
            Icon(
                painterResource(R.drawable.edit_icon),
                null,
            )
        }
    }
}

@Preview
@Composable
private fun WideOutlinedIconButtonDisabledPreview() {
    ComponentPreviewBox {
        WideOutlinedIconButton(
            onClick = {},
            enabled = false,
        ) {
            Icon(
                painterResource(R.drawable.edit_icon),
                null,
            )
        }
    }
}
