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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        if (canEdit) {
            Text(
                text = editText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
                    .padding(16.dp)
            )
            Text(
                text = deleteText,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDelete)
                    .padding(16.dp)
            )
        }
        Text(
            text = shareText,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onShare)
                .padding(16.dp)
        )
    }
}
