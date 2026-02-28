package me.floow.login.ui.createprofile.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.floow.uikit.components.loading.FlowLoadingIndicator

@Composable
fun UploadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        FlowLoadingIndicator()
    }
}
