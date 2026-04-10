package me.floow.shared.profile.uilogic

import me.floow.shared.post.ui.PostScreenModel
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot

data class ProfileActionContext(
    val id: String,
    val shortUsername: String?,
    val avatarUri: String?,
    val backgroundUri: String?,
    val displayName: String?,
    val description: String?,
)

data class ProfileMessageTarget(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
)

fun ProfileScreenState.Success.toActionContext(): ProfileActionContext {
    return ProfileActionContext(
        id = id,
        shortUsername = shortUsername,
        avatarUri = avatarUri,
        backgroundUri = backgroundUri,
        displayName = displayName,
        description = description,
    )
}

fun ProfileActionContext.toEditProfileOverlayData(): EditProfileOverlayData {
    return EditProfileOverlayData(
        name = displayName.orEmpty(),
        username = shortUsername.orEmpty(),
        bio = description.orEmpty(),
        avatarUrl = avatarUri,
        backgroundUrl = backgroundUri,
    )
}

fun ProfileActionContext.shareProfileSlug(): String {
    return shortUsername?.takeIf(String::isNotBlank) ?: id
}

fun ProfileActionContext.toMessageTarget(): ProfileMessageTarget {
    return ProfileMessageTarget(
        userId = id,
        displayName = displayName.orEmpty(),
        avatarUrl = avatarUri,
    )
}

fun ProfileScreenState.Success.findPost(postId: String): ProfilePostItem? {
    return posts.firstOrNull { it.id == postId }
}

fun ProfileScreenState.Success.toPostScreenModel(
    postId: String,
    sourceSnapshot: PostMediaSourceSnapshot?,
): PostScreenModel? {
    val post = findPost(postId) ?: return null
    return PostScreenModel(
        postId = post.id,
        imageUrls = post.viewerUrls.ifEmpty {
            listOfNotNull(post.fullUrl ?: post.previewUrl ?: post.lqUrl)
        },
        mediaTransferSnapshot = sourceSnapshot,
        description = post.description,
        authorId = id,
        authorName = displayName,
        authorUsername = shortUsername,
        authorAvatarUrl = avatarUri,
        category = post.category,
        createdAtLabel = post.createdAtLabel,
        likesCount = post.likesCount,
        commentsCount = post.commentsCount,
        commentersPreview = emptyList(),
        isSelf = isSelf,
    )
}
