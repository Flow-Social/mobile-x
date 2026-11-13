package me.floow.shared.post.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

import me.floow.uikit.components.misc.textwrap.TEXT_TAG_CODE
import me.floow.uikit.components.misc.textwrap.TEXT_TAG_POST
import me.floow.uikit.components.misc.textwrap.TEXT_TAG_PROFILE
import me.floow.uikit.components.misc.textwrap.TEXT_TAG_SPOILER
import me.floow.uikit.components.misc.textwrap.TEXT_TAG_TRAILING
import me.floow.uikit.components.misc.textwrap.TEXT_TAG_URL
import me.floow.uikit.components.misc.textwrap.TextWrap
import me.floow.uikit.components.misc.textwrap.TextWrapObstacleAlignment
import me.floow.uikit.components.misc.textwrap.buildFlowAnnotatedText
import me.floow.uikit.components.misc.textwrap.findFlowMarkdownMatches
import me.floow.uikit.components.misc.textwrap.findFlowTextLinks

const val TAG_PROFILE = TEXT_TAG_PROFILE
const val TAG_POST = TEXT_TAG_POST
const val TAG_TRAILING = TEXT_TAG_TRAILING
const val TAG_SPOILER = TEXT_TAG_SPOILER
const val TAG_CODE = TEXT_TAG_CODE
const val TAG_URL = TEXT_TAG_URL

@Composable
fun SharedLinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    trailingText: String? = null,
    trailingStyle: TextStyle = style.copy(color = linkColor, fontWeight = FontWeight.SemiBold),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    onUrlClick: ((String) -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null,
    onTextClick: (() -> Unit)? = null,
) {
    var isSpoilerRevealed by remember(text) { mutableStateOf(false) }
    val annotated = remember(text, linkColor, trailingText, trailingStyle, isSpoilerRevealed) {
        buildAnnotatedPostText(
            text = text,
            linkColor = linkColor,
            trailingText = trailingText,
            trailingStyle = trailingStyle,
            isSpoilerRevealed = isSpoilerRevealed,
        )
    }

    TextWrap(
        text = annotated,
        modifier = modifier,
        obstacleAlignment = TextWrapObstacleAlignment.BottomEnd,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        isSpoilerRevealed = isSpoilerRevealed,
        onRevealSpoiler = { isSpoilerRevealed = true },
        onProfileTagClick = onProfileTagClick,
        onPostLinkClick = onPostLinkClick,
        onUrlClick = onUrlClick,
        onTrailingClick = onTrailingClick,
        onTextClick = onTextClick,
        obstacleContent = {},
    )
}

internal enum class PostMarkdownType {
    BOLD,
    ITALIC,
    CODE,
    SPOILER,
}

internal data class PostMarkdownMatch(
    val start: Int,
    val end: Int,
    val innerText: String,
    val type: PostMarkdownType,
)

private val postCodePattern = Regex("`([^`\n]+)`")
private val postSpoilerPattern = Regex("\\|\\|([^|\\n]+)\\|\\|")
private val postBoldPattern = Regex("\\*\\*([^*\\n]+)\\*\\*")
private val postItalicPattern = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)")

internal fun findMarkdownMatches(text: String): List<PostMarkdownMatch> {
    val rawMatches = mutableListOf<PostMarkdownMatch>()

    postCodePattern.findAll(text).forEach { m ->
        rawMatches.add(
            PostMarkdownMatch(
                start = m.range.first,
                end = m.range.last + 1,
                innerText = m.groupValues[1],
                type = PostMarkdownType.CODE
            )
        )
    }

    postSpoilerPattern.findAll(text).forEach { m ->
        rawMatches.add(
            PostMarkdownMatch(
                start = m.range.first,
                end = m.range.last + 1,
                innerText = m.groupValues[1],
                type = PostMarkdownType.SPOILER
            )
        )
    }

    postBoldPattern.findAll(text).forEach { m ->
        rawMatches.add(
            PostMarkdownMatch(
                start = m.range.first,
                end = m.range.last + 1,
                innerText = m.groupValues[1],
                type = PostMarkdownType.BOLD
            )
        )
    }

    postItalicPattern.findAll(text).forEach { m ->
        rawMatches.add(
            PostMarkdownMatch(
                start = m.range.first,
                end = m.range.last + 1,
                innerText = m.groupValues[1],
                type = PostMarkdownType.ITALIC
            )
        )
    }

    val sorted = rawMatches.sortedWith(compareBy<PostMarkdownMatch> { it.start }.thenByDescending { it.end - it.start })
    val nonOverlapping = mutableListOf<PostMarkdownMatch>()
    var lastEnd = -1
    for (m in sorted) {
        if (m.start >= lastEnd) {
            nonOverlapping.add(m)
            lastEnd = m.end
        }
    }
    return nonOverlapping
}

private fun AnnotatedString.Builder.appendWithLinks(
    segment: String,
    linkColor: Color,
) {
    val links = findPostLinks(segment)
    if (links.isEmpty()) {
        append(segment)
        return
    }
    var cursor = 0
    links.forEach { link ->
        if (cursor < link.start) {
            append(segment.substring(cursor, link.start))
        }
        val linkText = segment.substring(link.start, link.end)
        val startIndex = length
        withStyle(SpanStyle(color = linkColor)) {
            append(linkText)
        }
        addStringAnnotation(
            tag = link.tag,
            annotation = link.value,
            start = startIndex,
            end = length,
        )
        cursor = link.end
    }
    if (cursor < segment.length) {
        append(segment.substring(cursor))
    }
}

fun buildAnnotatedPostText(
    text: String,
    linkColor: Color,
    trailingText: String? = null,
    trailingStyle: TextStyle = TextStyle.Default,
    isSpoilerRevealed: Boolean = false,
): AnnotatedString {
    return buildFlowAnnotatedText(
        text = text,
        linkColor = linkColor,
        isSpoilerRevealed = isSpoilerRevealed,
        trailingText = trailingText,
        trailingStyle = trailingStyle,
    )
}

internal data class LinkMatch(
    val start: Int,
    val end: Int,
    val tag: String,
    val value: String,
)

internal fun findPostLinks(text: String): List<LinkMatch> {
    return findFlowTextLinks(text).map { LinkMatch(it.start, it.end, it.tag, it.value) }
}
