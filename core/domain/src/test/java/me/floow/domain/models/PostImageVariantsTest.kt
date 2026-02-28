package me.floow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostImageVariantsTest {

	@Test
	fun `resolvedImageVariants derives lq and preview from full suffix with query`() {
		val content = PostContent(
			imageUrls = listOf("https://cdn.example.com/post_42_full.jpg?token=abc"),
			description = null
		)

		val variant = content.resolvedImageVariants().single()

		assertEquals("https://cdn.example.com/post_42_lq.jpg?token=abc", variant.lqUrl)
		assertEquals("https://cdn.example.com/post_42_preview.jpg?token=abc", variant.previewUrl)
		assertEquals("https://cdn.example.com/post_42_full.jpg?token=abc", variant.fullUrl)
	}

	@Test
	fun `resolvedImageVariants normalizes incomplete server variants to visible urls`() {
		val content = PostContent(
			imageUrls = emptyList(),
			description = null,
			imageVariants = listOf(
				PostImageVariant(fullUrl = "https://cdn.example.com/post_1_full.jpg")
			)
		)

		val variant = content.resolvedImageVariants().single()

		assertEquals("https://cdn.example.com/post_1_full.jpg", variant.lqUrl)
		assertEquals("https://cdn.example.com/post_1_full.jpg", variant.previewUrl)
		assertEquals("https://cdn.example.com/post_1_full.jpg", variant.fullUrl)
	}

	@Test
	fun `viewerImageUrls falls back to preview when full is absent`() {
		val content = PostContent(
			imageUrls = emptyList(),
			description = null,
			imageVariants = listOf(
				PostImageVariant(previewUrl = "https://cdn.example.com/post_7_preview.jpg")
			)
		)

		val urls = content.viewerImageUrls()

		assertEquals(listOf("https://cdn.example.com/post_7_preview.jpg"), urls)
	}

	@Test
	fun `resolvedImageVariants keeps non suffix urls visible`() {
		val raw = "https://picsum.photos/400/600?id=77"
		val content = PostContent(
			imageUrls = listOf(raw),
			description = null
		)

		val variant = content.resolvedImageVariants().single()

		assertEquals(raw, variant.lqUrl)
		assertEquals(raw, variant.previewUrl)
		assertEquals(raw, variant.fullUrl)
		assertTrue(content.previewImageUrls().isNotEmpty())
	}
}
