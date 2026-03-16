package me.floow.uikit.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.domain.models.PostImageVariant
import me.floow.domain.models.normalized
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.ProgressiveImageMode
import me.floow.uikit.theme.LocalTypography
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ChatPostBubble(
	authorName: String,
	imageVariants: List<PostImageVariant>,
	likesCount: Int,
	description: String,
	dateTime: LocalDateTime,
	onImageClick: (Int) -> Unit,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier
			.clip(RoundedCornerShape(16.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant)
			.padding(horizontal = 4.dp, vertical = 8.dp)
	) {
		if (authorName.isNotBlank()) {
			Text(
				text = authorName,
				style = LocalTypography.current.labelMedium,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Spacer(Modifier.height(6.dp))
		}

		PostImageGrid(
			imageVariants = imageVariants,
			onImageClick = onImageClick,
			modifier = Modifier.fillMaxWidth()
		)

		Column(modifier = Modifier.padding(horizontal = 4.dp)) {
			if (description.isNotBlank()) {
				Spacer(Modifier.height(8.dp))
				Text(
					text = description,
					style = LocalTypography.current.bodyMedium.copy(fontSize = 16.sp),
					color = MaterialTheme.colorScheme.onSurface
				)
			}

			Spacer(Modifier.height(8.dp))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = if (likesCount > 0) {
					Arrangement.SpaceBetween
				} else {
					Arrangement.End
				},
				verticalAlignment = Alignment.CenterVertically
			) {
				if (likesCount > 0) {
					Surface(
						shape = RoundedCornerShape(100.dp),
						color = Color(0x81FFFFFF),
						modifier = Modifier.wrapContentWidth()
					) {
						Row(
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(4.dp)
						) {
							Text(
								text = "\uD83D\uDC4D",
								style = LocalTypography.current.captionSmall.copy(
									fontSize = 14.sp,
									lineHeight = 11.sp
								)
							)
							Text(
								text = likesCount.toString(),
								style = LocalTypography.current.captionSmall.copy(
									fontSize = 11.sp,
									lineHeight = 11.sp,
									fontWeight = FontWeight.Medium
								),
								color = Color(0xFF121213)
							)
						}
					}
				}

				Text(
					text = dateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
					style = LocalTypography.current.captionSmall.copy(
						fontSize = 10.sp,
						lineHeight = 10.sp
					),
					color = Color(0xFF83858F)
				)
			}
		}
	}
}

@Composable
private fun PostImageGrid(
	imageVariants: List<PostImageVariant>,
	onImageClick: (Int) -> Unit,
	modifier: Modifier = Modifier
) {
	val variants = imageVariants
		.map(PostImageVariant::normalized)
		.filter(PostImageVariant::hasVisibleUrl)
		.take(4)
	if (variants.isEmpty()) return

	val shape = RoundedCornerShape(12.dp)

	when (variants.size) {
		1 -> {
			val variant = variants.first()
			Box(
				modifier = modifier
					.height(180.dp)
					.clip(shape)
					.clickable { onImageClick(0) }
			) {
					ProgressiveImage(
						lqUrl = variant.lqUrl,
						previewUrl = variant.previewUrl,
						fullUrl = variant.fullUrl,
						mode = ProgressiveImageMode.LIST,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize()
					)
				}
			}
		2 -> {
			Row(
				modifier = modifier
					.clip(shape)
			) {
				ImageCell(variants[0], Modifier.weight(1f), onClick = { onImageClick(0) })
				Spacer(Modifier.width(2.dp))
				ImageCell(variants[1], Modifier.weight(1f), onClick = { onImageClick(1) })
			}
		}
		3 -> {
			Column(
				modifier = modifier
					.clip(shape)
			) {
				Row {
					ImageCell(variants[0], Modifier.weight(1f), onClick = { onImageClick(0) })
					Spacer(Modifier.width(2.dp))
					ImageCell(variants[1], Modifier.weight(1f), onClick = { onImageClick(1) })
				}
				Spacer(Modifier.height(2.dp))
				ImageCell(variants[2], Modifier.fillMaxWidth(), onClick = { onImageClick(2) })
			}
		}
		else -> {
			Column(
				modifier = modifier
					.clip(shape)
			) {
				Row {
					ImageCell(variants[0], Modifier.weight(1f), onClick = { onImageClick(0) })
					Spacer(Modifier.width(2.dp))
					ImageCell(variants[1], Modifier.weight(1f), onClick = { onImageClick(1) })
				}
				Spacer(Modifier.height(2.dp))
				Row {
					ImageCell(variants[2], Modifier.weight(1f), onClick = { onImageClick(2) })
					Spacer(Modifier.width(2.dp))
					ImageCell(variants[3], Modifier.weight(1f), onClick = { onImageClick(3) })
				}
			}
		}
	}
}

@Composable
private fun ImageCell(
	variant: PostImageVariant,
	modifier: Modifier = Modifier,
	onClick: () -> Unit
) {
	Box(
		modifier = modifier
			.height(90.dp)
			.clip(RoundedCornerShape(4.dp))
			.background(Color.LightGray)
			.clickable { onClick() }
	) {
		ProgressiveImage(
			lqUrl = variant.lqUrl,
			previewUrl = variant.previewUrl,
			fullUrl = variant.fullUrl,
			mode = ProgressiveImageMode.LIST,
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier.fillMaxSize()
		)
	}
}

private fun PostImageVariant.hasVisibleUrl(): Boolean {
	return lqUrl?.isNotBlank() == true ||
		previewUrl?.isNotBlank() == true ||
		fullUrl?.isNotBlank() == true
}
