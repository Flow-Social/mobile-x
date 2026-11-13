package me.floow.shared.chats.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.floor

private val EmojiCellSize = 42.dp
private val EmojiCellShape = RoundedCornerShape(10.dp)

@Composable
fun FlowEmojiPanel(
    onEmojiPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    recentStore: FlowEmojiRecentStore = rememberFlowEmojiRecentStore(),
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val recentEmojis by recentStore.recentEmojis().collectAsState(initial = emptyList())
    val categories = FlowEmojiCatalog.categories

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Tab Row for fast jumping between categories
        ScrollableTabRow(
            selectedTabIndex = 0,
            edgePadding = 8.dp,
            divider = {},
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            if (recentEmojis.isNotEmpty()) {
                Tab(
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier.width(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = "Недавние",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            categories.forEachIndexed { index, category ->
                Tab(
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            val targetIndex = (if (recentEmojis.isNotEmpty()) 1 else 0) + index
                            listState.animateScrollToItem(targetIndex)
                        }
                    },
                    modifier = Modifier.width(40.dp)
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.title,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidth = maxWidth
            val columns = floor((totalWidth / EmojiCellSize).toDouble()).toInt().coerceAtLeast(6)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
            ) {
                // Recent Section
                if (recentEmojis.isNotEmpty()) {
                    item(key = "section_recent") {
                        EmojiSectionHeader(title = "Недавние")
                        val rows = recentEmojis.chunked(columns)
                        rows.forEach { rowEmojis ->
                            EmojiRow(
                                emojis = rowEmojis,
                                columns = columns,
                                onSelect = { emoji ->
                                    recentStore.persistRecentEmoji(emoji)
                                    onEmojiPicked(emoji)
                                }
                            )
                        }
                    }
                }

                // Standard Categories
                categories.forEach { category ->
                    item(key = "section_${category.key}") {
                        EmojiSectionHeader(title = category.title)
                        val rows = category.emojis.chunked(columns)
                        rows.forEach { rowEmojis ->
                            EmojiRow(
                                emojis = rowEmojis,
                                columns = columns,
                                onSelect = { emoji ->
                                    recentStore.persistRecentEmoji(emoji)
                                    onEmojiPicked(emoji)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun EmojiRow(
    emojis: List<String>,
    columns: Int,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(EmojiCellSize),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until columns) {
            val emoji = emojis.getOrNull(i)
            if (emoji != null) {
                Box(
                    modifier = Modifier
                        .size(EmojiCellSize)
                        .clip(EmojiCellShape)
                        .clickable { onSelect(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Box(modifier = Modifier.size(EmojiCellSize))
            }
        }
    }
}
