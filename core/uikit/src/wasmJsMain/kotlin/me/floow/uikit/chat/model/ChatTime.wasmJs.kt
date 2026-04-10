package me.floow.uikit.chat.model

import kotlin.js.JsName

@JsName("Date")
private external class JsDate {
	constructor()
	constructor(value: Double)
	constructor(
		year: Int,
		month: Int,
		date: Int,
		hours: Int,
		minutes: Int,
		seconds: Int,
		ms: Int
	)

	fun getTime(): Double
	fun getFullYear(): Int
	fun getMonth(): Int
	fun getDate(): Int
	fun getHours(): Int
	fun getMinutes(): Int

	companion object {
		fun now(): Double
	}
}

internal actual fun platformCurrentChatTimeMillis(): Long = JsDate.now().toLong()

internal actual fun platformChatLocalDayStartMillis(epochMillis: Long): Long {
	val date = JsDate(epochMillis.toDouble())
	return JsDate(
		year = date.getFullYear(),
		month = date.getMonth(),
		date = date.getDate(),
		hours = 0,
		minutes = 0,
		seconds = 0,
		ms = 0
	).getTime().toLong()
}

internal actual fun platformFormatChatClockTime(epochMillis: Long): String {
	val date = JsDate(epochMillis.toDouble())
	val hours = date.getHours().toString().padStart(2, '0')
	val minutes = date.getMinutes().toString().padStart(2, '0')
	return "$hours:$minutes"
}

internal actual fun platformIsChatLocalToday(dayStartMillis: Long): Boolean {
	return platformChatLocalDayStartMillis(platformCurrentChatTimeMillis()) == dayStartMillis
}

internal actual fun platformFormatChatDayLabel(dayStartMillis: Long): String {
	val date = JsDate(dayStartMillis.toDouble())
	val day = date.getDate().toString().padStart(2, '0')
	val month = (date.getMonth() + 1).toString().padStart(2, '0')
	val year = (date.getFullYear() % 100).toString().padStart(2, '0')
	return "$day.$month.$year"
}
