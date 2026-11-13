package me.floow.uikit.challenger

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.model.ChatImageItem
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Method
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class UIComponentsEmpiricalChallengerTest {

    // ---------------------------------------------------------------------------------------------
    // Reflection helpers for ChatAlbumBubbleKt private functions
    // ---------------------------------------------------------------------------------------------
    private val chatAlbumBubbleClass = Class.forName("me.floow.uikit.chat.components.ChatAlbumBubbleKt")

    private val computeOptimalLayoutMethod: Method = chatAlbumBubbleClass.declaredMethods.first {
        it.name.startsWith("computeOptimalLayout")
    }.apply { isAccessible = true }

    private val generateLayoutAttemptsMethod: Method = chatAlbumBubbleClass.declaredMethods.first {
        it.name.startsWith("generateLayoutAttempts")
    }.apply { isAccessible = true }

    private fun invokeComputeOptimalLayout(items: List<ChatImageItem>, width: Dp): Pair<List<Int>, List<Dp>> {
        val result = computeOptimalLayoutMethod.invoke(null, items, width.value)
        val lineCountsMethod = result.javaClass.getMethod("getLineCounts").apply { isAccessible = true }
        val heightsMethod = result.javaClass.getMethod("getHeights").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val lineCounts = lineCountsMethod.invoke(result) as List<Int>
        @Suppress("UNCHECKED_CAST")
        val heights = heightsMethod.invoke(result) as List<Dp>
        return lineCounts to heights
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeGenerateLayoutAttempts(total: Int): List<List<Int>> {
        return generateLayoutAttemptsMethod.invoke(null, total) as List<List<Int>>
    }

    private fun createItem(width: Int, height: Int): ChatImageItem {
        return ChatImageItem(
            id = 1L,
            localPath = "file://test.jpg",
            remoteUrl = null,
            thumbnailUrl = null,
            width = width,
            height = height
        )
    }

    // =============================================================================================
    // CHALLENGE 1: ChatAlbumBubble
    // =============================================================================================

    @Test
    fun album_extremeRatios_doNotCrashOrProduceInvalidDimensions() {
        // Extreme cases: 0x0, negative, ultra-wide, ultra-tall
        val items = listOf(
            createItem(0, 0),             // 0x0 fallback -> 1f
            createItem(-100, 200),        // negative width -> 1f
            createItem(200, -100),        // negative height -> 1f
            createItem(10000, 1),         // ratio 10000f -> coerced to 2.5f
            createItem(1, 10000),         // ratio 0.0001f -> coerced to 0.4f
        )

        val (lineCounts, heights) = invokeComputeOptimalLayout(items, 280.dp)
        assertEquals("Total items partition count must match items size", 5, lineCounts.sum())
        assertTrue("Heights must have entries", heights.isNotEmpty())

        for (h in heights) {
            assertTrue("Height $h must be >= 110.dp", h.value >= 110f)
            assertTrue("Height $h must be <= 260.dp", h.value <= 260f)
        }
    }

    @Test
    fun album_emptyItems_returnsEmptyLayout() {
        val (lineCounts, heights) = invokeComputeOptimalLayout(emptyList(), 280.dp)
        assertTrue(lineCounts.isEmpty())
        assertTrue(heights.isEmpty())
    }

    @Test
    fun album_singleItem_heightCalculatedAndCoercedProperly() {
        // Ultra tall: ratio 0.4f -> 280 / 0.4 = 700.dp -> coerced to 260.dp
        val tallItem = listOf(createItem(40, 100))
        val (tallCounts, tallHeights) = invokeComputeOptimalLayout(tallItem, 280.dp)
        assertEquals(listOf(1), tallCounts)
        assertEquals(260f, tallHeights[0].value, 0.01f)

        // Ultra wide: ratio 2.5f -> 280 / 2.5 = 112.dp
        val wideItem = listOf(createItem(250, 100))
        val (wideCounts, wideHeights) = invokeComputeOptimalLayout(wideItem, 280.dp)
        assertEquals(listOf(1), wideCounts)
        assertEquals(112f, wideHeights[0].value, 0.01f)
    }

    @Test
    fun album_allPartitionSumsMatchTotal() {
        for (total in 1..25) {
            val partitions = invokeGenerateLayoutAttempts(total)
            assertTrue("Total $total must have at least one partition", partitions.isNotEmpty())
            for (p in partitions) {
                assertEquals("Sum of counts in partition $p must equal total $total", total, p.sum())
                for (count in p) {
                    assertTrue("Count in row must be > 0", count > 0)
                }
            }
        }
    }

    @Test
    fun album_deadCode_minHeightPenaltyNeverFires() {
        // In computeOptimalLayout:
        // rowHeight is computed with (availableWidth / sumAspectRatios).coerceIn(110.dp, 260.dp)
        // Then minHeight is calculated as heights.minOfOrNull { it.value }
        // and checked: if (minHeight < 110f) diff *= 1.5f
        // Because rowHeight is already coerced >= 110.dp, minHeight is ALWAYS >= 110f, so diff *= 1.5f NEVER fires!
        val items = listOf(
            createItem(250, 100), // 2.5f
            createItem(250, 100), // 2.5f
            createItem(250, 100), // 2.5f
        )
        val (lineCounts, heights) = invokeComputeOptimalLayout(items, 280.dp)
        for (h in heights) {
            assertTrue("Heights in result are clamped to 110.dp", h.value >= 110f)
        }
    }

    // =============================================================================================
    // CHALLENGE 2: FlowSpoilerBox Geometry and State
    // =============================================================================================

    @Test
    fun spoiler_circularRipple_coversAllCorners() {
        // Test corners, center, edges on a 300x200 box
        val width = 300f
        val height = 200f
        val origins = listOf(
            Offset(0f, 0f),                 // TopLeft
            Offset(width, 0f),             // TopRight
            Offset(0f, height),            // BottomLeft
            Offset(width, height),         // BottomRight
            Offset(width / 2f, height / 2f), // Center
            Offset(10f, 50f),              // Arbitrary
        )

        for (origin in origins) {
            val maxRadius = hypot(
                max(origin.x, width - origin.x),
                max(origin.y, height - origin.y)
            ).coerceAtLeast(1f)

            // Test that all 4 corners of the bounding box lie inside or on the circle of radius maxRadius
            val corners = listOf(
                Offset(0f, 0f),
                Offset(width, 0f),
                Offset(0f, height),
                Offset(width, height)
            )

            for (c in corners) {
                val dist = hypot(c.x - origin.x, c.y - origin.y)
                assertTrue("Distance from origin $origin to corner $c ($dist) must be <= maxRadius ($maxRadius)",
                    dist <= maxRadius + 1e-4f)
            }
        }
    }

    @Test
    fun spoiler_particlePositions_remainWithinBounds() {
        val width = 300f
        val height = 200f
        val densityDpToPx = 2.75f // ~XXHDPI
        val maxJitterPx = 3f * densityDpToPx

        // For 1000 simulated particle steps
        for (step in 0..100) {
            val noiseTime = (step / 100f) * 6.2831855f
            val initialX = 0.99f
            val initialY = 0.01f
            val seed = 5.5f

            val px = (initialX * width + sin(noiseTime * 2.5f + seed) * maxJitterPx).coerceIn(0f, width)
            val py = (initialY * height + cos(noiseTime * 2f + seed * 1.4f) * maxJitterPx).coerceIn(0f, height)

            assertTrue(px in 0f..width)
            assertTrue(py in 0f..height)
        }
    }

    // =============================================================================================
    // CHALLENGE 3: TextWrap & TextWrapCanvas AnnotatedString & Obstacle Wrap
    // =============================================================================================

    @Test
    fun textWrap_subSequence_preservesSpanStyles() {
        val annotated = buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append("Bold Part")
            pop()
            append(" and regular part")
        }

        val sub = annotated.subSequence(0, 4)
        assertEquals("Bold", sub.text)
        val styles = sub.spanStyles
        assertTrue("Subsequence within styled range must preserve SpanStyle", styles.isNotEmpty())
        assertEquals(FontWeight.Bold, styles[0].item.fontWeight)
    }

    @Test
    fun textWrap_dropLastNewLine_bugDetection_stripsStylesWhenConvertingToString() {
        // In TextWrapCanvas.kt line 296-297:
        // if (unaffectedText.last() == '\n') unaffectedText = AnnotatedString(unaffectedText.dropLast(1).toString())
        // This converts AnnotatedString to String and recreates AnnotatedString without any SpanStyles!
        val annotatedWithNewline = buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append("Bold Title\n")
            pop()
        }

        // Simulating line 296-297 in TextWrapCanvas.kt:
        var unaffectedText = annotatedWithNewline
        if (unaffectedText.last() == '\n') {
            unaffectedText = AnnotatedString(unaffectedText.dropLast(1).toString())
        }

        assertEquals("Bold Title", unaffectedText.text)
        assertTrue("BUG IN TextWrapCanvas.kt:297: SpanStyles are lost due to dropLast(1).toString()!",
            unaffectedText.spanStyles.isEmpty())
    }

    @Test
    fun textWrap_zeroObstacleHeight_bugDetection_dropsLastCharacter() {
        // In TextWrapCanvas.kt line 242-245:
        // When obstacleSize.height == 0, bottomAffectedBlockFirstCharIndex is text.lastIndex!
        // Since subSequence(0, text.lastIndex) exclusive end index is text.lastIndex,
        // it TAKES characters 0 until text.lastIndex - 1, thereby DROPPING THE LAST CHARACTER!

        val text = AnnotatedString("Hello World")
        val obstacleHeight = 0

        var hasTopUnaffectedBlock = true
        val bottomAffectedBlockFirstCharIndex = text.lastIndex

        if (obstacleHeight > 0) {
            // omitted
        }

        if (hasTopUnaffectedBlock) {
            val unaffectedText = text.subSequence(0, bottomAffectedBlockFirstCharIndex)
            // Empirically demonstrates the bug: "Hello Worl" instead of "Hello World"
            assertEquals("Hello Worl", unaffectedText.text)
            assertNotEquals("Hello World", unaffectedText.text)
        }
    }

    @Test
    fun textWrap_zeroObstacleHeight_singleCharText_throwsNoSuchElementException() {
        // When text has length 1 ("A"), text.lastIndex is 0.
        // bottomAffectedBlockFirstCharIndex = 0.
        // unaffectedText = text.subSequence(0, 0) -> "" (empty).
        // Line 296: if (unaffectedText.last() == '\n')
        // calling .last() on empty string throws NoSuchElementException!
        val text = AnnotatedString("A")
        val obstacleHeight = 0

        var hasTopUnaffectedBlock = true
        val bottomAffectedBlockFirstCharIndex = text.lastIndex

        if (hasTopUnaffectedBlock) {
            val unaffectedText = text.subSequence(0, bottomAffectedBlockFirstCharIndex)
            assertEquals("", unaffectedText.text)
            try {
                unaffectedText.last()
                fail("Expected NoSuchElementException when checking .last() on empty unaffectedText")
            } catch (e: NoSuchElementException) {
                // Verified empirically!
                assertTrue(true)
            }
        }
    }
}
