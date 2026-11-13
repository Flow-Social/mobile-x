package me.floow.uikit.components.misc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PostActionsSheetContent(
    canEdit: Boolean,
    editText: String,
    deleteText: String,
    shareText: String,
    retryText: String? = null,
    cancelText: String? = null,
    queueDeleteText: String? = null,
    canDelete: Boolean = canEdit,
    canShare: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit = {},
    onCancel: () -> Unit = onDelete,
    onQueueDelete: () -> Unit = onDelete,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        if (retryText != null) {
            Text(
                text = retryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRetry)
                    .padding(16.dp)
            )
        }
        if (cancelText != null) {
            Text(
                text = cancelText,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCancel)
                    .padding(16.dp)
            )
        }
        if (queueDeleteText != null) {
            Text(
                text = queueDeleteText,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onQueueDelete)
                    .padding(16.dp)
            )
        }
        if (canEdit) {
            Text(
                text = editText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
                    .padding(16.dp)
            )
        }
        if (canDelete) {
            Text(
                text = deleteText,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDelete)
                    .padding(16.dp)
            )
        }
        if (canShare) {
            Text(
                text = shareText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShare)
                    .padding(16.dp)
            )
        }
    }
}
