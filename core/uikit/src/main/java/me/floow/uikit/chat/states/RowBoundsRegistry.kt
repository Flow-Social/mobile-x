package me.floow.uikit.chat.states

internal class RowBoundsRegistry(
	private val maxEntries: Int = 500
) {
	private val heightsByMessageId = LinkedHashMap<Long, Int>(maxEntries, 0.75f, true)

	fun putHeight(messageId: Long, rowHeightPx: Int) {
		if (messageId <= 0L || rowHeightPx <= 0) return
		heightsByMessageId[messageId] = rowHeightPx
		while (heightsByMessageId.size > maxEntries) {
			val firstKey = heightsByMessageId.entries.firstOrNull()?.key ?: break
			heightsByMessageId.remove(firstKey)
		}
	}

	fun getHeight(messageId: Long): Int? = heightsByMessageId[messageId]

	fun clear() {
		heightsByMessageId.clear()
	}
}
