package me.floow.shared.chats.uilogic.replies

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.floow.domain.readmodel.TimelineReadItem
import me.floow.domain.readmodel.TimelineReadOpenMode
import me.floow.domain.readmodel.TimelineReadProjectorInput
import me.floow.domain.readmodel.projectTimelineReadModel
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesActorTarget
import me.floow.shared.chats.model.RepliesNavigationTarget
import me.floow.shared.chats.model.RepliesThreadItemModel
import me.floow.shared.chats.model.toActorTargetOrNull
import me.floow.shared.chats.model.toNavigationTargetOrNull

sealed interface RepliesScreenState {
	data object Loading : RepliesScreenState
	data class Error(val message: String) : RepliesScreenState
	data object Empty : RepliesScreenState
	data class HasData(
		val items: List<RepliesThreadItemModel>,
		val unreadCount: Int,
		val unreadBoundaryMessageId: Long? = null,
		val openAnchorMessageId: Long? = null,
	) : RepliesScreenState
}

class RepliesStateHolder(
	private val repository: RepliesRepository,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
	sealed interface Event {
		data class OpenThread(val target: RepliesNavigationTarget) : Event
		data class OpenActorProfile(val target: RepliesActorTarget) : Event
		data object SeeAll : Event
		data class ShowMessage(val message: String) : Event
	}

	private val _state = MutableStateFlow<RepliesScreenState>(RepliesScreenState.Loading)
	val state: StateFlow<RepliesScreenState> = _state.asStateFlow()

	private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
	val events: SharedFlow<Event> = _events.asSharedFlow()

	fun load(
		openMode: ChatOpenMode = ChatOpenMode.FROM_UNREAD,
		anchorSeq: Long? = null,
	) {
		_state.value = RepliesScreenState.Loading
		scope.launch {
			repository.load(openMode = openMode, anchorSeq = anchorSeq)
				.onSuccess { data ->
					val presentation = projectRepliesPresentation(
						items = data.items,
						openMode = openMode,
						anchorSeq = anchorSeq,
					)
					_state.value = if (data.items.isEmpty()) {
						RepliesScreenState.Empty
					} else {
						RepliesScreenState.HasData(
							items = data.items,
							unreadCount = data.unreadCount,
							unreadBoundaryMessageId = presentation.unreadBoundaryMessageId,
							openAnchorMessageId = presentation.openAnchorMessageId,
						)
					}
				}
				.onFailure {
					_state.value = RepliesScreenState.Error(it.message ?: "replies load failed")
				}
		}
	}

	fun openThread(item: RepliesThreadItemModel) {
		val target = item.toNavigationTargetOrNull()
		if (target == null) {
			_events.tryEmit(Event.ShowMessage("Не удалось открыть тред ответа"))
			return
		}
		_events.tryEmit(Event.OpenThread(target))
	}

	fun openActorProfile(item: RepliesThreadItemModel) {
		val target = item.toActorTargetOrNull()
		if (target == null) {
			_events.tryEmit(Event.ShowMessage("Не удалось открыть профиль автора"))
			return
		}
		_events.tryEmit(Event.OpenActorProfile(target))
	}

	fun seeAll() {
		scope.launch {
			repository.markAllRead()
				.onSuccess {
					_state.value = when (val current = _state.value) {
						is RepliesScreenState.HasData -> current.copy(
							items = current.items.map { it.copy(isUnread = false) },
							unreadCount = 0,
							unreadBoundaryMessageId = null,
						)
						else -> current
					}
					_events.emit(Event.SeeAll)
				}
				.onFailure {
					_events.emit(Event.ShowMessage(it.message ?: "mark replies read failed"))
				}
		}
	}
}

private data class RepliesPresentationProjection(
	val unreadBoundaryMessageId: Long?,
	val openAnchorMessageId: Long?,
)

private fun projectRepliesPresentation(
	items: List<RepliesThreadItemModel>,
	openMode: ChatOpenMode,
	anchorSeq: Long?,
): RepliesPresentationProjection {
	val readUpToCursor = items
		.asSequence()
		.filterNot(RepliesThreadItemModel::isUnread)
		.map(RepliesThreadItemModel::messageId)
		.maxOrNull()
		?: 0L
	val projection = projectTimelineReadModel(
		input = TimelineReadProjectorInput(
			items = items.map { item ->
				TimelineReadItem(
					messageId = item.messageId,
					cursor = item.messageId,
					isIncoming = true,
				)
			},
			serverReadUpToCursor = readUpToCursor,
			localReadUpToCursor = readUpToCursor,
			firstUnreadCursor = items
				.asSequence()
				.filter(RepliesThreadItemModel::isUnread)
				.map(RepliesThreadItemModel::messageId)
				.minOrNull(),
			storedOpenAnchorCursor = readUpToCursor.takeIf {
				openMode == ChatOpenMode.FROM_LAST_SEEN && it > 0L
			},
			resolvedOpenAnchorCursor = null,
			openMode = openMode.toTimelineReadOpenMode(),
			messageLinkAnchorCursor = anchorSeq?.takeIf {
				openMode == ChatOpenMode.FROM_MESSAGE_LINK && it > 0L
			},
			preferCeilOpenAnchor = openMode != ChatOpenMode.FROM_LAST_SEEN,
			fallbackToOldestOpenAnchor = true,
		)
	)
	return RepliesPresentationProjection(
		unreadBoundaryMessageId = projection.unreadBoundaryMessageId,
		openAnchorMessageId = projection.openAnchorMessageId,
	)
}

private fun ChatOpenMode.toTimelineReadOpenMode(): TimelineReadOpenMode = when (this) {
	ChatOpenMode.FROM_UNREAD -> TimelineReadOpenMode.FROM_UNREAD
	ChatOpenMode.FROM_LAST_SEEN -> TimelineReadOpenMode.FROM_LAST_SEEN
	ChatOpenMode.FROM_MESSAGE_LINK -> TimelineReadOpenMode.FROM_MESSAGE_LINK
}
