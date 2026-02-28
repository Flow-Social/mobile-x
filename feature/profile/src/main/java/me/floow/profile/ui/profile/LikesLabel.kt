package me.floow.profile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.profile.R
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.util.toShortenedString

@Composable
fun LikesLabel(
	onClick: () -> Unit,
	totalLikesReceived: Int,
	modifier: Modifier = Modifier
) {
	val currentLocale = Locale.current
	val text = if (totalLikesReceived <= 0) {
		stringResource(R.string.no_likes_yet)
	} else {
		val shortCount = totalLikesReceived.toShortenedString(currentLocale.platformLocale)
		pluralStringResource(R.plurals.likes_count, totalLikesReceived, shortCount)
	}

	Text(
		text = text,
		style = LocalTypography.current.labelMedium,
		color = Color.White,
		textAlign = TextAlign.Center,
		modifier = modifier
			.clip(RoundedCornerShape(16.dp))
			.background(color = Color.Black)
			.border(2.dp, Color.White, RoundedCornerShape(16.dp))
			.height(30.dp)
			.width(128.dp)
			.wrapContentHeight(align = Alignment.CenterVertically)
			.clickable(onClick = onClick)
	)
}

@Preview(showBackground = false)
@Composable
private fun LikesLabelPreview_NoLikes(modifier: Modifier = Modifier) {
	Box(
		Modifier
			.size(300.dp)
			.background(Color.DarkGray), contentAlignment = Alignment.Center
	) {
		LikesLabel(
			totalLikesReceived = 0,
			onClick = {},
			modifier = Modifier.padding(24.dp)
		)
	}
}

@Preview(showBackground = false)
@Composable
private fun LikesLabelPreview_ManyLikes(modifier: Modifier = Modifier) {
	Box(
		Modifier
			.size(300.dp)
			.background(Color.DarkGray), contentAlignment = Alignment.Center
	) {
		LikesLabel(
			totalLikesReceived = 1_700_000,
			onClick = {},
			modifier = Modifier.padding(24.dp)
		)
	}
}
