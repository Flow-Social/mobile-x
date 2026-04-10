package me.floow.uikit.components.pickers

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.floow.uikit.R
import me.floow.uikit.theme.LocalTypography
import kotlin.math.floor

private val EmojiPanelHeaderHeight = 50.dp
private val EmojiPanelHeaderIconSize = 22.dp
private val EmojiPanelHeaderItemWidth = 34.dp
private val EmojiPanelHeaderIndicatorWidth = 28.dp
private val EmojiPanelHeaderIndicatorHeight = 3.dp
private val EmojiPanelHeaderHorizontalPadding = 12.dp
private val EmojiPanelHeaderBottomPadding = 5.dp
private val EmojiSectionHorizontalPadding = 12.dp
private val EmojiSectionTitleTopPadding = 10.dp
private val EmojiSectionTitleBottomPadding = 8.dp
private val EmojiCellSize = 42.dp
private val EmojiCellShape = RoundedCornerShape(12.dp)
private val EmojiFooterContainerShape = RoundedCornerShape(percent = 50)
private val EmojiFooterOuterPadding = 4.dp
private val EmojiFooterInnerPadding = 4.dp
private val EmojiFooterReservedHeight = 37.dp
private val EmojiFooterContainerShadow = 4.dp
private val EmojiFooterButtonVerticalPadding = 6.dp
private val EmojiFooterButtonHorizontalPadding = 10.dp
private val EmojiFooterButtonTextSize = 14.sp
private val EmojiFooterButtonMinHeight = 29.dp
private val EmojiFooterButtonMinWidth = 74.dp

private enum class EmojiPanelMode {
	Emoji,
	Stickers
}

@Immutable
private data class EmojiCategoryDefinition(
	val key: String,
	val titleResId: Int,
	val iconResId: Int,
	val rawResId: Int
)

@Immutable
private data class EmojiCategoryUi(
	val key: String,
	val title: String,
	val iconResId: Int,
	val items: List<EmojiItemUi>
)

@Immutable
private data class EmojiItemUi(
	val value: String,
	val variants: List<String>
)

@Immutable
private data class EmojiSectionUi(
	val key: String,
	val title: String,
	val iconResId: Int,
	val items: List<EmojiItemUi>
)

@Immutable
private data class EmojiPanelTabUi(
	val key: String,
	val title: String,
	val iconResId: Int
)

@Immutable
private data class EmojiSectionBlock(
	val sectionKey: String,
	val title: String,
	val columns: Int,
	val rows: List<List<EmojiItemUi>>
)

@Stable
private class EmojiListLayout(
	val blocks: List<EmojiSectionBlock>,
	val sectionStartIndices: Map<String, Int>
)

