package com.example.coloringapp

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coloringapp.ui.theme.FredokaFont
import com.example.coloringapp.ui.theme.GameColors
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Tool mode - brush for drawing or paint bucket for filling
 */
enum class ToolMode(val displayName: String, val emoji: String) {
    BRUSH("Brush", "🖌️"),
    PAINT_BUCKET("Fill", "🪣")
}

/**
 * Brush types for special effects
 */
enum class BrushType(val displayName: String, val emoji: String) {
    NORMAL("Normal", "✏️"),
    MARKER("Marker", "🖍️"),
    RAINBOW("Rainbow", "🌈"),
    GLOW("Glow", "✨"),
    SPRAY("Spray", "💨")
}

/**
 * A single drawing stroke (path segment)
 */
data class DrawingPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val brushType: BrushType = BrushType.NORMAL,
    val rainbowColors: List<Color>? = null // For rainbow brush, stores the gradient colors
)

/**
 * Free drawing canvas component with full drawing tools
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeDrawingCanvas(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Drawing state
    var paths by remember { mutableStateOf(listOf<DrawingPath>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var undoStack by remember { mutableStateOf(listOf<DrawingPath>()) }
    
    // Tool state
    var selectedColor by remember { mutableStateOf(Color.Black) }
    var brushSize by remember { mutableFloatStateOf(8f) }
    var selectedBrushType by remember { mutableStateOf(BrushType.NORMAL) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showBrushSizePicker by remember { mutableStateOf(false) }
    
    // Custom color picker state
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var lightness by remember { mutableFloatStateOf(0.5f) }
    
    // Canvas size for bitmap export
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }
    
    // Rainbow colors for rainbow brush
    val rainbowColors = listOf(
        Color.Red,
        Color(0xFFFF7F00), // Orange
        Color.Yellow,
        Color.Green,
        Color.Blue,
        Color(0xFF4B0082), // Indigo
        Color(0xFF9400D3)  // Violet
    )
    
    // Extended color palette with more colors
    val colorPalette = listOf(
        // Row 1: Basic colors
        Color.Black,
        Color.White,
        Color(0xFF808080), // Gray
        Color(0xFFC0C0C0), // Silver
        // Row 2: Reds and pinks
        Color.Red,
        Color(0xFFDC143C), // Crimson
        Color(0xFFFF69B4), // Hot Pink
        Color(0xFFFF1493), // Deep Pink
        // Row 3: Oranges and yellows
        Color(0xFFFF5722), // Deep Orange
        Color(0xFFFF9800), // Orange
        Color(0xFFFFEB3B), // Yellow
        Color(0xFFFFD700), // Gold
        // Row 4: Greens
        Color(0xFF4CAF50), // Green
        Color(0xFF8BC34A), // Light Green
        Color(0xFF00FF00), // Lime
        Color(0xFF006400), // Dark Green
        // Row 5: Blues
        Color(0xFF2196F3), // Blue
        Color(0xFF03A9F4), // Light Blue
        Color(0xFF00BCD4), // Cyan
        Color(0xFF000080), // Navy
        // Row 6: Purples
        Color(0xFF9C27B0), // Purple
        Color(0xFF673AB7), // Deep Purple
        Color(0xFFE91E63), // Pink
        Color(0xFF9400D3), // Violet
        // Row 7: Browns and earth tones
        Color(0xFF795548), // Brown
        Color(0xFF8B4513), // Saddle Brown
        Color(0xFFD2691E), // Chocolate
        Color(0xFFF5DEB3), // Wheat
    )
    
    // Brush sizes
    val brushSizes = listOf(4f, 8f, 12f, 20f, 32f)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.free_drawing),
                        fontFamily = FredokaFont,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // Undo
                    IconButton(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                undoStack = undoStack + paths.last()
                                paths = paths.dropLast(1)
                            }
                        },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.undo))
                    }
                    // Redo
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                paths = paths + undoStack.last()
                                undoStack = undoStack.dropLast(1)
                            }
                        },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.redo))
                    }
                    // Clear
                    IconButton(onClick = { 
                        paths = emptyList()
                        undoStack = emptyList()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear))
                    }
                    // Save
                    IconButton(onClick = {
                        saveDrawing(context, paths, canvasWidth, canvasHeight)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GameColors.WoodMedium
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(GameColors.WoodLight)
        ) {
            // Drawing canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPath = Path().apply {
                                        moveTo(offset.x, offset.y)
                                    }
                                },
                                onDrag = { change, _ ->
                                    currentPath?.let { path ->
                                        path.lineTo(change.position.x, change.position.y)
                                        currentPath = Path().apply {
                                            addPath(path)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    currentPath?.let { path ->
                                        paths = paths + DrawingPath(
                                            path = path,
                                            color = selectedColor,
                                            strokeWidth = brushSize,
                                            brushType = selectedBrushType,
                                            rainbowColors = if (selectedBrushType == BrushType.RAINBOW) rainbowColors else null
                                        )
                                        undoStack = emptyList() // Clear redo stack on new draw
                                    }
                                    currentPath = null
                                }
                            )
                        }
                ) {
                    canvasWidth = size.width.toInt()
                    canvasHeight = size.height.toInt()
                    
                    // Draw all completed paths with special effects
                    paths.forEach { drawingPath ->
                        drawPathWithBrushEffect(drawingPath)
                    }
                    
                    // Draw current path being drawn with current brush effect
                    currentPath?.let { path ->
                        val tempDrawingPath = DrawingPath(
                            path = path,
                            color = selectedColor,
                            strokeWidth = brushSize,
                            brushType = selectedBrushType,
                            rainbowColors = if (selectedBrushType == BrushType.RAINBOW) rainbowColors else null
                        )
                        drawPathWithBrushEffect(tempDrawingPath)
                    }
                }
            }
            
            // Tool bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GameColors.WoodMedium,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // Brush type selector row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrushType.entries.forEach { brushType ->
                            val isSelected = selectedBrushType == brushType
                            Surface(
                                modifier = Modifier
                                    .clickable { selectedBrushType = brushType },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) GameColors.WoodDark else Color.Transparent,
                                tonalElevation = if (isSelected) 4.dp else 0.dp
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = brushType.emoji,
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = brushType.displayName,
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.White else GameColors.TextDark,
                                        fontFamily = FredokaFont
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Color picker row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Custom color picker button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                    )
                                )
                                .border(2.dp, GameColors.WoodDark, CircleShape)
                                .clickable { showColorPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        ) {
                            items(colorPalette) { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedColor == color) 3.dp else 1.dp,
                                            color = if (selectedColor == color) GameColors.WoodDark else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = color }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Brush size row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✏️",
                            fontSize = 20.sp
                        )
                        
                        brushSizes.forEach { size ->
                            val isSelected = brushSize == size
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) selectedColor.copy(alpha = 0.3f) 
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) GameColors.WoodDark else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { brushSize = size },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(size.dp.coerceAtMost(32.dp))
                                        .clip(CircleShape)
                                        .background(selectedColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Custom Color Picker Dialog
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { 
                Text(
                    "Choose Color",
                    fontFamily = FredokaFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Color preview
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                Color.hsl(hue, saturation, lightness)
                            )
                            .border(2.dp, Color.Gray, CircleShape)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Hue slider (color wheel)
                    Text("Color", fontFamily = FredokaFont)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.hsl(0f, 1f, 0.5f),
                                        Color.hsl(60f, 1f, 0.5f),
                                        Color.hsl(120f, 1f, 0.5f),
                                        Color.hsl(180f, 1f, 0.5f),
                                        Color.hsl(240f, 1f, 0.5f),
                                        Color.hsl(300f, 1f, 0.5f),
                                        Color.hsl(360f, 1f, 0.5f)
                                    )
                                )
                            )
                    )
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Saturation slider
                    Text("Saturation", fontFamily = FredokaFont)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.hsl(hue, 0f, lightness),
                                        Color.hsl(hue, 1f, lightness)
                                    )
                                )
                            )
                    )
                    Slider(
                        value = saturation,
                        onValueChange = { saturation = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Lightness slider
                    Text("Brightness", fontFamily = FredokaFont)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Black,
                                        Color.hsl(hue, saturation, 0.5f),
                                        Color.White
                                    )
                                )
                            )
                    )
                    Slider(
                        value = lightness,
                        onValueChange = { lightness = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedColor = Color.hsl(hue, saturation, lightness)
                        showColorPicker = false
                    }
                ) {
                    Text("Select", fontFamily = FredokaFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showColorPicker = false }) {
                    Text("Cancel", fontFamily = FredokaFont)
                }
            }
        )
    }
}

/**
 * Save the drawing to device storage
 */
