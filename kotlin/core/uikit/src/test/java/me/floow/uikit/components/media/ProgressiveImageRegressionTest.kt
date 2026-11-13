package me.floow.uikit.components.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveImageRegressionTest {

    @Test
    fun test1_thumbShownBeforeFull() {
        // 1. thumb показывается до full
        val coordinator = ProgressiveImageCoordinator(
            thumbUrl = "https://cdn.example.com/photo_lq.jpg",
            targetUrl = "https://cdn.example.com/photo_full.jpg"
        )

        // Изначально оба еще не загружены
        assertFalse(coordinator.isTargetLoaded)
        assertFalse(coordinator.isThumbLoaded)

        // Thumb загрузился первым
        coordinator.onThumbLoaded()
        assertTrue(coordinator.isThumbLoaded)
        assertFalse(coordinator.isTargetLoaded)
        // Thumb должен активно отображаться, target еще не видим
        assertTrue(coordinator.shouldRenderThumb)
        assertEquals(0f, coordinator.targetAlpha, 0.001f)
    }

    @Test
    fun test2_targetLoadingDoesNotHideThumb() {
        // 2. target loading не скрывает thumb
        val coordinator = ProgressiveImageCoordinator(
            thumbUrl = "https://cdn.example.com/photo_lq.jpg",
            targetUrl = "https://cdn.example.com/photo_full.jpg"
        )
        coordinator.onThumbLoaded()

        // Симулируем процесс загрузки target (несколько кадров / шагов)
        coordinator.onTargetLoading()

        // Thumb по-прежнему рендерится и не скрывается
        assertTrue(coordinator.shouldRenderThumb)
        assertFalse(coordinator.hasBlackFrame)
        assertEquals(0f, coordinator.targetAlpha, 0.001f)
    }

    @Test
    fun test3_thumbRemovedOnlyAfterSuccessfulTarget() {
        // 3. thumb удаляется только после успешного target (когда targetAlpha достиг 1f)
        val coordinator = ProgressiveImageCoordinator(
            thumbUrl = "https://cdn.example.com/photo_lq.jpg",
            targetUrl = "https://cdn.example.com/photo_full.jpg"
        )
        coordinator.onThumbLoaded()
        coordinator.onTargetLoaded()

        // Target загрузился, но crossfade в процессе (targetAlpha < 1f)
        coordinator.updateCrossfadeProgress(0.5f)
        assertTrue(coordinator.shouldRenderThumb) // Thumb ОБЯЗАН оставаться под target!

        // Crossfade завершился: targetAlpha == 1f
        coordinator.updateCrossfadeProgress(1.0f)
        assertFalse(coordinator.shouldRenderThumb) // Теперь thumb можно убрать
    }

    @Test
    fun test4_targetCacheHitDoesNotShowBlackFrame() {
        // 4. target cache hit не показывает black frame
        val coordinator = ProgressiveImageCoordinator(
            thumbUrl = "https://cdn.example.com/photo_lq.jpg",
            targetUrl = "https://cdn.example.com/photo_full.jpg"
        )

        // Target мгновенно готов из кэша памяти
        coordinator.onTargetImmediateCacheHit()

        assertTrue(coordinator.isTargetLoaded)
        assertEquals(1f, coordinator.targetAlpha, 0.001f)
        assertFalse(coordinator.hasBlackFrame)
        assertFalse(coordinator.shouldRenderThumb) // Thumb не нужен, full сразу виден
    }

    @Test
    fun test5_targetFailureKeepsThumb() {
        // 5. target failure оставляет thumb
        val coordinator = ProgressiveImageCoordinator(
            thumbUrl = "https://cdn.example.com/photo_lq.jpg",
            targetUrl = "https://cdn.example.com/photo_full.jpg"
        )
        coordinator.onThumbLoaded()
        coordinator.onTargetFailed()

        // При ошибке загрузки full thumb обязан остаться
        assertTrue(coordinator.shouldRenderThumb)
        assertFalse(coordinator.isTargetLoaded)
        assertFalse(coordinator.hasBlackFrame)
    }

    @Test
    fun test6_localPreviewShownUntilUploadCompletes() {
        // 6. local preview показывается до окончания upload
        val coordinator = ProgressiveImageCoordinator(
            thumbUrl = null,
            targetUrl = "content://media/external/images/123"
        )
        // Локальный preview мгновенно загрузился
        coordinator.onTargetLoaded()
        coordinator.updateCrossfadeProgress(1.0f)

        // Upload завершился в фоне, UI обновился с remote URLs
        coordinator.updateModels(
            newThumbUrl = "https://cdn.example.com/uploaded_thumb.jpg",
            newTargetUrl = "https://cdn.example.com/uploaded_full.jpg"
        )

        // До тех пор, пока remote не загрузился, локальный preview удерживается!
        assertTrue(coordinator.hasRetainedPreview)
        assertFalse(coordinator.hasBlackFrame)
        assertEquals("content://media/external/images/123", coordinator.effectiveThumbModel)

        // Когда remote full загрузился
        coordinator.onTargetLoaded()
        coordinator.updateCrossfadeProgress(1.0f)
        assertFalse(coordinator.hasRetainedPreview)
    }

    @Test
    fun test7_foreignPhotoShowsRemoteThumbBeforeFull() {
        // 7. чужая фотография показывает remote thumb до full
        val resolved = resolveProgressiveTargets(
            thumbUrl = "https://cdn.example.com/foreign_lq.jpg",
            targetUrl = "https://cdn.example.com/foreign_full.jpg"
        )
        assertEquals("https://cdn.example.com/foreign_lq.jpg", resolved.thumb)
        assertEquals("https://cdn.example.com/foreign_full.jpg", resolved.target)

        val coordinator = ProgressiveImageCoordinator(
            thumbUrl = resolved.thumb,
            targetUrl = resolved.target
        )
        coordinator.onThumbLoaded()
        assertTrue(coordinator.shouldRenderThumb)
        assertEquals("https://cdn.example.com/foreign_lq.jpg", coordinator.effectiveThumbModel)
    }

    @Test
    fun test8_resolveProgressiveTargetsDerivesThumbFromFullUrlWhenThumbAbsent() {
        // В Telegram-стандарте без thumbUrl синтетический _lq не генерируется (нет 404 на S3)
        val resolved = resolveProgressiveTargets(
            thumbUrl = null,
            targetUrl = "https://cdn.example.com/post_99_full.jpg?token=xyz"
        )
        assertNull(resolved.thumb)
        assertEquals("https://cdn.example.com/post_99_full.jpg?token=xyz", resolved.target)
    }
}
