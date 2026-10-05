package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamQuestionDao {
    @Query("SELECT * FROM exam_questions ORDER BY timestamp DESC")
    fun getAllQuestions(): Flow<List<ExamQuestionEntity>>

    @Query("SELECT * FROM exam_questions WHERE id = :id LIMIT 1")
    fun getQuestionById(id: Long): Flow<ExamQuestionEntity?>

    @Query("SELECT * FROM exam_questions WHERE id = :id LIMIT 1")
    suspend fun getQuestionByIdOnce(id: Long): ExamQuestionEntity?

    @Query("SELECT * FROM exam_questions WHERE subject = :subject ORDER BY timestamp DESC")
    fun getQuestionsBySubject(subject: String): Flow<List<ExamQuestionEntity>>

    @Query("SELECT * FROM exam_questions WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteQuestions(): Flow<List<ExamQuestionEntity>>

    @Query("""
        SELECT * FROM exam_questions 
        WHERE questionText LIKE '%' || :query || '%' 
           OR subject LIKE '%' || :query || '%' 
           OR topic LIKE '%' || :query || '%'
           OR finalAnswer LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchQuestions(query: String): Flow<List<ExamQuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: ExamQuestionEntity): Long

    @Update
    suspend fun updateQuestion(question: ExamQuestionEntity)

    @Delete
    suspend fun deleteQuestion(question: ExamQuestionEntity)

    @Query("DELETE FROM exam_questions WHERE id = :id")
    suspend fun deleteQuestionById(id: Long)

    @Query("UPDATE exam_questions SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM exam_questions")
    suspend fun clearAll()
}
