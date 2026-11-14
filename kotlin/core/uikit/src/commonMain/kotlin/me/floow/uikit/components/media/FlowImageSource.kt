package me.floow.uikit.components.media

import androidx.compose.runtime.Immutable

@Immutable
data class FlowImageSource(
    val url: String?,
    val microThumb: String? = null,
) {
    val isEmpty: Boolean
        get() = url.isNullOrBlank() && microThumb.isNullOrBlank()

    companion object {
        val Empty = FlowImageSource(url = null, microThumb = null)

        fun of(url: String?, microThumb: String? = null): FlowImageSource {
            val cleanUrl = url?.trim()?.takeIf { it.isNotEmpty() }
            val cleanThumb = microThumb?.trim()?.takeIf { it.isNotEmpty() }
            return FlowImageSource(cleanUrl, cleanThumb)
        }
    }
}
