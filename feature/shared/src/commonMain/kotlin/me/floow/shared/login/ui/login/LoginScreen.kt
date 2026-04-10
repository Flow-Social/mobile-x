package me.floow.shared.login.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.flow_onboarding_explanation
import flow.feature.shared.generated.resources.flow_rounded_logo
import flow.feature.shared.generated.resources.social_media_flow_exclamation
import me.floow.shared.login.platform.openUrl
import me.floow.shared.login.ui.login.components.GoogleLoginButton
import me.floow.shared.login.ui.login.components.TermsAndPolicyText
import me.floow.uikit.components.loading.FlowLoadingIndicator
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(
    isLoading: Boolean,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .statusBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Icon(
            painterResource(Res.drawable.flow_rounded_logo),
            null,
            tint = Color.Unspecified,
            modifier = Modifier.size(67.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.social_media_flow_exclamation),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(Res.string.flow_onboarding_explanation),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .width(326.dp)
        )

        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            FlowLoadingIndicator()
        } else {
            GoogleLoginButton(onLoginClick, Modifier)
        }

        Spacer(Modifier.weight(1f))

        TermsAndPolicyText(
            onTermsClick = {
                openUrl("https://en.wikipedia.org/wiki/Terms_of_service")
            },
            onPrivacyClick = {
                openUrl("https://en.wikipedia.org/wiki/Privacy_policy")
            },
            Modifier.padding(horizontal = 10.dp)
        )

        Spacer(Modifier.height(24.dp))
    }
}
