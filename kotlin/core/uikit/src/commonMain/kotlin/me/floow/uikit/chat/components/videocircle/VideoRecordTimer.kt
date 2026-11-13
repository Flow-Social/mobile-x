package me.floow.uikit.chat.components.videocircle

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun VideoRecordTimer(
    elapsed: String,
    maxDuration: String,
    modifier: Modifier = Modifier,
    isSwitchingCamera: Boolean = false,
) {
    Text(
        text = if (isSwitchingCamera) "Смена…" else "$elapsed / $maxDuration",
        color = Color(0xFF1C1C1E),
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}
