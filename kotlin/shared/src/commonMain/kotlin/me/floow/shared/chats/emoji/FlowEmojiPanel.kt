package me.floow.shared.chats.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.chat_emoji_delete
import flow.shared.generated.resources.chat_emoji_recent
import kotlinx.coroutines.launch
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.stringResource

private val EmojiCellSize = 44.dp
private val EmojiKeyShape = RoundedCornerShape(12.dp)

@Composable
fun FlowEmojiPanel(
    onEmojiPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    recentStore: FlowEmojiRecentStore = rememberFlowEmojiRecentStore(),
    onDelete: (() -> Unit)? = null,
    canDelete: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val tabsState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme
    val recentEmojis by recentStore.recentEmojis().collectAsState(initial = emptyList())
    val recentLabel = stringResource(Res.string.chat_emoji_recent)
    val sections = remember(recentEmojis, recentLabel) {
        if (recentEmojis.isEmpty()) FlowEmojiCatalog.categories
        else listOf(FlowEmojiCategory("recent", recentLabel, Icons.Outlined.Schedule, recentEmojis)) + FlowEmojiCatalog.categories
    }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.surface)) {
        val columns = ((maxWidth - 24.dp) / EmojiCellSize).toInt().coerceAtLeast(1)
        val rowCount = remember(columns, sections) {
            sections.map { (it.emojis.size + columns - 1) / columns }
        }
        val sectionStarts = remember(rowCount) { rowCount.runningFold(0) { start, rows -> start + 1 + rows }.dropLast(1) }
        val activeSection by remember(sectionStarts) {
            derivedStateOf {
                if (listState.layoutInfo.totalItemsCount > 0 && !listState.canScrollForward &&
                    listState.firstVisibleItemIndex > 0
                ) sectionStarts.lastIndex else
                    sectionStarts.indexOfLast { it <= listState.firstVisibleItemIndex }.coerceAtLeast(0)
            }
        }
        LaunchedEffect(activeSection) {
            if (tabsState.layoutInfo.visibleItemsInfo.none { it.index == activeSection }) {
                tabsState.animateScrollToItem(activeSection)
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    state = tabsState,
                    modifier = Modifier.weight(1f).selectableGroup(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(sections, key = { _, section -> section.key }) { index, section ->
                        val selected = activeSection == index
                        Box(
                            Modifier.size(44.dp).selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { scope.launch { listState.animateScrollToItem(sectionStarts[index]) } },
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(EmojiKeyShape)
                                    .background(if (selected) colors.primary.copy(alpha = 0.12f) else colors.surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    section.icon,
                                    contentDescription = section.title,
                                    tint = if (selected) colors.primary else colors.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, enabled = canDelete) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Backspace,
                            contentDescription = stringResource(Res.string.chat_emoji_delete),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            ) {
                sections.forEachIndexed { sectionIndex, section ->
                    item(key = "${section.key}:header", contentType = "header") {
                        Text(
                            section.title,
                            style = LocalTypography.current.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(rowCount[sectionIndex], key = { "${section.key}:row:$it" }, contentType = { "emoji-row" }) { row ->
                        Row(Modifier.fillMaxWidth().height(EmojiCellSize), verticalAlignment = Alignment.CenterVertically) {
                            repeat(columns) { column ->
                                val emoji = section.emojis.getOrNull(row * columns + column)
                                val cell = Modifier.weight(1f).height(EmojiCellSize)
                                if (emoji == null) Box(cell)
                                else Box(
                                    cell.clip(EmojiKeyShape).clickable(role = Role.Button) {
                                        recentStore.persistRecentEmoji(emoji)
                                        onEmojiPicked(emoji)
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(emoji, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