private val emojiCategoryDefinitions = listOf(
	EmojiCategoryDefinition(
		key = "smileys",
		titleResId = R.string.chat_emoji_category_smileys,
		iconResId = androidx.emoji2.emojipicker.R.drawable.gm_filled_emoji_emotions_vd_theme_24,
		rawResId = androidx.emoji2.emojipicker.R.raw.emoji_category_emotions
	),
	EmojiCategoryDefinition(
		key = "people",
		titleResId = R.string.chat_emoji_category_people,
		iconResId = androidx.emoji2.emojipicker.R.drawable.gm_filled_emoji_people_vd_theme_24,
		rawResId = androidx.emoji2.emojipicker.R.raw.emoji_category_people
	),
	EmojiCategoryDefinition(
		key = "nature",
		titleResId = R.string.chat_emoji_category_nature,
		iconResId = androidx.emoji2.emojipicker.R.drawable.gm_filled_emoji_nature_vd_theme_24,
		rawResId = androidx.emoji2.emojipicker.R.raw.emoji_category_animals_nature
	),
	EmojiCategoryDefinition(
		key = "food",
		titleResId = R.string.chat_emoji_category_food,
		iconResId = androidx.emoji2.emojipicker.R.drawable.gm_filled_emoji_food_beverage_vd_theme_24,
		rawResId = androidx.emoji2.emojipicker.R.raw.emoji_category_food_drink
	),
	EmojiCategoryDefinition(
		key = "travel",
		titleResId = R.string.chat_emoji_category_travel,
		iconResId = androidx.emoji2.emojipicker.R.drawable.gm_filled_emoji_transportation_vd_theme_24,
		rawResId = androidx.emoji2.emojipicker.R.raw.emoji_category_travel_places
	),
	EmojiCategoryDefinition(
		key = "activities",
		titleResId = R.string.chat_emoji_category_activities,
		iconResId = androidx.emoji2.emojipicker.R.drawable.gm_filled_emoji_events_vd_theme_24,
		rawResId = androidx.emoji2.emojipicker.R.raw.emoji_category_activity
	),
	EmojiCategoryDefinition(
		key = "objects",
		titleResId = R.string.chat_emoji_category_objects,
		iconResId = androidx.emoji2.emojipicker.R.drawable.gm_filled_emoji_objects_vd_theme_24,
		rawResId = androidx.emoji2.emojipicker.R.raw.emoji_category_objects
	),
	EmojiCategoryDefinition(
		key = "symbols",
		titleResId = R.string.chat_emoji_category_symbols,
		iconResId = androidx.emoji2.emojipicker.R.drawable.gm_filled_emoji_symbols_vd_theme_24,
		rawResId = androidx.emoji2.emojipicker.R.raw.emoji_category_symbols
	),
	EmojiCategoryDefinition(
		key = "flags",
		titleResId = R.string.chat_emoji_category_flags,
		iconResId = androidx.emoji2.emojipicker.R.drawable.gm_filled_flag_vd_theme_24,
		rawResId = androidx.emoji2.emojipicker.R.raw.emoji_category_flags
	)
)

@Composable
fun FlowEmojiPanel(
	onEmojiPicked: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current
	val recentStore = rememberFlowEmojiRecentStore()
	val coroutineScope = rememberCoroutineScope()
	var panelMode by rememberSaveable { mutableStateOf(EmojiPanelMode.Emoji) }

	val recentEmojis by remember(recentStore) {
		recentStore.recentEmojis()
	}.collectAsState(initial = emptyList())
	val categories by produceState<List<EmojiCategoryUi>>(initialValue = emptyList(), context) {
		value = withContext(Dispatchers.IO) {
			loadEmojiCategories(context)
		}
	}

	if (categories.isEmpty()) {
		Box(
			modifier = modifier
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.surface)
				.testTag("chat_emoji_panel")
		)
		return
	}

	val recentItems by remember(recentEmojis, categories) {
		derivedStateOf {
			val knownEmojis = categories
				.asSequence()
				.flatMap { category -> category.items.asSequence() }
				.associateBy { item -> item.value }

			recentEmojis.mapNotNull(knownEmojis::get)
		}
	}

	val allSections by remember(categories, recentItems) {
		derivedStateOf {
			buildList {
				add(
					EmojiSectionUi(
						key = "recent",
						title = context.getString(R.string.chat_emoji_category_recent),
						iconResId = androidx.emoji2.emojipicker.R.drawable.quantum_gm_ic_access_time_filled_vd_theme_24,
						items = recentItems
					)
				)
				addAll(
					categories.map { category ->
						EmojiSectionUi(
							key = category.key,
							title = category.title,
							iconResId = category.iconResId,
							items = category.items
						)
					}
				)
			}
		}
	}

	BoxWithConstraints(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surface)
			.testTag("chat_emoji_panel")
	) {
		val columns = remember(maxWidth) {
			maxOf(6, floor(maxWidth.value / 44f).toInt())
		}
		val listState = rememberLazyListState()
		val layout = remember(allSections, columns) {
			buildEmojiListLayout(allSections, columns)
		}
		val tabs = remember(allSections) {
			allSections.map { section ->
				EmojiPanelTabUi(
					key = section.key,
					title = section.title,
					iconResId = section.iconResId
				)
			}
		}
		val selectedSectionKey by remember(listState, layout, tabs) {
			derivedStateOf {
				resolveSelectedSectionKey(
					listState = listState,
					layout = layout,
					fallbackKey = tabs.firstOrNull()?.key.orEmpty()
				)
			}
		}

		Box(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.fillMaxSize()) {
				if (panelMode == EmojiPanelMode.Emoji) {
					if (tabs.isNotEmpty()) {
						EmojiPanelCategoryHeader(
							tabs = tabs,
							selectedKey = selectedSectionKey,
							onTabClick = { sectionKey ->
								coroutineScope.launch {
									layout.sectionStartIndices[sectionKey]?.let { itemIndex ->
										listState.animateScrollToItem(itemIndex)
									}
								}
							}
						)
						HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
					}
					EmojiSectionList(
						layout = layout,
						listState = listState,
						footerHeight = EmojiFooterReservedHeight + EmojiFooterOuterPadding * 2,
						onEmojiPicked = { emoji ->
							onEmojiPicked(emoji)
							coroutineScope.launch {
								recentStore.persistRecentEmoji(emoji)
							}
						},
						modifier = Modifier.weight(1f)
					)
				} else {
					StickerPlaceholder(
						modifier = Modifier.weight(1f)
					)
				}
			}

			EmojiPanelFooter(
				panelMode = panelMode,
				onModeChange = { nextMode ->
					panelMode = nextMode
				},
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = EmojiFooterOuterPadding)
			)
		}
	}
}

