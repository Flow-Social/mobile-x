package me.floow.uikit.components.media.transfer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter

@Immutable
enum class PostMediaSourceOwner {
	FEED,
	PROFILE,
	POST,
	COMMENTS
}

@Immutable
data class PainterRef(
	val painter: Painter
)

@Immutable
data class PostMediaSourceSnapshot(
	val postId: String,
	val owner: PostMediaSourceOwner,
	val selectedIndex: Int,
	val urls: List<String>,
	val previewUrls: List<String>,
	val paintersByIndex: Map<Int, PainterRef> = emptyMap(),
	val boundsByIndex: Map<Int, Rect> = emptyMap(),
	val createdAtMs: Long = System.currentTimeMillis()
)

interface PostMediaTransferStore {
	fun save(snapshot: PostMediaSourceSnapshot): String
	fun consume(token: String): PostMediaSourceSnapshot?
	fun peek(postId: String): PostMediaSourceSnapshot?
}
