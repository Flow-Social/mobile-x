package me.floow.uikit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.noto_colrv1_emojicompat
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberWasmEmojiFontReady(): Boolean {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val emojiFontState = preloadFont(Res.font.noto_colrv1_emojicompat)
    var isReady by remember(fontFamilyResolver, emojiFontState.value) {
        mutableStateOf(false)
    }

    LaunchedEffect(fontFamilyResolver, emojiFontState.value) {
        val emojiFont = emojiFontState.value
        if (emojiFont == null) {
            isReady = false
            return@LaunchedEffect
        }

        isReady = false
        fontFamilyResolver.preload(
            FontFamily(
                emojiFont
            )
        )
        isReady = true
    }

    return isReady
}
