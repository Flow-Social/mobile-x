package me.floow.uikit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class FlowTypography(
    val titleLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.38.sp
    ),
    val profileName: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp
    ),
    val titleMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.32).sp
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp
    ),
    val captionSmall: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    ),
    val captionMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    ),
    val labelMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    )
)

@Immutable
data class FlowColorScheme(
    val statusBarColor: Color = Color.White,
)

data class SystemBarStyle(
    val statusBarColor: Color? = null,
    val useDarkStatusBarIcons: Boolean? = null,
    val navigationBarColor: Color? = null,
    val useDarkNavigationBarIcons: Boolean? = null,
    val isNavigationBarContrastEnforced: Boolean? = null
)

private fun SystemBarStyle.merge(override: SystemBarStyle): SystemBarStyle = copy(
    statusBarColor = override.statusBarColor ?: statusBarColor,
    useDarkStatusBarIcons = override.useDarkStatusBarIcons ?: useDarkStatusBarIcons,
    navigationBarColor = override.navigationBarColor ?: navigationBarColor,
    useDarkNavigationBarIcons = override.useDarkNavigationBarIcons ?: useDarkNavigationBarIcons,
    isNavigationBarContrastEnforced = override.isNavigationBarContrastEnforced ?: isNavigationBarContrastEnforced
)

@Stable
class SystemBarStyleController {
    private val styleByToken = mutableStateMapOf<Any, SystemBarStyle>()
    private val tokenOrder = mutableStateListOf<Any>()

    var overrideStyle by mutableStateOf(SystemBarStyle())
        private set

    fun update(token: Any, style: SystemBarStyle) {
        if (!styleByToken.containsKey(token)) {
            tokenOrder.add(token)
        }
        styleByToken[token] = style
        recompute()
    }

    fun remove(token: Any) {
        if (styleByToken.remove(token) != null) {
            tokenOrder.remove(token)
            recompute()
        }
    }

    private fun recompute() {
        overrideStyle = tokenOrder.fold(SystemBarStyle()) { merged, token ->
            merged.merge(styleByToken[token] ?: SystemBarStyle())
        }
    }
}

val LocalTypography = staticCompositionLocalOf {
    FlowTypography()
}

val LocalColorScheme = staticCompositionLocalOf {
    FlowColorScheme()
}

val LocalSystemBarStyle = staticCompositionLocalOf<SystemBarStyleController> {
    error("LocalSystemBarStyle not provided")
}

object FlowCustomTheme {
    val typography: FlowTypography
        @Composable
        get() = LocalTypography.current
    val colorScheme: FlowColorScheme
        @Composable
        get() = LocalColorScheme.current
}

private val DarkFlowColorScheme = FlowColorScheme(
    statusBarColor = Color.Black,
)

private val LightFlowColorScheme = FlowColorScheme(
    statusBarColor = Color.White,
)

private val UnifiedOutlineLight = Color.Black.copy(alpha = 0.10f)
private val UnifiedOutlineDark = Color.White.copy(alpha = 0.10f)

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

val typography = Typography(
    headlineLarge = TextStyle(
        fontSize = 46.sp
    ),
    headlineSmall = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = generateColorScheme(darkTheme = darkTheme, dynamicColor = dynamicColor)
    val flowColorScheme = if (darkTheme) DarkFlowColorScheme else LightFlowColorScheme
    val flowTypography = rememberFlowTypography()
    val systemBarStyleController = remember { SystemBarStyleController() }
    val systemBarStyle = systemBarStyleController.overrideStyle
    PlatformSystemAppearance(colorScheme = colorScheme, systemBarStyle = systemBarStyle)

    val flowRippleTheme = RippleConfiguration(
        color = colorScheme.primary
    )

    MaterialTheme(
        content = {
            CompositionLocalProvider(
                LocalTypography provides flowTypography,
                LocalColorScheme provides flowColorScheme,
                LocalRippleConfiguration provides flowRippleTheme,
                LocalSystemBarStyle provides systemBarStyleController
            ) {
                content()
            }
        },
        colorScheme = colorScheme,
        typography = typography
    )
}

@Composable
private fun generateColorScheme(
    dynamicColor: Boolean,
    darkTheme: Boolean
): ColorScheme {
    // Dynamic color stays disabled in shared theme until we add a separate platform hook for it.
    @Suppress("UNUSED_VARIABLE")
    val ignoredDynamicColor = dynamicColor
    val baseColorScheme = if (darkTheme) darkScheme else lightScheme
    return overrideColors(baseColorScheme, darkTheme)
}

private fun overrideColors(
    colorScheme: ColorScheme,
    darkTheme: Boolean
): ColorScheme {
    val unifiedOutline = if (darkTheme) UnifiedOutlineDark else UnifiedOutlineLight
    return colorScheme.copy(
        outline = unifiedOutline,
        outlineVariant = unifiedOutline
    )
}

@Composable
private fun rememberFlowTypography(): FlowTypography {
    val roboto = rememberRobotoFontFamily()
    val montserrat = rememberMontserratFontFamily()
    val contentFontFamily = rememberPlatformContentFontFamily(roboto)
    return remember(roboto, montserrat, contentFontFamily) {
        FlowTypography(
            titleLarge = TextStyle(
                fontFamily = roboto,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.38.sp
            ),
            profileName = TextStyle(
                fontFamily = montserrat,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp
            ),
            titleMedium = TextStyle(
                fontFamily = contentFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.32).sp
            ),
            bodyMedium = TextStyle(
                fontFamily = contentFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.24).sp
            ),
            captionSmall = TextStyle(
                fontFamily = contentFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 9.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.2.sp
            ),
            captionMedium = TextStyle(
                fontFamily = contentFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.sp
            ),
            labelMedium = TextStyle(
                fontFamily = contentFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.2.sp
            )
        )
    }
}
