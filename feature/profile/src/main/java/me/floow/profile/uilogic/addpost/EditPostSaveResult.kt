package me.floow.profile.uilogic.addpost

data class EditPostSaveResult(
    val postId: String,
    val description: String?,
    val imageUrls: List<String>,
)
