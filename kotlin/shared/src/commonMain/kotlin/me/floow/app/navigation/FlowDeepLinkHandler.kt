package me.floow.app.navigation

import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object FlowDeepLinkHandler {
    private val _deepLinks = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val deepLinks = _deepLinks.asSharedFlow()

    fun openUri(uriString: String) {
        _deepLinks.tryEmit(uriString)
    }
}

fun handleOpenUrl(urlString: String) {
    FlowDeepLinkHandler.openUri(urlString)
}

fun handleFlowDeepLink(
    uriString: String,
    navController: NavHostController,
    onOpenSubscreen: ((FlowSubscreen) -> Unit)? = null,
) {
    val clean = uriString.trim()
    if (clean.isBlank()) return

    val pathWithQuery = when {
        clean.startsWith("floow://", ignoreCase = true) -> clean.substring("floow://".length)
        clean.startsWith("me.floow.app://", ignoreCase = true) -> clean.substring("me.floow.app://".length)
        clean.startsWith("https://floow.me/", ignoreCase = true) -> clean.substring("https://floow.me/".length)
        clean.startsWith("https://api.floow.me/", ignoreCase = true) -> clean.substring("https://api.floow.me/".length)
        else -> return
    }

    val path = pathWithQuery.substringBefore("?").trim('/')
    val segments = path.split('/').filter(String::isNotBlank)
    if (segments.isEmpty()) return

    when (segments[0].lowercase()) {
        "chat", "chats", "c" -> {
            val convId = segments.getOrNull(1)?.toLongOrNull()
            if (convId != null) {
                if (onOpenSubscreen != null) {
                    onOpenSubscreen(FlowSubscreen.DirectChat(conversationId = convId))
                } else {
                    navController.navigate(DirectChatDestination(conversationId = convId))
                }
            }
        }
        "post", "posts", "p" -> {
            val postId = segments.getOrNull(1)
            if (!postId.isNullOrBlank()) {
                if (onOpenSubscreen != null) {
                    onOpenSubscreen(FlowSubscreen.PostDetail(postId = postId, presentation = PostPresentation.FullScreen))
                } else {
                    navController.navigate(PostDestination(postId = postId, presentation = PostPresentation.FullScreen))
                }
            }
        }
        "profile", "u", "user" -> {
            val userId = segments.getOrNull(1) ?: "self"
            if (onOpenSubscreen != null) {
                onOpenSubscreen(FlowSubscreen.Profile(userId = userId))
            } else {
                navController.navigate(ProfileDestination(userId = userId))
            }
        }
        "comments" -> {
            val postId = segments.getOrNull(1)
            if (!postId.isNullOrBlank()) {
                if (onOpenSubscreen != null) {
                    onOpenSubscreen(FlowSubscreen.Comments(postId = postId))
                } else {
                    navController.navigate(CommentsDestination(postId = postId))
                }
            }
        }
        "replies" -> {
            if (onOpenSubscreen != null) {
                onOpenSubscreen(FlowSubscreen.Replies)
            } else {
                navController.navigate(RepliesDestination)
            }
        }
        "search" -> {
            if (onOpenSubscreen != null) {
                onOpenSubscreen(FlowSubscreen.SearchUsers)
            } else {
                navController.navigate(SearchUsersDestination)
            }
        }
        "settings" -> {
            if (onOpenSubscreen != null) {
                onOpenSubscreen(FlowSubscreen.Settings)
            } else {
                navController.navigate(SettingsDestination)
            }
        }
    }
}
