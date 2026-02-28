package me.floow.feed.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.floow.domain.models.AnalysisResult
import me.floow.domain.models.AnalysisType

@Composable
fun AnalysisToast(
	analysisResult: AnalysisResult?,
	modifier: Modifier = Modifier
) {
	var isVisible by remember { mutableStateOf(false) }
	
	// Показываем тост при появлении нового анализа
	LaunchedEffect(analysisResult) {
		if (analysisResult != null) {
			isVisible = true
			delay(4000) // Показываем 4 секунды
			isVisible = false
		}
	}
	
	AnimatedVisibility(
		visible = isVisible && analysisResult != null,
		enter = slideInVertically(
			initialOffsetY = { -it },
			animationSpec = tween(300)
		) + fadeIn(animationSpec = tween(300)),
		exit = slideOutVertically(
			targetOffsetY = { -it },
			animationSpec = tween(300)
		) + fadeOut(animationSpec = tween(300)),
		modifier = modifier
	) {
		analysisResult?.let { result ->
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
					.clip(RoundedCornerShape(12.dp))
					.background(MaterialTheme.colorScheme.primaryContainer)
					.padding(16.dp)
			) {
				Column {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Text(
							text = "🧠",
							style = MaterialTheme.typography.titleMedium
						)
						Text(
							text = "Умный анализ",
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onPrimaryContainer
						)
					}
					
					Spacer(modifier = Modifier.height(8.dp))
					
					Text(
						text = result.explanation,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onPrimaryContainer
					)
					
					// Показываем основные корректировки
					if (result.corrections.isNotEmpty()) {
						Spacer(modifier = Modifier.height(8.dp))
						
						val mainCorrections = result.corrections.entries.take(2)
						mainCorrections.forEach { (target, delta) ->
							val displayTarget = if (target.startsWith("category_")) {
								target.removePrefix("category_")
							} else {
								target
							}
							
							Text(
								text = "• $displayTarget: ${if (delta > 0) "+" else ""}${String.format("%.2f", delta)}",
								style = MaterialTheme.typography.bodySmall,
								color = if (delta > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
							)
						}
					}
				}
			}
		}
	}
}

@Preview
@Composable
private fun AnalysisToastPreview() {
	AnalysisToast(
		analysisResult = AnalysisResult(
			type = AnalysisType.PROBLEMATIC_AUTHOR,
			target = "alex_memes",
			corrections = mapOf(
				"alex_memes" to -0.16f,
				"category_MEMES" to 0.05f
			),
			explanation = "Обнаружен проблемный автор: alex_memes. Скипнуто 4/4 постов из разных категорий",
			timestamp = System.currentTimeMillis()
		)
	)
}