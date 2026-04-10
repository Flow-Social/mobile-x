package me.floow.uikit.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
internal expect fun rememberPlatformContentFontFamily(preferred: FontFamily): FontFamily
