package me.floow.uikit.components.text

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
private const val TAG_PROFILE = "profile"
private const val TAG_POST = "post"
private const val BASE_URL = "https://flow-social.github.io"

private data class LinkMatch(
    val start: Int,
    val end: Int,
    val tag: String,
    val value: String
)

@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> }
) {
    val annotated = remember(text, linkColor) {
        buildAnnotatedText(text, linkColor)
    }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { tapOffset ->
                val layout = textLayout ?: return@detectTapGestures
                val offset = layout.getOffsetForPosition(tapOffset)
                annotated.getStringAnnotations(TAG_POST, offset, offset).firstOrNull()?.let { ann ->
                    val parts = ann.item.split("/", limit = 2)
                    if (parts.size == 2) {
                        onPostLinkClick(parts[0], parts[1])
                    }
                    return@detectTapGestures
                }

                annotated.getStringAnnotations(TAG_PROFILE, offset, offset).firstOrNull()?.let { ann ->
                    onProfileTagClick(ann.item)
                }
            }
        },
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { textLayout = it }
    )
}

private fun buildAnnotatedText(text: String, linkColor: Color): AnnotatedString {
    val links = findLinks(text)
    if (links.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var cursor = 0
        links.forEach { link ->
            if (cursor < link.start) {
                append(text.substring(cursor, link.start))
            }
            val segment = text.substring(link.start, link.end)
            withStyle(SpanStyle(color = linkColor)) {
                append(segment)
            }
            addStringAnnotation(
                tag = link.tag,
                annotation = link.value,
                start = length - segment.length,
                end = length
            )
            cursor = link.end
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

private fun findLinks(text: String): List<LinkMatch> {
    val matches = mutableListOf<LinkMatch>()

    val deeplinkPattern = Regex(
        Regex.escape(BASE_URL) + "/([a-z0-9_]+)/([A-Za-z0-9_-]+)",
        RegexOption.IGNORE_CASE
    )
    val mentionPattern = Regex("@([a-z0-9_]+)", RegexOption.IGNORE_CASE)

    deeplinkPattern.findAll(text).forEach { match ->
        val username = match.groupValues[1].lowercase()
        val postId = match.groupValues[2]
        matches += LinkMatch(
            start = match.range.first,
            end = match.range.last + 1,
            tag = TAG_POST,
            value = "$username/$postId"
        )
    }

    mentionPattern.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (matches.any { overlaps(it.start, it.end, start, end) }) return@forEach
        matches += LinkMatch(
            start = start,
            end = end,
            tag = TAG_PROFILE,
            value = match.groupValues[1].lowercase()
        )
    }

    return matches.sortedBy { it.start }
}

private fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
    return aStart < bEnd && bStart < aEnd
}
