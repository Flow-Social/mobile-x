package me.floow.uikit.chat

import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.model.ChatImageItem
import me.floow.uikit.chat.model.ChatMessageDeliveryStatus
import me.floow.uikit.chat.model.ImageMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.VoiceMessage
import me.floow.uikit.chat.states.ChatTimelineItem
import me.floow.uikit.chat.states.buildMessageLayoutStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageLayoutStateTest {

	private fun createOutMsg(id: Long, timeMs: Long, text: String = "test") = PrimaryOutMessage(
		id = id,
		uiKey = "msg_$id",
		messageText = text,
		createdAtMillis = timeMs,
		deliveryStatus = ChatMessageDeliveryStatus.SENT,
	)

	private fun createInMsg(id: Long, timeMs: Long, author: String = "alice", text: String = "test") = PrimaryInMessage(
		id = id,
		uiKey = "msg_$id",
		messageText = text,
		createdAtMillis = timeMs,
		authorUsername = author,
		authorName = author,
		deliveryStatus = ChatMessageDeliveryStatus.SENT,
	)

	private fun createVoiceMsg(id: Long, timeMs: Long, isOut: Boolean = true) = VoiceMessage(
		id = id,
		uiKey = "voice_$id",
		durationMs = 5000L,
		waveform = listOf(1, 2, 3),
		createdAtMillis = timeMs,
		isOutgoing = isOut,
		deliveryStatus = ChatMessageDeliveryStatus.SENT,
	)

	private fun createImageMsg(id: Long, timeMs: Long, isOut: Boolean = true) = ImageMessage(
		id = id,
		uiKey = "img_$id",
		images = listOf(
			ChatImageItem(id = id, remoteUrl = "https://example.com/img.jpg")
		),
		createdAtMillis = timeMs,
		isOutgoing = isOut,
		deliveryStatus = ChatMessageDeliveryStatus.SENT,
	)

	@Test
	fun testCaseA_threeOutgoingMessages_withinOneMinute() {
		val msg1 = createOutMsg(1, 1000000L, "first")
		val msg2 = createOutMsg(2, 1000020L, "second")
		val msg3 = createOutMsg(3, 1000040L, "third")

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(0L, msg3),
			ChatTimelineItem.MessageRow(0L, msg2),
			ChatTimelineItem.MessageRow(0L, msg1),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		val s1 = layoutStates[msg1.uiKey]!!
		val s2 = layoutStates[msg2.uiKey]!!
		val s3 = layoutStates[msg3.uiKey]!!

		assertTrue(s1.isFirstInCluster)
		assertFalse(s1.isLastInCluster)
		assertFalse(s1.showTail)
		assertFalse(s1.showTimestamp)
		assertEquals(2.dp, s1.spacingAfter)

		assertFalse(s2.isFirstInCluster)
		assertFalse(s2.isLastInCluster)
		assertTrue(s2.isMiddleInCluster)
		assertFalse(s2.showTail)
		assertFalse(s2.showTimestamp)
		assertEquals(2.dp, s2.spacingAfter)

		assertFalse(s3.isFirstInCluster)
		assertTrue(s3.isLastInCluster)
		assertTrue(s3.showTail)
		assertTrue(s3.showTimestamp)
		assertEquals(0.dp, s3.spacingAfter)
	}

	@Test
	fun testCaseB_threeOutgoingMessages_greaterThanOneMinute() {
		val msg1 = createOutMsg(1, 1000000L, "first")
		val msg2 = createOutMsg(2, 1070000L, "second")
		val msg3 = createOutMsg(3, 1150000L, "third")

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(0L, msg3),
			ChatTimelineItem.MessageRow(0L, msg2),
			ChatTimelineItem.MessageRow(0L, msg1),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		val s1 = layoutStates[msg1.uiKey]!!
		val s2 = layoutStates[msg2.uiKey]!!
		val s3 = layoutStates[msg3.uiKey]!!

		assertTrue(s1.isFirstInCluster)
		assertTrue(s1.isLastInCluster)
		assertTrue(s1.showTail)
		assertTrue(s1.showTimestamp)
		assertEquals(16.dp, s1.spacingAfter)

		assertTrue(s2.isFirstInCluster)
		assertTrue(s2.isLastInCluster)
		assertTrue(s2.showTail)
		assertTrue(s2.showTimestamp)
		assertEquals(16.dp, s2.spacingAfter)

		assertTrue(s3.isFirstInCluster)
		assertTrue(s3.isLastInCluster)
		assertTrue(s3.showTail)
		assertTrue(s3.showTimestamp)
		assertEquals(0.dp, s3.spacingAfter)
	}

	@Test
	fun testCaseC_and_D_directionSwitch_incomingOutgoing() {
		val msgIn = createInMsg(1, 1000000L, author = "alice", text = "hi")
		val msgOut = createOutMsg(2, 1000010L, text = "hello")

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(0L, msgOut),
			ChatTimelineItem.MessageRow(0L, msgIn),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		val sIn = layoutStates[msgIn.uiKey]!!
		val sOut = layoutStates[msgOut.uiKey]!!

		assertTrue(sIn.isLastInCluster)
		assertTrue(sIn.showTail)
		assertTrue(sIn.showTimestamp)
		assertEquals(16.dp, sIn.spacingAfter)

		assertTrue(sOut.isFirstInCluster)
		assertTrue(sOut.isLastInCluster)
		assertTrue(sOut.showTail)
		assertTrue(sOut.showTimestamp)
		assertEquals(0.dp, sOut.spacingAfter)
	}

	@Test
	fun testCaseE_textToVoice_sameAuthor_withinOneMinute() {
		val textMsg = createOutMsg(1, 1000000L, "Listen to this")
		val voiceMsg = createVoiceMsg(2, 1000020L, isOut = true)

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(0L, voiceMsg),
			ChatTimelineItem.MessageRow(0L, textMsg),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		val sText = layoutStates[textMsg.uiKey]!!
		val sVoice = layoutStates[voiceMsg.uiKey]!!

		// Voice message is an independent entity: text message terminates its own cluster
		assertTrue(sText.isFirstInCluster)
		assertTrue(sText.isLastInCluster)
		assertTrue(sText.showTail)
		assertTrue(sText.showTimestamp)
		assertEquals(16.dp, sText.spacingAfter)

		// Voice message starts and ends its own cluster
		assertTrue(sVoice.isFirstInCluster)
		assertTrue(sVoice.isLastInCluster)
		assertTrue(sVoice.showTail)
		assertTrue(sVoice.showTimestamp)
		assertEquals(0.dp, sVoice.spacingAfter)
	}

	@Test
	fun testCaseG_textToImage_sameAuthor_withinOneMinute() {
		val textMsg = createOutMsg(1, 1000000L, "Look at photo")
		val imgMsg = createImageMsg(2, 1000020L, isOut = true)

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(0L, imgMsg),
			ChatTimelineItem.MessageRow(0L, textMsg),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		val sText = layoutStates[textMsg.uiKey]!!
		val sImg = layoutStates[imgMsg.uiKey]!!

		// Image album is an independent entity: text message terminates its own cluster
		assertTrue(sText.isFirstInCluster)
		assertTrue(sText.isLastInCluster)
		assertTrue(sText.showTail)
		assertTrue(sText.showTimestamp)
		assertEquals(16.dp, sText.spacingAfter)

		// Image album starts and ends its own cluster
		assertTrue(sImg.isFirstInCluster)
		assertTrue(sImg.isLastInCluster)
		assertTrue(sImg.showTail)
		assertTrue(sImg.showTimestamp)
		assertEquals(0.dp, sImg.spacingAfter)
	}

	@Test
	fun testCaseJ_replyToText_sameAuthor_withinOneMinute() {
		val replyMsg = ReplyOutMessage(
			id = 1,
			uiKey = "reply_1",
			messageText = "answer",
			replyMessageId = 999L,
			replyMessageText = "question",
			createdAtMillis = 1000000L,
			deliveryStatus = ChatMessageDeliveryStatus.SENT,
		)
		val textMsg = createOutMsg(2, 1000020L, "followup")

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(0L, textMsg),
			ChatTimelineItem.MessageRow(0L, replyMsg),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		val sReply = layoutStates[replyMsg.uiKey]!!
		val sText = layoutStates[textMsg.uiKey]!!

		assertTrue(sReply.isFirstInCluster)
		assertFalse(sReply.isLastInCluster)
		assertFalse(sReply.showTail)
		assertEquals(2.dp, sReply.spacingAfter)

		assertTrue(sText.isLastInCluster)
		assertTrue(sText.showTail)
		assertTrue(sText.showTimestamp)
	}

	@Test
	fun testCaseK_textToReply_breaksCluster() {
		val textMsg = createOutMsg(1, 1000000L, "question")
		val replyMsg = ReplyOutMessage(
			id = 2,
			uiKey = "reply_2",
			messageText = "replying to something else",
			replyMessageId = 888L,
			replyMessageText = "other",
			createdAtMillis = 1000020L,
			deliveryStatus = ChatMessageDeliveryStatus.SENT,
		)

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(0L, replyMsg),
			ChatTimelineItem.MessageRow(0L, textMsg),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		val sText = layoutStates[textMsg.uiKey]!!
		val sReply = layoutStates[replyMsg.uiKey]!!

		// textMsg cannot group with subsequent replyMsg -> textMsg ends its own cluster
		assertTrue(sText.isFirstInCluster)
		assertTrue(sText.isLastInCluster)
		assertTrue(sText.showTail)
		assertTrue(sText.showTimestamp)
		assertEquals(16.dp, sText.spacingAfter)

		// replyMsg starts a new cluster
		assertTrue(sReply.isFirstInCluster)
		assertTrue(sReply.isLastInCluster)
		assertTrue(sReply.showTail)
		assertTrue(sReply.showTimestamp)
		assertEquals(0.dp, sReply.spacingAfter)
	}

	@Test
	fun testExactUserCluster_sixMessages() {
		val m1 = createOutMsg(270, 1789764679357L, "жопа")
		val m2 = createOutMsg(271, 1789764681329L, "жопа")
		val m3 = createOutMsg(272, 1789764686397L, "жопа")
		val m4 = createOutMsg(273, 1789764689069L, "пипец")
		val m5 = createOutMsg(274, 1789764692181L, "пиздец")
		val m6 = createOutMsg(275, 1789764696386L, "говно")

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(0L, m6),
			ChatTimelineItem.MessageRow(0L, m5),
			ChatTimelineItem.MessageRow(0L, m4),
			ChatTimelineItem.MessageRow(0L, m3),
			ChatTimelineItem.MessageRow(0L, m2),
			ChatTimelineItem.MessageRow(0L, m1),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		assertEquals(2.dp, layoutStates[m1.uiKey]!!.spacingAfter)
		assertEquals(2.dp, layoutStates[m2.uiKey]!!.spacingAfter)
		assertEquals(2.dp, layoutStates[m3.uiKey]!!.spacingAfter)
		assertEquals(2.dp, layoutStates[m4.uiKey]!!.spacingAfter)
		assertEquals(2.dp, layoutStates[m5.uiKey]!!.spacingAfter)
		assertEquals(0.dp, layoutStates[m6.uiKey]!!.spacingAfter)

		assertFalse(layoutStates[m5.uiKey]!!.showTail)
		assertTrue(layoutStates[m6.uiKey]!!.showTail)
	}

	@Test
	fun testImageAlbum_doesNotGroupWithText_hasTailAndTimestamp() {
		val imgMsg = createImageMsg(100, 1000000L, isOut = true)
		val textMsg = createOutMsg(101, 1000010L, "text after photo")

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(0L, textMsg),
			ChatTimelineItem.MessageRow(0L, imgMsg),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		val sImg = layoutStates[imgMsg.uiKey]!!
		val sText = layoutStates[textMsg.uiKey]!!

		assertTrue(sImg.isFirstInCluster)
		assertTrue(sImg.isLastInCluster)
		assertTrue(sImg.showTail)
		assertTrue(sImg.showTimestamp)
		assertEquals(16.dp, sImg.spacingAfter)

		assertTrue(sText.isFirstInCluster)
		assertTrue(sText.isLastInCluster)
		assertTrue(sText.showTail)
		assertTrue(sText.showTimestamp)
		assertEquals(0.dp, sText.spacingAfter)
	}

	@Test
	fun testVoiceMessage_doesNotGroupWithText_hasTailAndTimestamp() {
		val voiceMsg = createVoiceMsg(200, 1000000L, isOut = true)
		val textMsg = createOutMsg(201, 1000010L, "жопа")

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(0L, textMsg),
			ChatTimelineItem.MessageRow(0L, voiceMsg),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		val sVoice = layoutStates[voiceMsg.uiKey]!!
		val sText = layoutStates[textMsg.uiKey]!!

		assertTrue(sVoice.isFirstInCluster)
		assertTrue(sVoice.isLastInCluster)
		assertTrue(sVoice.showTail)
		assertTrue(sVoice.showTimestamp)
		assertEquals(16.dp, sVoice.spacingAfter)

		assertTrue(sText.isFirstInCluster)
		assertTrue(sText.isLastInCluster)
		assertTrue(sText.showTail)
		assertTrue(sText.showTimestamp)
		assertEquals(0.dp, sText.spacingAfter)
	}

	@Test
	fun testDateHeader_breaksCluster() {
		val m1 = createOutMsg(300, 1000000L, "msg before midnight")
		val m2 = createOutMsg(301, 1000020L, "msg after midnight")

		val timelineItems = listOf(
			ChatTimelineItem.MessageRow(1000000L, m2),
			ChatTimelineItem.DateHeader(1000000L),
			ChatTimelineItem.MessageRow(0L, m1),
		)

		val layoutStates = buildMessageLayoutStates(
			timelineItems = timelineItems,
			isReverseLayout = true,
			showAuthorHeaderForInMessages = true,
		)

		val s1 = layoutStates[m1.uiKey]!!
		val s2 = layoutStates[m2.uiKey]!!

		assertTrue(s1.isLastInCluster)
		assertTrue(s1.showTail)
		assertTrue(s1.showTimestamp)
		assertEquals(16.dp, s1.spacingAfter)

		assertTrue(s2.isFirstInCluster)
		assertTrue(s2.isLastInCluster)
		assertTrue(s2.showTail)
		assertTrue(s2.showTimestamp)
	}
}
