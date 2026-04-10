package me.floow.uikit.chat.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun ChatDeleteConfirmationDialog(
	title: String,
	text: String,
	confirmText: String,
	cancelText: String,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = { Text(text) },
		confirmButton = {
			Button(onClick = onConfirm) {
				Text(confirmText)
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(cancelText)
			}
		}
	)
}
