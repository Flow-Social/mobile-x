package me.floow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostImageVariantsTest {

	@Test
	fun `resolvedImageVariants preserves raw url as fullUrl without synthetic lq`() {
		val content = PostContent(
			imageUrls = listOf("https://cdn.example.com/post_42_full.jpg?token=abc"),
			description = null
		)

		val variant = content.resolvedImageVariants().single()

		assertEquals("https://cdn.example.com/post_42_full.jpg?token=abc", variant.fullUrl)
		assertEquals(null, variant.thumbUrl)
		assertEquals("https://cdn.example.com/post_42_full.jpg?token=abc", variant.effectiveThumbUrl)
		assertEquals("https://cdn.example.com/post_42_full.jpg?token=abc", variant.targetUrl)
	}

	@Test
	fun `thumbUrl with base64 data URI resolves correctly as effectiveThumbUrl`() {
		val dataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ..."
		val variant = PostImageVariant(
			thumbUrl = dataUri,
			fullUrl = "https://cdn.example.com/full.jpg"
		)
		assertEquals(dataUri, variant.effectiveThumbUrl)
		assertEquals("https://cdn.example.com/full.jpg", variant.targetUrl)
		assertEquals("https://cdn.example.com/full.jpg", variant.detailUrl)
	}

	@Test
	fun `resolvedImageVariants normalizes server variants with thumbs`() {
		val dataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ..."
		val content = PostContent(
			imageUrls = emptyList(),
			description = null,
			imageVariants = listOf(
				PostImageVariant(
					thumbUrl = dataUri,
					fullUrl = "https://cdn.example.com/post_1_full.jpg"
				)
			)
		)

		val variant = content.resolvedImageVariants().single()

		assertEquals(dataUri, variant.thumbUrl)
		assertEquals("https://cdn.example.com/post_1_full.jpg", variant.fullUrl)
	}

	@Test
	fun `viewerImageUrls uses fullUrl`() {
		val content = PostContent(
			imageUrls = emptyList(),
			description = null,
			imageVariants = listOf(
				PostImageVariant(fullUrl = "https://cdn.example.com/post_7_full.jpg")
			)
		)

		val urls = content.viewerImageUrls()

		assertEquals(listOf("https://cdn.example.com/post_7_full.jpg"), urls)
	}

	@Test
	fun `resolvedImageVariants keeps non suffix urls visible`() {
		val raw = "https://picsum.photos/400/600?id=77"
		val content = PostContent(
			imageUrls = listOf(raw),
			description = null
		)

		val variant = content.resolvedImageVariants().single()

		assertEquals(raw, variant.fullUrl)
		assertTrue(content.previewImageUrls().isNotEmpty())
	}
}
