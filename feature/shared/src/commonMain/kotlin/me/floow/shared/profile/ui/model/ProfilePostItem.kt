package me.floow.shared.profile.ui.model

data class ProfilePostItem(
    val id: String,
    val description: String?,
    val lqUrl: String?,
    val previewUrl: String?,
    val fullUrl: String?,
    val viewerUrls: List<String>,
    val previewUrls: List<String>,
    val likesCount: Int,
    val commentsCount: Int,
    val category: String? = null,
    val createdAtLabel: String? = null,
    val createdAtMillis: Long = 0L,
)
