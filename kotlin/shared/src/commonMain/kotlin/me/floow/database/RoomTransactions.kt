package me.floow.database

import androidx.room.RoomDatabase
import androidx.room.useWriterConnection
import androidx.room.immediateTransaction

suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R {
    return this.useWriterConnection { transactor ->
        transactor.immediateTransaction {
            block()
        }
    }
}
