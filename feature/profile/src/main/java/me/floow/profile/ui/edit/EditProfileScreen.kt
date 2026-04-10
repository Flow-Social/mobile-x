package me.floow.profile.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.floow.profile.R
import me.floow.shared.profile.ui.edit.EditProfileState
import me.floow.shared.profile.ui.edit.SharedEditProfileFormContent
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.topbar.TitleTopBarWithActionButtonWithNavBack
import androidx.compose.foundation.verticalScroll

@Composable
internal fun EditProfileScreen(
	state: EditProfileState,
	onBackClick: () -> Unit,
	onDoneClick: () -> Unit,
	onAvatarPickerClick: () -> Unit,
	onBackgroundPickerClick: () -> Unit,
	onNameChange: (String) -> Unit,
	onUsernameChange: (String) -> Unit,
	onBiographyChange: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	Scaffold(
		topBar = {
			EditProfileScreenTopBar(
				onBackClick = onBackClick,
				onDoneClick = onDoneClick
			)
		},
		modifier = modifier
	) { innerPadding ->
		Box(
			modifier = Modifier
				.padding(innerPadding)
				.fillMaxSize()
				.navigationBarsPadding()
		) {
			val editState = state as? EditProfileState.Edit ?: return@Box
			Column(
				modifier = Modifier
					.fillMaxSize()
					.verticalScroll(rememberScrollState())
			) {
				SharedEditProfileFormContent(
					state = editState,
					onAvatarPickerClick = onAvatarPickerClick,
					onBackgroundPickerClick = onBackgroundPickerClick,
					onNameChange = onNameChange,
					onUsernameChange = onUsernameChange,
					onBiographyChange = onBiographyChange,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 14.dp),
				)
			}
			if (editState.isSubmitting) {
				FlowLoadingIndicator(
					Modifier.align(Alignment.Center)
				)
			}
		}
	}
}

@Composable
internal fun EditProfileScreenTopBar(
	onBackClick: () -> Unit,
	onDoneClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	TitleTopBarWithActionButtonWithNavBack(
		titleText = stringResource(R.string.edit),
		onBackClick = onBackClick,
		onActionButtonClick = onDoneClick,
		icon = {
			Icon(
				painter = painterResource(me.floow.uikit.R.drawable.done_icon),
				contentDescription = null
			)
		},
		modifier = modifier.statusBarsPadding()
	)
}
