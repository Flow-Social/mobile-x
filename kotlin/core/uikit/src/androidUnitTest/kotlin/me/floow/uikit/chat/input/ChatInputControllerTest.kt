package me.floow.uikit.chat.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputControllerTest {
    @Test
    fun emojiStaysMountedUntilShorterKeyboardSettles() {
        val controller = ChatInputController("", null, fallbackPanelHeightPx = 340)
        controller.openEmoji()
        controller.openKeyboard()
        controller.onImeHeightChanged(0)
        assertTrue(controller.showEmojiPanel)

        controller.onImeHeightChanged(240)
        assertEquals(340, controller.bottomPanelHeightPx(240))
        controller.finishEmojiKeyboardHandoff()

        assertFalse(controller.showEmojiPanel)
        assertEquals(240, controller.bottomPanelHeightPx(240))
        controller.openEmoji()
        controller.onImeHeightChanged(180)
        controller.onImeHeightChanged(0)
        assertEquals(240, controller.bottomPanelHeightPx(0))
    }

    @Test
    fun missingSoftwareKeyboardRestoresEmojiAndReleasesHandoff() {
        val controller = ChatInputController("", null, fallbackPanelHeightPx = 340)
        controller.openEmoji()
        controller.openKeyboard()
        controller.finishEmojiKeyboardHandoff()

        assertTrue(controller.showEmojiPanel)
        assertFalse(controller.isSwitchingEmojiToKeyboard)
        assertEquals(ChatInputMode.Emoji, controller.inputMode)
    }
}
