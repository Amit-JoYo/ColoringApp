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

    private val _selectedColor = MutableStateFlow(Color.Red)
    val selectedColor = _selectedColor.asStateFlow()

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
            _imageBitmap.value = processImageToLineArt(bitmap)
        }
    }

    fun setImageBitmapFromDrawable(context: android.content.Context, drawableId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val options = android.graphics.BitmapFactory.Options()
            options.inMutable = true
            val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, drawableId, options)
            _imageBitmap.value = processImageToLineArt(bitmap)
        }
    }

    fun setSelectedColor(color: Color) {
        _selectedColor.value = color
    }

    val isPaintingScreen: Boolean
        get() = _imageBitmap.value != null

    fun clearImage() {
        _imageBitmap.value = null
    }

    fun startFloodFill(x: Int, y: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            _imageBitmap.value?.let {
                val newBitmap = floodFill(it, x, y, _selectedColor.value)
                _imageBitmap.value = newBitmap
            }
        }
    }
}