package me.floow.feed.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.domain.models.AnalysisResult
import me.floow.domain.models.AnalysisType

@Composable
fun AnalysisDebugCard(
	analysisResult: AnalysisResult,
	modifier: Modifier = Modifier
) {
	Card(
		modifier = modifier,
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.primaryContainer
		)
	) {
		Column(
			modifier = Modifier.padding(16.dp)
		) {
			Text(
				text = when (analysisResult.type) {
					AnalysisType.PROBLEMATIC_AUTHOR -> "🔍 УМНЫЙ АНАЛИЗ"
					AnalysisType.FAVORITE_CATEGORY -> "🔍 УМНЫЙ АНАЛИЗ"
					AnalysisType.DISLIKED_CATEGORY -> "🔍 УМНЫЙ АНАЛИЗ"
					AnalysisType.FAVORITE_AUTHOR -> "🔍 УМНЫЙ АНАЛИЗ"
					AnalysisType.CATEGORY_SATURATION -> "🔍 УМНЫЙ АНАЛИЗ"
					AnalysisType.AUTHOR_SATURATION -> "🔍 УМНЫЙ АНАЛИЗ"
				},
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold
			)
			
			Text(
				text = analysisResult.explanation,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(vertical = 8.dp)
			)
			
			// Дополнительная информация для перенасыщения
			if (analysisResult.type == AnalysisType.CATEGORY_SATURATION) {
				Text(
					text = "💡 Вывод: Снижаем популярность категории из-за частых скипов",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onPrimaryContainer,
					modifier = Modifier.padding(bottom = 8.dp)
				)
			}
			
			if (analysisResult.type == AnalysisType.AUTHOR_SATURATION) {
				Text(
					text = "💡 Вывод: Снижаем популярность автора из-за частых скипов",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onPrimaryContainer,
					modifier = Modifier.padding(bottom = 8.dp)
				)
			}
			
			// Показываем примененные корректировки
			analysisResult.corrections.forEach { (target, delta) ->
				val displayTarget = if (target.startsWith("category_")) {
					target.removePrefix("category_")
				} else {
					target
				}
				
				Text(
					text = "• $displayTarget: ${if (delta > 0) "+" else ""}${String.format("%.2f", delta)}",
					style = MaterialTheme.typography.bodySmall,
					color = if (delta > 0) Color.Green else Color.Red
				)
			}
		}
	}
}

@Preview
@Composable
private fun AnalysisDebugCardPreview() {
	AnalysisDebugCard(
		analysisResult = AnalysisResult(
			type = AnalysisType.PROBLEMATIC_AUTHOR,
			target = "alex_memes",
			corrections = mapOf(
				"alex_memes" to -0.16f,
				"category_MEMES" to 0.05f,
				"category_TECH" to 0.05f
			),
			explanation = "Обнаружен проблемный автор: alex_memes. Скипнуто 4/4 постов из разных категорий",
			timestamp = System.currentTimeMillis()
		),
		modifier = Modifier.fillMaxWidth()
	)
}