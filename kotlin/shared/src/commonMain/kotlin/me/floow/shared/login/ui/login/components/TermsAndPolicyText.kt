package me.floow.shared.login.ui.login.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.auth_accepting_and
import flow.shared.generated.resources.auth_accepting_by_continuing
import flow.shared.generated.resources.auth_accepting_will_be_applied
import flow.shared.generated.resources.privacy_policy
import flow.shared.generated.resources.terms_and_conditions
import me.floow.uikit.theme.FlowCustomTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TermsAndPolicyText(
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val privacyPolicyString = stringResource(Res.string.privacy_policy)
    val termsAndConditionsString = stringResource(Res.string.terms_and_conditions)

    val annotatedString = buildAnnotatedString {
        withStyle(SpanStyle(color = Color.White)) {
            append(stringResource(Res.string.auth_accepting_by_continuing))

            pushStringAnnotation(tag = "terms_and_conditions", annotation = "terms_and_conditions")
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = Color.White)) {
                append(termsAndConditionsString)
            }
            pop()

            append(stringResource(Res.string.auth_accepting_and))

            pushStringAnnotation(tag = "privacy_policy", annotation = "privacy_policy")
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = Color.White)) {
                append(privacyPolicyString)
            }
            pop()

            append(stringResource(Res.string.auth_accepting_will_be_applied))
        }
    }

    var textLayout by rememberTextLayoutState()
    Text(
        text = annotatedString,
        style = FlowCustomTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            color = Color.White
        ),
        modifier = modifier.pointerInput(annotatedString) {
            detectTapGestures { tapOffset ->
                val layout = textLayout ?: return@detectTapGestures
                val offset = layout.getOffsetForPosition(tapOffset)
                annotatedString.getStringAnnotations(
                    tag = "terms_and_conditions",
                    start = offset,
                    end = offset
                ).firstOrNull()?.let {
                    onTermsClick()
                    return@detectTapGestures
                }

                annotatedString.getStringAnnotations(
                    tag = "privacy_policy",
                    start = offset,
                    end = offset
                ).firstOrNull()?.let {
                    onPrivacyClick()
                }
            }
        },
        onTextLayout = { textLayout = it }
    )
}

@Composable
private fun rememberTextLayoutState(): androidx.compose.runtime.MutableState<TextLayoutResult?> {
	return androidx.compose.runtime.remember { mutableStateOf(null) }
}
