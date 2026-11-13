package me.floow.domain.models

import me.floow.domain.values.ProfileDescription
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername

data class PublicProfile(
    val id: String,
    val name: ProfileName?,
    val username: ProfileUsername?,
    val avatarUrl: String?,
    val avatarThumb: String? = null,
    val backgroundUrl: String?,
    val backgroundThumb: String? = null,
    val backgroundUpdatedAt: Long?,
    val description: ProfileDescription?,
    val totalLikesReceived: Int = 0,
)
