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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.floow.domain.models.AnalysisResult
import me.floow.domain.models.AnalysisType
import me.floow.domain.models.PostCategories
import me.floow.domain.models.UserProfile
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

@Composable
internal fun SharedFeedUserProfileInfoBlock(
    userProfile: UserProfile,
    lastSwipeInfo: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "\uD83D\uDCCA Debug: Твой профиль",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = "Общее: ${userProfile.totalSwipes} свайпа | Стадия: ${userProfileStage(userProfile)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "\uD83D\uDCC2 Категории:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
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
                    text = "\u2022 ${categoryEmoji(category)} ${displayTag(category)}: ${formatScore(score)} $status",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "\uD83D\uDC65 Авторы (топ-5):",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )

            val topAuthors = userProfile.authorScores
                .toList()
                .sortedByDescending { it.second }
                .take(5)

            if (topAuthors.isNotEmpty()) {
                topAuthors.forEach { (author, score) ->
                    val favoriteSuffix = if (score > 0.7f) " ⭐ (любимый)" else ""
                    Text(
                        text = "\u2022 $author: ${formatScore(score)}$favoriteSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                Text(
                    text = "\u2022 Пока нет данных",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            lastSwipeInfo?.let { swipeInfo ->
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "\uD83C\uDFAF Последний свайп:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = swipeInfo,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
internal fun SharedFeedAnalysisDebugCard(
    analysisResult: AnalysisResult,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = when (analysisResult.type) {
                    AnalysisType.PROBLEMATIC_AUTHOR,
                    AnalysisType.FAVORITE_CATEGORY,
                    AnalysisType.DISLIKED_CATEGORY,
                    AnalysisType.FAVORITE_AUTHOR,
                    AnalysisType.CATEGORY_SATURATION,
                    AnalysisType.AUTHOR_SATURATION,
                    -> "🔍 УМНЫЙ АНАЛИЗ"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = analysisResult.explanation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (analysisResult.type == AnalysisType.CATEGORY_SATURATION) {
                Text(
                    text = "💡 Вывод: Снижаем популярность категории из-за частых скипов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (analysisResult.type == AnalysisType.AUTHOR_SATURATION) {
                Text(
                    text = "💡 Вывод: Снижаем популярность автора из-за частых скипов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            analysisResult.corrections.forEach { (target, delta) ->
                val displayTarget = target.removePrefix("category_")
                Text(
                    text = "\u2022 $displayTarget: ${formatSignedScore(delta)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (delta > 0f) Color.Green else Color.Red,
                )
            }
        }
    }
}

private fun formatSignedScore(value: Float): String {
    val prefix = if (value > 0f) "+" else ""
    return prefix + formatScore(value)
}

private fun displayTag(category: String): String {
    val normalized = category.trim()
    if (normalized.isBlank()) return "#unknown"
    return "#${normalized.lowercase().replace('_', ' ')}"
}

private fun formatScore(value: Float): String {
    val scaled = (value * 100f).roundToInt() / 100f
    return scaled.toString()
}

private fun categoryEmoji(categoryCode: String): String {
    return when (categoryCode) {
        PostCategories.NATURE -> "\uD83C\uDF3F"
        PostCategories.FOOD -> "\uD83C\uDF55"
        PostCategories.ART -> "\uD83C\uDFA8"
        PostCategories.TRAVEL -> "✈️"
        PostCategories.TECH -> "\uD83D\uDCBB"
        PostCategories.MEMES -> "\uD83D\uDE02"
        PostCategories.LIFESTYLE -> "\uD83C\uDFC3"
        PostCategories.SPACE -> "\uD83D\uDE80"
        PostCategories.PETS -> "\uD83D\uDC3E"
        PostCategories.SPORT -> "⚽️"
        else -> "\uD83C\uDFF7️"
    }
}

private fun userProfileStage(userProfile: UserProfile): String {
    return when {
        userProfile.isNewUser() -> "Новичок"
        userProfile.isDevelopingUser() -> "Развивающийся"
        userProfile.isMatureUser() -> "Опытный"
        else -> "Неизвестно"
    }
}
