package me.floow.shared.chats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesActorTarget
import me.floow.shared.chats.model.RepliesNavigationTarget
import me.floow.shared.chats.uilogic.replies.RepliesStateHolder

@Composable
fun SharedRepliesRoute(
	stateHolder: RepliesStateHolder,
	onBackClick: () -> Unit,
	onOpenThread: (RepliesNavigationTarget) -> Unit,
	onOpenActorProfile: (RepliesActorTarget) -> Unit,
	onShowMessage: (String) -> Unit,
	onSeeAll: () -> Unit = {},
	openMode: ChatOpenMode = ChatOpenMode.FROM_UNREAD,
	anchorSeq: Long? = null,
	modifier: Modifier = Modifier,
) {
	val state by stateHolder.state.collectAsState()

	LaunchedEffect(stateHolder, openMode, anchorSeq) {
		stateHolder.load(openMode = openMode, anchorSeq = anchorSeq)
	}

	LaunchedEffect(stateHolder) {
		stateHolder.events.collectLatest { event ->
			when (event) {
				is RepliesStateHolder.Event.OpenThread -> onOpenThread(event.target)
				is RepliesStateHolder.Event.OpenActorProfile -> onOpenActorProfile(event.target)
				RepliesStateHolder.Event.SeeAll -> {
					onSeeAll()
					onShowMessage("Все ответы помечены как прочитанные")
				}
				is RepliesStateHolder.Event.ShowMessage -> onShowMessage(event.message)
			}
		}
	}

	SharedRepliesScreen(
		state = state,
		onBackClick = onBackClick,
		onThreadClick = stateHolder::openThread,
		onActorClick = stateHolder::openActorProfile,
		onSeeAllClick = stateHolder::seeAll,
		modifier = modifier,
	)
}
