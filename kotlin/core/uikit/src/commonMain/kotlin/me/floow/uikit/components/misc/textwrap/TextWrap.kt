package me.floow.uikit.components.misc.textwrap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

const val TEXT_TAG_PROFILE = "profile"
const val TEXT_TAG_POST = "post"
const val TEXT_TAG_URL = "url"
const val TEXT_TAG_SPOILER = "spoiler"
const val TEXT_TAG_CODE = "code"
const val TEXT_TAG_TRAILING = "trailing"

@Composable
fun TextWrap(
	text: AnnotatedString,
	modifier: Modifier = Modifier,
	obstacleAlignment: TextWrapObstacleAlignment = TextWrapObstacleAlignment.BottomEnd,
	forcedObstacleOffset: IntOffset = IntOffset.Zero,
	color: Color = Color.Unspecified,
	fontSize: TextUnit = TextUnit.Unspecified,
	fontStyle: FontStyle? = null,
	fontWeight: FontWeight? = null,
	fontFamily: FontFamily? = null,
	letterSpacing: TextUnit = TextUnit.Unspecified,
	textDecoration: TextDecoration? = null,
	textAlign: TextAlign = TextAlign.Unspecified,
	lineHeight: TextUnit = TextUnit.Unspecified,
	softWrap: Boolean = true,
	maxLines: Int = Int.MAX_VALUE,
	overflow: TextOverflow = TextOverflow.Clip,
	style: TextStyle = LocalTextStyle.current,
	isSpoilerRevealed: Boolean = false,
	onRevealSpoiler: (() -> Unit)? = null,
	onProfileTagClick: ((String) -> Unit)? = null,
	onPostLinkClick: ((String, String) -> Unit)? = null,
	onUrlClick: ((String) -> Unit)? = null,
	onTrailingClick: (() -> Unit)? = null,
	onTextClick: (() -> Unit)? = null,
	obstacleContent: @Composable () -> Unit = {},
) {
	val textColor = color.takeOrElse {
		style.color.takeOrElse {
			LocalContentColor.current
		}
	}

	TextWrapLayout(
		text = text,
		style = style,
		modifier = modifier,
		obstacleAlignment = obstacleAlignment,
		color = textColor,
		fontSize = fontSize,
		fontStyle = fontStyle,
		fontWeight = fontWeight,
		fontFamily = fontFamily,
		letterSpacing = letterSpacing,
		textDecoration = textDecoration,
		textAlign = textAlign,
		lineHeight = lineHeight,
		softWrap = softWrap,
		maxLines = maxLines,
		overflow = overflow,
		isSpoilerRevealed = isSpoilerRevealed,
		onRevealSpoiler = onRevealSpoiler,
		onProfileTagClick = onProfileTagClick,
		onPostLinkClick = onPostLinkClick,
		onUrlClick = onUrlClick,
		onTrailingClick = onTrailingClick,
		onTextClick = onTextClick,
		obstacleContent = obstacleContent,
		forcedObstacleOffset = forcedObstacleOffset,
	)
}

@Composable
fun TextWrap(
	text: String,
	modifier: Modifier = Modifier,
	obstacleAlignment: TextWrapObstacleAlignment = TextWrapObstacleAlignment.BottomEnd,
	forcedObstacleOffset: IntOffset = IntOffset.Zero,
	color: Color = Color.Unspecified,
	fontSize: TextUnit = TextUnit.Unspecified,
	fontStyle: FontStyle? = null,
	fontWeight: FontWeight? = null,
	fontFamily: FontFamily? = null,
	letterSpacing: TextUnit = TextUnit.Unspecified,
	textDecoration: TextDecoration? = null,
	textAlign: TextAlign = TextAlign.Unspecified,
	lineHeight: TextUnit = TextUnit.Unspecified,
	softWrap: Boolean = true,
	maxLines: Int = Int.MAX_VALUE,
	overflow: TextOverflow = TextOverflow.Clip,
	style: TextStyle = LocalTextStyle.current,
	isSpoilerRevealed: Boolean = false,
	onRevealSpoiler: (() -> Unit)? = null,
	onProfileTagClick: ((String) -> Unit)? = null,
	onPostLinkClick: ((String, String) -> Unit)? = null,
	onUrlClick: ((String) -> Unit)? = null,
	onTrailingClick: (() -> Unit)? = null,
	onTextClick: (() -> Unit)? = null,
	obstacleContent: @Composable () -> Unit = {},
) {
	TextWrap(
		text = AnnotatedString(text),
		modifier = modifier,
		obstacleAlignment = obstacleAlignment,
		forcedObstacleOffset = forcedObstacleOffset,
		color = color,
		fontSize = fontSize,
		fontStyle = fontStyle,
		fontWeight = fontWeight,
		fontFamily = fontFamily,
		letterSpacing = letterSpacing,
		textDecoration = textDecoration,
		textAlign = textAlign,
		lineHeight = lineHeight,
		softWrap = softWrap,
		maxLines = maxLines,
		overflow = overflow,
		style = style,
		isSpoilerRevealed = isSpoilerRevealed,
		onRevealSpoiler = onRevealSpoiler,
		onProfileTagClick = onProfileTagClick,
		onPostLinkClick = onPostLinkClick,
		onUrlClick = onUrlClick,
		onTrailingClick = onTrailingClick,
		onTextClick = onTextClick,
		obstacleContent = obstacleContent,
	)
}

enum class TextWrapObstacleAlignment {
	TopStart,
	TopEnd,
	BottomStart,
	BottomEnd,
}

@Composable
private fun TextWrapPreview() {
	val text =
		"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus porttitor tortor sit amet tortor varius congue."
	val obstacleContent: @Composable () -> Unit = {
		Box(
			Modifier
				.height(30.dp)
				.width(70.dp)
				.background(color = Color.Red)
		)
	}

	Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
		TextWrap(
			text = text,
			obstacleAlignment = TextWrapObstacleAlignment.TopStart,
			obstacleContent = obstacleContent
		)
	}
}
