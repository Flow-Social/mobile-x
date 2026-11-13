package me.floow.shared.login.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.flow_onboarding_explanation
import flow.shared.generated.resources.login_background
import flow.shared.generated.resources.login_title
import me.floow.shared.login.platform.openUrl
import me.floow.shared.login.ui.login.components.GoogleLoginButton
import me.floow.shared.login.ui.login.components.TermsAndPolicyText
import me.floow.uikit.theme.rememberMontserratFontFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import flow.shared.generated.resources.title_flow_logo

@Composable
fun LoginScreen(
    isLoading: Boolean,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Image(
            painter = painterResource(Res.drawable.login_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                painter = painterResource(Res.drawable.title_flow_logo),
                contentDescription = stringResource(Res.string.login_title),
                tint = Color.White,
                modifier = Modifier
                    .size(width = 292.dp, height = 71.dp)
            )

            Spacer(Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.flow_onboarding_explanation),
                        color = Color(0xFF121213),
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Start
                    )

                    GoogleLoginButton(
                        onClick = onLoginClick,
                        isLoading = isLoading
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                TermsAndPolicyText(
                    onTermsClick = {
                        openUrl("https://en.wikipedia.org/wiki/Terms_of_service")
                    },
                    onPrivacyClick = {
                        openUrl("https://en.wikipedia.org/wiki/Privacy_policy")
                    }
                )
            }
        }
    }
}

