package me.floow.domain.deeplink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkUrlsTest {

	@Test
	fun `supported web urls include primary host`() {
		assertTrue(DeepLinkUrls.isSupportedWebUrl("https://floow.me/bogdan/post-123"))
	}

	@Test
	fun `supported web urls include legacy host`() {
		assertTrue(DeepLinkUrls.isSupportedWebUrl("https://flow-social.github.io/bogdan/post-123"))
	}

	@Test
	fun `unsupported hosts are rejected`() {
		assertFalse(DeepLinkUrls.isSupportedWebUrl("https://example.com/bogdan/post-123"))
	}
}
