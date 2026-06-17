package com.example.data

import kotlinx.coroutines.flow.Flow

class TranscriptionRepository(private val transcriptionDao: TranscriptionDao) {
    val allTranscriptions: Flow<List<TranscriptionItem>> = transcriptionDao.getAllTranscriptions()

    suspend fun insert(item: TranscriptionItem) = transcriptionDao.insertTranscription(item)

    suspend fun update(item: TranscriptionItem) = transcriptionDao.updateTranscription(item)

    suspend fun deleteById(id: Int) = transcriptionDao.deleteTranscriptionById(id)

    suspend fun clearAll() = transcriptionDao.clearAll()
}
