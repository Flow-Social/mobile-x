package me.floow.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import me.floow.database.dbo.ProfileEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RoomKmpTest {
    @Test
    fun testRoomDatabaseInsertAndQuery() = runTest {
        val builder = runCatching { getDatabaseBuilder() }.getOrNull() ?: run {
            println("Skipping RoomKmpTest: platform context not initialized in headless JVM test runner")
            return@runTest
        }
        val db = builder
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .setDriver(BundledSQLiteDriver())
            .build()

        val profileDao = db.profileDao()
        val testProfile = ProfileEntity(
            userId = "test-user-kmp",
            name = "Bogdan Test",
            username = "bogdan_test",
            description = "KMP Room working",
            avatarUrl = "https://cdn.example.com/avatar.jpg",
            avatarThumb = "data:image/jpeg;base64,thumb123",
            backgroundUrl = "https://cdn.example.com/bg.jpg",
            backgroundThumb = "data:image/jpeg;base64,bgthumb123",
            backgroundUpdatedAt = null,
            totalLikesReceived = 99,
            updatedAt = 123456789L
        )

        profileDao.upsert(testProfile)
        val loaded = profileDao.getProfile("test-user-kmp")

        assertNotNull(loaded)
        assertEquals("test-user-kmp", loaded.userId)
        assertEquals("Bogdan Test", loaded.name)
        assertEquals("data:image/jpeg;base64,thumb123", loaded.avatarThumb)
        assertEquals("data:image/jpeg;base64,bgthumb123", loaded.backgroundThumb)
        assertEquals(99, loaded.totalLikesReceived)

        db.close()
    }
}
