package me.floow.profile.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
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
import me.floow.profile.uilogic.edit.EditProfileViewModel
import me.floow.uikit.util.SetNavigationBarColor

data class EditProfileRouteInitialData(
	val name: String,
	val username: String,
	val description: String,
	val avatarUrl: String? = null,
	val backgroundUrl: String? = null,
)

@Composable
fun EditProfileRoute(
	initialData: EditProfileRouteInitialData? = null,
	onBackClick: () -> Unit = {},
	onDoneClick: () -> Unit,
	vm: EditProfileViewModel,
	modifier: Modifier = Modifier
) {
	val state by vm.state.collectAsState()
	val context = LocalContext.current
	val hapticFeedback = LocalHapticFeedback.current
	val lifecycle = LocalLifecycleOwner.current.lifecycle
	val pickAvatarLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.PickVisualMedia()
	) { uri ->
		vm.setAvatarFromPicker(uri?.toString())
	}
	val pickBackgroundLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.PickVisualMedia()
	) { uri ->
		vm.setBackgroundFromPicker(uri?.toString())
	}

	LaunchedEffect(Unit) {
		if (initialData != null) {
			vm.setInitialData(initialData)
		} else {
			vm.loadData()
		}

		lifecycle.repeatOnLifecycle(state = Lifecycle.State.STARTED) {
			launch {
				vm.hapticFeedbackFlow.collectLatest {
					hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
				}
			}
		}
	}

	EditProfileScreen(
		state = state,
		onBackClick = onBackClick,
		onDoneClick = {
			vm.updateProfile(
				onSuccess = onDoneClick,
				onFailure = {
					val toast = Toast.makeText(context, "Failure", Toast.LENGTH_SHORT)
					toast.show()
				}
			)
		},
		onAvatarPickerClick = {
			pickAvatarLauncher.launch(
				PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
			)
		},
		onBackgroundPickerClick = {
			pickBackgroundLauncher.launch(
				PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
			)
		},
		onNameChange = vm::updateName,
		onUsernameChange = vm::updateUsername,
		onBiographyChange = vm::updateBiography,
		modifier = modifier,
	)

	SetNavigationBarColor(
		MaterialTheme.colorScheme.background
	)
}
