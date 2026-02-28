package me.floow.feed.models

sealed interface RecommendationReason {
	data class Personal(val category: String, val score: Float) : RecommendationReason
	data class FavoriteAuthor(val authorUsername: String, val authorScore: Float) : RecommendationReason
	data class Collaborative(val similarUserType: String) : RecommendationReason
	data class Exploration(val category: String) : RecommendationReason
	data object Random : RecommendationReason
}