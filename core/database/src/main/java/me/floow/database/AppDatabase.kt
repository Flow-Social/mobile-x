package me.floow.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.floow.database.dao.FeedSyncCommandsDao
import me.floow.database.dao.PostsDao
import me.floow.database.dao.ProfileDao
import me.floow.database.dbo.FeedSyncCommandEntity
import me.floow.database.dbo.PostEntity
import me.floow.database.dbo.ProfileEntity
import me.floow.database.dbo.TestDbo

@Database(
    entities = [
        TestDbo::class,
        ProfileEntity::class,
        PostEntity::class,
		FeedSyncCommandEntity::class
    ],
    version = 12
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun postsDao(): PostsDao
	abstract fun feedSyncCommandsDao(): FeedSyncCommandsDao
}
