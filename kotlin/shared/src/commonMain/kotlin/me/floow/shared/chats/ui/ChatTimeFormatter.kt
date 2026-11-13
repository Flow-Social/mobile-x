package me.floow.shared.chats.ui

internal expect fun formatChatClockTime(epochMillis: Long): String

internal expect fun formatChatDayLabel(epochMillis: Long, nowEpochMillis: Long): String

internal expect fun currentChatEpochMillis(): Long
