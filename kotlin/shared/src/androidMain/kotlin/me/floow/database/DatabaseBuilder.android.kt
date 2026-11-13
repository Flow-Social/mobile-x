package me.floow.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

object AndroidDatabaseContext {
    private var appContext: Context? = null

    fun set(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context {
        return appContext ?: error("AndroidDatabaseContext must be initialized with Context before opening Room DB")
    }
}

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val context = AndroidDatabaseContext.get()
    val dbFile = context.getDatabasePath("flowme.db")
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = dbFile.absolutePath
    )
}

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    AndroidDatabaseContext.set(context)
    return getDatabaseBuilder()
}
