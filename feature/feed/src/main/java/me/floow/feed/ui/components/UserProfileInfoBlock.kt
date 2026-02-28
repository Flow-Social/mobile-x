package me.floow.feed.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.domain.models.PostCategories
import me.floow.domain.models.UserProfile
import me.floow.uikit.theme.toDisplayTag

@Composable
internal fun UserProfileInfoBlock(
	userProfile: UserProfile,
	lastSwipeInfo: String?,
	modifier: Modifier = Modifier
) {
	Surface(
		color = MaterialTheme.colorScheme.surfaceVariant,
		shape = RoundedCornerShape(8.dp),
		modifier = modifier
			.fillMaxWidth()
			.height(200.dp)
	) {
		Column(
			modifier = Modifier
				.padding(12.dp)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = "📊 Debug: Твой профиль",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.Medium
			)

			Text(
				text = "Общее: ${userProfile.totalSwipes} свайпа | Стадия: ${getUserStage(userProfile)}",
				style = MaterialTheme.typography.bodySmall,
				fontFamily = FontFamily.Monospace
			)

			Spacer(modifier = Modifier.height(4.dp))

			Text(
				text = "📂 Категории:",
				style = MaterialTheme.typography.bodySmall,
				fontWeight = FontWeight.Medium
			)

			val categories = (userProfile.categoryScores.keys + userProfile.seenCategories).toSet().ifEmpty {
				PostCategories.defaults.toSet()
			}

			categories.sorted().forEach { category ->
				val score = userProfile.categoryScores[category] ?: 0f
				val status = when {
					category !in userProfile.seenCategories -> "(не видел)"
					score > 0.3f -> "(топ)"
					score < -0.1f -> "(не нравится)"
					else -> ""
				}

				Text(
					text = "• ${getCategoryEmoji(category)} ${category.toDisplayTag()}: ${String.format("%.2f", score)} $status",
					style = MaterialTheme.typography.bodySmall,
					fontFamily = FontFamily.Monospace
				)
			}

			Spacer(modifier = Modifier.height(4.dp))

			Text(
				text = "👥 Авторы (топ-5):",
				style = MaterialTheme.typography.bodySmall,
				fontWeight = FontWeight.Medium
			)

			val topAuthors = userProfile.authorScores
				.toList()
				.sortedByDescending { it.second }
				.take(5)

			if (topAuthors.isNotEmpty()) {
				topAuthors.forEach { (author, score) ->
					val isFavorite = score > 0.7f
					val favoriteIcon = if (isFavorite) " ⭐ (любимый)" else ""

					Text(
						text = "• $author: ${String.format("%.2f", score)}$favoriteIcon",
						style = MaterialTheme.typography.bodySmall,
						fontFamily = FontFamily.Monospace
					)
				}
			} else {
				Text(
					text = "• Пока нет данных",
					style = MaterialTheme.typography.bodySmall,
					fontFamily = FontFamily.Monospace
				)
			}

			lastSwipeInfo?.let { swipeInfo ->
				Spacer(modifier = Modifier.height(4.dp))

				Text(
					text = "🎯 Последний свайп:",
					style = MaterialTheme.typography.bodySmall,
					fontWeight = FontWeight.Medium
				)

				Text(
					text = swipeInfo,
					style = MaterialTheme.typography.bodySmall,
					fontFamily = FontFamily.Monospace
				)
			}
		}
	}
}

private fun getCategoryEmoji(categoryCode: String): String {
	return when (categoryCode) {
		PostCategories.NATURE -> "🌿"
		PostCategories.FOOD -> "🍕"
		PostCategories.ART -> "🎨"
		PostCategories.TRAVEL -> "✈️"
		PostCategories.TECH -> "💻"
		PostCategories.MEMES -> "😂"
		PostCategories.LIFESTYLE -> "🏃"
		PostCategories.SPACE -> "🚀"
		PostCategories.PETS -> "🐾"
		PostCategories.SPORT -> "⚽️"
		else -> "🏷️"
	}
}

private fun getUserStage(userProfile: UserProfile): String {
	return when {
		userProfile.isNewUser() -> "Новичок"
		userProfile.isDevelopingUser() -> "Развивающийся"
		userProfile.isMatureUser() -> "Опытный"
		else -> "Неизвестно"
	}
}

@Preview
@Composable
private fun UserProfileInfoBlockPreview() {
	UserProfileInfoBlock(
		userProfile = UserProfile(
			categoryScores = mapOf(
				PostCategories.NATURE to 0.45f,
				PostCategories.FOOD to 0.25f,
				PostCategories.ART to 0.15f,
				PostCategories.TRAVEL to 0.05f,
				PostCategories.TECH to -0.10f,
				PostCategories.MEMES to -0.15f
			),
			authorScores = mapOf(
				"anna_cats" to 0.80f,
				"petr_travel" to 0.45f,
				"maria_art" to 0.25f,
				"chef_ivan" to 0.15f,
				"alex_memes" to -0.20f
			),
			seenCategories = setOf(
				PostCategories.NATURE,
				PostCategories.FOOD,
				PostCategories.ART,
				PostCategories.TRAVEL,
				PostCategories.TECH,
				PostCategories.MEMES
			),
			totalSwipes = 23
		),
		lastSwipeInfo = "ЛАЙК → 🌿 NATURE +0.10, anna_cats +0.10"
	)
}

@Preview
@Composable
private fun UserProfileInfoBlockPreview_NewUser() {
	UserProfileInfoBlock(
		userProfile = UserProfile(
			categoryScores = emptyMap(),
			authorScores = emptyMap(),
			seenCategories = emptySet(),
			totalSwipes = 0
		),
		lastSwipeInfo = null
	)
}
