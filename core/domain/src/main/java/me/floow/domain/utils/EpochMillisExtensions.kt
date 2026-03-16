package me.floow.domain.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun Long.toLocalDateTimeFromEpochMillis(
	zoneId: ZoneId = ZoneId.systemDefault()
): LocalDateTime {
	return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zoneId)
}
