package me.floow.shared.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize

@Composable
internal expect fun PrefetchImageUrlsEffect(
    urls: List<String>,
    size: IntSize?,
)
