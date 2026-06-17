package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcriptions")
data class TranscriptionItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val engine: String, // "Vosk (Nativo)", "Whisper API", "Gemini", etc.
    val durationSecs: Long,
    val isFavorite: Boolean = false
)
