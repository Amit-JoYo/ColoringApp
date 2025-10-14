package com.example.coloringapp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedList
import kotlin.math.abs

// Helper function to check if two colors are similar within a given tolerance.
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

    return abs(a1 - a2) <= tolerance &&
           abs(r1 - r2) <= tolerance &&
           abs(g1 - g2) <= tolerance &&
           abs(b1 - b2) <= tolerance
}

suspend fun floodFill(bitmap: Bitmap, x: Int, y: Int, newColor: Color, tolerance: Int = 30): Bitmap = withContext(Dispatchers.Default) {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val targetColor = pixels[y * width + x]
    val newColorArgb = newColor.toArgb()

    // If the target color is already the new color, do nothing.
    if (areColorsSimilar(targetColor, newColorArgb, tolerance)) {
        return@withContext bitmap
    }

    val queue = LinkedList<Pair<Int, Int>>()
    queue.add(x to y)

    while (queue.isNotEmpty()) {
        val (px, py) = queue.poll()!!

        if (px in 0 until width && py in 0 until height) {
            val pixelOffset = py * width + px
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