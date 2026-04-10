package me.floow.uikit.theme

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.absoluteValue
import me.floow.domain.models.PostCategories
import me.floow.domain.models.PostCategory

private val knownCategoryColors: Map<String, Color> = mapOf(
	PostCategories.NATURE to Color(0xFF2E7D32),
	PostCategories.FOOD to Color(0xFFF57C00),
	PostCategories.ART to Color(0xFFD81B60),
	PostCategories.TRAVEL to Color(0xFF0288D1),
	PostCategories.TECH to Color(0xFF3949AB),
	PostCategories.MEMES to Color(0xFF8E24AA),
	PostCategories.LIFESTYLE to Color(0xFF00897B),
	PostCategories.SPACE to Color(0xFF5E35B1),
	PostCategories.PETS to Color(0xFF6D4C41),
	PostCategories.SPORT to Color(0xFFC62828),
)

fun PostCategory.toDisplayTag(): String {
	val normalized = trim()
	if (normalized.isBlank()) return "#unknown"
	return "#${normalized.lowercase(Locale.ROOT).replace('_', ' ')}"
}

fun PostCategory.categoryColor(): Color {
	val normalized = trim().uppercase(Locale.ROOT)
	if (normalized.isBlank()) {
		return Color(0xFF546E7A)
	}

	knownCategoryColors[normalized]?.let { return it }

	// Deterministic color for server-defined categories.
	val seed = normalized.hashCode().absoluteValue
	val r = 60 + (seed and 0x7F)
	val g = 60 + ((seed shr 8) and 0x7F)
	val b = 60 + ((seed shr 16) and 0x7F)
	val argb = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
	return Color(argb)
}
