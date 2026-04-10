package me.floow.shared.login.ui.createprofile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.create_profile_finish_hint
import flow.feature.shared.generated.resources.create_profile_title
import flow.feature.shared.generated.resources.done_icon
import flow.feature.shared.generated.resources.edit_profile_sheet_save
import me.floow.shared.login.uilogic.createprofile.CreateProfileState
import me.floow.shared.profile.ui.edit.SharedProfileDetailsFields
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.topbar.TitleTopBarWithActionButton
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreateProfileScreen(
    state: CreateProfileState,
    snackbarHostState: SnackbarHostState,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBiographyChange: (String) -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editState = state as? CreateProfileState.Edit
    Scaffold(
        topBar = {
            TitleTopBarWithActionButton(
                titleText = stringResource(Res.string.create_profile_title),
                onActionButtonClick = onDoneClick,
                showActionButton = state !is CreateProfileState.Uploading,
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.done_icon),
                        contentDescription = stringResource(Res.string.edit_profile_sheet_save),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            if (editState != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    SharedProfileDetailsFields(
                        name = editState.name,
                        username = editState.username,
                        bio = editState.bio,
                        onNameChange = onNameChange,
                        onUsernameChange = onUsernameChange,
                        onBiographyChange = onBiographyChange,
                        modifier = Modifier.fillMaxWidth(),
                        beforeFieldsContent = {
                            Text(
                                text = stringResource(Res.string.create_profile_finish_hint),
                                style = LocalTypography.current.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (state is CreateProfileState.Uploading) {
                FlowLoadingIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}
