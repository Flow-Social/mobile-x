package me.floow.shared.post.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PostTextLinkifyTest {

    @Test
    fun `findPostLinks extracts mentions`() {
        val text = "Hello @alice and @bob!"
        val links = findPostLinks(text)

        assertEquals(2, links.size)
        assertEquals("alice", links[0].value)
        assertEquals(TAG_PROFILE, links[0].tag)
        assertEquals(6, links[0].start)
        assertEquals(12, links[0].end)

        assertEquals("bob", links[1].value)
        assertEquals(TAG_PROFILE, links[1].tag)
        assertEquals(17, links[1].start)
        assertEquals(21, links[1].end)
    }

    @Test
    fun `findPostLinks extracts post links`() {
        val text = "Check this out https://flow.me/alice/post-123 cool"
        val links = findPostLinks(text)

        assertEquals(1, links.size)
        assertEquals("alice/post-123", links[0].value)
        assertEquals(TAG_POST, links[0].tag)
    }

    @Test
    fun `findPostLinks handles combined post links and mentions`() {
        val text = "Check https://flow.me/bob/post-456 and ping @alice"
        val links = findPostLinks(text)

        assertEquals(2, links.size)
        assertEquals(TAG_POST, links[0].tag)
        assertEquals("bob/post-456", links[0].value)
        assertEquals(TAG_PROFILE, links[1].tag)
        assertEquals("alice", links[1].value)
    }

    @Test
    fun `extractTitleAndBody does not break spoiler across boundary`() {
        val text = "||Секретный текст из нескольких слов тут||"
        val (title, body) = extractTitleAndBody(text)

        assertEquals("||Секретный текст из нескольких слов тут||", title)
        assertEquals("", body)
    }

    @Test
    fun `extractTitleAndBody keeps spoiler in body when it starts at boundary`() {
        val text = "Раз два три ||секретный текст из многих слов||"
        val (title, body) = extractTitleAndBody(text)

        assertEquals("Раз два три", title)
        assertEquals("||секретный текст из многих слов||", body)
    }

    @Test
    fun `extractTitleAndBody does not split mid-spoiler when spoiler spans 3rd word`() {
        val text = "Раз два ||секретный текст из многих слов|| конец"
        val (title, body) = extractTitleAndBody(text)

        assertEquals("Раз два", title)
        assertEquals("||секретный текст из многих слов|| конец", body)
    }
}
