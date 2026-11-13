package me.floow.uikit.components.misc.textwrap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

enum class FlowMarkdownType {
	BOLD,
	ITALIC,
	CODE,
	SPOILER,
}

data class FlowMarkdownMatch(
	val start: Int,
	val end: Int,
	val innerText: String,
	val type: FlowMarkdownType,
)

data class FlowLinkMatch(
	val start: Int,
	val end: Int,
	val tag: String,
	val value: String,
)

private val flowCodePattern = Regex("`([^`\n]+)`")
private val flowSpoilerPattern = Regex("\\|\\|([^|\\n]+)\\|\\|")
private val flowBoldPattern = Regex("\\*\\*([^*\\n]+)\\*\\*")
private val flowItalicPattern = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)")

fun findFlowMarkdownMatches(text: String): List<FlowMarkdownMatch> {
	val rawMatches = mutableListOf<FlowMarkdownMatch>()

	flowCodePattern.findAll(text).forEach { m ->
		rawMatches.add(
			FlowMarkdownMatch(
				start = m.range.first,
				end = m.range.last + 1,
				innerText = m.groupValues[1],
				type = FlowMarkdownType.CODE
			)
		)
	}

	flowSpoilerPattern.findAll(text).forEach { m ->
		rawMatches.add(
			FlowMarkdownMatch(
				start = m.range.first,
				end = m.range.last + 1,
				innerText = m.groupValues[1],
				type = FlowMarkdownType.SPOILER
			)
		)
	}

	flowBoldPattern.findAll(text).forEach { m ->
		rawMatches.add(
			FlowMarkdownMatch(
				start = m.range.first,
				end = m.range.last + 1,
				innerText = m.groupValues[1],
				type = FlowMarkdownType.BOLD
			)
		)
	}

	flowItalicPattern.findAll(text).forEach { m ->
		rawMatches.add(
			FlowMarkdownMatch(
				start = m.range.first,
				end = m.range.last + 1,
				innerText = m.groupValues[1],
				type = FlowMarkdownType.ITALIC
			)
		)
	}

	val sorted = rawMatches.sortedWith(compareBy<FlowMarkdownMatch> { it.start }.thenByDescending { it.end - it.start })
	val nonOverlapping = mutableListOf<FlowMarkdownMatch>()
	var lastEnd = -1
	for (m in sorted) {
		if (m.start >= lastEnd) {
			nonOverlapping.add(m)
			lastEnd = m.end
		}
	}
	return nonOverlapping
}

private fun overlaps(start1: Int, end1: Int, start2: Int, end2: Int): Boolean {
	return start1 < end2 && start2 < end1
}

fun findFlowTextLinks(text: String): List<FlowLinkMatch> {
	val matches = mutableListOf<FlowLinkMatch>()
	val postPattern = Regex("https?://[^\\s]+/([a-z0-9_]+)/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
	val generalUrlPattern = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
	val mentionPattern = Regex("@([a-z0-9_]+)", RegexOption.IGNORE_CASE)

	postPattern.findAll(text).forEach { match ->
		matches += FlowLinkMatch(
			start = match.range.first,
			end = match.range.last + 1,
			tag = TEXT_TAG_POST,
			value = "${match.groupValues[1].lowercase()}/${match.groupValues[2]}",
		)
	}

	generalUrlPattern.findAll(text).forEach { match ->
		val start = match.range.first
		val end = match.range.last + 1
		if (matches.none { overlaps(it.start, it.end, start, end) }) {
			matches += FlowLinkMatch(
				start = start,
				end = end,
				tag = TEXT_TAG_URL,
				value = match.value,
			)
		}
	}

	mentionPattern.findAll(text).forEach { match ->
		val start = match.range.first
		val end = match.range.last + 1
		if (matches.none { overlaps(it.start, it.end, start, end) }) {
			matches += FlowLinkMatch(
				start = start,
				end = end,
				tag = TEXT_TAG_PROFILE,
				value = match.groupValues[1].lowercase(),
			)
		}
	}

	return matches.sortedBy { it.start }
}

private fun AnnotatedString.Builder.appendWithLinks(
	segment: String,
	linkColor: Color,
) {
	val links = findFlowTextLinks(segment)
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

fun buildFlowAnnotatedText(
	text: String,
	linkColor: Color = Color(0xFF388E3C),
	isSpoilerRevealed: Boolean = false,
	trailingText: String? = null,
	trailingStyle: TextStyle = TextStyle.Default,
): AnnotatedString {
	val markdownMatches = findFlowMarkdownMatches(text)
	val links = findFlowTextLinks(text)
	if (markdownMatches.isEmpty() && links.isEmpty() && trailingText.isNullOrEmpty()) {
		return AnnotatedString(text)
	}

	return buildAnnotatedString {
		var cursor = 0
		for (m in markdownMatches) {
			if (cursor < m.start) {
				appendWithLinks(text.substring(cursor, m.start), linkColor)
			}

			val spanStyle = when (m.type) {
				FlowMarkdownType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
				FlowMarkdownType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
				FlowMarkdownType.CODE -> SpanStyle(
					fontFamily = FontFamily.Monospace,
					background = Color(0x1F888888)
				)
				FlowMarkdownType.SPOILER -> SpanStyle(
					color = Color.Unspecified,
					background = if (isSpoilerRevealed) Color(0x1F888888) else Color.Transparent
				)
			}

			val startIdx = length
			withStyle(spanStyle) {
				if (m.type == FlowMarkdownType.CODE) {
					append(m.innerText)
					addStringAnnotation(
						tag = TEXT_TAG_CODE,
						annotation = m.innerText,
						start = startIdx,
						end = length,
					)
				} else {
					val innerStart = length
					appendWithLinks(m.innerText, linkColor)
					if (m.type == FlowMarkdownType.SPOILER) {
						addStringAnnotation(
							tag = TEXT_TAG_SPOILER,
							annotation = m.innerText,
							start = innerStart,
							end = length,
						)
					}
				}
			}

			cursor = m.end
		}

		if (cursor < text.length) {
			appendWithLinks(text.substring(cursor), linkColor)
		}

		if (!trailingText.isNullOrEmpty()) {
			val startIdx = length
			withStyle(
				SpanStyle(
					color = trailingStyle.color,
					fontWeight = trailingStyle.fontWeight,
					fontStyle = trailingStyle.fontStyle,
					fontSize = trailingStyle.fontSize,
				)
			) {
				append(trailingText)
			}
			addStringAnnotation(
				tag = TEXT_TAG_TRAILING,
				annotation = trailingText,
				start = startIdx,
				end = length,
			)
		}
	}
}
