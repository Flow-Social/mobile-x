package me.floow.shared.chats.uilogic.direct

import me.floow.shared.chats.ui.formatChatClockTime

internal fun formatTimeLabel(createdAtMillis: Long): String {
    return formatChatClockTime(createdAtMillis)
}
