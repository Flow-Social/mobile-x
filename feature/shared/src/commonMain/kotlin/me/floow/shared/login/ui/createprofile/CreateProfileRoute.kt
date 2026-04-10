package me.floow.shared.login.ui.createprofile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.collectLatest
import me.floow.shared.login.uilogic.createprofile.CreateProfileStateHolder

@Composable
fun CreateProfileRoute(
    stateHolder: CreateProfileStateHolder,
    onDone: () -> Unit,
) {
    val state by stateHolder.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(stateHolder) {
        onDispose { stateHolder.dispose() }
    }

    LaunchedEffect(stateHolder) {
        stateHolder.events.collectLatest { event ->
            when (event) {
                CreateProfileStateHolder.Event.Completed -> onDone()
                CreateProfileStateHolder.Event.HapticFeedback -> Unit
                is CreateProfileStateHolder.Event.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    CreateProfileScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNameChange = stateHolder::updateName,
        onUsernameChange = stateHolder::updateUsername,
        onBiographyChange = stateHolder::updateBio,
        onDoneClick = stateHolder::createProfile,
    )
}
