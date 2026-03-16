package me.floow.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS profiles (
                    userId TEXT NOT NULL PRIMARY KEY,
                    name TEXT,
                    username TEXT,
                    description TEXT,
                    avatarUrl TEXT,
                    totalLikesReceived INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS posts (
                    postId TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    authorId TEXT NOT NULL,
                    authorName TEXT,
                    authorUsername TEXT,
                    authorAvatarUrl TEXT,
                    imageUrl TEXT NOT NULL,
                    commenters_preview TEXT NOT NULL DEFAULT '[]',
                    description TEXT,
                    category TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    likesCount INTEGER NOT NULL,
                    commentsCount INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )

            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_posts_userId ON posts(userId)"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS posts_new (
                    postId TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    authorId TEXT NOT NULL,
                    authorName TEXT,
                    authorUsername TEXT,
                    authorAvatarUrl TEXT,
                    image_urls TEXT NOT NULL,
                    commenters_preview TEXT NOT NULL DEFAULT '[]',
                    description TEXT,
                    category TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    likesCount INTEGER NOT NULL,
                    commentsCount INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )

            val json = Json { encodeDefaults = true }
            val serializer = ListSerializer(String.serializer())

            val cursor = database.query(
                """
                SELECT postId, userId, authorId, authorName, authorUsername, authorAvatarUrl,
                       imageUrl, description, category, createdAt, likesCount, updatedAt
                FROM posts
                """.trimIndent()
            )
            cursor.use {
                val idxPostId = cursor.getColumnIndexOrThrow("postId")
                val idxUserId = cursor.getColumnIndexOrThrow("userId")
                val idxAuthorId = cursor.getColumnIndexOrThrow("authorId")
                val idxAuthorName = cursor.getColumnIndexOrThrow("authorName")
                val idxAuthorUsername = cursor.getColumnIndexOrThrow("authorUsername")
                val idxAuthorAvatarUrl = cursor.getColumnIndexOrThrow("authorAvatarUrl")
                val idxImageUrl = cursor.getColumnIndexOrThrow("imageUrl")
                val idxDescription = cursor.getColumnIndexOrThrow("description")
                val idxCategory = cursor.getColumnIndexOrThrow("category")
                val idxCreatedAt = cursor.getColumnIndexOrThrow("createdAt")
                val idxLikesCount = cursor.getColumnIndexOrThrow("likesCount")
                val idxUpdatedAt = cursor.getColumnIndexOrThrow("updatedAt")

                while (cursor.moveToNext()) {
                    val imageUrl = cursor.getString(idxImageUrl) ?: ""
                    val imageUrlsJson = json.encodeToString(serializer, listOf(imageUrl))

                    database.execSQL(
                        """
                        INSERT INTO posts_new(
                            postId, userId, authorId, authorName, authorUsername, authorAvatarUrl,
                            image_urls, commenters_preview, description, category, createdAt, likesCount, commentsCount, updatedAt
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """.trimIndent(),
                        arrayOf(
                            cursor.getString(idxPostId),
                            cursor.getString(idxUserId),
                            cursor.getString(idxAuthorId),
                            cursor.getString(idxAuthorName),
                            cursor.getString(idxAuthorUsername),
                            cursor.getString(idxAuthorAvatarUrl),
                            imageUrlsJson,
                            "[]",
                            cursor.getString(idxDescription),
                            cursor.getString(idxCategory),
                            cursor.getLong(idxCreatedAt),
                            cursor.getInt(idxLikesCount),
                            0,
                            cursor.getLong(idxUpdatedAt)
                        )
                    )
                }
            }

            database.execSQL("DROP TABLE posts")
            database.execSQL("ALTER TABLE posts_new RENAME TO posts")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_posts_userId ON posts(userId)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS posts_new (
                    postId TEXT NOT NULL,
                    userId TEXT NOT NULL,
                    authorId TEXT NOT NULL,
                    authorName TEXT,
                    authorUsername TEXT,
                    authorAvatarUrl TEXT,
                    image_urls TEXT NOT NULL,
                    commenters_preview TEXT NOT NULL DEFAULT '[]',
                    description TEXT,
                    category TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    likesCount INTEGER NOT NULL,
                    commentsCount INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY (postId, userId)
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                INSERT INTO posts_new(
                    postId, userId, authorId, authorName, authorUsername, authorAvatarUrl,
                    image_urls, commenters_preview, description, category, createdAt, likesCount, commentsCount, updatedAt
                )
                SELECT
                    postId, userId, authorId, authorName, authorUsername, authorAvatarUrl,
                    image_urls, '[]', description, category, createdAt, likesCount, 0, updatedAt
                FROM posts
                """.trimIndent()
            )

            database.execSQL("DROP TABLE posts")
            database.execSQL("ALTER TABLE posts_new RENAME TO posts")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_posts_userId ON posts(userId)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE posts ADD COLUMN commentsCount INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE posts ADD COLUMN commenters_preview TEXT NOT NULL DEFAULT '[]'"
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE profiles ADD COLUMN backgroundUrl TEXT"
            )
            database.execSQL(
                "ALTER TABLE profiles ADD COLUMN backgroundUpdatedAt INTEGER"
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE profiles ADD COLUMN totalLikesReceived INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE posts ADD COLUMN image_variants TEXT NOT NULL DEFAULT '[]'"
            )
        }
    }

	val MIGRATION_9_10 = object : Migration(9, 10) {
		override fun migrate(database: SupportSQLiteDatabase) {
			database.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS feed_sync_commands (
					id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
					userId TEXT NOT NULL,
					commandType TEXT NOT NULL,
					postId TEXT NOT NULL,
					isLiked INTEGER,
					createdAt INTEGER NOT NULL,
					attempts INTEGER NOT NULL DEFAULT 0,
					nextAttemptAt INTEGER NOT NULL DEFAULT 0
				)
				""".trimIndent()
			)

			database.execSQL(
				"CREATE INDEX IF NOT EXISTS index_feed_sync_commands_userId_id ON feed_sync_commands(userId, id)"
			)
		}
	}

	val MIGRATION_10_11 = object : Migration(10, 11) {
		override fun migrate(database: SupportSQLiteDatabase) {
			if (!hasColumn(database, "feed_sync_commands", "nextAttemptAt")) {
				database.execSQL(
					"ALTER TABLE feed_sync_commands ADD COLUMN nextAttemptAt INTEGER NOT NULL DEFAULT 0"
				)
			}
			database.execSQL(
				"UPDATE feed_sync_commands SET nextAttemptAt = createdAt WHERE nextAttemptAt = 0"
			)
		}
	}

	val MIGRATION_11_12 = object : Migration(11, 12) {
		override fun migrate(database: SupportSQLiteDatabase) {
			database.execSQL(
				"CREATE INDEX IF NOT EXISTS index_feed_sync_commands_userId_nextAttemptAt_id ON feed_sync_commands(userId, nextAttemptAt, id)"
			)
		}
	}

	val MIGRATION_12_13 = object : Migration(12, 13) {
		override fun migrate(database: SupportSQLiteDatabase) {
			database.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS replies_inbox_notifications (
					seq INTEGER NOT NULL PRIMARY KEY,
					id TEXT NOT NULL,
					type TEXT NOT NULL,
					channel TEXT NOT NULL,
					actor_id TEXT NOT NULL,
					actor_username TEXT,
					actor_name TEXT,
					actor_avatar_url TEXT,
					post_id TEXT NOT NULL,
					comment_id TEXT NOT NULL,
					thread_id TEXT NOT NULL,
					reply_to_comment_id TEXT,
					comment_text TEXT,
					reply_to_comment_text TEXT,
					title TEXT NOT NULL,
					body TEXT NOT NULL,
					is_read INTEGER NOT NULL,
					read_at INTEGER,
					created_at INTEGER NOT NULL,
					updated_at INTEGER NOT NULL
				)
				""".trimIndent()
			)
			database.execSQL(
				"CREATE INDEX IF NOT EXISTS index_replies_inbox_notifications_created_at ON replies_inbox_notifications(created_at)"
			)
			database.execSQL(
				"CREATE INDEX IF NOT EXISTS index_replies_inbox_notifications_is_read_seq ON replies_inbox_notifications(is_read, seq)"
			)
			database.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS replies_inbox_meta (
					channel TEXT NOT NULL PRIMARY KEY,
					last_read_seq INTEGER NOT NULL,
					unread_count INTEGER NOT NULL,
					first_unread_seq INTEGER,
					max_seq INTEGER NOT NULL,
					updated_at INTEGER NOT NULL
				)
				""".trimIndent()
			)
		}
	}

	val MIGRATION_13_14 = object : Migration(13, 14) {
		override fun migrate(database: SupportSQLiteDatabase) {
			database.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS direct_chat_conversations (
					id INTEGER NOT NULL PRIMARY KEY,
					kind TEXT NOT NULL,
					peer_id TEXT NOT NULL,
					peer_username TEXT,
					peer_name TEXT,
					peer_avatar_url TEXT,
					last_message_id INTEGER,
					last_message_sender_id TEXT,
					last_message_sender_username TEXT,
					last_message_sender_name TEXT,
					last_message_sender_avatar_url TEXT,
					last_message_text TEXT,
					last_message_created_at INTEGER,
					last_message_updated_at INTEGER,
					unread_count INTEGER NOT NULL,
					last_read_message_id INTEGER NOT NULL,
					created_at INTEGER NOT NULL,
					updated_at INTEGER NOT NULL
				)
				""".trimIndent()
			)
			database.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_conversations_updated_at ON direct_chat_conversations(updated_at)"
			)
			database.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS direct_chat_messages (
					id INTEGER NOT NULL PRIMARY KEY,
					conversation_id INTEGER NOT NULL,
					sender_id TEXT NOT NULL,
					sender_username TEXT,
					sender_name TEXT,
					sender_avatar_url TEXT,
					text TEXT NOT NULL,
					created_at INTEGER NOT NULL,
					updated_at INTEGER NOT NULL
				)
				""".trimIndent()
			)
			database.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_id ON direct_chat_messages(conversation_id, id)"
			)
			database.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_created_at ON direct_chat_messages(conversation_id, created_at)"
			)
			database.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS direct_chat_read_state (
					conversation_id INTEGER NOT NULL PRIMARY KEY,
					last_read_message_id INTEGER NOT NULL,
					unread_count INTEGER NOT NULL,
					first_unread_id INTEGER,
					max_message_id INTEGER NOT NULL,
					updated_at INTEGER NOT NULL
				)
				""".trimIndent()
			)
		}
	}

	val MIGRATION_14_15 = object : Migration(14, 15) {
		override fun migrate(database: SupportSQLiteDatabase) {
			if (!hasColumn(database, "direct_chat_messages", "reply_to_message_id")) {
				database.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN reply_to_message_id INTEGER"
				)
			}
			if (!hasColumn(database, "direct_chat_messages", "reply_to_message_text")) {
				database.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN reply_to_message_text TEXT"
				)
			}
		}
	}

	val MIGRATION_15_16 = object : Migration(15, 16) {
		override fun migrate(database: SupportSQLiteDatabase) {
			if (!hasColumn(database, "direct_chat_messages", "is_pinned")) {
				database.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0"
				)
			}
			if (!hasColumn(database, "direct_chat_messages", "pinned_at")) {
				database.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN pinned_at INTEGER"
				)
			}
			if (!hasColumn(database, "direct_chat_messages", "pinned_by_user_id")) {
				database.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN pinned_by_user_id TEXT"
				)
			}
			database.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_pinned_at ON direct_chat_messages(conversation_id, pinned_at DESC, id DESC)"
			)
		}
	}

	val MIGRATION_16_17 = object : Migration(16, 17) {
		override fun migrate(database: SupportSQLiteDatabase) {
			if (!hasColumn(database, "direct_chat_conversations", "peer_last_read_message_id")) {
				database.execSQL(
					"ALTER TABLE direct_chat_conversations ADD COLUMN peer_last_read_message_id INTEGER"
				)
			}
		}
	}

	val MIGRATION_17_18 = object : Migration(17, 18) {
		override fun migrate(database: SupportSQLiteDatabase) {
			if (!hasColumn(database, "direct_chat_messages", "client_message_id")) {
				database.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN client_message_id TEXT"
				)
			}
		}
	}

	val MIGRATION_18_19 = object : Migration(18, 19) {
		override fun migrate(database: SupportSQLiteDatabase) {
			database.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_client_message_id " +
					"ON direct_chat_messages(conversation_id, client_message_id)"
			)
		}
	}

	private fun hasColumn(
		database: SupportSQLiteDatabase,
		tableName: String,
		columnName: String
	): Boolean {
		database.query("PRAGMA table_info($tableName)").use { cursor ->
			val nameIndex = cursor.getColumnIndex("name")
			if (nameIndex == -1) return false
			while (cursor.moveToNext()) {
				if (cursor.getString(nameIndex) == columnName) {
					return true
				}
			}
		}
		return false
	}
}
