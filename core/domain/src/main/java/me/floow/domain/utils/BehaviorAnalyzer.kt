package me.floow.domain.utils

import me.floow.domain.models.AnalysisResult
import me.floow.domain.models.AnalysisType
import me.floow.domain.models.PostCategory
import me.floow.domain.models.SwipeRecord

class BehaviorAnalyzer(
	private val getCurrentCategoryWeight: (PostCategory) -> Float = { 0f },
	private val getCurrentAuthorWeight: (String) -> Float = { 0f }
) {
	
	fun analyzeRecentBehavior(recentSwipes: List<SwipeRecord>): AnalysisResult? {
		analyzeProblematicAuthors(recentSwipes)?.let { return it }
		
		analyzeCategorySaturation(recentSwipes)?.let { return it }
		
		analyzeAuthorSaturation(recentSwipes)?.let { return it }
		
		analyzeFavoriteCategories(recentSwipes)?.let { return it }
		
		analyzeDislikedCategories(recentSwipes)?.let { return it }
		
		analyzeFavoriteAuthors(recentSwipes)?.let { return it }
		
		return null
	}
	
	private fun analyzeCategorySaturation(swipes: List<SwipeRecord>): AnalysisResult? {
		val recentSwipes = swipes.takeLast(WeightCalculator.SATURATION_ANALYSIS_WINDOW)
		
		val categorySkips = recentSwipes
			.filter { !it.isLiked }
			.groupBy { it.category }
		
		for ((category, skipsList) in categorySkips) {
			if (skipsList.size >= WeightCalculator.MIN_SKIPS_FOR_SATURATION) {
				val categoryWeight = getCurrentCategoryWeight(category)
				
				if (categoryWeight > WeightCalculator.MIN_WEIGHT_FOR_SATURATION) {
					val corrections = mutableMapOf<String, Float>()
					corrections["category_$category"] = -WeightCalculator.SATURATION_PENALTY_DELTA * skipsList.size
					
						return AnalysisResult(
							type = AnalysisType.CATEGORY_SATURATION,
							target = category,
						corrections = corrections,
						explanation = "Обнаружено перенасыщение категорией: $category. Скипнуто ${skipsList.size}/${WeightCalculator.SATURATION_ANALYSIS_WINDOW} постов при высоком рейтинге (${String.format("%.2f", categoryWeight)})",
						timestamp = System.currentTimeMillis()
					)
				}
			}
		}
		
		return null
	}
	
	private fun analyzeAuthorSaturation(swipes: List<SwipeRecord>): AnalysisResult? {
		val recentSwipes = swipes.takeLast(WeightCalculator.SATURATION_ANALYSIS_WINDOW)
		
		val authorSkips = recentSwipes
			.filter { !it.isLiked }
			.groupBy { it.authorUsername }
		
		for ((author, skipsList) in authorSkips) {
			if (skipsList.size >= WeightCalculator.MIN_AUTHOR_SKIPS_FOR_SATURATION) {
				val authorWeight = getCurrentAuthorWeight(author)
				
				if (authorWeight > WeightCalculator.MIN_AUTHOR_WEIGHT_FOR_SATURATION) {
					val corrections = mutableMapOf<String, Float>()
					corrections[author] = -WeightCalculator.AUTHOR_SATURATION_PENALTY_DELTA * skipsList.size
					
					return AnalysisResult(
						type = AnalysisType.AUTHOR_SATURATION,
						target = author,
						corrections = corrections,
						explanation = "Обнаружено перенасыщение автором: $author. Скипнуто ${skipsList.size}/${WeightCalculator.SATURATION_ANALYSIS_WINDOW} постов при высоком рейтинге (${String.format("%.2f", authorWeight)})",
						timestamp = System.currentTimeMillis()
					)
				}
			}
		}
		
		return null
	}
	
	private fun analyzeProblematicAuthors(swipes: List<SwipeRecord>): AnalysisResult? {
		val recentSwipes = swipes.takeLast(WeightCalculator.PROBLEMATIC_AUTHORS_WINDOW)
		
		val authorSwipes = recentSwipes.groupBy { it.authorUsername }
		
		for ((author, authorSwipesList) in authorSwipes) {
			if (authorSwipesList.size >= WeightCalculator.MIN_AUTHOR_POSTS_FOR_ANALYSIS) {
				val categories = authorSwipesList.map { it.category }.toSet()
				if (categories.size >= WeightCalculator.MIN_CATEGORIES_FOR_AUTHOR) {
					val skipCount = authorSwipesList.count { !it.isLiked }
					val skipPercentage = skipCount.toFloat() / authorSwipesList.size
					
					if (skipPercentage >= WeightCalculator.PROBLEMATIC_AUTHOR_SKIP_THRESHOLD) {
						val corrections = mutableMapOf<String, Float>()
						
						corrections[author] = -WeightCalculator.MASS_CORRECTION_DELTA * skipCount
						
						categories.forEach { category ->
							corrections["category_$category"] = WeightCalculator.RESTORATION_DELTA
						}
						
						return AnalysisResult(
							type = AnalysisType.PROBLEMATIC_AUTHOR,
							target = author,
							corrections = corrections,
							explanation = "Обнаружен проблемный автор: $author. Скипнуто $skipCount/${authorSwipesList.size} постов из разных категорий",
							timestamp = System.currentTimeMillis()
						)
					}
				}
			}
		}
		
		return null
	}
	
	private fun analyzeFavoriteCategories(swipes: List<SwipeRecord>): AnalysisResult? {
		val recentSwipes = swipes.takeLast(WeightCalculator.FAVORITE_CATEGORIES_WINDOW)
		
		val categorySwipes = recentSwipes.groupBy { it.category }
		
		for ((category, categorySwipesList) in categorySwipes) {
			if (categorySwipesList.size >= WeightCalculator.MIN_CATEGORY_POSTS_FOR_ANALYSIS) {
				val authors = categorySwipesList.map { it.authorUsername }.toSet()
				if (authors.size >= WeightCalculator.MIN_AUTHORS_FOR_CATEGORY) {
					val likeCount = categorySwipesList.count { it.isLiked }
					val likePercentage = likeCount.toFloat() / categorySwipesList.size
					
					if (likePercentage >= WeightCalculator.FAVORITE_CATEGORY_LIKE_THRESHOLD) {
						val corrections = mutableMapOf<String, Float>()
						
						corrections["category_$category"] = WeightCalculator.MASS_CORRECTION_DELTA * likeCount
						
						authors.forEach { author ->
							corrections[author] = -WeightCalculator.RESTORATION_DELTA
						}
						
						return AnalysisResult(
							type = AnalysisType.FAVORITE_CATEGORY,
							target = category,
							corrections = corrections,
							explanation = "Обнаружена любимая категория: $category. Лайкнуто $likeCount/${categorySwipesList.size} постов от ${authors.size} разных авторов",
							timestamp = System.currentTimeMillis()
						)
					}
				}
			}
		}
		
		return null
	}
	
	private fun analyzeDislikedCategories(swipes: List<SwipeRecord>): AnalysisResult? {
		val recentSwipes = swipes.takeLast(WeightCalculator.DISLIKED_CATEGORIES_WINDOW)
		
		val categorySwipes = recentSwipes.groupBy { it.category }
		
		for ((category, categorySwipesList) in categorySwipes) {
			if (categorySwipesList.size >= WeightCalculator.MIN_DISLIKED_CATEGORY_POSTS) {
				val authors = categorySwipesList.map { it.authorUsername }.toSet()
				if (authors.size >= WeightCalculator.MIN_AUTHORS_FOR_CATEGORY) {
					val skipCount = categorySwipesList.count { !it.isLiked }
					val skipPercentage = skipCount.toFloat() / categorySwipesList.size
					
					if (skipPercentage >= WeightCalculator.DISLIKED_CATEGORY_SKIP_THRESHOLD) {
						val corrections = mutableMapOf<String, Float>()
						
						corrections["category_$category"] = -WeightCalculator.MASS_CORRECTION_DELTA * skipCount
						
						authors.forEach { author ->
							corrections[author] = WeightCalculator.RESTORATION_DELTA
						}
						
						return AnalysisResult(
							type = AnalysisType.DISLIKED_CATEGORY,
							target = category,
							corrections = corrections,
							explanation = "Обнаружена нелюбимая категория: $category. Скипнуто $skipCount/${categorySwipesList.size} постов от ${authors.size} разных авторов",
							timestamp = System.currentTimeMillis()
						)
					}
				}
			}
		}
		
		return null
	}
	
	private fun analyzeFavoriteAuthors(swipes: List<SwipeRecord>): AnalysisResult? {
		val recentSwipes = swipes.takeLast(WeightCalculator.FAVORITE_AUTHORS_WINDOW)
		
		val authorSwipes = recentSwipes.groupBy { it.authorUsername }
		
		for ((author, authorSwipesList) in authorSwipes) {
			if (authorSwipesList.size >= WeightCalculator.MIN_FAVORITE_AUTHOR_POSTS) {
				val categories = authorSwipesList.map { it.category }.toSet()
				if (categories.size >= WeightCalculator.MIN_CATEGORIES_FOR_FAVORITE_AUTHOR) {
					val likeCount = authorSwipesList.count { it.isLiked }
					val likePercentage = likeCount.toFloat() / authorSwipesList.size
					
					if (likePercentage >= WeightCalculator.FAVORITE_AUTHOR_LIKE_THRESHOLD) {
						val corrections = mutableMapOf<String, Float>()
						
						corrections[author] = WeightCalculator.FAVORITE_AUTHOR_BONUS
						
						categories.forEach { category ->
							corrections["category_$category"] = -WeightCalculator.RESTORATION_DELTA
						}
						
						return AnalysisResult(
							type = AnalysisType.FAVORITE_AUTHOR,
							target = author,
							corrections = corrections,
							explanation = "Обнаружен любимый автор: $author. Лайкнуто $likeCount/${authorSwipesList.size} постов из разных категорий",
							timestamp = System.currentTimeMillis()
						)
					}
				}
			}
		}
		
		return null
	}
}
