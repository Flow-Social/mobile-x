package me.floow.domain.models

import me.floow.domain.values.ProfileDescription
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername

data class PublicProfile(
    val id: String,
    val name: ProfileName?,
    val username: ProfileUsername?,
    val avatarUrl: String?,
    val backgroundUrl: String?,
    val backgroundUpdatedAt: Long?,
    val description: ProfileDescription?,
    val totalLikesReceived: Int = 0,
)
