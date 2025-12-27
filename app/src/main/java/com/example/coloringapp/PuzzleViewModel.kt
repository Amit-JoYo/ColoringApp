package com.example.coloringapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing puzzle game state.
 */
class PuzzleViewModel : ViewModel() {

    // The bitmap to use for the puzzle
    private val _puzzleBitmap = MutableStateFlow<Bitmap?>(null)
    val puzzleBitmap = _puzzleBitmap.asStateFlow()

    // Puzzle configuration
    private val _puzzleConfig = MutableStateFlow<PuzzleConfig?>(null)
    val puzzleConfig = _puzzleConfig.asStateFlow()

    // Whether the puzzle game is active
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    // Whether the puzzle has been solved
    private val _isSolved = MutableStateFlow(false)
    val isSolved = _isSolved.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Pre-loaded images for puzzle mode (same as coloring)
    val preloadedImages = listOf(
        R.drawable.coloring_page_1,
        R.drawable.coloring_page_2,
        R.drawable.coloring_page_3,
        R.drawable.coloring_page_4,
        R.drawable.coloring_page_5,
        R.drawable.coloring_page_6
    )

    /**
     * Sets the bitmap for the puzzle from an existing bitmap.
     */
    fun setBitmap(bitmap: Bitmap) {
        _puzzleBitmap.value = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    /**
     * Sets the bitmap from a drawable resource.
     */
    fun setBitmapFromDrawable(context: Context, drawableRes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val drawable = context.resources.getDrawable(drawableRes, context.theme)
                val bitmap = drawable.toBitmap()
                _puzzleBitmap.value = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Sets the puzzle configuration and starts the game.
     */
    fun startPuzzle(config: PuzzleConfig) {
        _puzzleConfig.value = config
        _isPlaying.value = true
        _isSolved.value = false
    }

    /**
     * Called when the puzzle is solved.
     */
    fun onPuzzleSolved() {
        _isSolved.value = true
    }

    /**
     * Resets the puzzle to play again with the same image.
     */
    fun resetPuzzle() {
        _isPlaying.value = false
        _isSolved.value = false
        _puzzleConfig.value = null
    }

    /**
     * Clears everything and goes back to image selection.
     */
    fun clearPuzzle() {
        _puzzleBitmap.value = null
        _puzzleConfig.value = null
        _isPlaying.value = false
        _isSolved.value = false
    }

    /**
     * Just clears the game state, keeping the bitmap for reconfiguration.
     */
    fun backToConfig() {
        _puzzleConfig.value = null
        _isPlaying.value = false
        _isSolved.value = false
    }
}
