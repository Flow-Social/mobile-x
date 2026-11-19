package me.floow.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
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

            connection.execSQL(
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

            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_posts_userId ON posts(userId)"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
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

            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
            val serializer = ListSerializer(String.serializer())

            data class OldPostRow(
                val postId: String,
                val userId: String,
                val authorId: String,
                val authorName: String?,
                val authorUsername: String?,
                val authorAvatarUrl: String?,
                val imageUrl: String,
                val description: String?,
                val category: String,
                val createdAt: Long,
                val likesCount: Int,
                val updatedAt: Long
            )

            val rows = mutableListOf<OldPostRow>()
            connection.prepare(
                """
                SELECT postId, userId, authorId, authorName, authorUsername, authorAvatarUrl,
                       imageUrl, description, category, createdAt, likesCount, updatedAt
                FROM posts
                """.trimIndent()
            ).use { stmt ->
                while (stmt.step()) {
                    rows.add(
                        OldPostRow(
                            postId = stmt.getText(0),
                            userId = stmt.getText(1),
                            authorId = stmt.getText(2),
                            authorName = if (stmt.isNull(3)) null else stmt.getText(3),
                            authorUsername = if (stmt.isNull(4)) null else stmt.getText(4),
                            authorAvatarUrl = if (stmt.isNull(5)) null else stmt.getText(5),
                            imageUrl = if (stmt.isNull(6)) "" else stmt.getText(6),
                            description = if (stmt.isNull(7)) null else stmt.getText(7),
                            category = stmt.getText(8),
                            createdAt = stmt.getLong(9),
                            likesCount = stmt.getLong(10).toInt(),
                            updatedAt = stmt.getLong(11)
                        )
                    )
                }
            }

            for (row in rows) {
                val imageUrlsJson = json.encodeToString(serializer, listOf(row.imageUrl))
                connection.prepare(
                    """
                    INSERT INTO posts_new(
                        postId, userId, authorId, authorName, authorUsername, authorAvatarUrl,
                        image_urls, commenters_preview, description, category, createdAt, likesCount, commentsCount, updatedAt
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """.trimIndent()
                ).use { insertStmt ->
                    insertStmt.bindText(1, row.postId)
                    insertStmt.bindText(2, row.userId)
                    insertStmt.bindText(3, row.authorId)
                    if (row.authorName != null) insertStmt.bindText(4, row.authorName) else insertStmt.bindNull(4)
                    if (row.authorUsername != null) insertStmt.bindText(5, row.authorUsername) else insertStmt.bindNull(5)
                    if (row.authorAvatarUrl != null) insertStmt.bindText(6, row.authorAvatarUrl) else insertStmt.bindNull(6)
                    insertStmt.bindText(7, imageUrlsJson)
                    insertStmt.bindText(8, "[]")
                    if (row.description != null) insertStmt.bindText(9, row.description) else insertStmt.bindNull(9)
                    insertStmt.bindText(10, row.category)
                    insertStmt.bindLong(11, row.createdAt)
                    insertStmt.bindLong(12, row.likesCount.toLong())
                    insertStmt.bindLong(13, 0L)
                    insertStmt.bindLong(14, row.updatedAt)
                    insertStmt.step()
                }
            }

            connection.execSQL("DROP TABLE posts")
            connection.execSQL("ALTER TABLE posts_new RENAME TO posts")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_posts_userId ON posts(userId)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
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

            connection.execSQL(
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

            connection.execSQL("DROP TABLE posts")
            connection.execSQL("ALTER TABLE posts_new RENAME TO posts")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_posts_userId ON posts(userId)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE posts ADD COLUMN commentsCount INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE posts ADD COLUMN commenters_preview TEXT NOT NULL DEFAULT '[]'"
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE profiles ADD COLUMN backgroundUrl TEXT"
            )
            connection.execSQL(
                "ALTER TABLE profiles ADD COLUMN backgroundUpdatedAt INTEGER"
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE profiles ADD COLUMN totalLikesReceived INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE posts ADD COLUMN image_variants TEXT NOT NULL DEFAULT '[]'"
            )
        }
    }

	val MIGRATION_9_10 = object : Migration(9, 10) {
		override fun migrate(connection: SQLiteConnection) {
			connection.execSQL(
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

			connection.execSQL(
				"CREATE INDEX IF NOT EXISTS index_feed_sync_commands_userId_id ON feed_sync_commands(userId, id)"
			)
		}
	}

	val MIGRATION_10_11 = object : Migration(10, 11) {
		override fun migrate(connection: SQLiteConnection) {
			if (!hasColumn(connection, "feed_sync_commands", "nextAttemptAt")) {
				connection.execSQL(
					"ALTER TABLE feed_sync_commands ADD COLUMN nextAttemptAt INTEGER NOT NULL DEFAULT 0"
				)
			}
			connection.execSQL(
				"UPDATE feed_sync_commands SET nextAttemptAt = createdAt WHERE nextAttemptAt = 0"
			)
		}
	}

	val MIGRATION_11_12 = object : Migration(11, 12) {
		override fun migrate(connection: SQLiteConnection) {
			connection.execSQL(
				"CREATE INDEX IF NOT EXISTS index_feed_sync_commands_userId_nextAttemptAt_id ON feed_sync_commands(userId, nextAttemptAt, id)"
			)
		}
	}

	val MIGRATION_12_13 = object : Migration(12, 13) {
		override fun migrate(connection: SQLiteConnection) {
			connection.execSQL(
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
			connection.execSQL(
				"CREATE INDEX IF NOT EXISTS index_replies_inbox_notifications_created_at ON replies_inbox_notifications(created_at)"
			)
			connection.execSQL(
				"CREATE INDEX IF NOT EXISTS index_replies_inbox_notifications_is_read_seq ON replies_inbox_notifications(is_read, seq)"
			)
			connection.execSQL(
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
		override fun migrate(connection: SQLiteConnection) {
			connection.execSQL(
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
			connection.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_conversations_updated_at ON direct_chat_conversations(updated_at)"
			)
			connection.execSQL(
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
			connection.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_id ON direct_chat_messages(conversation_id, id)"
			)
			connection.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_created_at ON direct_chat_messages(conversation_id, created_at)"
			)
			connection.execSQL(
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
		override fun migrate(connection: SQLiteConnection) {
			if (!hasColumn(connection, "direct_chat_messages", "reply_to_message_id")) {
				connection.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN reply_to_message_id INTEGER"
				)
			}
			if (!hasColumn(connection, "direct_chat_messages", "reply_to_message_text")) {
				connection.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN reply_to_message_text TEXT"
				)
			}
		}
	}

	val MIGRATION_15_16 = object : Migration(15, 16) {
		override fun migrate(connection: SQLiteConnection) {
			if (!hasColumn(connection, "direct_chat_messages", "is_pinned")) {
				connection.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0"
				)
			}
			if (!hasColumn(connection, "direct_chat_messages", "pinned_at")) {
				connection.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN pinned_at INTEGER"
				)
			}
			if (!hasColumn(connection, "direct_chat_messages", "pinned_by_user_id")) {
				connection.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN pinned_by_user_id TEXT"
				)
			}
			connection.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_pinned_at ON direct_chat_messages(conversation_id, pinned_at DESC, id DESC)"
			)
		}
	}

	val MIGRATION_16_17 = object : Migration(16, 17) {
		override fun migrate(connection: SQLiteConnection) {
			if (!hasColumn(connection, "direct_chat_conversations", "peer_last_read_message_id")) {
				connection.execSQL(
					"ALTER TABLE direct_chat_conversations ADD COLUMN peer_last_read_message_id INTEGER"
				)
			}
		}
	}

	val MIGRATION_17_18 = object : Migration(17, 18) {
		override fun migrate(connection: SQLiteConnection) {
			if (!hasColumn(connection, "direct_chat_messages", "client_message_id")) {
				connection.execSQL(
					"ALTER TABLE direct_chat_messages ADD COLUMN client_message_id TEXT"
				)
			}
		}
	}

	val MIGRATION_18_19 = object : Migration(18, 19) {
		override fun migrate(connection: SQLiteConnection) {
			connection.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_client_message_id " +
					"ON direct_chat_messages(conversation_id, client_message_id)"
			)
		}
	}

	val MIGRATION_19_20 = object : Migration(19, 20) {
		override fun migrate(connection: SQLiteConnection) {
			addColumnIfMissing(connection, "direct_chat_messages", "content_type", "TEXT")
			addColumnIfMissing(connection, "direct_chat_messages", "media_url", "TEXT")
			addColumnIfMissing(connection, "direct_chat_messages", "media_object_key", "TEXT")
			addColumnIfMissing(connection, "direct_chat_messages", "media_mime_type", "TEXT")
			addColumnIfMissing(connection, "direct_chat_messages", "media_size_bytes", "INTEGER")
			addColumnIfMissing(connection, "direct_chat_messages", "media_duration_ms", "INTEGER")
			addColumnIfMissing(connection, "direct_chat_messages", "media_width", "INTEGER")
			addColumnIfMissing(connection, "direct_chat_messages", "media_height", "INTEGER")

			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_content_type", "TEXT")
			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_url", "TEXT")
			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_object_key", "TEXT")
			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_mime_type", "TEXT")
			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_size_bytes", "INTEGER")
			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_duration_ms", "INTEGER")
			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_width", "INTEGER")
			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_height", "INTEGER")
		}
	}

	val MIGRATION_20_21 = object : Migration(20, 21) {
		override fun migrate(connection: SQLiteConnection) {
			connection.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS feed_stack_snapshots (
					userId TEXT NOT NULL PRIMARY KEY,
					snapshotJson TEXT NOT NULL,
					updatedAt INTEGER NOT NULL
				)
				""".trimIndent()
			)
		}
	}

	val MIGRATION_21_22 = object : Migration(21, 22) {
		override fun migrate(connection: SQLiteConnection) {
			addColumnIfMissing(connection, "direct_chat_messages", "media_local_path", "TEXT")
		}
	}

	val MIGRATION_22_23 = object : Migration(22, 23) {
		override fun migrate(connection: SQLiteConnection) {
			connection.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS pending_post_mutations (
					commandId TEXT NOT NULL PRIMARY KEY,
					type TEXT NOT NULL,
					localPostId TEXT NOT NULL,
					remotePostId TEXT,
					clientMutationId TEXT NOT NULL,
					state TEXT NOT NULL,
					description TEXT,
					category TEXT,
					imagePayloadJson TEXT NOT NULL,
					stagedMediaJson TEXT NOT NULL,
					previousPostSnapshotJson TEXT,
					lastError TEXT,
					retryCount INTEGER NOT NULL,
					nextRetryAt INTEGER NOT NULL,
					createdAt INTEGER NOT NULL,
					updatedAt INTEGER NOT NULL
				)
				""".trimIndent()
			)
			connection.execSQL("CREATE INDEX IF NOT EXISTS index_pending_post_mutations_state_nextRetryAt_createdAt ON pending_post_mutations(state, nextRetryAt, createdAt)")
			connection.execSQL("CREATE INDEX IF NOT EXISTS index_pending_post_mutations_remotePostId ON pending_post_mutations(remotePostId)")
			connection.execSQL("CREATE INDEX IF NOT EXISTS index_pending_post_mutations_localPostId ON pending_post_mutations(localPostId)")
			connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_post_mutations_clientMutationId ON pending_post_mutations(clientMutationId)")
		}
	}

	val MIGRATION_23_24 = object : Migration(23, 24) {
		override fun migrate(connection: SQLiteConnection) {
			connection.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS direct_chat_messages_new (
					local_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
					id INTEGER NOT NULL,
					conversation_id INTEGER NOT NULL,
					sender_id TEXT NOT NULL,
					sender_username TEXT,
					sender_name TEXT,
					sender_avatar_url TEXT,
					text TEXT NOT NULL,
					content_type TEXT,
					media_url TEXT,
					media_object_key TEXT,
					media_mime_type TEXT,
					media_size_bytes INTEGER,
					media_duration_ms INTEGER,
					media_local_path TEXT,
					media_width INTEGER,
					media_height INTEGER,
					client_message_id TEXT,
					reply_to_message_id INTEGER,
					reply_to_message_text TEXT,
					is_pinned INTEGER NOT NULL,
					pinned_at INTEGER,
					pinned_by_user_id TEXT,
					delivery_status TEXT NOT NULL,
					created_at INTEGER NOT NULL,
					updated_at INTEGER NOT NULL
				)
				""".trimIndent()
			)
			connection.execSQL(
				"""
				INSERT OR IGNORE INTO direct_chat_messages_new (
					id,
					conversation_id,
					sender_id,
					sender_username,
					sender_name,
					sender_avatar_url,
					text,
					content_type,
					media_url,
					media_object_key,
					media_mime_type,
					media_size_bytes,
					media_duration_ms,
					media_local_path,
					media_width,
					media_height,
					client_message_id,
					reply_to_message_id,
					reply_to_message_text,
					is_pinned,
					pinned_at,
					pinned_by_user_id,
					delivery_status,
					created_at,
					updated_at
				)
				SELECT
					id,
					conversation_id,
					sender_id,
					sender_username,
					sender_name,
					sender_avatar_url,
					text,
					content_type,
					media_url,
					media_object_key,
					media_mime_type,
					media_size_bytes,
					media_duration_ms,
					media_local_path,
					media_width,
					media_height,
					client_message_id,
					reply_to_message_id,
					reply_to_message_text,
					is_pinned,
					pinned_at,
					pinned_by_user_id,
					delivery_status,
					created_at,
					updated_at
				FROM direct_chat_messages
				ORDER BY conversation_id, created_at, id
				""".trimIndent()
			)
			connection.execSQL("DROP TABLE direct_chat_messages")
			connection.execSQL("ALTER TABLE direct_chat_messages_new RENAME TO direct_chat_messages")
			connection.execSQL("UPDATE direct_chat_messages SET client_message_id = NULL WHERE client_message_id IS NOT NULL AND TRIM(client_message_id) = ''")
			connection.execSQL(
				"""
				DELETE FROM direct_chat_messages
				WHERE client_message_id IS NOT NULL
				  AND EXISTS (
					SELECT 1
					FROM direct_chat_messages AS newer
					WHERE newer.conversation_id = direct_chat_messages.conversation_id
					  AND newer.client_message_id = direct_chat_messages.client_message_id
					  AND (
						(newer.id > 0 AND direct_chat_messages.id <= 0)
						OR (
							(newer.id > 0) = (direct_chat_messages.id > 0)
							AND newer.created_at > direct_chat_messages.created_at
						)
						OR (
							(newer.id > 0) = (direct_chat_messages.id > 0)
							AND newer.created_at = direct_chat_messages.created_at
							AND newer.local_id > direct_chat_messages.local_id
						)
					  )
				  )
				""".trimIndent()
			)
			connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_id ON direct_chat_messages(conversation_id, id)")
			connection.execSQL("CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_created_at ON direct_chat_messages(conversation_id, created_at)")
			connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_client_message_id ON direct_chat_messages(conversation_id, client_message_id)")
			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_client_message_id", "TEXT")
			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_delivery_status", "TEXT")
		}
	}

	val MIGRATION_24_25 = object : Migration(24, 25) {
		override fun migrate(connection: SQLiteConnection) {
			connection.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS direct_chat_outbox_commands (
					client_message_id TEXT NOT NULL PRIMARY KEY,
					conversation_id INTEGER NOT NULL,
					text TEXT NOT NULL,
					reply_to_message_id INTEGER,
					state TEXT NOT NULL,
					attempt_count INTEGER NOT NULL,
					next_attempt_at INTEGER NOT NULL,
					last_error TEXT,
					created_at INTEGER NOT NULL,
					updated_at INTEGER NOT NULL
				)
				""".trimIndent()
			)
			connection.execSQL(
				"""
				CREATE INDEX IF NOT EXISTS index_direct_chat_outbox_commands_conversation_id_state_next_attempt_at_created_at
				ON direct_chat_outbox_commands(conversation_id, state, next_attempt_at, created_at)
				""".trimIndent()
			)
			connection.execSQL(
				"""
				CREATE INDEX IF NOT EXISTS index_direct_chat_outbox_commands_state_next_attempt_at_created_at
				ON direct_chat_outbox_commands(state, next_attempt_at, created_at)
				""".trimIndent()
			)
			connection.execSQL(
				"""
				INSERT OR IGNORE INTO direct_chat_outbox_commands (
					client_message_id,
					conversation_id,
					text,
					reply_to_message_id,
					state,
					attempt_count,
					next_attempt_at,
					last_error,
					created_at,
					updated_at
				)
				SELECT
					client_message_id,
					conversation_id,
					text,
					reply_to_message_id,
					CASE WHEN delivery_status = 'FAILED' THEN 'FAILED' ELSE 'QUEUED' END,
					0,
					0,
					NULL,
					created_at,
					updated_at
				FROM direct_chat_messages
				WHERE id <= 0
				  AND client_message_id IS NOT NULL
				  AND TRIM(client_message_id) != ''
				  AND delivery_status != 'SENT'
				  AND (content_type IS NULL OR TRIM(content_type) = '')
				""".trimIndent()
			)
		}
	}

	val MIGRATION_25_26 = object : Migration(25, 26) {
		override fun migrate(connection: SQLiteConnection) {
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "content_type", "TEXT")
			connection.execSQL(
				"""
				UPDATE direct_chat_outbox_commands
				SET content_type = NULL
				WHERE content_type IS NOT NULL
				  AND TRIM(content_type) = ''
				""".trimIndent()
			)
			connection.execSQL(
				"""
				INSERT OR IGNORE INTO direct_chat_outbox_commands (
					client_message_id,
					conversation_id,
					text,
					content_type,
					reply_to_message_id,
					state,
					attempt_count,
					next_attempt_at,
					last_error,
					created_at,
					updated_at
				)
				SELECT
					client_message_id,
					conversation_id,
					text,
					content_type,
					reply_to_message_id,
					CASE WHEN delivery_status = 'FAILED' THEN 'FAILED' ELSE 'QUEUED' END,
					0,
					0,
					NULL,
					created_at,
					updated_at
				FROM direct_chat_messages
				WHERE id <= 0
				  AND client_message_id IS NOT NULL
				  AND TRIM(client_message_id) != ''
				  AND delivery_status != 'SENT'
				  AND content_type = 'video_circle'
				""".trimIndent()
			)
		}
	}

	val MIGRATION_26_27 = object : Migration(26, 27) {
		override fun migrate(connection: SQLiteConnection) {
			addColumnIfMissing(connection, "direct_chat_conversations", "is_pinned", "INTEGER NOT NULL DEFAULT 0")
			addColumnIfMissing(connection, "direct_chat_conversations", "pinned_at", "INTEGER")
			connection.execSQL(
				"CREATE INDEX IF NOT EXISTS index_direct_chat_conversations_pinned_at ON direct_chat_conversations(is_pinned, pinned_at DESC)"
			)
			addColumnIfMissing(connection, "direct_chat_read_state", "read_state_version", "INTEGER NOT NULL DEFAULT 0")
		}
	}

	val MIGRATION_27_28 = object : Migration(27, 28) {
		override fun migrate(connection: SQLiteConnection) {
			ensureDirectChatReadStateDefault(connection)
		}
	}

	val MIGRATION_28_29 = object : Migration(28, 29) {
		override fun migrate(connection: SQLiteConnection) {
			addColumnIfMissing(connection, "direct_chat_messages", "media_thumbnail_url", "TEXT")
			addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_thumbnail_url", "TEXT")
			ensureDirectChatReadStateDefault(connection)
			ensureDirectChatConversationsShape(connection)
		}
	}

	val MIGRATION_29_30 = object : Migration(29, 30) {
		override fun migrate(connection: SQLiteConnection) {
			addColumnIfMissing(connection, "direct_chat_messages", "media_thumbnail_url", "TEXT")
			ensureDirectChatReadStateDefault(connection)
			ensureDirectChatConversationsShape(connection)
		}
	}

	val MIGRATION_30_31 = object : Migration(30, 31) {
		override fun migrate(connection: SQLiteConnection) {
			addColumnIfMissing(connection, "direct_chat_messages", "media_waveform", "TEXT")
			ensureDirectChatReadStateDefault(connection)
			ensureDirectChatConversationsShape(connection)
		}
	}

	val MIGRATION_31_32 = object : Migration(31, 32) {
		override fun migrate(connection: SQLiteConnection) {
			addColumnIfMissing(connection, "direct_chat_messages", "group_id", "TEXT")
			addColumnIfMissing(connection, "direct_chat_messages", "media_position", "INTEGER NOT NULL DEFAULT 0")
			connection.execSQL("CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_group_id ON direct_chat_messages(conversation_id, group_id)")
			ensureDirectChatReadStateDefault(connection)
			ensureDirectChatConversationsShape(connection)
		}
	}

	val MIGRATION_32_33 = object : Migration(32, 33) {
		override fun migrate(connection: SQLiteConnection) {
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "local_file_path", "TEXT")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "media_url", "TEXT")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "media_thumbnail_url", "TEXT")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "media_mime_type", "TEXT")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "media_size_bytes", "INTEGER NOT NULL DEFAULT 0")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "media_duration_ms", "INTEGER NOT NULL DEFAULT 0")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "media_width", "INTEGER NOT NULL DEFAULT 0")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "media_height", "INTEGER NOT NULL DEFAULT 0")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "media_waveform", "TEXT")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "group_id", "TEXT")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "media_position", "INTEGER NOT NULL DEFAULT 0")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "upload_stage", "TEXT NOT NULL DEFAULT 'QUEUED'")
			addColumnIfMissing(connection, "direct_chat_outbox_commands", "idempotency_key", "TEXT")
			connection.execSQL("CREATE INDEX IF NOT EXISTS index_direct_chat_outbox_commands_upload_stage_next_attempt_at_created_at ON direct_chat_outbox_commands(upload_stage, next_attempt_at, created_at)")
			ensureDirectChatReadStateDefault(connection)
			ensureDirectChatConversationsShape(connection)
		}
	}

	val MIGRATION_33_34 = object : Migration(33, 34) {
		override fun migrate(connection: SQLiteConnection) {
			connection.execSQL("DROP INDEX IF EXISTS index_direct_chat_messages_conversation_id_id")
			connection.execSQL("CREATE INDEX IF NOT EXISTS index_direct_chat_messages_conversation_id_id ON direct_chat_messages(conversation_id, id)")
		}
	}

	private fun addColumnIfMissing(
		connection: SQLiteConnection,
		tableName: String,
		columnName: String,
		columnDefinition: String
	) {
		if (!hasColumn(connection, tableName, columnName)) {
			connection.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDefinition")
		}
	}

	private fun hasColumn(
		connection: SQLiteConnection,
		tableName: String,
		columnName: String
	): Boolean {
		connection.prepare("PRAGMA table_info($tableName)").use { stmt ->
			while (stmt.step()) {
				if (stmt.getText(1) == columnName) {
					return true
				}
			}
		}
		return false
	}

	private fun hasTable(
		connection: SQLiteConnection,
		tableName: String
	): Boolean {
		connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").use { stmt ->
			stmt.bindText(1, tableName)
			return stmt.step()
		}
	}

	private fun ensureDirectChatReadStateDefault(connection: SQLiteConnection) {
		if (!hasTable(connection, "direct_chat_read_state")) {
			connection.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS direct_chat_read_state (
					conversation_id INTEGER NOT NULL,
					last_read_message_id INTEGER NOT NULL,
					unread_count INTEGER NOT NULL,
					first_unread_id INTEGER,
					max_message_id INTEGER NOT NULL,
					read_state_version INTEGER NOT NULL DEFAULT 0,
					updated_at INTEGER NOT NULL,
					PRIMARY KEY(conversation_id)
				)
				""".trimIndent()
			)
			return
		}
		addColumnIfMissing(connection, "direct_chat_read_state", "read_state_version", "INTEGER NOT NULL DEFAULT 0")
		connection.execSQL("DROP TABLE IF EXISTS direct_chat_read_state_new")
		connection.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS direct_chat_read_state_new (
				conversation_id INTEGER NOT NULL,
				last_read_message_id INTEGER NOT NULL,
				unread_count INTEGER NOT NULL,
				first_unread_id INTEGER,
				max_message_id INTEGER NOT NULL,
				read_state_version INTEGER NOT NULL DEFAULT 0,
				updated_at INTEGER NOT NULL,
				PRIMARY KEY(conversation_id)
			)
			""".trimIndent()
		)
		connection.execSQL(
			"""
			INSERT OR REPLACE INTO direct_chat_read_state_new (
				conversation_id,
				last_read_message_id,
				unread_count,
				first_unread_id,
				max_message_id,
				read_state_version,
				updated_at
			)
			SELECT
				conversation_id,
				last_read_message_id,
				unread_count,
				first_unread_id,
				max_message_id,
				CASE
					WHEN read_state_version IS NULL THEN 0
					ELSE read_state_version
				END,
				updated_at
			FROM direct_chat_read_state
			""".trimIndent()
		)
		connection.execSQL("DROP TABLE direct_chat_read_state")
		connection.execSQL("ALTER TABLE direct_chat_read_state_new RENAME TO direct_chat_read_state")
	}

	private fun ensureDirectChatConversationsShape(connection: SQLiteConnection) {
		if (!hasTable(connection, "direct_chat_conversations")) {
			createDirectChatConversationsTable(connection, "direct_chat_conversations")
			return
		}

		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_content_type", "TEXT")
		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_url", "TEXT")
		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_object_key", "TEXT")
		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_mime_type", "TEXT")
		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_size_bytes", "INTEGER")
		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_duration_ms", "INTEGER")
		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_width", "INTEGER")
		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_height", "INTEGER")
		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_media_thumbnail_url", "TEXT")
		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_client_message_id", "TEXT")
		addColumnIfMissing(connection, "direct_chat_conversations", "last_message_delivery_status", "TEXT")
		addColumnIfMissing(connection, "direct_chat_conversations", "peer_last_read_message_id", "INTEGER")
		addColumnIfMissing(connection, "direct_chat_conversations", "is_pinned", "INTEGER NOT NULL DEFAULT 0")
		addColumnIfMissing(connection, "direct_chat_conversations", "pinned_at", "INTEGER")

		connection.execSQL("DROP TABLE IF EXISTS direct_chat_conversations_new")
		createDirectChatConversationsTable(connection, "direct_chat_conversations_new")
		connection.execSQL(
			"""
			INSERT OR REPLACE INTO direct_chat_conversations_new (
				id,
				kind,
				peer_id,
				peer_username,
				peer_name,
				peer_avatar_url,
				last_message_id,
				last_message_sender_id,
				last_message_sender_username,
				last_message_sender_name,
				last_message_sender_avatar_url,
				last_message_text,
				last_message_content_type,
				last_message_media_url,
				last_message_media_object_key,
				last_message_media_mime_type,
				last_message_media_size_bytes,
				last_message_media_duration_ms,
				last_message_media_width,
				last_message_media_height,
				last_message_media_thumbnail_url,
				last_message_client_message_id,
				last_message_delivery_status,
				last_message_created_at,
				last_message_updated_at,
				unread_count,
				last_read_message_id,
				peer_last_read_message_id,
				is_pinned,
				pinned_at,
				created_at,
				updated_at
			)
			SELECT
				id,
				kind,
				peer_id,
				peer_username,
				peer_name,
				peer_avatar_url,
				last_message_id,
				last_message_sender_id,
				last_message_sender_username,
				last_message_sender_name,
				last_message_sender_avatar_url,
				last_message_text,
				last_message_content_type,
				last_message_media_url,
				last_message_media_object_key,
				last_message_media_mime_type,
				last_message_media_size_bytes,
				last_message_media_duration_ms,
				last_message_media_width,
				last_message_media_height,
				last_message_media_thumbnail_url,
				last_message_client_message_id,
				last_message_delivery_status,
				last_message_created_at,
				last_message_updated_at,
				unread_count,
				last_read_message_id,
				peer_last_read_message_id,
				CASE WHEN is_pinned IS NULL THEN 0 ELSE is_pinned END,
				pinned_at,
				created_at,
				updated_at
			FROM direct_chat_conversations
			""".trimIndent()
		)
		connection.execSQL("DROP TABLE direct_chat_conversations")
		connection.execSQL("ALTER TABLE direct_chat_conversations_new RENAME TO direct_chat_conversations")
	}

	private fun createDirectChatConversationsTable(
		connection: SQLiteConnection,
		tableName: String
	) {
		connection.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS $tableName (
				id INTEGER NOT NULL,
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
				last_message_content_type TEXT,
				last_message_media_url TEXT,
				last_message_media_object_key TEXT,
				last_message_media_mime_type TEXT,
				last_message_media_size_bytes INTEGER,
				last_message_media_duration_ms INTEGER,
				last_message_media_width INTEGER,
				last_message_media_height INTEGER,
				last_message_media_thumbnail_url TEXT,
				last_message_client_message_id TEXT,
				last_message_delivery_status TEXT,
				last_message_created_at INTEGER,
				last_message_updated_at INTEGER,
				unread_count INTEGER NOT NULL,
				last_read_message_id INTEGER NOT NULL,
				peer_last_read_message_id INTEGER,
				is_pinned INTEGER NOT NULL DEFAULT 0,
				pinned_at INTEGER,
				created_at INTEGER NOT NULL,
				updated_at INTEGER NOT NULL,
				PRIMARY KEY(id)
			)
			""".trimIndent()
		)
	}
    val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS TestDbo")
        }
    }
    val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE direct_chat_conversations ADD COLUMN peer_avatar_thumb TEXT")
            connection.execSQL("ALTER TABLE profiles ADD COLUMN avatar_thumb TEXT")
            connection.execSQL("ALTER TABLE profiles ADD COLUMN background_thumb TEXT")
            connection.execSQL("ALTER TABLE posts ADD COLUMN author_avatar_thumb TEXT")
        }
    }

    val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("""
                CREATE TABLE direct_chat_progress (
                    account_id TEXT NOT NULL, conversation_id INTEGER NOT NULL,
                    anchor_message_id INTEGER, anchor_offset_px INTEGER NOT NULL,
                    bottom_pinned INTEGER NOT NULL, pending_read INTEGER NOT NULL,
                    oldest_cached_id INTEGER, newest_cached_id INTEGER,
                    cache_has_older INTEGER NOT NULL, cache_has_newer INTEGER NOT NULL,
                    PRIMARY KEY(account_id, conversation_id)
                )
            """.trimIndent())
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23,
        MIGRATION_23_24,
        MIGRATION_24_25,
        MIGRATION_25_26,
        MIGRATION_26_27,
        MIGRATION_27_28,
        MIGRATION_28_29,
        MIGRATION_29_30,
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
        MIGRATION_33_34,
        MIGRATION_34_35,
        MIGRATION_35_36,
        MIGRATION_36_37,
    )
}
