package me.floow.comments.uilogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentOrderingTest {

	@Test
	fun `sortCommentsByOrder keeps ui order even when timestamps are equal`() {
		val first = FakeComment(id = "temp_1", createdAt = 1000L)
		val second = FakeComment(id = "temp_2", createdAt = 1000L)
		val third = FakeComment(id = "10", createdAt = 1000L)

		val orderState = createInitialCommentOrder(listOf(first, second, third).map { it.id })
		val sorted = sortCommentsByOrder(
			comments = listOf(third, second, first),
			orderById = orderState.orderById,
			idSelector = { it.id },
			createdAtSelector = { it.createdAt }
		)

		assertEquals(listOf("temp_1", "temp_2", "10"), sorted.map { it.id })
	}

	@Test
	fun `transferOrderBetweenComments preserves position when optimistic id is replaced`() {
		val optimistic = FakeComment(id = "temp_42", createdAt = 1000L)
		val another = FakeComment(id = "temp_99", createdAt = 1001L)
		val server = FakeComment(id = "501", createdAt = 1005L)

		val initialOrder = createInitialCommentOrder(listOf(optimistic, another).map { it.id })
		val transferred = transferOrderBetweenComments(
			state = initialOrder,
			fromCommentId = optimistic.id,
			toCommentId = server.id
		)
		val sorted = sortCommentsByOrder(
			comments = listOf(another, server),
			orderById = transferred.orderById,
			idSelector = { it.id },
			createdAtSelector = { it.createdAt }
		)

		assertEquals(listOf("501", "temp_99"), sorted.map { it.id })
	}

	@Test
	fun `appendMissingOrderForComments adds keys only for new comments`() {
		val first = FakeComment(id = "1", createdAt = 1L)
		val second = FakeComment(id = "2", createdAt = 2L)
		val third = FakeComment(id = "3", createdAt = 3L)

		val initialOrder = createInitialCommentOrder(listOf(first, second).map { it.id })
		val extended = appendMissingOrderForComments(
			state = initialOrder,
			commentIds = listOf(first, second, third).map { it.id }
		)

		assertEquals(0L, extended.orderById.getValue("1"))
		assertEquals(1L, extended.orderById.getValue("2"))
		assertEquals(2L, extended.orderById.getValue("3"))
		assertTrue(extended.nextOrder >= 3L)
	}
}

private data class FakeComment(
	val id: String,
	val createdAt: Long
)
