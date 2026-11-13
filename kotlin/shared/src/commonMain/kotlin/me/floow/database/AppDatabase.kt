package me.floow.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import me.floow.database.dao.FeedStackSnapshotDao
import me.floow.database.dao.FeedSyncCommandsDao
import me.floow.database.dao.PendingPostMutationDao
import me.floow.database.dao.PostsDao
import me.floow.database.dao.ProfileDao
import me.floow.database.dao.RepliesInboxDao
import me.floow.database.dao.DirectChatsDao
import me.floow.database.dbo.DirectChatConversationEntity
import me.floow.database.dbo.DirectChatMessageEntity
import me.floow.database.dbo.DirectChatOutboxCommandEntity
import me.floow.database.dbo.DirectChatReadStateEntity
import me.floow.database.dbo.FeedStackSnapshotEntity
import me.floow.database.dbo.FeedSyncCommandEntity
import me.floow.database.dbo.PendingPostMutationEntity
import me.floow.database.dbo.PostEntity
import me.floow.database.dbo.ProfileEntity
import me.floow.database.dbo.RepliesInboxMetaEntity
import me.floow.database.dbo.RepliesInboxNotificationEntity

@Database(
    entities = [
        ProfileEntity::class,
        PostEntity::class,
        FeedStackSnapshotEntity::class,
        FeedSyncCommandEntity::class,
        RepliesInboxNotificationEntity::class,
        RepliesInboxMetaEntity::class,
        DirectChatConversationEntity::class,
        DirectChatMessageEntity::class,
        DirectChatOutboxCommandEntity::class,
        DirectChatReadStateEntity::class,
        PendingPostMutationEntity::class
    ],
    version = 35,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun postsDao(): PostsDao
    abstract fun feedStackSnapshotDao(): FeedStackSnapshotDao
    abstract fun feedSyncCommandsDao(): FeedSyncCommandsDao
    abstract fun repliesInboxDao(): RepliesInboxDao
    abstract fun directChatsDao(): DirectChatsDao
    abstract fun pendingPostMutationDao(): PendingPostMutationDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
