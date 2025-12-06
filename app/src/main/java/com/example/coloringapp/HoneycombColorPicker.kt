package com.example.coloringapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun HoneycombColorPicker(
    onColorSelected: (Color) -> Unit,
    currentBitmap: Bitmap? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    var customColor by remember { mutableStateOf(Color.Red) }
    var redValue by remember { mutableStateOf(255f) }
    var greenValue by remember { mutableStateOf(0f) }
    var blueValue by remember { mutableStateOf(0f) }
    var eyeDropperMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // Tab selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Palette", "Custom", "Eyedropper").forEachIndexed { index, title ->
                Button(
                    onClick = { 
                        selectedTab = index
                        if (index == 2) eyeDropperMode = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == index) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                ) {
                    Text(title, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content based on selected tab
        when (selectedTab) {
            0 -> {
                // Palette Tab - Pre-defined colors with categories
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ColorPaletteSection("Essential", getEssentialColors(), onColorSelected)
                    ColorPaletteSection("Rainbow", getRainbowColors(), onColorSelected)
                    ColorPaletteSection("Pastels", getPastelColors(), onColorSelected)
                    ColorPaletteSection("Earth Tones", getEarthToneColors(), onColorSelected)
                    ColorPaletteSection("Vibrant", getVibrantColors(), onColorSelected)
                    ColorPaletteSection("Grays", getGrayScaleColors(), onColorSelected)
                }
            }
            1 -> {
                // Custom RGB Tab
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Color preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(customColor)
                            .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // RGB Sliders
                    ColorSlider(
                        label = "Red",
                        value = redValue,
                        color = Color.Red,
                        onValueChange = {
                            redValue = it
                            customColor = Color(
                                red = redValue / 255f,
                                green = greenValue / 255f,
                                blue = blueValue / 255f
                            )
                        }
                    )

                    ColorSlider(
                        label = "Green",
                        value = greenValue,
                        color = Color.Green,
                        onValueChange = {
                            greenValue = it
                            customColor = Color(
                                red = redValue / 255f,
                                green = greenValue / 255f,
                                blue = blueValue / 255f
                            )
                        }
                    )

                    ColorSlider(
                        label = "Blue",
                        value = blueValue,
                        color = Color.Blue,
                        onValueChange = {
                            blueValue = it
                            customColor = Color(
                                red = redValue / 255f,
                                green = greenValue / 255f,
                                blue = blueValue / 255f
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Apply button
                    Button(
                        onClick = { onColorSelected(customColor) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use This Color")
                    }
                }
            }
            2 -> {
                // Eyedropper Tab
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "💧",
                        fontSize = 64.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "Eyedropper Tool",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "Tap on the image to pick a color from it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (currentBitmap != null) {
                        Text(
                            "Close this picker and tap anywhere on your coloring page",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Load an image first to use the eyedropper",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPaletteSection(
    title: String,
    colors: List<Color>,
    onColorSelected: (Color) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .height(((colors.size / 8 + 1) * 48).dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(colors) { color ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                        .clickable { onColorSelected(color) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value.roundToInt().toString(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color
            )
        )
    }
}

// Color palette generators
private fun getEssentialColors(): List<Color> = listOf(
    Color.Black,
    Color.White,
    Color.Red,
    Color.Blue,
    Color.Green,
    Color.Yellow,
    Color(0xFFFFA500), // Orange
    Color(0xFF800080)  // Purple
)

private fun getRainbowColors(): List<Color> = listOf(
    Color(0xFFFF0000), // Red
    Color(0xFFFF7F00), // Orange
    Color(0xFFFFFF00), // Yellow
    Color(0xFF00FF00), // Green
    Color(0xFF0000FF), // Blue
    Color(0xFF4B0082), // Indigo
    Color(0xFF9400D3), // Violet
    Color(0xFFFF1493), // Deep Pink
    Color(0xFFFF69B4), // Hot Pink
    Color(0xFFFFB6C1), // Light Pink
    Color(0xFFFFC0CB), // Pink
    Color(0xFFDDA0DD), // Plum
    Color(0xFFEE82EE), // Violet
    Color(0xFFBA55D3), // Medium Orchid
    Color(0xFF8B008B), // Dark Magenta
    Color(0xFF800000)  // Maroon
)

private fun getPastelColors(): List<Color> = listOf(
    Color(0xFFFFB3BA), // Pastel Red
    Color(0xFFFFDFBA), // Pastel Orange
    Color(0xFFFFFFBA), // Pastel Yellow
    Color(0xFFBAFFC9), // Pastel Green
    Color(0xFFBAE1FF), // Pastel Blue
    Color(0xFFE0BBE4), // Pastel Purple
    Color(0xFFFEC8D8), // Pastel Pink
    Color(0xFFFFDFD3), // Pastel Peach
    Color(0xFFFFF4E0), // Pastel Cream
    Color(0xFFD4F1F4), // Pastel Cyan
    Color(0xFFE8DFF5), // Pastel Lavender
    Color(0xFFFCE4EC), // Pastel Rose
    Color(0xFFF0E68C), // Pastel Khaki
    Color(0xFFE6E6FA), // Lavender
    Color(0xFFF5F5DC), // Beige
    Color(0xFFFAF0E6)  // Linen
)

private fun getEarthToneColors(): List<Color> = listOf(
    Color(0xFF8B4513), // Saddle Brown
    Color(0xFFA0522D), // Sienna
    Color(0xFFD2691E), // Chocolate
    Color(0xFFCD853F), // Peru
    Color(0xFFDEB887), // Burlywood
    Color(0xFFF4A460), // Sandy Brown
    Color(0xFFD2B48C), // Tan
    Color(0xFFBC8F8F), // Rosy Brown
    Color(0xFF8B7355), // Khaki
    Color(0xFF6B8E23), // Olive Drab
    Color(0xFF556B2F), // Dark Olive Green
    Color(0xFF8FBC8F), // Dark Sea Green
    Color(0xFF2F4F4F), // Dark Slate Gray
    Color(0xFF696969), // Dim Gray
    Color(0xFF708090), // Slate Gray
    Color(0xFF778899)  // Light Slate Gray
)

private fun getVibrantColors(): List<Color> = listOf(
    Color(0xFFFF0000), // Pure Red
    Color(0xFF00FF00), // Lime
    Color(0xFF0000FF), // Pure Blue
    Color(0xFFFFFF00), // Pure Yellow
    Color(0xFFFF00FF), // Magenta
    Color(0xFF00FFFF), // Cyan
    Color(0xFFFF4500), // Orange Red
    Color(0xFF32CD32), // Lime Green
    Color(0xFF1E90FF), // Dodger Blue
    Color(0xFFFFD700), // Gold
    Color(0xFFFF1493), // Deep Pink
    Color(0xFF00CED1), // Dark Turquoise
    Color(0xFFFF6347), // Tomato
    Color(0xFF7FFF00), // Chartreuse
    Color(0xFF4169E1), // Royal Blue
    Color(0xFFFFA500)  // Orange
)

private fun getGrayScaleColors(): List<Color> = listOf(
    Color(0xFF000000), // Black
    Color(0xFF1A1A1A),
    Color(0xFF333333),
    Color(0xFF4D4D4D),
    Color(0xFF666666),
    Color(0xFF808080), // Gray
    Color(0xFF999999),
    Color(0xFFB3B3B3),
    Color(0xFFCCCCCC),
    Color(0xFFE6E6E6),
    Color(0xFFF2F2F2),
    Color(0xFFFFFFFF)  // White
)
