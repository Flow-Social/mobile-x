package me.floow.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.floow.database.dbo.ProfileEntity

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfile(userId: String): ProfileEntity?

    @Query("SELECT updatedAt FROM profiles WHERE userId = :userId LIMIT 1")
    suspend fun getUpdatedAt(userId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)
}
