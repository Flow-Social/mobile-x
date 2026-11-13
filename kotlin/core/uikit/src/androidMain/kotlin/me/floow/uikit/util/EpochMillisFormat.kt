package me.floow.uikit.util

fun formatEpochMillisToHourMinuteLabel(epochMillis: Long): String =
    FlowTimeFormatters.formatHourMinute(epochMillis)
