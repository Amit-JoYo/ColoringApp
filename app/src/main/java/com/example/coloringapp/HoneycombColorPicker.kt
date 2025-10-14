package com.example.coloringapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.pow
import kotlin.math.sqrt

import androidx.compose.ui.input.pointer.pointerInput

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HoneycombColorPicker(onColorSelected: (Color) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val hexagonSize = 30f
        val hexagonWidth = hexagonSize * 2
        val hexagonHeight = hexagonSize * kotlin.math.sqrt(3f)

        val colors = generateColorPalette()
        val hexagons = mutableListOf<Pair<Offset, Color>>()

        val gridWidth = 10 * hexagonWidth
        val gridHeight = (colors.size / 10) * hexagonHeight * 0.75f

        val offsetX = (constraints.maxWidth - gridWidth) / 2
        val offsetY = (constraints.maxHeight - gridHeight) / 2

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    val tappedOffset = it
                    for ((center, color) in hexagons) {
                        if ((tappedOffset.x - center.x).pow(2) + (tappedOffset.y - center.y).pow(2) < hexagonSize.pow(2)) {
                            onColorSelected(color)
                            return@detectTapGestures
                        }
                    }
                }
            }) {
            for ((index, color) in colors.withIndex()) {
                val (x, y) = getHexagonCoordinates(index, hexagonWidth, hexagonHeight, offsetX, offsetY)
                val center = Offset(x, y)
                hexagons.add(center to color)
                val path = Path()
                for (i in 0..5) {
                    val angle = (60 * i - 30) * (PI / 180)
                    val px = x + hexagonSize * cos(angle).toFloat()
                    val py = y + hexagonSize * sin(angle).toFloat()
                    if (i == 0) {
                        path.moveTo(px, py)
                    } else {
                        path.lineTo(px, py)
                    }
                }
                path.close()
                drawPath(path, color = color)
            }
        }
    }
}

private fun getHexagonCoordinates(index: Int, width: Float, height: Float, offsetX: Float, offsetY: Float): Pair<Float, Float> {
    val row = (index / 10)
    val col = index % 10

    val x = col * width + (if (row % 2 == 1) width / 2 else 0f) + offsetX
    val y = row * height * 0.75f + offsetY

    return x to y
}

private fun generateColorPalette(): List<Color> {
    val colors = mutableListOf<Color>()
    val baseColors = listOf(
        Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta,
        Color(0xFFFFA500), Color(0xFF800080), Color(0xFF00FFFF), Color(0xFFFFC0CB),
        Color(0xFF008000), Color(0xFF800000), Color(0xFF000080), Color(0xFF808000)
    )

    for (baseColor in baseColors) {
        for (i in 1..10) {
            val tint = baseColor.copy(alpha = 1f, red = (baseColor.red + (1 - baseColor.red) * (i / 10f)), green = (baseColor.green + (1 - baseColor.green) * (i / 10f)), blue = (baseColor.blue + (1 - baseColor.blue) * (i / 10f)))
            val shade = baseColor.copy(alpha = 1f, red = baseColor.red * (1 - (i / 10f)), green = baseColor.green * (1 - (i / 10f)), blue = baseColor.blue * (1 - (i / 10f)))
            colors.add(tint)
            colors.add(shade)
        }
    }

    return colors
}