private fun saveDrawing(
    context: Context,
    paths: List<DrawingPath>,
    width: Int,
    height: Int
) {
    if (width <= 0 || height <= 0) {
        Toast.makeText(context, "Cannot save - canvas not ready", Toast.LENGTH_SHORT).show()
        return
    }
    
    try {
        // Create bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        
        // Draw all paths
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        
        paths.forEach { drawingPath ->
            paint.color = drawingPath.color.toArgb()
            paint.strokeWidth = drawingPath.strokeWidth
            
            // Convert Compose Path to Android Path
            val androidPath = drawingPath.path.asAndroidPath()
            canvas.drawPath(androidPath, paint)
        }
        
        // Save to MediaStore
        val filename = "drawing_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ColoringApp")
        }
        
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            Toast.makeText(context, "Saved to Pictures/ColoringApp", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
        }
        
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension function to draw a path with special brush effects
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPathWithBrushEffect(
    drawingPath: DrawingPath
) {
    when (drawingPath.brushType) {
        BrushType.NORMAL -> {
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color,
                style = Stroke(
                    width = drawingPath.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        BrushType.MARKER -> {
            // Marker effect: semi-transparent with flat cap
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color.copy(alpha = 0.6f),
                style = Stroke(
                    width = drawingPath.strokeWidth * 2f,
                    cap = StrokeCap.Square,
                    join = StrokeJoin.Bevel
                )
            )
        }
        BrushType.RAINBOW -> {
            // Rainbow effect: cycle through rainbow colors with gradient brush
            val rainbowBrush = Brush.sweepGradient(
                colors = drawingPath.rainbowColors ?: listOf(
                    Color.Red, Color(0xFFFF7F00), Color.Yellow,
                    Color.Green, Color.Blue, Color(0xFF4B0082), Color(0xFF9400D3)
                )
            )
            drawPath(
                path = drawingPath.path,
                brush = rainbowBrush,
                style = Stroke(
                    width = drawingPath.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        BrushType.GLOW -> {
            // Glow effect: draw multiple layers with decreasing alpha
            for (i in 3 downTo 0) {
                val glowWidth = drawingPath.strokeWidth + (i * 6f)
                val alpha = 0.15f + (0.2f * (3 - i) / 3f)
                drawPath(
                    path = drawingPath.path,
                    color = drawingPath.color.copy(alpha = alpha),
                    style = Stroke(
                        width = glowWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            // Core stroke
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color,
                style = Stroke(
                    width = drawingPath.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        BrushType.SPRAY -> {
            // Spray effect: draw with stippled/dotted appearance
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color.copy(alpha = 0.4f),
                style = Stroke(
                    width = drawingPath.strokeWidth * 3f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(2f, 4f, 6f, 4f), 0f
                    )
                )
            )
            // Add core line
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color.copy(alpha = 0.7f),
                style = Stroke(
                    width = drawingPath.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
