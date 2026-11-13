package me.floow.uikit.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object FlowTimeFormatters {
    val ruLocale = Locale("ru")
    val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", ruLocale)
    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", ruLocale)
    val shortDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", ruLocale)

    fun formatHourMinute(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (epochMillis <= 0L) return ""
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .format(hourMinute)
    }

    fun formatDayMonth(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (epochMillis <= 0L) return ""
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .format(dayMonth)
    }

    fun formatRelativeTime(
        epochMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (epochMillis <= 0L) return ""
        val targetDateTime = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        val nowDateTime = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        if (targetDateTime.isAfter(nowDateTime)) {
            return targetDateTime.format(dayMonth)
        }

        val daysBetween = ChronoUnit.DAYS.between(targetDateTime.toLocalDate(), nowDateTime.toLocalDate())
        val minutesBetween = ChronoUnit.MINUTES.between(targetDateTime, nowDateTime).coerceAtLeast(0)

        return when {
            daysBetween == 0L -> when {
                minutesBetween < 1L -> "только что"
                minutesBetween < 60L -> "$minutesBetween мин назад"
                minutesBetween < 120L -> "час назад"
                else -> "${minutesBetween / 60L} ч назад"
            }
            daysBetween == 1L -> "вчера"
            daysBetween in 2L..6L -> "$daysBetween дн назад"
            daysBetween in 7L..13L -> "неделю назад"
            targetDateTime.year == nowDateTime.year -> targetDateTime.format(dayMonth)
            else -> {
                val yearsAgo = ChronoUnit.YEARS
                    .between(targetDateTime.toLocalDate(), nowDateTime.toLocalDate())
                    .coerceAtLeast(1)
                val yearsLabel = when (yearsAgo) {
                    1L -> "год назад"
                    2L, 3L, 4L -> "$yearsAgo года назад"
                    else -> "$yearsAgo лет назад"
                }
                "${targetDateTime.format(dayMonth)}, $yearsLabel"
            }
        }
    }
}
