package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exam_questions")
data class ExamQuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String,
    val subject: String,
    val topic: String,
    val questionText: String,
    val finalAnswer: String,
    val solutionMarkdown: String,
    val stepsJson: String,
    val isFavorite: Boolean = false
)
