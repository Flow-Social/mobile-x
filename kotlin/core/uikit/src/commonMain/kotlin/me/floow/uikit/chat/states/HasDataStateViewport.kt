package me.floow.uikit.chat.states

internal data class ChatViewportCoordinatorSnapshot(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val visibleItemKeys: Set<Any>,
    val visibleMessageIds: Set<Long>,
    val visibleItems: List<me.floow.uikit.chat.model.ChatVisibleItemSnapshot>,
    val viewportAnchorMessageId: Long?,
    val viewportAnchorOffsetPx: Int,
    val visibleReadCandidateId: Long?,
    val isAtBottom: Boolean,
    val firstVisibleDayStartMillis: Long?,
)

internal inline fun <T> traceChatUiSection(name: String, block: () -> T): T {
    return block()
}

internal fun androidx.compose.foundation.lazy.LazyListItemInfo.visibleFraction(
    viewportStart: Int,
    viewportEnd: Int,
): Float {
    if (size <= 0) return 0f
    val itemStart = offset
    val itemEnd = offset + size
    val visibleStart = maxOf(itemStart, viewportStart)
    val visibleEnd = minOf(itemEnd, viewportEnd)
    return ((visibleEnd - visibleStart).coerceAtLeast(0).toFloat() / size.toFloat()).coerceIn(0f, 1f)
}

internal fun androidx.compose.foundation.lazy.LazyListItemInfo.isVisibleEnoughToBeRead(
    viewportStart: Int,
    viewportEnd: Int,
): Boolean {
    if (size <= 0) return false
    val itemStart = offset
    val itemEnd = offset + size
    val visibleStart = maxOf(itemStart, viewportStart)
    val visibleEnd = minOf(itemEnd, viewportEnd)
    val visibleHeight = (visibleEnd - visibleStart).coerceAtLeast(0)
    if (visibleHeight <= 0) return false
    val fraction = visibleHeight.toFloat() / size.toFloat()
    val viewportHeight = (viewportEnd - viewportStart).coerceAtLeast(1)
    val viewportOccupation = visibleHeight.toFloat() / viewportHeight.toFloat()
    return fraction >= 0.6f || viewportOccupation >= 0.6f
}
