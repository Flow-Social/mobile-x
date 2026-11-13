package me.floow.domain.settings

enum class AutoDownloadPreset {
    LOW,
    MEDIUM,
    HIGH,
    DISABLED,
}

data class AutoDownloadConfig(
    val cellularEnabled: Boolean = true,
    val wifiEnabled: Boolean = true,
    val roamingEnabled: Boolean = false,
    val preset: AutoDownloadPreset = AutoDownloadPreset.MEDIUM,
    val maxPhotoBytes: Long = 10L * 1024L * 1024L,
    val maxVideoBytes: Long = 15L * 1024L * 1024L,
    val maxVoiceBytes: Long = 5L * 1024L * 1024L,
)
