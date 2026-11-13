package me.floow.uikit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.montserrat_black
import flow.core.uikit.generated.resources.montserrat_regular
import flow.core.uikit.generated.resources.montserrat_semibold
import flow.core.uikit.generated.resources.roboto_black
import flow.core.uikit.generated.resources.roboto_bold
import flow.core.uikit.generated.resources.roboto_medium
import flow.core.uikit.generated.resources.roboto_regular
import org.jetbrains.compose.resources.Font

@Composable
internal fun rememberRobotoFontFamily(): FontFamily {
    val regular = Font(Res.font.roboto_regular, FontWeight.Normal)
    val black = Font(Res.font.roboto_black, FontWeight.Black)
    val bold = Font(Res.font.roboto_bold, FontWeight.Bold)
    val medium = Font(Res.font.roboto_medium, FontWeight.Medium)
    return remember(
        regular,
        black,
        bold,
        medium,
    ) {
        FontFamily(
            regular,
            black,
            bold,
            medium,
        )
    }
}

@Composable
fun rememberMontserratFontFamily(): FontFamily {
    val regular = Font(Res.font.montserrat_regular, FontWeight.Normal)
    val black = Font(Res.font.montserrat_black, FontWeight.Black)
    val semibold = Font(Res.font.montserrat_semibold, FontWeight.SemiBold)
    return remember(regular, black, semibold) {
        FontFamily(
            regular,
            black,
            semibold,
        )
    }
}
