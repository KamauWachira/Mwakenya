package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ExamQuestionEntity
import com.example.data.model.SampleExamProblem
import com.example.data.repository.ExamSolverRepository
import com.example.util.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ScreenDestination {
    object Camera : ScreenDestination()
    object Processing : ScreenDestination()
    data class Result(val questionId: Long) : ScreenDestination()
    object History : ScreenDestination()
}

class ExamSolverViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = ExamSolverRepository(application, database.examQuestionDao())

    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Camera)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    private val _currentQuestionId = MutableStateFlow<Long?>(null)
    val currentQuestionId: StateFlow<Long?> = _currentQuestionId.asStateFlow()

    private val _currentCapturedBitmap = MutableStateFlow<Bitmap?>(null)
    val currentCapturedBitmap: StateFlow<Bitmap?> = _currentCapturedBitmap.asStateFlow()

    private val _processingMessage = MutableStateFlow("Initializing multimodal analysis...")
    val processingMessage: StateFlow<String> = _processingMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSubjectFilter = MutableStateFlow("All")
    val selectedSubjectFilter: StateFlow<String> = _selectedSubjectFilter.asStateFlow()

    val sampleProblems: List<SampleExamProblem> = repository.getSampleProblems()

    // Filtered list of history questions based on search query and subject filter
    val historyQuestions: StateFlow<List<ExamQuestionEntity>> = combine(
        repository.allQuestions,
        _searchQuery,
        _selectedSubjectFilter
    ) { questions, query, subjectFilter ->
        questions.filter { entity ->
            val matchesSubject = when (subjectFilter) {
                "All" -> true
                "Favorites" -> entity.isFavorite
                else -> entity.subject.contains(subjectFilter, ignoreCase = true)
            }
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                entity.questionText.contains(query, ignoreCase = true) ||
                        entity.subject.contains(query, ignoreCase = true) ||
                        entity.topic.contains(query, ignoreCase = true) ||
                        entity.finalAnswer.contains(query, ignoreCase = true)
            }
            matchesSubject && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current question entity flow
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentQuestion: StateFlow<ExamQuestionEntity?> = _currentQuestionId.flatMapLatest { id ->
        if (id != null) {
            repository.getQuestionById(id)
        } else {
            MutableStateFlow(null)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun navigateTo(screen: ScreenDestination) {
        _currentScreen.value = screen
    }

    fun openQuestionDetail(id: Long) {
        _currentQuestionId.value = id
        _currentScreen.value = ScreenDestination.Result(id)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSubjectFilter(subject: String) {
        _selectedSubjectFilter.value = subject
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun processCapturedPhoto(bitmap: Bitmap) {
        _currentCapturedBitmap.value = bitmap
        _currentScreen.value = ScreenDestination.Processing
        _processingMessage.value = "Analyzing exam question & formulas..."

        viewModelScope.launch {
            val result = repository.solveExamQuestion(bitmap) { stage ->
                _processingMessage.value = stage
            }

            if (result.isSuccess) {
                val newId = result.getOrThrow()
                _currentQuestionId.value = newId
                _currentScreen.value = ScreenDestination.Result(newId)
            } else {
                val ex = result.exceptionOrNull()
                _errorMessage.value = ex?.localizedMessage ?: "Failed to analyze question"
                _currentScreen.value = ScreenDestination.Camera
            }
        }
    }

    fun processImageFromGallery(uri: Uri, context: Context) {
        viewModelScope.launch {
            val bitmap = ImageUtils.loadBitmapFromUri(context, uri, 1600)
            if (bitmap != null) {
                processCapturedPhoto(bitmap)
            } else {
                _errorMessage.value = "Failed to load image from gallery."
            }
        }
    }

    fun solveSampleExamProblem(problem: SampleExamProblem) {
        _currentScreen.value = ScreenDestination.Processing
        _processingMessage.value = "Generating exam problem sheet..."

        viewModelScope.launch {
            val result = repository.solveSampleProblem(problem) { stage ->
                _processingMessage.value = stage
            }

            if (result.isSuccess) {
                val newId = result.getOrThrow()
                _currentQuestionId.value = newId
                _currentScreen.value = ScreenDestination.Result(newId)
            } else {
                val ex = result.exceptionOrNull()
                _errorMessage.value = ex?.localizedMessage ?: "Failed to solve sample question"
                _currentScreen.value = ScreenDestination.Camera
            }
        }
    }

    fun toggleFavorite(question: ExamQuestionEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(question.id, !question.isFavorite)
        }
    }

    fun deleteQuestion(id: Long) {
        viewModelScope.launch {
            repository.deleteQuestion(id)
            if (_currentQuestionId.value == id) {
                _currentScreen.value = ScreenDestination.History
            }
        }
    }
}