@Composable
private fun EmojiPanelCategoryHeader(
	tabs: List<EmojiPanelTabUi>,
	selectedKey: String,
	onTabClick: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	val colorScheme = MaterialTheme.colorScheme
	val selectedTint = colorScheme.primary
	val unselectedTint = colorScheme.onSurfaceVariant

	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(EmojiPanelHeaderHeight)
			.padding(horizontal = EmojiPanelHeaderHorizontalPadding),
		verticalAlignment = Alignment.Bottom,
		horizontalArrangement = Arrangement.SpaceBetween
	) {
		tabs.forEach { tab ->
			val selected = tab.key == selectedKey
			val tint = if (selected) selectedTint else unselectedTint

			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Bottom,
				modifier = Modifier
					.height(EmojiPanelHeaderHeight)
					.width(EmojiPanelHeaderItemWidth)
					.clickable { onTabClick(tab.key) }
					.padding(bottom = EmojiPanelHeaderBottomPadding)
			) {
				Box(
					modifier = Modifier.weight(1f),
					contentAlignment = Alignment.Center
				) {
					Icon(
						painter = painterResource(tab.iconResId),
						contentDescription = tab.title,
						tint = tint,
						modifier = Modifier.size(EmojiPanelHeaderIconSize)
					)
				}

				Box(
					modifier = Modifier
						.size(
							width = EmojiPanelHeaderIndicatorWidth,
							height = EmojiPanelHeaderIndicatorHeight
						)
						.clip(RoundedCornerShape(percent = 100))
						.background(if (selected) selectedTint else Color.Transparent)
				)
			}
		}
	}
}

@Composable
private fun EmojiSectionList(
	layout: EmojiListLayout,
	listState: LazyListState,
	footerHeight: Dp,
	onEmojiPicked: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	LazyColumn(
		state = listState,
		modifier = modifier.fillMaxSize(),
		contentPadding = PaddingValues(bottom = footerHeight)
	) {
		layout.blocks.forEach { block ->
			emojiSectionBlock(
				block = block,
				onEmojiPicked = onEmojiPicked
			)
		}
	}
}

