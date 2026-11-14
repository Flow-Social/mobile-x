package me.floow.shared.chats.camera

enum class VideoCodecProfile {
    H264_MAIN,
    H264_HIGH
}

data class VideoCircleSpec(
    val targetSidePx: Int = 384,
    val frameRate: Int = 30,
    val videoBitrateBps: Int = 420_000,
    val iFrameIntervalSeconds: Int = 1,
    val codecProfile: VideoCodecProfile = VideoCodecProfile.H264_MAIN,
    val audioBitrateBps: Int = 32_000,
    val audioSampleRateHz: Int = 44_100,
    val audioChannels: Int = 1,
    val maxDurationSeconds: Int = 60,
    val maxFileSizeBytes: Long = 4_000_000L,
)

object VideoCirclePreset {
    val Standard = VideoCircleSpec()
}
