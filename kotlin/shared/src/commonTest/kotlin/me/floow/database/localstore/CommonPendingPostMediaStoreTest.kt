package me.floow.database.localstore

import kotlinx.coroutines.test.runTest
import me.floow.domain.data.repos.UploadImageData
import me.floow.shared.profile.image.InMemoryPendingPostFileStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CommonPendingPostMediaStoreTest {

    @Test
    fun stagesAndReadsFilesViaStorage() = runTest {
        val storage = InMemoryPendingPostFileStorage()
        val mediaStore = CommonPendingPostMediaStore(storage)

        val files = listOf(
            UploadImageData(
                fileName = "photo.jpg",
                contentType = "image/jpeg",
                sizeBytes = 100L,
                bytes = byteArrayOf(1, 2, 3, 4)
            )
        )

        val staged = mediaStore.stageFiles("cmd_1", files)
        assertEquals(1, staged.size)
        assertEquals("photo.jpg", staged[0].fileName)

        val readData = mediaStore.readUploadData(staged[0])
        assertNotNull(readData)
        assertEquals("photo.jpg", readData.fileName)
        assertEquals("image/jpeg", readData.contentType)
        assertEquals(0, readData.bytes.size)
        assertEquals(staged[0].absolutePath, readData.filePath)
        assertEquals(100L, readData.sizeBytes)

        // After process restart simulation: new store instance with same storage retains data!
        val newMediaStore = CommonPendingPostMediaStore(storage)
        val readDataAfterRestart = newMediaStore.readUploadData(staged[0])
        assertNotNull(readDataAfterRestart)
        assertEquals(0, readDataAfterRestart.bytes.size)
        assertEquals(staged[0].absolutePath, readDataAfterRestart.filePath)

        // After delete
        mediaStore.deleteStaged("cmd_1")
        assertNull(newMediaStore.readUploadData(staged[0]))
    }

    @Test
    fun stagesAndReadsFilesWithLocalPathViaStorage() = runTest {
        val storage = InMemoryPendingPostFileStorage()
        // Put simulated local file in storage
        val sourcePath = storage.saveFile("temp", "preparer_output.jpg", byteArrayOf(10, 20, 30))
        val mediaStore = CommonPendingPostMediaStore(storage)

        // UploadImageData as returned by AndroidLocalImageFileReader (bytes = empty, filePath set)
        val files = listOf(
            UploadImageData(
                fileName = "photo.jpg",
                contentType = "image/jpeg",
                sizeBytes = 3L,
                bytes = ByteArray(0),
                filePath = sourcePath
            )
        )

        val staged = mediaStore.stageFiles("cmd_2", files)
        assertEquals(1, staged.size)
        assertEquals("photo.jpg", staged[0].fileName)

        val readData = mediaStore.readUploadData(staged[0])
        assertNotNull(readData)
        assertEquals("photo.jpg", readData.fileName)
        assertEquals("image/jpeg", readData.contentType)
        assertEquals(0, readData.bytes.size)
        assertEquals(staged[0].absolutePath, readData.filePath)
        assertEquals(3L, readData.sizeBytes)
    }
}