private fun LazyListScope.emojiSectionBlock(
	block: EmojiSectionBlock,
	onEmojiPicked: (String) -> Unit
) {
	item(key = "${block.sectionKey}_title") {
		Text(
			text = block.title,
			style = LocalTypography.current.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
			fontWeight = FontWeight.Medium,
			modifier = Modifier
				.fillMaxWidth()
				.padding(
					start = EmojiSectionHorizontalPadding,
					end = EmojiSectionHorizontalPadding,
					top = EmojiSectionTitleTopPadding,
					bottom = EmojiSectionTitleBottomPadding
				)
		)
	}

	itemsIndexed(
		items = block.rows,
		key = { rowIndex, _ -> "${block.sectionKey}_row_$rowIndex" }
	) { _, row ->
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = EmojiSectionHorizontalPadding),
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			row.forEach { emoji ->
				EmojiCell(
					emoji = emoji.value,
					onClick = { onEmojiPicked(emoji.value) },
					modifier = Modifier.size(EmojiCellSize)
				)
			}
			repeat((block.columns - row.size).coerceAtLeast(0)) {
				Box(modifier = Modifier.size(EmojiCellSize))
			}
		}
	}
}

@Composable
private fun EmojiCell(
	emoji: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier
			.clip(EmojiCellShape)
			.clickable(onClick = onClick)
	) {
		Text(
			text = emoji,
			style = MaterialTheme.typography.headlineMedium,
			textAlign = TextAlign.Center
		)
	}
}

@Composable
private fun EmojiPanelFooter(
	panelMode: EmojiPanelMode,
	onModeChange: (EmojiPanelMode) -> Unit,
	modifier: Modifier = Modifier
) {
	val footerBackground = MaterialTheme.colorScheme.surfaceContainerHighest
	val textStyle = LocalTypography.current.bodyMedium.copy(
		fontSize = EmojiFooterButtonTextSize,
		lineHeight = 17.sp,
		letterSpacing = 0.sp
	)
	val textMeasurer = rememberTextMeasurer()
	val density = LocalDensity.current
	val emojiLabel = stringResource(R.string.chat_emoji_footer_emoji)
	val stickersLabel = stringResource(R.string.chat_emoji_footer_stickers)
	val maxLabelWidth = remember(emojiLabel, stickersLabel, textStyle) {
		val emojiWidthPx = textMeasurer.measure(
			text = AnnotatedString(emojiLabel),
			style = textStyle,
			maxLines = 1
		).size.width
		val stickersWidthPx = textMeasurer.measure(
			text = AnnotatedString(stickersLabel),
			style = textStyle,
			maxLines = 1
		).size.width
		with(density) {
			maxOf(emojiWidthPx, stickersWidthPx).toDp()
		}
	}
	val segmentWidth =
		if (maxLabelWidth + EmojiFooterButtonHorizontalPadding * 2 > EmojiFooterButtonMinWidth) {
			maxLabelWidth + EmojiFooterButtonHorizontalPadding * 2
		} else {
			EmojiFooterButtonMinWidth
		}
	val containerWidth = segmentWidth * 2 + EmojiFooterInnerPadding * 2

	Box(
		modifier = modifier
			.fillMaxWidth(),
		contentAlignment = Alignment.Center
	) {
		Box(
			modifier = Modifier
				.width(containerWidth)
				.shadow(
					elevation = EmojiFooterContainerShadow,
					shape = EmojiFooterContainerShape,
					clip = false
				)
				.clip(EmojiFooterContainerShape)
				.background(footerBackground)
				.padding(EmojiFooterInnerPadding)
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically
				) {
				EmojiFooterButton(
					text = emojiLabel,
					selected = panelMode == EmojiPanelMode.Emoji,
					onClick = { onModeChange(EmojiPanelMode.Emoji) },
					modifier = Modifier.width(segmentWidth)
				)
				EmojiFooterButton(
					text = stickersLabel,
					selected = panelMode == EmojiPanelMode.Stickers,
					onClick = { onModeChange(EmojiPanelMode.Stickers) },
					modifier = Modifier.width(segmentWidth)
				)
			}
		}
	}
}

