package com.example.coloringapp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.coloringapp.R

class PaintingViewModel : ViewModel() {

    private val _imageBitmap = MutableStateFlow<Bitmap?>(null)
    val imageBitmap = _imageBitmap.asStateFlow()

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
            val processedBitmap = convertToGrayscaleWithEdges(bitmap)
            _imageBitmap.value = processedBitmap
            undoStack.clear()
            redoStack.clear()
            undoStack.add(processedBitmap.copy(processedBitmap.config, true))
            updateUndoRedoStates()
            _isLoading.value = false
        }
    }

    fun setImageBitmapFromDrawable(context: android.content.Context, drawableId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val options = android.graphics.BitmapFactory.Options()
            options.inMutable = true
            val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, drawableId, options)
            val processedBitmap = convertToGrayscaleWithEdges(bitmap)
            _imageBitmap.value = processedBitmap
            undoStack.clear()
            redoStack.clear()
            undoStack.add(processedBitmap.copy(processedBitmap.config, true))
            updateUndoRedoStates()
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
}