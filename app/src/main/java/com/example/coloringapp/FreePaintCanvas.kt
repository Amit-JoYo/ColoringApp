package com.example.coloringapp

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coloringapp.ui.theme.FredokaFont
import com.example.coloringapp.ui.theme.GameColors
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast

/**
 * Optimized flood fill using scanline algorithm with pixel array for performance
 */
private fun floodFill(bitmap: Bitmap, startX: Int, startY: Int, fillColor: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    
    // Get all pixels at once (much faster than individual getPixel calls)
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    
    val targetColor = pixels[startY * width + startX]
    
    // Don't fill if target color is same as fill color
    if (targetColor == fillColor) return bitmap
    
    // Don't fill black lines (the outlines)
    val targetRed = android.graphics.Color.red(targetColor)
    val targetGreen = android.graphics.Color.green(targetColor)
    val targetBlue = android.graphics.Color.blue(targetColor)
    val brightness = (targetRed + targetGreen + targetBlue) / 3
    if (brightness < 50) return bitmap // Too dark, likely a line
    
    // Tolerance for color matching (to handle anti-aliased edges)
    val tolerance = 32
    
    // Pre-calculate target color components
    val tR = targetRed
    val tG = targetGreen
    val tB = targetBlue
    
    // Fast color matching using inline calculation
    fun shouldFill(pixelIndex: Int): Boolean {
        val c = pixels[pixelIndex]
        if (c == fillColor) return false
        val r = android.graphics.Color.red(c)
        val g = android.graphics.Color.green(c)
        val b = android.graphics.Color.blue(c)
        return kotlin.math.abs(r - tR) <= tolerance &&
               kotlin.math.abs(g - tG) <= tolerance &&
               kotlin.math.abs(b - tB) <= tolerance
    }
    
    // Scanline flood fill - much faster than BFS pixel-by-pixel
    val stack = java.util.ArrayDeque<Int>()
    stack.push(startY * width + startX)
    
    while (stack.isNotEmpty()) {
        val pos = stack.pop()
        val y = pos / width
        var x = pos % width
        
        // Move left to find the start of the scanline
        var leftX = x
        while (leftX > 0 && shouldFill((y * width) + leftX - 1)) {
            leftX--
        }
        
        // Move right and fill, checking above and below
        var rightX = leftX
        var checkAbove = true
        var checkBelow = true
        
        while (rightX < width && shouldFill(y * width + rightX)) {
            pixels[y * width + rightX] = fillColor
            
            // Check pixel above
            if (y > 0) {
                val aboveIdx = (y - 1) * width + rightX
                if (shouldFill(aboveIdx)) {
                    if (checkAbove) {
                        stack.push(aboveIdx)
                        checkAbove = false
                    }
                } else {
                    checkAbove = true
                }
            }
            
            // Check pixel below
            if (y < height - 1) {
                val belowIdx = (y + 1) * width + rightX
                if (shouldFill(belowIdx)) {
                    if (checkBelow) {
                        stack.push(belowIdx)
                        checkBelow = false
                    }
                } else {
                    checkBelow = true
                }
            }
            
            rightX++
        }
    }
    
    // Set all pixels at once (much faster than individual setPixel calls)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

/**
 * Helper data class for image drawing calculations
 */
private data class ImageDrawInfo(
    val offsetX: Float,
    val offsetY: Float,
    val width: Int,
    val height: Int
)

/**
 * Free paint canvas - paint freely on top of an image
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreePaintCanvas(
    backgroundBitmap: Bitmap,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Mutable bitmap for flood fill operations
    var paintedBitmap by remember { 
        mutableStateOf(backgroundBitmap.copy(Bitmap.Config.ARGB_8888, true)) 
    }
    var bitmapVersion by remember { mutableStateOf(0) } // Force recomposition when bitmap changes
    
    // Drawing state for brush strokes
    var paths by remember { mutableStateOf(listOf<DrawingPath>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var undoStack by remember { mutableStateOf(listOf<DrawingPath>()) }
    
    // Undo stack for fill operations (stores previous bitmap states)
    var fillUndoStack by remember { mutableStateOf(listOf<Bitmap>()) }
    
    // Tool state
    var selectedTool by remember { mutableStateOf(ToolMode.PAINT_BUCKET) } // Default to paint bucket for coloring
    var selectedColor by remember { mutableStateOf(Color.Red) } // Start with a color, not black
    var brushSize by remember { mutableFloatStateOf(8f) }
    var selectedBrushType by remember { mutableStateOf(BrushType.NORMAL) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    // Custom color picker state
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var lightness by remember { mutableFloatStateOf(0.5f) }
    
    // Canvas and image sizing
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val imageBitmap = remember(paintedBitmap, bitmapVersion) { paintedBitmap.asImageBitmap() }
    
    // Calculate image dimensions to maintain aspect ratio
    val imageDrawInfo = remember(canvasSize, imageBitmap) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val imageAspect = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
            val canvasAspect = canvasSize.width.toFloat() / canvasSize.height.toFloat()
            
            val (drawWidth, drawHeight) = if (imageAspect > canvasAspect) {
                // Image is wider - fit to width
                canvasSize.width.toFloat() to (canvasSize.width.toFloat() / imageAspect)
            } else {
                // Image is taller - fit to height
                (canvasSize.height.toFloat() * imageAspect) to canvasSize.height.toFloat()
            }
            
            val offsetX = (canvasSize.width - drawWidth) / 2f
            val offsetY = (canvasSize.height - drawHeight) / 2f
            
            ImageDrawInfo(offsetX, offsetY, drawWidth.toInt(), drawHeight.toInt())
        } else {
            ImageDrawInfo(0f, 0f, canvasSize.width, canvasSize.height)
        }
    }
    
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
                        stringResource(R.string.free_paint),
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
                    // Undo (handles both fills and brush strokes)
                    IconButton(
                        onClick = {
                            if (selectedTool == ToolMode.PAINT_BUCKET && fillUndoStack.isNotEmpty()) {
                                // Undo fill
                                paintedBitmap = fillUndoStack.last()
                                fillUndoStack = fillUndoStack.dropLast(1)
                                bitmapVersion++
                            } else if (paths.isNotEmpty()) {
                                // Undo brush stroke
                                undoStack = undoStack + paths.last()
                                paths = paths.dropLast(1)
                            }
                        },
                        enabled = (selectedTool == ToolMode.PAINT_BUCKET && fillUndoStack.isNotEmpty()) || paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.undo))
                    }
                    // Redo (only for brush strokes)
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
                    // Clear drawing (reset to original image)
                    IconButton(onClick = { 
                        paths = emptyList()
                        undoStack = emptyList()
                        fillUndoStack = emptyList()
                        paintedBitmap = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, true)
                        bitmapVersion++
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear))
                    }
                    // Save
                    IconButton(onClick = {
                        savePainting(context, paintedBitmap, paths, canvasSize, imageDrawInfo)
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
            // Drawing canvas with background image
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
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(selectedTool, selectedColor) {
                            if (selectedTool == ToolMode.PAINT_BUCKET) {
                                // Tap to fill
                                detectTapGestures { offset ->
                                    // Convert screen coordinates to bitmap coordinates
                                    val bitmapX = ((offset.x - imageDrawInfo.offsetX) / imageDrawInfo.width * paintedBitmap.width).toInt()
                                    val bitmapY = ((offset.y - imageDrawInfo.offsetY) / imageDrawInfo.height * paintedBitmap.height).toInt()
                                    
                                    if (bitmapX in 0 until paintedBitmap.width && bitmapY in 0 until paintedBitmap.height) {
                                        // Save current state for undo
                                        fillUndoStack = fillUndoStack + paintedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                                        
                                        // Perform flood fill
                                        val newBitmap = floodFill(
                                            paintedBitmap.copy(Bitmap.Config.ARGB_8888, true),
                                            bitmapX,
                                            bitmapY,
                                            selectedColor.toArgb()
                                        )
                                        paintedBitmap = newBitmap
                                        bitmapVersion++
                                    }
                                }
                            } else {
                                // Brush drawing
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
                                            undoStack = emptyList()
                                        }
                                        currentPath = null
                                    }
                                )
                            }
                        }
                ) {
                    // Draw painted bitmap (with flood fills applied)
                    val paintedImageBitmap = paintedBitmap.asImageBitmap()
                    drawImage(
                        image = paintedImageBitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            imageDrawInfo.offsetX.toInt(),
                            imageDrawInfo.offsetY.toInt()
                        ),
                        dstSize = IntSize(imageDrawInfo.width, imageDrawInfo.height)
                    )
                    
                    // Draw all completed paths with brush effects (only in brush mode)
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
                    // Tool mode selector (Paint Bucket / Brush)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Paint Bucket tool
                        Surface(
                            modifier = Modifier
                                .clickable { selectedTool = ToolMode.PAINT_BUCKET },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedTool == ToolMode.PAINT_BUCKET) GameColors.WoodDark else Color.Transparent,
                            tonalElevation = if (selectedTool == ToolMode.PAINT_BUCKET) 4.dp else 0.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🪣",
                                    fontSize = 24.sp
                                )
                                Text(
                                    text = "Fill",
                                    fontSize = 12.sp,
                                    color = if (selectedTool == ToolMode.PAINT_BUCKET) Color.White else GameColors.TextDark,
                                    fontFamily = FredokaFont
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Brush tool
                        Surface(
                            modifier = Modifier
                                .clickable { selectedTool = ToolMode.BRUSH },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedTool == ToolMode.BRUSH) GameColors.WoodDark else Color.Transparent,
                            tonalElevation = if (selectedTool == ToolMode.BRUSH) 4.dp else 0.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🖌️",
                                    fontSize = 24.sp
                                )
                                Text(
                                    text = "Brush",
                                    fontSize = 12.sp,
                                    color = if (selectedTool == ToolMode.BRUSH) Color.White else GameColors.TextDark,
                                    fontFamily = FredokaFont
                                )
                            }
                        }
                    }
                    
                    // Show brush type selector only when brush mode is selected
                    if (selectedTool == ToolMode.BRUSH) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
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

/**
 * Save the painting (image + drawing) to device storage
 */
private fun savePainting(
    context: Context,
    backgroundBitmap: Bitmap,
    paths: List<DrawingPath>,
    canvasSize: IntSize,
    imageDrawInfo: ImageDrawInfo
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) {
        Toast.makeText(context, "Cannot save - canvas not ready", Toast.LENGTH_SHORT).show()
        return
    }
    
    try {
        // Create bitmap at canvas size
        val bitmap = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Fill with white background
        canvas.drawColor(android.graphics.Color.WHITE)
        
        // Draw scaled background image maintaining aspect ratio
        val scaledBg = Bitmap.createScaledBitmap(backgroundBitmap, imageDrawInfo.width, imageDrawInfo.height, true)
        canvas.drawBitmap(scaledBg, imageDrawInfo.offsetX, imageDrawInfo.offsetY, null)
        
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
            val androidPath = drawingPath.path.asAndroidPath()
            canvas.drawPath(androidPath, paint)
        }
        
        // Save to MediaStore
        val filename = "painting_${System.currentTimeMillis()}.png"
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
