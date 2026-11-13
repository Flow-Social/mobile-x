package me.floow.uikit.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val PRESENCE_ZONE_ID: ZoneId = ZoneId.systemDefault()
private val PRESENCE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal actual fun currentPresenceStatusEpochMillis(): Long = System.currentTimeMillis()

internal actual fun platformFormatPresenceStatusLabel(
	isOnline: Boolean,
	lastSeenAtMillis: Long?,
	nowMillis: Long,
): String {
	val locale = Locale.getDefault()
	if (isOnline) return if (locale.isRussian()) "В сети" else "Online"
	val normalizedLastSeenAt = lastSeenAtMillis?.takeIf { it > 0L }
		?: return if (locale.isRussian()) "Оффлайн" else "Offline"

	val nowDate = Instant.ofEpochMilli(nowMillis).atZone(PRESENCE_ZONE_ID).toLocalDate()
	val seenDateTime = Instant.ofEpochMilli(normalizedLastSeenAt).atZone(PRESENCE_ZONE_ID)
	val seenDate = seenDateTime.toLocalDate()
	val seenTime = seenDateTime.format(PRESENCE_TIME_FORMATTER)
	val daysDiff = max(0L, java.time.temporal.ChronoUnit.DAYS.between(seenDate, nowDate))

	return when {
		daysDiff == 0L -> if (locale.isRussian()) {
			"Был(а) в $seenTime"
		} else {
			"Last seen at $seenTime"
		}
		daysDiff == 1L -> if (locale.isRussian()) {
			"Был(а) вчера в $seenTime"
		} else {
			"Last seen yesterday at $seenTime"
		}
		daysDiff in 2L..6L -> if (locale.isRussian()) {
			"Был(а) на этой неделе"
		} else {
			"Last seen this week"
		}
		isSameLocalMonth(seenDate, nowDate) && daysDiff in 7L..30L -> if (locale.isRussian()) {
			"Был(а) в этом месяце"
		} else {
			"Last seen this month"
		}
		else -> {
			val dateFormatter = DateTimeFormatter.ofPattern(
				if (seenDate.year == nowDate.year) "d MMMM" else "d MMMM yyyy",
				locale,
			)
			val dateLabel = seenDateTime.format(dateFormatter)
			if (locale.isRussian()) {
				"Был(а) $dateLabel в $seenTime"
			} else {
				"Last seen $dateLabel at $seenTime"
			}
		}
	}
}

private fun isSameLocalMonth(left: LocalDate, right: LocalDate): Boolean {
	return left.year == right.year && left.monthValue == right.monthValue
}

private fun Locale.isRussian(): Boolean = language.equals("ru", ignoreCase = true)
