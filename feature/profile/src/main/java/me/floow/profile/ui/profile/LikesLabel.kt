package me.floow.profile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.font.FontWeight
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
	val shape = RoundedCornerShape(100.dp)

	Box(
		modifier = modifier
			.wrapContentWidth()
	) {
		Icon(
			imageVector = Icons.Default.Favorite,
			contentDescription = null,
			tint = Color.White,
			modifier = Modifier
				.align(Alignment.CenterStart)
				.offset(x = (-18).dp, y = 4.dp)
				.rotate(-8f)
				.size(27.dp)
		)

		Box(
			modifier = Modifier
				.height(34.dp)
				.clip(shape)
				.background(
					brush = Brush.verticalGradient(
						colors = listOf(
							Color(0xFFB33333),
							Color(0xFF240C0C)
						)
					)
				)
				.border(2.dp, Color(0x40121212), shape)
				.clickable(onClick = onClick)
				.padding(horizontal = 12.dp),
			contentAlignment = Alignment.Center
		) {
			Text(
				text = text,
				style = LocalTypography.current.labelMedium.copy(
					fontWeight = FontWeight.Medium,
					fontSize = 15.sp
				),
				color = Color.White,
				textAlign = TextAlign.Center
			)
		}

		Icon(
			imageVector = Icons.Default.Favorite,
			contentDescription = null,
			tint = Color.White,
			modifier = Modifier
				.align(Alignment.TopStart)
				.offset(x = 1.dp, y = (-9).dp)
				.rotate(14f)
				.size(22.dp)
		)
	}
}

@Preview(
	name = "NoLikes",
	showBackground = true,
	backgroundColor = 0xFF222222,
	widthDp = 220,
	heightDp = 120
)
@Preview(
	name = "NoLikes FontScale 1.3",
	fontScale = 1.3f,
	showBackground = true,
	backgroundColor = 0xFF222222,
	widthDp = 240,
	heightDp = 140
)
@Composable
fun LikesLabelPreview_NoLikes(modifier: Modifier = Modifier) {
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

@Preview(
	name = "ManyLikes",
	showBackground = true,
	backgroundColor = 0xFF222222,
	widthDp = 220,
	heightDp = 120
)
@Preview(
	name = "ManyLikes FontScale 1.3",
	fontScale = 1.3f,
	showBackground = true,
	backgroundColor = 0xFF222222,
	widthDp = 240,
	heightDp = 140
)
@Composable
fun LikesLabelPreview_ManyLikes(modifier: Modifier = Modifier) {
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
