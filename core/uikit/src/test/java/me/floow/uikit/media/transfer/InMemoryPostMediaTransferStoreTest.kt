package me.floow.uikit.media.transfer

import me.floow.uikit.components.media.transfer.InMemoryPostMediaTransferStore
import me.floow.uikit.components.media.transfer.PostMediaSourceOwner
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryPostMediaTransferStoreTest {

	@Test
	fun `consume returns snapshot once`() {
		val store = InMemoryPostMediaTransferStore(ttlMs = 90_000L)
		val snapshot = sampleSnapshot(postId = "p1")

		val token = store.save(snapshot)
		val first = store.consume(token)
		val second = store.consume(token)

		assertNotNull(first)
		assertEquals("p1", first?.postId)
		assertNull(second)
	}

	@Test
	fun `new save replaces previous entry because capacity is one`() {
		val store = InMemoryPostMediaTransferStore(ttlMs = 90_000L)
		val firstToken = store.save(sampleSnapshot(postId = "p1"))
		val secondToken = store.save(sampleSnapshot(postId = "p2"))

		assertNull(store.consume(firstToken))
		assertEquals("p2", store.consume(secondToken)?.postId)
	}

	@Test
	fun `expired snapshot is not returned`() {
		val store = InMemoryPostMediaTransferStore(ttlMs = 1L)
		val token = store.save(sampleSnapshot(postId = "p1"))

		Thread.sleep(5L)

		assertNull(store.consume(token))
		assertNull(store.peek("p1"))
	}

	@Test
	fun `peek does not consume snapshot`() {
		val store = InMemoryPostMediaTransferStore(ttlMs = 90_000L)
		val token = store.save(sampleSnapshot(postId = "p1"))

		val peeked = store.peek("p1")
		val consumed = store.consume(token)

		assertEquals("p1", peeked?.postId)
		assertEquals("p1", consumed?.postId)
	}

	private fun sampleSnapshot(postId: String): PostMediaSourceSnapshot {
		return PostMediaSourceSnapshot(
			postId = postId,
			owner = PostMediaSourceOwner.POST,
			selectedIndex = 0,
			urls = listOf("https://example.com/$postId/full.jpg"),
			previewUrls = listOf("https://example.com/$postId/preview.jpg")
		)
	}
}
