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
import androidx.compose.ui.graphics.toArgb
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

    // Line mask layer - contains only the black lines (for overlay rendering)
    private val _lineMask = MutableStateFlow<Bitmap?>(null)
    val lineMask = _lineMask.asStateFlow()

    // Color layer - where painting happens (rendered behind lines)
    private val _colorLayer = MutableStateFlow<Bitmap?>(null)
    val colorLayer = _colorLayer.asStateFlow()

    // Threshold for what's considered a "line" (dark pixel) - pixels darker than this are lines
    private val lineThreshold = 200

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

    // Track last brush position for smooth lines
    private var lastBrushX: Float? = null
    private var lastBrushY: Float? = null

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

    /**
     * Extract line mask from a grayscale coloring page image.
     * Dark pixels (below threshold) become opaque black lines.
     * Light pixels become transparent.
     */
    private fun extractLineMask(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val maskPixels = IntArray(width * height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            
            // Calculate luminance (brightness)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            
            if (luminance < lineThreshold) {
                // This is a line pixel - keep it as opaque black
                // Use the original darkness level for anti-aliasing
                val alpha = 255 - luminance // Darker = more opaque
                maskPixels[i] = android.graphics.Color.argb(alpha.coerceIn(0, 255), 0, 0, 0)
            } else {
                // This is a white/light pixel - make it transparent
                maskPixels[i] = android.graphics.Color.TRANSPARENT
            }
        }
        
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
        return mask
    }

    /**
     * Create a white color layer of the same size as the source image
     */
    private fun createColorLayer(width: Int, height: Int): Bitmap {
        val colorLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        colorLayer.eraseColor(android.graphics.Color.WHITE)
        return colorLayer
    }

    /**
     * Composite the color layer and line mask into the display bitmap
     */
    private fun compositeLayersInternal(): Bitmap? {
        val colorLayer = _colorLayer.value ?: return null
        val lineMask = _lineMask.value ?: return colorLayer.copy(colorLayer.config, true)
        
        val result = colorLayer.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        canvas.drawBitmap(lineMask, 0f, 0f, null)
        return result
    }

    /**
     * Update the display bitmap by compositing layers
     */
    private fun updateDisplayBitmap() {
        _imageBitmap.value = compositeLayersInternal()
    }

    /**
     * Initialize layers from a coloring page bitmap
     */
    private fun initializeLayers(bitmap: Bitmap) {
        _lineMask.value = extractLineMask(bitmap)
        _colorLayer.value = createColorLayer(bitmap.width, bitmap.height)
        updateDisplayBitmap()
    }

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
                // Grayscale image - initialize layers
                initializeLayers(bitmap)
                undoStack.clear()
                redoStack.clear()
                _colorLayer.value?.let { colorLayer ->
                    undoStack.add(UndoState(colorLayer.copy(colorLayer.config, true), "Initial", System.currentTimeMillis()))
                }
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
            
            // Pre-loaded images are already grayscale - initialize layers
            initializeLayers(bitmap)
            undoStack.clear()
            redoStack.clear()
            _colorLayer.value?.let { colorLayer ->
                undoStack.add(UndoState(colorLayer.copy(colorLayer.config, true), "Initial", System.currentTimeMillis()))
            }
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
            // Initialize layers from the adjusted (now grayscale) image
            initializeLayers(bitmap)
            undoStack.clear()
            redoStack.clear()
            _colorLayer.value?.let { colorLayer ->
                undoStack.add(UndoState(colorLayer.copy(colorLayer.config, true), "Initial", System.currentTimeMillis()))
            }
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
        _lineMask.value = null
        _colorLayer.value = null
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoStates()
    }

    /**
     * Check if a pixel at (x, y) is a line pixel (should not be painted)
     */
    private fun isLinePixel(x: Int, y: Int): Boolean {
        val lineMask = _lineMask.value ?: return false
        if (x < 0 || x >= lineMask.width || y < 0 || y >= lineMask.height) return false
        val pixel = lineMask.getPixel(x, y)
        // If alpha > 0, it's a line pixel
        return android.graphics.Color.alpha(pixel) > 50
    }

    fun startFloodFill(x: Int, y: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val colorLayer = _colorLayer.value ?: return@launch
            val lineMask = _lineMask.value
            
            // Don't fill if clicked on a line
            if (isLinePixel(x, y)) return@launch
            
            // Save current color layer for undo
            undoStack.add(UndoState(colorLayer.copy(colorLayer.config, true), "Fill", System.currentTimeMillis()))
            redoStack.clear()
            
            // Flood fill on the color layer, respecting line boundaries
            val newColorLayer = floodFillWithMask(colorLayer, lineMask, x, y, _selectedColor.value)
            _colorLayer.value = newColorLayer
            
            // Update display bitmap
            updateDisplayBitmap()
            updateUndoRedoStates()
        }
    }

    /**
     * Flood fill that respects line mask boundaries
     */
    private suspend fun floodFillWithMask(
        colorLayer: Bitmap,
        lineMask: Bitmap?,
        x: Int,
        y: Int,
        newColor: Color,
        tolerance: Int = 30
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = colorLayer.width
        val height = colorLayer.height
        val pixels = IntArray(width * height)
        colorLayer.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Get line mask pixels if available
        val maskPixels = if (lineMask != null) {
            IntArray(width * height).also { lineMask.getPixels(it, 0, width, 0, 0, width, height) }
        } else null
        
        val targetColor = pixels[y * width + x]
        val newColorArgb = newColor.toArgb()
        
        // If the target color is already the new color, do nothing
        if (areColorsSimilar(targetColor, newColorArgb, tolerance)) {
            return@withContext colorLayer
        }
        
        val queue = java.util.LinkedList<Pair<Int, Int>>()
        queue.add(x to y)
        val visited = HashSet<Int>()
        
        while (queue.isNotEmpty()) {
            val (px, py) = queue.poll()!!
            val pixelOffset = py * width + px
            
            if (px in 0 until width && py in 0 until height && !visited.contains(pixelOffset)) {
                visited.add(pixelOffset)
                
                // Skip if this is a line pixel
                if (maskPixels != null && android.graphics.Color.alpha(maskPixels[pixelOffset]) > 50) {
                    continue
                }
                
                // Check if the current pixel is similar to the target color
                if (areColorsSimilar(pixels[pixelOffset], targetColor, tolerance)) {
                    // Change the color
                    pixels[pixelOffset] = newColorArgb
                    
                    // Add neighbors to the queue
                    queue.add(px + 1 to py)
                    queue.add(px - 1 to py)
                    queue.add(px to py + 1)
                    queue.add(px to py - 1)
                }
            }
        }
        
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        newBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        newBitmap
    }

    /**
     * Helper function to check color similarity
     */
    private fun areColorsSimilar(color1: Int, color2: Int, tolerance: Int): Boolean {
        if (tolerance == 0) return color1 == color2
        val a1 = android.graphics.Color.alpha(color1)
        val r1 = android.graphics.Color.red(color1)
        val g1 = android.graphics.Color.green(color1)
        val b1 = android.graphics.Color.blue(color1)

        val a2 = android.graphics.Color.alpha(color2)
        val r2 = android.graphics.Color.red(color2)
        val g2 = android.graphics.Color.green(color2)
        val b2 = android.graphics.Color.blue(color2)

        return kotlin.math.abs(a1 - a2) <= tolerance &&
               kotlin.math.abs(r1 - r2) <= tolerance &&
               kotlin.math.abs(g1 - g2) <= tolerance &&
               kotlin.math.abs(b1 - b2) <= tolerance
    }

    /**
     * Draw with brush at specified coordinates (only on non-line areas)
     */
    fun brushDraw(x: Int, y: Int) {
        val colorLayer = _colorLayer.value ?: return
        
        // Skip if trying to draw on a line pixel
        if (isLinePixel(x, y)) {
            // Still update lastBrush position for smooth lines, just don't draw
            lastBrushX = x.toFloat()
            lastBrushY = y.toFloat()
            return
        }
        
        val canvas = android.graphics.Canvas(colorLayer)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (_selectedColor.value.alpha * 255).toInt(),
                (_selectedColor.value.red * 255).toInt(),
                (_selectedColor.value.green * 255).toInt(),
                (_selectedColor.value.blue * 255).toInt()
            )
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 30f
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        
        val currentX = x.toFloat()
        val currentY = y.toFloat()

        if (lastBrushX != null && lastBrushY != null) {
            // Draw line from last point to current point
            canvas.drawLine(lastBrushX!!, lastBrushY!!, currentX, currentY, paint)
        } else {
            // First point - just draw a dot
            canvas.drawLine(currentX, currentY, currentX, currentY, paint)
        }
        
        // Update last position
        lastBrushX = currentX
        lastBrushY = currentY
        
        // Update display bitmap
        updateDisplayBitmap()
    }

    /**
     * Start a new brush stroke (for undo/redo)
     */
    fun startBrushStroke() {
        // Reset last brush position
        lastBrushX = null
        lastBrushY = null

        _colorLayer.value?.let { colorLayer ->
            // Save the current color layer state before starting the brush stroke
            undoStack.add(UndoState(colorLayer.copy(colorLayer.config, true), "Brush Stroke", System.currentTimeMillis()))
            redoStack.clear()
            updateUndoRedoStates()
        }
    }

    fun undo() {
        if (undoStack.size > 1) {
            val currentState = undoStack.removeAt(undoStack.size - 1)
            _colorLayer.value?.let { colorLayer ->
                redoStack.add(UndoState(colorLayer.copy(colorLayer.config, true), currentState.action, System.currentTimeMillis()))
            }
            val previousState = undoStack.last()
            _colorLayer.value = previousState.bitmap.copy(previousState.bitmap.config, true)
            updateDisplayBitmap()
            updateUndoRedoStates()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.size - 1)
            _colorLayer.value?.let { colorLayer ->
                undoStack.add(UndoState(colorLayer.copy(colorLayer.config, true), nextState.action, System.currentTimeMillis()))
            }
            _colorLayer.value = nextState.bitmap.copy(nextState.bitmap.config, true)
            updateDisplayBitmap()
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