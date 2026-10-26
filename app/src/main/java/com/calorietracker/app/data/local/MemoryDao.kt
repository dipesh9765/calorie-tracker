package com.calorietracker.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
  @Query("SELECT * FROM memories ORDER BY category, updatedAt DESC")
  fun observeAll(): Flow<List<MemoryEntity>>

  @Query("SELECT * FROM memories ORDER BY category, updatedAt DESC")
  suspend fun getAll(): List<MemoryEntity>

  @Query("SELECT * FROM memories WHERE id = :id") suspend fun getById(id: Long): MemoryEntity?

  @Insert suspend fun insert(memory: MemoryEntity): Long

  @Update suspend fun update(memory: MemoryEntity)

  @Query("DELETE FROM memories WHERE id = :id") suspend fun deleteById(id: Long)
}
