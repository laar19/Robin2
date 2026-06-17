package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAllTranscriptions(): Flow<List<TranscriptionItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscription(item: TranscriptionItem)

    @Update
    suspend fun updateTranscription(item: TranscriptionItem)

    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteTranscriptionById(id: Int)

    @Query("DELETE FROM transcriptions")
    suspend fun clearAll()
}
