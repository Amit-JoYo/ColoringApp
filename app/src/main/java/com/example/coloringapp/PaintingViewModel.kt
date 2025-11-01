package com.example.coloringapp

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.coloringapp.R

class PaintingViewModel : ViewModel() {

    private val _imageBitmap = MutableStateFlow<Bitmap?>(null)
    val imageBitmap = _imageBitmap.asStateFlow()

    // Original bitmap before processing (for adjustment)
    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap = _originalBitmap.asStateFlow()

    // Whether to show adjustment screen
    private val _showAdjustment = MutableStateFlow(false)
    val showAdjustment = _showAdjustment.asStateFlow()

    // Web search state
    private val _webSearchQuery = MutableStateFlow<String?>(null)
    val webSearchQuery = _webSearchQuery.asStateFlow()

    // A unique ID for the current image session. Changes only when a new image is loaded.
    private val _imageSessionId = MutableStateFlow(0)
    val imageSessionId = _imageSessionId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color.Red)
    val selectedColor = _selectedColor.asStateFlow()

    // Color history for recent colors
    private val _colorHistory = MutableStateFlow<List<Color>>(emptyList())
    val colorHistory = _colorHistory.asStateFlow()

    // Drawing mode: Fill or Brush
    private val _drawingMode = MutableStateFlow<DrawingMode>(DrawingMode.Fill)
    val drawingMode = _drawingMode.asStateFlow()

    private val undoStack = mutableListOf<UndoState>()
    private val redoStack = mutableListOf<UndoState>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    // Undo/Redo history info for preview
    private val _undoHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val undoHistory = _undoHistory.asStateFlow()

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus = _saveStatus.asStateFlow()

    val initialImages = listOf(
        R.drawable.coloring_page_1,
        R.drawable.coloring_page_2,
        R.drawable.coloring_page_3,
        R.drawable.coloring_page_4,
        R.drawable.coloring_page_5,
        R.drawable.coloring_page_6
    )

    fun setImageBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            _isLoading.value = true
            
            // Check if image needs processing (is it colored?)
            val needsProcessing = !isGrayscaleImage(bitmap)
            
            if (needsProcessing) {
                // Store original and show adjustment screen
                _originalBitmap.value = bitmap
                _showAdjustment.value = true
                _isLoading.value = false
            } else {
                // Grayscale image - use directly
                _imageBitmap.value = bitmap
                undoStack.clear()
                redoStack.clear()
                undoStack.add(UndoState(bitmap.copy(bitmap.config, true), "Initial", System.currentTimeMillis()))
                updateUndoRedoStates()
                _imageSessionId.value++
                _isLoading.value = false
            }
        }
    }

    fun setImageBitmapFromDrawable(context: android.content.Context, drawableId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val options = android.graphics.BitmapFactory.Options()
            options.inMutable = true
            val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, drawableId, options)
            
            // Pre-loaded images are already grayscale, use directly
            _imageBitmap.value = bitmap
            undoStack.clear()
            redoStack.clear()
            undoStack.add(UndoState(bitmap.copy(bitmap.config, true), "Initial", System.currentTimeMillis()))
            updateUndoRedoStates()
            _imageSessionId.value++
            _isLoading.value = false
        }
    }

    /**
     * Apply the adjusted bitmap from the adjustment screen
     */
    fun applyAdjustedBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            _imageBitmap.value = bitmap
            undoStack.clear()
            redoStack.clear()
            undoStack.add(UndoState(bitmap.copy(bitmap.config, true), "Initial", System.currentTimeMillis()))
            updateUndoRedoStates()
            _imageSessionId.value++
            _showAdjustment.value = false
            _originalBitmap.value = null
        }
    }

    /**
     * Cancel adjustment and return to image selection
     */
    fun cancelAdjustment() {
        _showAdjustment.value = false
        _originalBitmap.value = null
    }

    /**
     * Start web search for coloring pages
     */
    fun startWebSearch(query: String) {
        _webSearchQuery.value = query
    }

    /**
     * Cancel web search and return to image selection
     */
    fun cancelWebSearch() {
        _webSearchQuery.value = null
    }

    /**
     * Check if bitmap is grayscale
     */
    private fun isGrayscaleImage(bitmap: Bitmap): Boolean {
        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)

        val hsvMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, hsvMat, org.opencv.imgproc.Imgproc.COLOR_BGR2HSV)

        val hsvChannels = mutableListOf<org.opencv.core.Mat>()
        org.opencv.core.Core.split(hsvMat, hsvChannels)

        val saturationChannel = hsvChannels[1]
        val meanSaturation = org.opencv.core.Core.mean(saturationChannel)

        mat.release()
        hsvMat.release()
        hsvChannels.forEach { it.release() }

        val grayscaleThreshold = 15.0
        return meanSaturation.`val`[0] < grayscaleThreshold
    }

    fun setSelectedColor(color: Color) {
        _selectedColor.value = color
        // Add to color history
        val currentHistory = _colorHistory.value.toMutableList()
        currentHistory.remove(color) // Remove if exists
        currentHistory.add(0, color) // Add to front
        _colorHistory.value = currentHistory.take(10) // Keep last 10 colors
    }

    fun setDrawingMode(mode: DrawingMode) {
        _drawingMode.value = mode
    }

    val isPaintingScreen: Boolean
        get() = _imageBitmap.value != null

    fun clearImage() {
        _imageBitmap.value = null
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoStates()
    }

    fun startFloodFill(x: Int, y: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            _imageBitmap.value?.let {
                val currentBitmap = it.copy(it.config, true)
                undoStack.add(UndoState(currentBitmap, "Fill", System.currentTimeMillis()))
                redoStack.clear()
                val newBitmap = floodFill(it, x, y, _selectedColor.value)
                _imageBitmap.value = newBitmap
                updateUndoRedoStates()
            }
        }
    }

    /**
     * Draw with brush at specified coordinates
     */
    fun brushDraw(x: Int, y: Int) {
        _imageBitmap.value?.let { bitmap ->
            // Draw directly on the existing bitmap (which is already mutable from startBrushStroke)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    (_selectedColor.value.alpha * 255).toInt(),
                    (_selectedColor.value.red * 255).toInt(),
                    (_selectedColor.value.green * 255).toInt(),
                    (_selectedColor.value.blue * 255).toInt()
                )
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
            }
            // Fixed 15px brush size - ideal for coloring details
            canvas.drawCircle(x.toFloat(), y.toFloat(), 15f, paint)
            
            // Force StateFlow update by creating a new reference
            _imageBitmap.value = bitmap.copy(bitmap.config, true)
        }
    }

    /**
     * Start a new brush stroke (for undo/redo)
     */
    fun startBrushStroke() {
        _imageBitmap.value?.let { currentBitmap ->
            // Save the current state before starting the brush stroke
            undoStack.add(UndoState(currentBitmap.copy(currentBitmap.config, true), "Brush Stroke", System.currentTimeMillis()))
            redoStack.clear()
            updateUndoRedoStates()
        }
    }

    fun undo() {
        if (undoStack.size > 1) {
            val currentState = undoStack.removeAt(undoStack.size - 1)
            _imageBitmap.value?.let {
                redoStack.add(UndoState(it.copy(it.config, true), currentState.action, System.currentTimeMillis()))
            }
            val previousState = undoStack.last()
            _imageBitmap.value = previousState.bitmap.copy(previousState.bitmap.config, true)
            updateUndoRedoStates()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.size - 1)
            _imageBitmap.value?.let {
                undoStack.add(UndoState(it.copy(it.config, true), nextState.action, System.currentTimeMillis()))
            }
            _imageBitmap.value = nextState.bitmap.copy(nextState.bitmap.config, true)
            updateUndoRedoStates()
        }
    }

    private fun updateUndoRedoStates() {
        _canUndo.value = undoStack.size > 1
        _canRedo.value = redoStack.isNotEmpty()
        
        // Update undo/redo history for UI preview
        _undoHistory.value = undoStack.takeLast(5).map { state ->
            HistoryItem(state.action, state.timestamp, isUndo = true)
        }.reversed()
    }

    /**
     * Saves the current colored image to the device gallery
     */
    fun saveImageToGallery(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _saveStatus.value = SaveStatus.Saving
                
                _imageBitmap.value?.let { bitmap ->
                    val fileName = "ColoringApp_${System.currentTimeMillis()}.png"
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ (API 29+) - Use MediaStore
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ColoringApp")
                        }
                        
                        val contentResolver = context.contentResolver
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        
                        uri?.let {
                            contentResolver.openOutputStream(it)?.use { outputStream ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            }
                            _saveStatus.value = SaveStatus.Success(uri)
                        } ?: run {
                            _saveStatus.value = SaveStatus.Error("Failed to create file")
                        }
                    } else {
                        // Android 9 and below - Use traditional file writing
                        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val appDir = File(picturesDir, "ColoringApp")
                        if (!appDir.exists()) {
                            appDir.mkdirs()
                        }
                        
                        val imageFile = File(appDir, fileName)
                        FileOutputStream(imageFile).use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        }
                        
                        // Notify media scanner
                        val uri = Uri.fromFile(imageFile)
                        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
                        context.sendBroadcast(mediaScanIntent)
                        
                        _saveStatus.value = SaveStatus.Success(uri)
                    }
                } ?: run {
                    _saveStatus.value = SaveStatus.Error("No image to save")
                }
            } catch (e: Exception) {
                _saveStatus.value = SaveStatus.Error(e.message ?: "Failed to save image")
            }
        }
    }

    /**
     * Creates a shareable URI for the current image
     */
    fun shareImage(context: Context): Intent? {
        return try {
            _imageBitmap.value?.let { bitmap ->
                // Create a temporary file in cache directory
                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                
                val fileName = "ColoringApp_share_${System.currentTimeMillis()}.png"
                val file = File(cachePath, fileName)
                
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                
                // Get URI using FileProvider
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                
                // Create share intent
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_SUBJECT, "My Colored Artwork")
                    putExtra(Intent.EXTRA_TEXT, "Check out my coloring from ColoringApp!")
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resets the save status to idle
     */
    fun resetSaveStatus() {
        _saveStatus.value = SaveStatus.Idle
    }
}

/**
 * Drawing mode for painting
 */
enum class DrawingMode {
    Fill,   // Flood fill mode
    Brush   // Free-hand brush drawing
}

/**
 * Represents an undo/redo state with metadata
 */
data class UndoState(
    val bitmap: Bitmap,
    val action: String,
    val timestamp: Long
)

/**
 * History item for UI display
 */
data class HistoryItem(
    val action: String,
    val timestamp: Long,
    val isUndo: Boolean
)

/**
 * Represents the status of a save operation
 */
sealed class SaveStatus {
    object Idle : SaveStatus()
    object Saving : SaveStatus()
    data class Success(val uri: Uri) : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}