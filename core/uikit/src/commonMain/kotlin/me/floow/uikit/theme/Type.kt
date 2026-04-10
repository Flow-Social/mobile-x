package me.floow.uikit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.montserrat_black
import flow.core.uikit.generated.resources.montserrat_black_italic
import flow.core.uikit.generated.resources.montserrat_italic
import flow.core.uikit.generated.resources.montserrat_regular
import flow.core.uikit.generated.resources.montserrat_semibold
import flow.core.uikit.generated.resources.montserrat_semibolditalic
import flow.core.uikit.generated.resources.roboto_black
import flow.core.uikit.generated.resources.roboto_black_italic
import flow.core.uikit.generated.resources.roboto_bold
import flow.core.uikit.generated.resources.roboto_bold_italic
import flow.core.uikit.generated.resources.roboto_italic
import flow.core.uikit.generated.resources.roboto_light
import flow.core.uikit.generated.resources.roboto_light_italic
import flow.core.uikit.generated.resources.roboto_medium
import flow.core.uikit.generated.resources.roboto_medium_italic
import flow.core.uikit.generated.resources.roboto_regular
import flow.core.uikit.generated.resources.roboto_thin
import flow.core.uikit.generated.resources.roboto_thin_italic
import org.jetbrains.compose.resources.Font

@Composable
internal fun rememberRobotoFontFamily(): FontFamily {
    val regular = Font(Res.font.roboto_regular, FontWeight.Normal)
    val italic = Font(Res.font.roboto_italic, FontWeight.Normal, FontStyle.Italic)
    val black = Font(Res.font.roboto_black, FontWeight.Black)
    val blackItalic = Font(Res.font.roboto_black_italic, FontWeight.Black, FontStyle.Italic)
    val bold = Font(Res.font.roboto_bold, FontWeight.Bold)
    val boldItalic = Font(Res.font.roboto_bold_italic, FontWeight.Bold, FontStyle.Italic)
    val medium = Font(Res.font.roboto_medium, FontWeight.Medium)
    val mediumItalic = Font(Res.font.roboto_medium_italic, FontWeight.Medium, FontStyle.Italic)
    val light = Font(Res.font.roboto_light, FontWeight.Light)
    val lightItalic = Font(Res.font.roboto_light_italic, FontWeight.Light, FontStyle.Italic)
    val thin = Font(Res.font.roboto_thin, FontWeight.Thin)
    val thinItalic = Font(Res.font.roboto_thin_italic, FontWeight.Thin, FontStyle.Italic)
    return remember(
        regular,
        italic,
        black,
        blackItalic,
        bold,
        boldItalic,
        medium,
        mediumItalic,
        light,
        lightItalic,
        thin,
        thinItalic
    ) {
        FontFamily(
            regular,
            italic,
            black,
            blackItalic,
            bold,
            boldItalic,
            medium,
            mediumItalic,
            light,
            lightItalic,
            thin,
            thinItalic
        )
    }
}

@Composable
internal fun rememberMontserratFontFamily(): FontFamily {
    val regular = Font(Res.font.montserrat_regular, FontWeight.Normal)
    val italic = Font(Res.font.montserrat_italic, FontWeight.Normal, FontStyle.Italic)
    val black = Font(Res.font.montserrat_black, FontWeight.Black)
    val blackItalic = Font(Res.font.montserrat_black_italic, FontWeight.Black, FontStyle.Italic)
    val semibold = Font(Res.font.montserrat_semibold, FontWeight.SemiBold)
    val semiboldItalic = Font(Res.font.montserrat_semibolditalic, FontWeight.SemiBold, FontStyle.Italic)
    return remember(regular, italic, black, blackItalic, semibold, semiboldItalic) {
        FontFamily(
            regular,
            italic,
            black,
            blackItalic,
            semibold,
            semiboldItalic
        )
    }
}