@Composable
private fun EmojiFooterButton(
	text: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	val colorScheme = MaterialTheme.colorScheme
	val selectedBackground = colorScheme.primaryContainer
	val selectedTextColor = colorScheme.onPrimaryContainer
	val unselectedTextColor = colorScheme.onSurfaceVariant

	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier
			.heightIn(min = EmojiFooterButtonMinHeight)
			.clip(RoundedCornerShape(21.dp))
			.background(if (selected) selectedBackground else Color.Transparent)
			.clickable(onClick = onClick)
			.padding(
				horizontal = EmojiFooterButtonHorizontalPadding,
				vertical = EmojiFooterButtonVerticalPadding
			)
	) {
		Text(
			text = text,
			style = LocalTypography.current.bodyMedium.copy(
				fontSize = EmojiFooterButtonTextSize,
				lineHeight = 17.sp
			),
			fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
			color = if (selected) selectedTextColor else unselectedTextColor,
			textAlign = TextAlign.Center,
			maxLines = 1,
			softWrap = false,
			overflow = TextOverflow.Clip
		)
	}
}

@Composable
private fun StickerPlaceholder(
	modifier: Modifier = Modifier
) {
	EmojiEmptyState(
		text = stringResource(R.string.chat_emoji_stickers_placeholder),
		modifier = modifier
	)
}

@Composable
private fun EmojiEmptyState(
	text: String,
	modifier: Modifier = Modifier
) {
	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 24.dp)
	) {
		Text(
			text = text,
			style = LocalTypography.current.titleMedium,
			color = MaterialTheme.colorScheme.secondary,
			textAlign = TextAlign.Center,
			modifier = Modifier.heightIn(min = 80.dp)
		)
	}
}

private fun buildEmojiListLayout(
	sections: List<EmojiSectionUi>,
	columns: Int
): EmojiListLayout {
	val blocks = sections.map { section ->
		EmojiSectionBlock(
			sectionKey = section.key,
			title = section.title,
			columns = columns,
			rows = section.items.chunked(columns)
		)
	}

	var currentIndex = 0
	val sectionIndices = buildMap {
		blocks.forEach { block ->
			put(block.sectionKey, currentIndex)
			currentIndex += 1 + block.rows.size
		}
	}

	return EmojiListLayout(
		blocks = blocks,
		sectionStartIndices = sectionIndices
	)
}

private fun resolveSelectedSectionKey(
	listState: LazyListState,
	layout: EmojiListLayout,
	fallbackKey: String
): String {
	val firstVisible = listState.firstVisibleItemIndex
	return layout.sectionStartIndices.entries
		.filter { (_, index) -> index <= firstVisible }
		.maxByOrNull { (_, index) -> index }
		?.key
		?: fallbackKey
}

private suspend fun loadEmojiCategories(context: Context): List<EmojiCategoryUi> =
	emojiCategoryDefinitions.map { definition ->
		EmojiCategoryUi(
			key = definition.key,
			title = context.getString(definition.titleResId),
			iconResId = definition.iconResId,
			items = parseEmojiCategory(context, definition.rawResId)
		)
	}

private fun parseEmojiCategory(
	context: Context,
	rawResId: Int
): List<EmojiItemUi> = context.resources.openRawResource(rawResId)
	.bufferedReader()
	.useLines { lines ->
		lines
			.map(String::trim)
			.filter(String::isNotBlank)
			.mapNotNull { line ->
				val variants = line.split(',')
					.map(String::trim)
					.filter(String::isNotBlank)
					.distinct()
				variants.firstOrNull()?.let { primaryEmoji ->
					EmojiItemUi(
						value = primaryEmoji,
						variants = variants
					)
				}
			}
			.distinctBy(EmojiItemUi::value)
			.toList()
	}
