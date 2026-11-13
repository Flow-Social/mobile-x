package me.floow.shared.chats.ui

import java.time.Instant
import java.time.ZoneId
import me.floow.uikit.util.FlowTimeFormatters

private val chatTimeFormatter = FlowTimeFormatters.hourMinute
private val chatDayFormatter = FlowTimeFormatters.shortDayMonth

internal actual fun formatChatClockTime(epochMillis: Long): String {
	return FlowTimeFormatters.formatHourMinute(epochMillis)
}

internal actual fun formatChatDayLabel(epochMillis: Long, nowEpochMillis: Long): String {
	val zoneId = ZoneId.systemDefault()
	val messageDate = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
	val nowDate = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
	return when (messageDate) {
		nowDate -> "Сегодня"
		nowDate.minusDays(1) -> "Вчера"
		else -> Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(chatDayFormatter)
	}
}

internal actual fun currentChatEpochMillis(): Long = System.currentTimeMillis()
