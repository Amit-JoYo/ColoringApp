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
     * Clears the current bitmap.
     */
    fun clearBitmap() {
        _puzzleBitmap.value = null
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
     * Sets the bitmap from a drawable resource and converts it to a coloring page (black & white line art).
     */
    fun setBitmapFromDrawableAsColoringPage(context: Context, drawableRes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val drawable = context.resources.getDrawable(drawableRes, context.theme)
                val bitmap = drawable.toBitmap()
                val coloringPage = convertToColoringPage(bitmap.copy(Bitmap.Config.ARGB_8888, true))
                _puzzleBitmap.value = coloringPage
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Check if an image is already mostly black and white.
     */
    private fun isAlreadyBlackAndWhite(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        
        var colorfulPixels = 0
        var totalSamples = 0
        
        for (y in 0 until height step 10) {
            for (x in 0 until width step 10) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val diff = max - min
                
                if (diff > 30) {
                    colorfulPixels++
                }
                totalSamples++
            }
        }
        
        return colorfulPixels < totalSamples * 0.1
    }
    
    /**
     * Convert a colorful image to a black and white coloring page (line art style).
     * If already black and white, returns the original.
     */
    private fun convertToColoringPage(bitmap: Bitmap): Bitmap {
        if (isAlreadyBlackAndWhite(bitmap)) {
            return bitmap
        }
        
        val width = bitmap.width
        val height = bitmap.height
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Convert to grayscale
        val grayscale = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            grayscale[i] = gray
        }
        
        // Apply edge detection (Sobel-like filter)
        val output = IntArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                val gx = (-grayscale[(y - 1) * width + (x - 1)] + grayscale[(y - 1) * width + (x + 1)]
                        - 2 * grayscale[y * width + (x - 1)] + 2 * grayscale[y * width + (x + 1)]
                        - grayscale[(y + 1) * width + (x - 1)] + grayscale[(y + 1) * width + (x + 1)])
                
                val gy = (-grayscale[(y - 1) * width + (x - 1)] - 2 * grayscale[(y - 1) * width + x] - grayscale[(y - 1) * width + (x + 1)]
                        + grayscale[(y + 1) * width + (x - 1)] + 2 * grayscale[(y + 1) * width + x] + grayscale[(y + 1) * width + (x + 1)])
                
                val magnitude = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt()
                val edgeValue = if (magnitude > 30) 0 else 255
                output[idx] = (0xFF shl 24) or (edgeValue shl 16) or (edgeValue shl 8) or edgeValue
            }
        }
        
        // Fill edges with white
        for (x in 0 until width) {
            output[x] = 0xFFFFFFFF.toInt()
            output[(height - 1) * width + x] = 0xFFFFFFFF.toInt()
        }
        for (y in 0 until height) {
            output[y * width] = 0xFFFFFFFF.toInt()
            output[y * width + width - 1] = 0xFFFFFFFF.toInt()
        }
        
        result.setPixels(output, 0, width, 0, 0, width, height)
        return result
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
