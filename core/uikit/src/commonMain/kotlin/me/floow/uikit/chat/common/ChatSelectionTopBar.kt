package me.floow.uikit.chat.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.chat_selection_copy_icon
import flow.core.uikit.generated.resources.chat_selection_delete_icon
import org.jetbrains.compose.resources.painterResource

@Composable
fun ChatSelectionTopBar(
	selectedCount: Int,
	canCopy: Boolean,
	canDelete: Boolean,
	onCloseClick: () -> Unit,
	onCopyClick: () -> Unit,
	onDeleteClick: () -> Unit,
	selectedCountLabel: String = "Выбрано: $selectedCount",
	closeContentDescription: String = "Закрыть выбор",
	copyContentDescription: String = "Скопировать",
	deleteContentDescription: String = "Удалить",
	dividerColor: androidx.compose.ui.graphics.Color? = null,
	modifier: Modifier = Modifier,
) {
	Column(modifier = modifier) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(TopAppBarDefaults.TopAppBarExpandedHeight)
				.padding(horizontal = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(onClick = onCloseClick) {
				Icon(
					imageVector = Icons.Default.Close,
					contentDescription = closeContentDescription,
				)
			}

			Text(
				text = selectedCountLabel,
				style = MaterialTheme.typography.titleMedium.copy(
					fontSize = 18.sp,
					fontWeight = FontWeight.Medium,
				),
				modifier = Modifier.weight(1f),
			)

			IconButton(
				onClick = onCopyClick,
				enabled = canCopy,
			) {
				Icon(
					painter = painterResource(Res.drawable.chat_selection_copy_icon),
					contentDescription = copyContentDescription,
					modifier = Modifier.size(26.dp),
				)
			}

			IconButton(
				onClick = onDeleteClick,
				enabled = canDelete,
			) {
				Icon(
					painter = painterResource(Res.drawable.chat_selection_delete_icon),
					contentDescription = deleteContentDescription,
					modifier = Modifier.size(26.dp),
				)
			}
		}

		HorizontalDivider(color = dividerColor ?: MaterialTheme.colorScheme.outlineVariant)
	}
}
