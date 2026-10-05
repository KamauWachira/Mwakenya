package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.ui.screens.CameraScannerScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.SolutionResultScreen
import com.example.ui.theme.ExamSnapTheme
import com.example.viewmodel.ExamSolverViewModel
import com.example.viewmodel.ScreenDestination

class MainActivity : ComponentActivity() {

    private val viewModel: ExamSolverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExamSnapTheme {
                ExamSnapApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun ExamSnapApp(viewModel: ExamSolverViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()
    val capturedBitmap by viewModel.currentCapturedBitmap.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    is ScreenDestination.Camera -> {
                        CameraScannerScreen(
                            viewModel = viewModel,
                            onNavigateToHistory = {
                                viewModel.navigateTo(ScreenDestination.History)
                            }
                        )
                    }

                    is ScreenDestination.Processing -> {
                        ProcessingScreen(
                            viewModel = viewModel,
                            processingMessage = processingMessage,
                            capturedBitmap = capturedBitmap
                        )
                    }

                    is ScreenDestination.Result -> {
                        SolutionResultScreen(
                            viewModel = viewModel,
                            onBackToCamera = {
                                viewModel.navigateTo(ScreenDestination.Camera)
                            }
                        )
                    }

                    is ScreenDestination.History -> {
                        HistoryScreen(
                            viewModel = viewModel,
                            onBackToCamera = {
                                viewModel.navigateTo(ScreenDestination.Camera)
                            },
                            onSelectQuestion = { questionId ->
                                viewModel.openQuestionDetail(questionId)
                            }
                        )
                    }
                }
            }
        }
    }
}
