package me.floow.shared.profile.ui.model

import me.floow.shared.profile.uilogic.ProfilePostPendingMutationType
import me.floow.shared.profile.uilogic.ProfilePostSyncState

data class ProfilePostItem(
    val id: String,
    val description: String?,
    val thumbUrl: String? = null,
    val targetUrl: String? = null,
    val lqUrl: String? = thumbUrl,
    val previewUrl: String? = targetUrl,
    val fullUrl: String? = null,
    val viewerUrls: List<String>,
    val previewUrls: List<String>,
    val likesCount: Int,
    val commentsCount: Int,
    val commentersPreview: List<String> = emptyList(),
    val category: String? = null,
    val createdAtLabel: String? = null,
    val createdAtMillis: Long = 0L,
    val optimisticState: String? = null,
    val syncState: ProfilePostSyncState? = null,
    val pendingMutationType: ProfilePostPendingMutationType? = null,
    val isOptimistic: Boolean = false,
    val localPostId: String? = null,
    val remotePostId: String? = null,
    val lastSyncError: String? = null,
)
