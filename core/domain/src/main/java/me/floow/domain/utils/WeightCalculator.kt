package me.floow.domain.utils

import kotlin.math.abs

object WeightCalculator {
	
	fun calculateDelta(currentWeight: Float, baseDelta: Float): Float {
		val absWeight = abs(currentWeight)
		return when {
			absWeight < 0.5f -> baseDelta
			absWeight < 0.8f -> baseDelta * 0.5f
			else -> baseDelta * 0.2f
		}
	}
	
	fun applyWeightLimits(newWeight: Float, isAuthor: Boolean): Float {
		return if (isAuthor) {
			newWeight.coerceIn(-1.0f, 2.0f)
		} else {
			newWeight.coerceIn(-1.0f, 1.0f)
		}
	}
	
	// Базовые дельты из SMART_ALGORITHM_V2.md
	const val LIKE_DELTA = 0.1f
	const val SKIP_DELTA = -0.05f
	const val MASS_CORRECTION_DELTA = 0.04f
	const val RESTORATION_DELTA = 0.05f
	const val SATURATION_PENALTY_DELTA = 0.06f
	
	// Константы для системы контроля перенасыщения
	const val MIN_SKIPS_FOR_SATURATION = 4
	const val MIN_WEIGHT_FOR_SATURATION = 0.6f
	const val SATURATION_ANALYSIS_WINDOW = 10
	const val MAX_CONSECUTIVE_SAME_CATEGORY = 3
	const val LAST_SHOWN_CATEGORIES_HISTORY_SIZE = 5
	
	// Константы для перенасыщения авторов
	const val MIN_AUTHOR_SKIPS_FOR_SATURATION = 3
	const val MIN_AUTHOR_WEIGHT_FOR_SATURATION = 0.8f
	const val AUTHOR_SATURATION_PENALTY_DELTA = 0.08f
	
	// Константы для анализа поведения
	const val PROBLEMATIC_AUTHORS_WINDOW = 10
	const val MIN_AUTHOR_POSTS_FOR_ANALYSIS = 3
	const val MIN_CATEGORIES_FOR_AUTHOR = 2
	const val PROBLEMATIC_AUTHOR_SKIP_THRESHOLD = 0.7f
	
	const val FAVORITE_CATEGORIES_WINDOW = 15
	const val MIN_CATEGORY_POSTS_FOR_ANALYSIS = 5
	const val MIN_AUTHORS_FOR_CATEGORY = 3
	const val FAVORITE_CATEGORY_LIKE_THRESHOLD = 0.8f
	
	const val DISLIKED_CATEGORIES_WINDOW = 12
	const val MIN_DISLIKED_CATEGORY_POSTS = 4
	const val DISLIKED_CATEGORY_SKIP_THRESHOLD = 0.75f
	
	const val FAVORITE_AUTHORS_WINDOW = 15
	const val MIN_FAVORITE_AUTHOR_POSTS = 4
	const val MIN_CATEGORIES_FOR_FAVORITE_AUTHOR = 3
	const val FAVORITE_AUTHOR_LIKE_THRESHOLD = 0.8f
	const val FAVORITE_AUTHOR_BONUS = 0.03f
	
	// Константы для блокировок
	const val PROBLEMATIC_AUTHOR_BLOCK_SWIPES = 30
	const val DISLIKED_CATEGORY_BLOCK_SWIPES = 20
}