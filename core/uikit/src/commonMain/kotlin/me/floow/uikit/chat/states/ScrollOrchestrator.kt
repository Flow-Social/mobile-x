package me.floow.uikit.chat.states

internal enum class ScrollOrchestratorMode {
	Idle,
	InitialAnchorPlacement,
	JumpPending,
	JumpToTarget,
	FollowUser
}

internal data class ScrollTarget(
	val messageId: Long,
	val itemIndex: Int,
	val requestToken: Long
)

internal class ScrollOrchestrator {
	var mode: ScrollOrchestratorMode = ScrollOrchestratorMode.Idle
		private set

	private var lastHandledAnchorToken: Long = 0L
	private var lastHandledAnchorId: Long? = null
	private var lastHandledHighlightToken: Long = 0L
	private var lastHandledHighlightId: Long? = null
	private var lastHandledScrollToken: Long = 0L

	fun onUserScrollStarted() {
		mode = ScrollOrchestratorMode.FollowUser
	}

	fun beginInitialAnchor() {
		mode = ScrollOrchestratorMode.InitialAnchorPlacement
	}

	fun beginPendingJump() {
		mode = ScrollOrchestratorMode.JumpPending
	}

	fun beginJump() {
		mode = ScrollOrchestratorMode.JumpToTarget
	}

	fun onProgrammaticActionFinished() {
		if (mode != ScrollOrchestratorMode.FollowUser) {
			mode = ScrollOrchestratorMode.Idle
		}
	}

	fun shouldHandleAnchor(target: ScrollTarget?, keepAnchored: Boolean): Boolean {
		target ?: return false
		val byToken = target.requestToken > 0L && target.requestToken != lastHandledAnchorToken
		val byId = target.messageId != lastHandledAnchorId
		return if (keepAnchored && mode == ScrollOrchestratorMode.FollowUser) {
			false
		} else {
			byToken || byId
		}
	}

	fun markAnchorHandled(target: ScrollTarget) {
		lastHandledAnchorToken = target.requestToken
		lastHandledAnchorId = target.messageId
	}

	fun shouldHandleHighlight(target: ScrollTarget?): Boolean {
		target ?: return false
		val byToken = target.requestToken > 0L && target.requestToken != lastHandledHighlightToken
		val byId = target.messageId != lastHandledHighlightId
		return byToken || byId
	}

	fun markHighlightHandled(target: ScrollTarget) {
		lastHandledHighlightToken = target.requestToken
		lastHandledHighlightId = target.messageId
	}

	fun shouldHandleScrollRequest(token: Long): Boolean {
		if (token <= 0L) return false
		return token != lastHandledScrollToken
	}

	fun markScrollHandled(token: Long) {
		if (token > 0L) {
			lastHandledScrollToken = token
		}
	}
}
