package com.batmudcn.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT value FROM translations WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: TranslationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(entities: List<TranslationEntity>)

    @Query("SELECT COUNT(*) FROM translations")
    suspend fun count(): Int

    @Query("DELETE FROM translations")
    suspend fun clearAll()
}
