package me.floow.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowNavHostSelfOwnershipTest {

	@Test
	fun `matches post ownership against stored self user id`() {
		assertTrue(isPostOwnedBySelf(postAuthorId = "self-user", selfUserId = "self-user"))
	}

	@Test
	fun `normalizes blank padding before comparing ids`() {
		assertTrue(isPostOwnedBySelf(postAuthorId = "  self-user ", selfUserId = "self-user  "))
	}

	@Test
	fun `treats missing self user id as foreign post`() {
		assertFalse(isPostOwnedBySelf(postAuthorId = "self-user", selfUserId = null))
		assertFalse(isPostOwnedBySelf(postAuthorId = "self-user", selfUserId = " "))
	}
}
