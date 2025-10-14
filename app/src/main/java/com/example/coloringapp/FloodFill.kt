package com.example.coloringapp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedList

suspend fun floodFill(bitmap: Bitmap, x: Int, y: Int, newColor: Color): Bitmap = withContext(Dispatchers.Default) {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val targetColor = pixels[y * width + x]
    val newColorArgb = newColor.toArgb()

    if (targetColor == newColorArgb) {
        return@withContext bitmap
    }

    val queue = LinkedList<Pair<Int, Int>>()
    queue.add(x to y)

    while (queue.isNotEmpty()) {
        val (px, py) = queue.poll()!!

        if (px in 0 until width && py in 0 until height) {
            val pixelOffset = py * width + px
            if (pixels[pixelOffset] == targetColor) {
                pixels[pixelOffset] = newColorArgb

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