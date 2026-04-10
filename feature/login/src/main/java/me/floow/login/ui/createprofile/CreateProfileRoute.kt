package me.floow.login.ui.createprofile

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.floow.shared.login.uilogic.createprofile.CreateProfileStateHolder

@Composable
fun CreateProfileRoute(
	onDone: () -> Unit,
	stateHolder: CreateProfileStateHolder,
	modifier: Modifier = Modifier
) {
	val state by stateHolder.state.collectAsState()
	val context = LocalContext.current
	val hapticFeedback = LocalHapticFeedback.current
	val lifecycle = LocalLifecycleOwner.current.lifecycle

	DisposableEffect(stateHolder) { onDispose { stateHolder.dispose() } }

	LaunchedEffect(stateHolder) {
		lifecycle.repeatOnLifecycle(state = Lifecycle.State.STARTED) {
			launch {
				stateHolder.events.collectLatest { event ->
					when (event) {
						CreateProfileStateHolder.Event.HapticFeedback -> {
							hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
						}

						is CreateProfileStateHolder.Event.ShowMessage -> {
							Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
						}

						CreateProfileStateHolder.Event.Completed -> onDone()
					}
				}
			}
		}
	}

	CreateProfileScreen(
		state = state,
		onAvatarPickerClick = {
			Toast.makeText(context, "Выбор аватара пока недоступен на этом экране", Toast.LENGTH_SHORT).show()
		},
		onNameChange = stateHolder::updateName,
		onUsernameChange = stateHolder::updateUsername,
		onBiographyChange = stateHolder::updateBio,
		onDoneClick = stateHolder::createProfile,
		modifier = modifier
	)
}
