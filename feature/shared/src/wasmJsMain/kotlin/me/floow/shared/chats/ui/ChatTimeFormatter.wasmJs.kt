@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package me.floow.shared.chats.ui

@JsName("Date")
private external class JsDate(value: Double = definedExternally) {
	fun getHours(): Int
	fun getMinutes(): Int
	fun getDate(): Int
	fun getMonth(): Int
	fun getFullYear(): Int

	companion object {
		fun now(): Double
	}
}

private val monthLabels = arrayOf(
	"янв", "фев", "мар", "апр", "мая", "июн",
	"июл", "авг", "сен", "окт", "ноя", "дек",
)

internal actual fun formatChatClockTime(epochMillis: Long): String {
	val date = JsDate(epochMillis.toDouble())
	return "${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}"
}

internal actual fun formatChatDayLabel(epochMillis: Long, nowEpochMillis: Long): String {
	val date = JsDate(epochMillis.toDouble())
	val now = JsDate(nowEpochMillis.toDouble())
	return when {
		date.getFullYear() == now.getFullYear() &&
			date.getMonth() == now.getMonth() &&
			date.getDate() == now.getDate() -> "Сегодня"
		date.getFullYear() == now.getFullYear() &&
			date.getMonth() == now.getMonth() &&
			date.getDate() == now.getDate() - 1 -> "Вчера"
		else -> "${date.getDate()} ${monthLabels.getOrElse(date.getMonth()) { "" }}"
	}
}

internal actual fun currentChatEpochMillis(): Long = JsDate.now().toLong()
