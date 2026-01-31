package com.example.coloringapp

import android.graphics.Bitmap
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
import com.example.coloringapp.audio.MusicManager
import com.example.coloringapp.audio.SoundEffectManager
import com.example.coloringapp.utils.LanguageManager

/**
 * Enum representing the current screen in the app.
 */
enum class AppScreen {
    GAME_HUB,       // New game hub with wooden UI (main screen)
    PAINTING,       // Painting/coloring flow
    SETTINGS        // Settings screen
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize language settings (safe - wrapped in try-catch)
        try {
            LanguageManager.init(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to init LanguageManager", e)
        }
        
        // Initialize audio managers (safe - wrapped in try-catch)
        try {
            MusicManager.init(this)
            SoundEffectManager.init(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to init audio managers", e)
        }
        
        // OpenCV initialization
        try {
            if (!OpenCVLoader.initDebug()) {
                Log.e("OpenCV", "Unable to load OpenCV!")
            } else {
                Log.d("OpenCV", "OpenCV loaded Successfully!")
            }
        } catch (e: Exception) {
            Log.e("OpenCV", "OpenCV initialization failed", e)
        }
        
        setContent {
            ColoringAppTheme {
                val viewModel: PaintingViewModel = viewModel()
                val isPaintingScreen by viewModel.imageBitmap.collectAsState()
                val showAdjustment by viewModel.showAdjustment.collectAsState()
                val originalBitmap by viewModel.originalBitmap.collectAsState()
                val webSearchQuery by viewModel.webSearchQuery.collectAsState()

                // Track current screen - start with GameHubScreen
                var currentScreen by remember { mutableStateOf(AppScreen.GAME_HUB) }
                var puzzleBitmap by remember { mutableStateOf<Bitmap?>(null) }

                BackHandler(enabled = currentScreen != AppScreen.GAME_HUB) {
                    when (currentScreen) {
                        AppScreen.PAINTING, AppScreen.SETTINGS -> {
                            currentScreen = AppScreen.GAME_HUB
                            puzzleBitmap = null
                            viewModel.clearImage()
                        }
                        else -> { /* Already at home */ }
                    }
                }

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        AppScreen.GAME_HUB -> {
                            // Main game hub with wooden UI
                            GameHubScreen(
                                initialBitmap = puzzleBitmap,
                                onBack = { /* Exit app or do nothing at home */ },
                                onSettings = { currentScreen = AppScreen.SETTINGS }
                            )
                        }
                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                onBack = { currentScreen = AppScreen.GAME_HUB }
                            )
                        }
                        AppScreen.PAINTING -> {
                            when {
                                webSearchQuery != null -> {
                                    WebImageSearchScreen(
                                        searchQuery = webSearchQuery!!,
                                        searchMode = ImageSearchMode.COLORING_PAGE,
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
                                            currentScreen = AppScreen.GAME_HUB
                                        },
                                        onPuzzleFromBitmap = { bitmap ->
                                            puzzleBitmap = bitmap
                                            currentScreen = AppScreen.GAME_HUB
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
    
    override fun onPause() {
        super.onPause()
        // Pause music when app goes to background
        MusicManager.pause()
    }
    
    override fun onResume() {
        super.onResume()
        // Resume music when app comes back
        MusicManager.resume()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Release audio resources
        MusicManager.release()
        SoundEffectManager.release()
    }
}