package me.floow.shared.profile.ui.edit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.current_reply_close_icon
import flow.feature.shared.generated.resources.done_icon
import flow.feature.shared.generated.resources.edit
import flow.feature.shared.generated.resources.edit_profile_sheet_bio_label
import flow.feature.shared.generated.resources.edit_profile_sheet_bio_placeholder
import flow.feature.shared.generated.resources.edit_profile_sheet_change_avatar
import flow.feature.shared.generated.resources.edit_profile_sheet_change_background
import flow.feature.shared.generated.resources.edit_profile_sheet_close
import flow.feature.shared.generated.resources.edit_profile_sheet_name_label
import flow.feature.shared.generated.resources.edit_profile_sheet_name_placeholder
import flow.feature.shared.generated.resources.edit_profile_sheet_save
import flow.feature.shared.generated.resources.edit_profile_sheet_title
import flow.feature.shared.generated.resources.edit_profile_sheet_username_label
import flow.feature.shared.generated.resources.edit_profile_sheet_username_placeholder
import flow.feature.shared.generated.resources.edit_profile_sheet_username_prefix
import flow.feature.shared.generated.resources.edit_profile_sheet_username_taken
import flow.feature.shared.generated.resources.username_field_supporting_text
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.theme.getFlowStarShape
import me.floow.uikit.util.state.ValidatedField
import me.floow.uikit.util.state.ValidationErrorType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditProfileScreen(
    state: EditProfileState,
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit,
    onAvatarPickerClick: () -> Unit,
    onBackgroundPickerClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBiographyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
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
            if (editState.isSubmitting) {
                FlowLoadingIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun EditProfileBottomSheet(
    state: EditProfileState,
    onDismissRequest: () -> Unit,
    onDoneClick: () -> Unit,
    onAvatarPickerClick: () -> Unit,
    onBackgroundPickerClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBiographyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editState = state as? EditProfileState.Edit ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        SharedEditProfileSheetTopBar(
            state = editState,
            onDismissRequest = onDismissRequest,
            onDoneClick = onDoneClick,
        )
        SharedEditProfileFormContent(
            state = editState,
            onAvatarPickerClick = onAvatarPickerClick,
            onBackgroundPickerClick = onBackgroundPickerClick,
            onNameChange = onNameChange,
            onUsernameChange = onUsernameChange,
            onBiographyChange = onBiographyChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun EditProfileScreenTopBar(
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(Res.drawable.current_reply_close_icon),
                contentDescription = stringResource(Res.string.edit_profile_sheet_close),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(Res.string.edit),
            style = LocalTypography.current.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDoneClick) {
            Icon(
                painter = painterResource(Res.drawable.done_icon),
                contentDescription = stringResource(Res.string.edit_profile_sheet_save),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun SharedEditProfileSheetTopBar(
    state: EditProfileState.Edit,
    onDismissRequest: () -> Unit,
    onDoneClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 8.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onDismissRequest,
            enabled = !state.isSubmitting,
        ) {
            Icon(
                painter = painterResource(Res.drawable.current_reply_close_icon),
                contentDescription = stringResource(Res.string.edit_profile_sheet_close),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(Res.string.edit_profile_sheet_title),
            style = LocalTypography.current.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = onDoneClick,
            enabled = !state.isSubmitting,
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(min = 54.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = stringResource(Res.string.edit_profile_sheet_save),
                    style = LocalTypography.current.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun SharedEditProfileFormContent(
    state: EditProfileState.Edit,
    onAvatarPickerClick: () -> Unit,
    onBackgroundPickerClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBiographyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = 16.dp),
    ) {
        EditProfileMediaCard(
            avatarModel = state.avatarPreviewUri ?: state.avatarRemoteUrl,
            backgroundModel = state.backgroundPreviewUri ?: state.backgroundRemoteUrl,
            onAvatarPickerClick = onAvatarPickerClick,
            onBackgroundPickerClick = onBackgroundPickerClick,
        )

        if (!state.avatarErrorMessage.isNullOrBlank()) {
            SheetErrorLabel(
                text = state.avatarErrorMessage,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (!state.backgroundErrorMessage.isNullOrBlank()) {
            SheetErrorLabel(
                text = state.backgroundErrorMessage,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SharedProfileDetailsFields(
            name = state.name,
            username = state.username,
            bio = state.bio,
            onNameChange = onNameChange,
            onUsernameChange = onUsernameChange,
            onBiographyChange = onBiographyChange,
        )
    }
}

@Composable
fun SharedProfileDetailsFields(
    name: ValidatedField,
    username: ValidatedField,
    bio: ValidatedField,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBiographyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    beforeFieldsContent: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        beforeFieldsContent?.invoke(this)

        EditProfileSheetField(
            title = stringResource(Res.string.edit_profile_sheet_name_label),
            value = name.value,
            placeholder = stringResource(Res.string.edit_profile_sheet_name_placeholder),
            isError = name is ValidatedField.Invalid,
            onValueChange = onNameChange,
        )

        Spacer(modifier = Modifier.height(12.dp))

        EditProfileSheetField(
            title = stringResource(Res.string.edit_profile_sheet_username_label),
            value = username.value,
            placeholder = stringResource(Res.string.edit_profile_sheet_username_placeholder),
            isError = username is ValidatedField.Invalid,
            prefix = stringResource(Res.string.edit_profile_sheet_username_prefix),
            supportingText = stringResource(Res.string.username_field_supporting_text),
            errorText = usernameErrorText(username),
            onValueChange = onUsernameChange,
        )

        Spacer(modifier = Modifier.height(18.dp))

        EditProfileSheetField(
            title = stringResource(Res.string.edit_profile_sheet_bio_label),
            value = bio.value,
            placeholder = stringResource(Res.string.edit_profile_sheet_bio_placeholder),
            isError = bio is ValidatedField.Invalid,
            minLines = 5,
            maxLines = 5,
            singleLine = false,
            onValueChange = onBiographyChange,
        )
    }
}

@Composable
private fun EditProfileMediaCard(
    avatarModel: String?,
    backgroundModel: String?,
    onAvatarPickerClick: () -> Unit,
    onBackgroundPickerClick: () -> Unit,
) {
    val starShape = getFlowStarShape()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(223.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp),
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onBackgroundPickerClick),
        ) {
            if (!backgroundModel.isNullOrBlank()) {
                AsyncImage(
                    model = backgroundModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.92f),
                        )
                    )
                )
        )

        Text(
            text = stringResource(Res.string.edit_profile_sheet_change_background),
            style = LocalTypography.current.labelMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 14.dp)
                .clickable(onClick = onBackgroundPickerClick),
        )

        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.Center)
                .padding(bottom = 16.dp)
                .clickable(onClick = onAvatarPickerClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(starShape)
                    .background(Color.LightGray)
                    .border(4.dp, Color.White, starShape)
            ) {
                if (!avatarModel.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
            }
        }

        Surface(
            onClick = onAvatarPickerClick,
            shape = RoundedCornerShape(100.dp),
            color = Color.White,
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.edit_profile_sheet_change_avatar),
                style = LocalTypography.current.bodyMedium,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun EditProfileSheetField(
    title: String,
    value: String,
    placeholder: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefix: String = "",
    supportingText: String = "",
    errorText: String? = null,
    minLines: Int = 1,
    maxLines: Int = 1,
    singleLine: Boolean = true,
) {
    val resolvedMaxLines = if (singleLine) 1 else maxOf(minLines, maxLines)
    val colors = MaterialTheme.colorScheme
    var isFocused by remember { mutableStateOf(false) }
    val showFloatingLabel = isFocused
    val borderColor = when {
        isError -> colors.error
        showFloatingLabel -> colors.onSurface
        else -> colors.onSurface.copy(alpha = 0.1f)
    }
    val titleColor = when {
        isError -> colors.error
        showFloatingLabel -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val fieldMinHeight = if (singleLine) {
        if (showFloatingLabel) 57.dp else 58.dp
    } else {
        if (showFloatingLabel) 80.dp else 88.dp
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            minLines = minLines,
            maxLines = resolvedMaxLines,
            singleLine = singleLine,
            textStyle = LocalTypography.current.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = fieldMinHeight)
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(
                            start = 14.dp,
                            end = 14.dp,
                            top = 10.dp,
                            bottom = 10.dp,
                        ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style = LocalTypography.current.captionMedium,
                        color = titleColor,
                        modifier = Modifier.alpha(if (showFloatingLabel) 1f else 0.72f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (prefix.isNotBlank()) {
                            Text(
                                text = prefix,
                                style = LocalTypography.current.bodyMedium,
                                color = colors.onSurface,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = if (singleLine) 0.dp else 1.dp)
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = LocalTypography.current.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.alpha(0.92f),
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            }
        )

        if (errorText != null) {
            SheetErrorLabel(
                text = errorText,
                modifier = Modifier.padding(top = 6.dp, start = 8.dp),
            )
        } else if (supportingText.isNotBlank()) {
            Text(
                text = supportingText,
                style = LocalTypography.current.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 8.dp),
            )
        }
    }
}

@Composable
private fun SheetErrorLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = LocalTypography.current.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

@Composable
private fun usernameErrorText(field: ValidatedField): String? {
    val invalidField = field as? ValidatedField.Invalid ?: return null
    return when (invalidField.errorType) {
        ValidationErrorType.UsernameAlreadyExists -> stringResource(Res.string.edit_profile_sheet_username_taken)
        else -> null
    }
}
