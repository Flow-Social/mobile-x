package me.floow.comments.uilogic

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentsMergeTest {

	@Test
	fun `mergeCommentsById updates existing comments by id`() {
		val existing = listOf(
			item(id = "1", text = "old"),
			item(id = "2", text = "same")
		)
		val incoming = listOf(
			item(id = "1", text = "new")
		)

		val merged = mergeByStableId(existing = existing, incoming = incoming, idSelector = TestItem::id)

		assertEquals(2, merged.size)
		assertEquals("new", merged.first { it.id == "1" }.text)
		assertEquals("same", merged.first { it.id == "2" }.text)
	}

	@Test
	fun `mergeCommentsById appends new comments in stable order`() {
		val existing = listOf(
			item(id = "1", text = "first"),
			item(id = "2", text = "second")
		)
		val incoming = listOf(
			item(id = "3", text = "third"),
			item(id = "4", text = "fourth")
		)

		val merged = mergeByStableId(existing = existing, incoming = incoming, idSelector = TestItem::id)

		assertEquals(listOf("1", "2", "3", "4"), merged.map(TestItem::id))
	}

	@Test
	fun `mergeCommentsById keeps latest duplicate from incoming batch`() {
		val existing = listOf(item(id = "1", text = "existing"))
		val incoming = listOf(
			item(id = "2", text = "first copy"),
			item(id = "2", text = "second copy")
		)

		val merged = mergeByStableId(existing = existing, incoming = incoming, idSelector = TestItem::id)

		assertEquals(2, merged.size)
		assertEquals("second copy", merged.first { it.id == "2" }.text)
	}
}

private data class TestItem(
	val id: String,
	val text: String
)

private fun item(id: String, text: String): TestItem = TestItem(id = id, text = text)
