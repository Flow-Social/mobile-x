package me.floow.login.ui.login

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.floow.shared.login.platform.showToast
import me.floow.shared.login.uilogic.LoginState
import me.floow.shared.login.uilogic.LoginViewModel
import me.floow.shared.login.ui.login.LoginScreen
import me.floow.uikit.util.SetNavigationBarColor
import me.flowme.login.R

@Composable
fun LoginRoute(
    onGoToCreateProfile: () -> Unit,
    onGoToHome: () -> Unit,
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier
) {
    val loginUnsuccessfulToastMessage = stringResource(R.string.login_unsuccessful_toast_message)
    val state by viewModel.state.collectAsState()

    SetNavigationBarColor(MaterialTheme.colorScheme.background)

    LaunchedEffect(Unit) {
        viewModel.resumeAuthorization()
    }

    LaunchedEffect(state) {
        when (state) {
            LoginState.Authenticated -> onGoToHome()
            LoginState.PendingRegistration -> onGoToCreateProfile()
            is LoginState.Error -> {
                showToast(loginUnsuccessfulToastMessage)
                viewModel.consumeError()
            }

            LoginState.Idle,
            LoginState.Loading -> Unit
        }
    }

    LoginScreen(
        isLoading = state is LoginState.Loading,
        onLoginClick = viewModel::signInWithGoogle,
        modifier = modifier
    )
}
