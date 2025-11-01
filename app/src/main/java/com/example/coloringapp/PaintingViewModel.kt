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

    // A unique ID for the current image session. Changes only when a new image is loaded.
    private val _imageSessionId = MutableStateFlow(0)
    val imageSessionId = _imageSessionId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color.Red)
    val selectedColor = _selectedColor.asStateFlow()

    private val undoStack = mutableListOf<Bitmap>()
    private val redoStack = mutableListOf<Bitmap>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

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
            val processedBitmap = segmentImageByColor(bitmap)
            _imageBitmap.value = processedBitmap
            undoStack.clear()
            redoStack.clear()
            undoStack.add(processedBitmap.copy(processedBitmap.config, true))
            updateUndoRedoStates()
            _imageSessionId.value++ // New image session
            _isLoading.value = false
        }
    }

    fun setImageBitmapFromDrawable(context: android.content.Context, drawableId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val options = android.graphics.BitmapFactory.Options()
            options.inMutable = true
            val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, drawableId, options)
            val processedBitmap = segmentImageByColor(bitmap)
            _imageBitmap.value = processedBitmap
            undoStack.clear()
            redoStack.clear()
            undoStack.add(processedBitmap.copy(processedBitmap.config, true))
            updateUndoRedoStates()
            _imageSessionId.value++ // New image session
            _isLoading.value = false
        }
    }

    fun setSelectedColor(color: Color) {
        _selectedColor.value = color
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
                undoStack.add(currentBitmap)
                redoStack.clear()
                val newBitmap = floodFill(it, x, y, _selectedColor.value)
                _imageBitmap.value = newBitmap
                updateUndoRedoStates()
            }
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val lastBitmap = undoStack.removeAt(undoStack.size - 1)
            _imageBitmap.value?.let {
                redoStack.add(it.copy(it.config, true))
            }
            _imageBitmap.value = lastBitmap
            updateUndoRedoStates()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextBitmap = redoStack.removeAt(redoStack.size - 1)
            _imageBitmap.value?.let {
                undoStack.add(it.copy(it.config, true))
            }
            _imageBitmap.value = nextBitmap
            updateUndoRedoStates()
        }
    }

    private fun updateUndoRedoStates() {
        _canUndo.value = undoStack.size > 1
        _canRedo.value = redoStack.isNotEmpty()
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
 * Represents the status of a save operation
 */
sealed class SaveStatus {
    object Idle : SaveStatus()
    object Saving : SaveStatus()
    data class Success(val uri: Uri) : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}