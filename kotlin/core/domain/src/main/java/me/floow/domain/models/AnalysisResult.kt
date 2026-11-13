package me.floow.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class AnalysisType {
	PROBLEMATIC_AUTHOR,
	FAVORITE_CATEGORY,
	DISLIKED_CATEGORY,
	FAVORITE_AUTHOR,
	CATEGORY_SATURATION,
	AUTHOR_SATURATION
}

@Serializable
data class AnalysisResult(
	val type: AnalysisType,
	val target: String, // имя автора или категории
	val corrections: Map<String, Float>, // примененные корректировки
	val explanation: String, // объяснение для пользователя
	val timestamp: Long
)