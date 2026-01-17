package com.example.coloringapp

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.coloringapp.ui.theme.ColoringAppTheme

import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opencv.android.OpenCVLoader
import android.util.Log

/**
 * Enum representing the current screen in the app.
 */
enum class AppScreen {
    MAIN,           // Main painting flow
    PUZZLE,         // Puzzle mode
    PUZZLE_FROM_PAINTING,  // Puzzle from painted image
    SETTINGS        // Settings screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV!")
        } else {
            Log.d("OpenCV", "OpenCV loaded Successfully!")
        }
        setContent {
            ColoringAppTheme {
                val viewModel: PaintingViewModel = viewModel()
                val isPaintingScreen by viewModel.imageBitmap.collectAsState()
                val showAdjustment by viewModel.showAdjustment.collectAsState()
                val originalBitmap by viewModel.originalBitmap.collectAsState()
                val webSearchQuery by viewModel.webSearchQuery.collectAsState()

                // Track current screen
                var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }
                var puzzleBitmap by remember { mutableStateOf<Bitmap?>(null) }

                BackHandler(enabled = currentScreen != AppScreen.MAIN || isPaintingScreen != null || showAdjustment || webSearchQuery != null) {
                    when {
                        currentScreen == AppScreen.PUZZLE || currentScreen == AppScreen.PUZZLE_FROM_PAINTING || currentScreen == AppScreen.SETTINGS -> {
                            currentScreen = AppScreen.MAIN
                            puzzleBitmap = null
                        }
                        webSearchQuery != null -> viewModel.cancelWebSearch()
                        showAdjustment -> viewModel.cancelAdjustment()
                        else -> viewModel.clearImage()
                    }
                }

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                onBack = { currentScreen = AppScreen.MAIN }
                            )
                        }
                        AppScreen.PUZZLE, AppScreen.PUZZLE_FROM_PAINTING -> {
                            PuzzleScreen(
                                initialBitmap = puzzleBitmap,
                                onBack = {
                                    currentScreen = AppScreen.MAIN
                                    puzzleBitmap = null
                                }
                            )
                        }
                        AppScreen.MAIN -> {
                            when {
                                webSearchQuery != null -> {
                                    WebImageSearchScreen(
                                        searchQuery = webSearchQuery!!,
                                        searchMode = ImageSearchMode.COLORING_PAGE,  // Painting needs coloring pages
                                        onImageSelected = { bitmap ->
                                            viewModel.cancelWebSearch()
                                            viewModel.setImageBitmap(bitmap)
                                        },
                                        onBack = {
                                            viewModel.cancelWebSearch()
                                        }
                                    )
                                }
                                showAdjustment && originalBitmap != null -> {
                                    ImageAdjustmentScreen(
                                        originalBitmap = originalBitmap!!,
                                        onApply = { adjustedBitmap ->
                                            viewModel.applyAdjustedBitmap(adjustedBitmap)
                                        },
                                        onCancel = {
                                            viewModel.cancelAdjustment()
                                        }
                                    )
                                }
                                else -> {
                                    PaintingScreen(
                                        viewModel = viewModel,
                                        onPuzzleMode = {
                                            currentScreen = AppScreen.PUZZLE
                                        },
                                        onPuzzleFromBitmap = { bitmap ->
                                            puzzleBitmap = bitmap
                                            currentScreen = AppScreen.PUZZLE_FROM_PAINTING
                                        },
                                        onSettings = {
                                            currentScreen = AppScreen.SETTINGS
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}