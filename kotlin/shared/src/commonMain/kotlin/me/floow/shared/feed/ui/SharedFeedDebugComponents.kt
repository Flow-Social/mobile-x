package me.floow.shared.feed.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.floow.domain.models.AnalysisResult
import kotlin.math.roundToInt

@Composable
internal fun SharedFeedAnalysisToast(
    analysisResult: AnalysisResult?,
    modifier: Modifier = Modifier,
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(analysisResult) {
        if (analysisResult != null) {
            isVisible = true
            delay(4_000)
            isVisible = false
        }
    }

    AnimatedVisibility(
        visible = isVisible && analysisResult != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300),
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300),
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        analysisResult?.let { result ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp),
            ) {
                Column {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "\uD83E\uDDE0",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Умный анализ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = result.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    if (result.corrections.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        result.corrections.entries.take(2).forEach { (target, delta) ->
                            val displayTarget = target.removePrefix("category_")
                            Text(
                                text = "\u2022 $displayTarget: ${formatSignedScore(delta)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (delta > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSignedScore(value: Float): String {
    val prefix = if (value > 0f) "+" else ""
    return prefix + formatScore(value)
}

private fun formatScore(value: Float): String {
    val scaled = (value * 100f).roundToInt() / 100f
    return scaled.toString()
}
