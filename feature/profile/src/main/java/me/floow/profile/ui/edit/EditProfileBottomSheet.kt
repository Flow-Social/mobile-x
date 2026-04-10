package me.floow.profile.ui.edit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import me.floow.profile.R
import me.floow.profile.ui.common.SheetStatusBarStyle
import me.floow.profile.ui.common.editProfileSheetStatusBarAppearance
import me.floow.shared.profile.ui.edit.EditProfileState
import me.floow.shared.profile.ui.edit.SharedEditProfileFormContent
import me.floow.shared.profile.ui.edit.SharedEditProfileSheetTopBar
import me.floow.uikit.theme.FlowTheme
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.theme.NinehedronShape
import me.floow.uikit.util.state.ValidatedField
import me.floow.uikit.util.state.ValidationErrorType

@Composable
internal fun EditProfileBottomSheet(
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
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val statusBarAppearance = editProfileSheetStatusBarAppearance(
        colors = MaterialTheme.colorScheme,
        imeVisible = isImeVisible,
    )
    SheetStatusBarStyle(
        appearance = statusBarAppearance,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        EditProfileBottomSheetContent(
            state = editState,
            onDismissRequest = onDismissRequest,
            onDoneClick = onDoneClick,
            onAvatarPickerClick = onAvatarPickerClick,
            onBackgroundPickerClick = onBackgroundPickerClick,
            onNameChange = onNameChange,
            onUsernameChange = onUsernameChange,
            onBiographyChange = onBiographyChange,
        )
    }
}

@Composable
private fun EditProfileBottomSheetContent(
    state: EditProfileState.Edit,
    onDismissRequest: () -> Unit,
    onDoneClick: () -> Unit,
    onAvatarPickerClick: () -> Unit,
    onBackgroundPickerClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBiographyChange: (String) -> Unit,
) {
    val avatarPainter = rememberProfilePainter(
        previewUri = state.avatarPreviewUri,
        remoteUrl = state.avatarRemoteUrl,
    )
    val backgroundPainter = rememberProfilePainter(
        previewUri = state.backgroundPreviewUri,
        remoteUrl = state.backgroundRemoteUrl,
        fallback = ColorPainter(MaterialTheme.colorScheme.surfaceContainerLow),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SharedEditProfileSheetTopBar(
            state = state,
            onDismissRequest = onDismissRequest,
            onDoneClick = onDoneClick,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
        ) {
            SharedEditProfileFormContent(
                state = state,
                onAvatarPickerClick = onAvatarPickerClick,
                onBackgroundPickerClick = onBackgroundPickerClick,
                onNameChange = onNameChange,
                onUsernameChange = onUsernameChange,
                onBiographyChange = onBiographyChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EditProfileSheetTopBar(
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
                painter = painterResource(me.floow.uikit.R.drawable.current_reply_close_icon),
                contentDescription = stringResource(R.string.profile_edit_sheet_close),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }

        Text(
            text = stringResource(R.string.profile_edit_sheet_title),
            style = LocalTypography.current.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp),
        )

        OutlinedButton(
            onClick = onDoneClick,
            enabled = !state.isSubmitting,
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(min = 54.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
            ),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = stringResource(R.string.profile_edit_sheet_save),
                    style = LocalTypography.current.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun EditProfileMediaCard(
    avatarPainter: Painter,
    backgroundPainter: Painter,
    onAvatarPickerClick: () -> Unit,
    onBackgroundPickerClick: () -> Unit,
) {
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
        androidx.compose.foundation.Image(
            painter = backgroundPainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onBackgroundPickerClick),
        )

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
            text = stringResource(R.string.profile_edit_sheet_change_background),
            style = LocalTypography.current.labelMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 14.dp)
                .clickable(onClick = onBackgroundPickerClick),
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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
                        .clip(NinehedronShape)
                        .border(4.dp, Color.White, NinehedronShape)
                ) {
                    androidx.compose.foundation.Image(
                        painter = avatarPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
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
                    text = stringResource(R.string.profile_edit_sheet_change_avatar),
                    style = LocalTypography.current.bodyMedium,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
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
    previewFocused: Boolean? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Words,
    ),
) {
    val resolvedMaxLines = if (singleLine) 1 else maxOf(minLines, maxLines)
    val colors = MaterialTheme.colorScheme
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val showFloatingLabel = previewFocused ?: isFocused
    val labelOffsetY by animateDpAsState(
        targetValue = if (showFloatingLabel) 0.dp else 13.dp,
        animationSpec = tween(durationMillis = 160),
        label = "editProfileLabelOffsetY",
    )
    val floatingLabelAlpha by animateFloatAsState(
        targetValue = if (showFloatingLabel) 1f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "editProfileFloatingLabelAlpha",
    )
    val inlineLabelAlpha by animateFloatAsState(
        targetValue = if (showFloatingLabel) 0f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "editProfileInlineLabelAlpha",
    )
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
            keyboardOptions = keyboardOptions,
            textStyle = LocalTypography.current.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused && previewFocused == null) {
                        scope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(9.dp))
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
                        if (!showFloatingLabel) {
                            Text(
                                text = title,
                                style = LocalTypography.current.captionMedium,
                                color = titleColor,
                                modifier = Modifier.alpha(inlineLabelAlpha),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            if (!prefix.isBlank()) {
                                Text(
                                    text = prefix,
                                    style = LocalTypography.current.bodyMedium,
                                    color = colors.onSurface,
                                )
                                Spacer(modifier = Modifier.width(2.dp))
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

                    if (showFloatingLabel) {
                        Text(
                            text = title,
                            style = LocalTypography.current.captionMedium,
                            color = titleColor,
                            modifier = Modifier
                                .alpha(floatingLabelAlpha)
                                .padding(start = 12.dp)
                                .offset(y = labelOffsetY - 7.dp)
                                .background(
                                    color = colors.surfaceContainerLow,
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 4.dp)
                                .align(Alignment.TopStart),
                        )
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
        ValidationErrorType.UsernameAlreadyExists -> stringResource(R.string.profile_edit_sheet_username_taken)
        else -> null
    }
}

@Composable
private fun rememberProfilePainter(
    previewUri: String?,
    remoteUrl: String?,
    fallback: Painter = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
): Painter {
    val model = previewUri ?: remoteUrl
    return if (model.isNullOrBlank()) {
        fallback
    } else {
        rememberAsyncImagePainter(model = model)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 376, heightDp = 760)
@Composable
private fun EditProfileBottomSheetPreview() {
    FlowTheme {
        EditProfileBottomSheet(
            state = EditProfileState.Edit(
                name = ValidatedField.Valid("Bogdan"),
                username = ValidatedField.Valid("bogdan_flow"),
                bio = ValidatedField.Valid("Compose preview for edit profile bottom sheet"),
            ),
            onDismissRequest = {},
            onDoneClick = {},
            onAvatarPickerClick = {},
            onBackgroundPickerClick = {},
            onNameChange = {},
            onUsernameChange = {},
            onBiographyChange = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360)
@Composable
private fun EditProfileSheetFieldUnselectedPreview() {
    FlowTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            EditProfileSheetField(
                title = "Имя",
                value = "",
                placeholder = "введите имя",
                isError = false,
                previewFocused = false,
                onValueChange = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360)
@Composable
private fun EditProfileSheetFieldSelectedPreview() {
    FlowTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            EditProfileSheetField(
                title = "Имя",
                value = "Bogdan",
                placeholder = "введите имя",
                isError = false,
                previewFocused = true,
                onValueChange = {},
            )
        }
    }
}
