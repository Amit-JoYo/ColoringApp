package com.example.coloringapp

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class ColorRegion(
    val colorIndex: Int,
    val originalColor: Int,
    val pixels: MutableSet<Pair<Int, Int>>,
    val centerX: Int,
    val centerY: Int,
    var isFilled: Boolean = false
)

data class GeminiColorInfo(
    val number: Int,
    val colorName: String,
    val hexColor: String
)

enum class Difficulty(val label: String, val minRegionSize: Float, val edgeThreshold: Int) {
    EASY("Easy", 0.005f, 40),      // Fewer, larger regions
    MEDIUM("Medium", 0.002f, 30),   // Balanced
    HARD("Hard", 0.001f, 20);        // More, smaller regions
    
    companion object {
        fun fromColorByNumberDifficulty(cbnDifficulty: ColorByNumberDifficulty): Difficulty {
            return when (cbnDifficulty) {
                ColorByNumberDifficulty.EASY -> EASY
                ColorByNumberDifficulty.MEDIUM -> MEDIUM
                ColorByNumberDifficulty.HARD -> HARD
            }
        }
    }
}

/**
 * Downscale a bitmap if it's too large to prevent OOM crashes
 * Target max dimension is 1200px which is good for color by number
 */
private fun downscaleIfNeeded(bitmap: Bitmap, maxDimension: Int = 1200): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val maxSide = maxOf(width, height)
    
    if (maxSide <= maxDimension) {
        return bitmap
    }
    
    val scale = maxDimension.toFloat() / maxSide
    val newWidth = (width * scale).toInt()
    val newHeight = (height * scale).toInt()
    
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
@Composable
fun ColorByNumberGame(
    imageBitmap: Bitmap,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    // Optional pre-configured settings (if provided, skip settings dialog)
    configuredNumberOfColors: Int? = null,
    configuredDifficulty: ColorByNumberDifficulty? = null
) {
    val context = LocalContext.current
    var regions by remember { mutableStateOf<List<ColorRegion>>(emptyList()) }
    var colorPalette by remember { mutableStateOf<List<Int>>(emptyList()) }
    var colorNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("Preparing...") }
    var displayBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var colorByNumberBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCompleted by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Pair(0f, 0f)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var usingAI by remember { mutableStateOf(false) }
    
    // Actual image bounds within canvas (for aspect ratio fitting)
    var imageRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    
    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val minScale = 1f
    val maxScale = 5f
    
    // Settings state - use pre-configured values if provided
    var showSettings by remember { mutableStateOf(configuredNumberOfColors == null) }
    var numberOfColors by remember { mutableStateOf(configuredNumberOfColors ?: 12) }
    var difficulty by remember { mutableStateOf(
        if (configuredDifficulty != null) 
            Difficulty.fromColorByNumberDifficulty(configuredDifficulty) 
        else 
            Difficulty.MEDIUM
    ) }
    
    val textMeasurer = rememberTextMeasurer()
    
    // Get API settings
    val apiKey = remember { getGeminiApiKey(context) }
    val aiProvider = remember { getAIProvider(context) }
    
    // Track if processing has started - auto-start if settings were pre-configured
    var processingStarted by remember { mutableStateOf(configuredNumberOfColors != null) }
    
    // Also set isLoading when auto-starting
    LaunchedEffect(Unit) {
        if (configuredNumberOfColors != null) {
            isLoading = true
        }
    }
    
    // Generate color-by-number image when settings are confirmed
    LaunchedEffect(processingStarted) {
        if (!processingStarted) return@LaunchedEffect
        
        withContext(Dispatchers.IO) {
            try {
                // Downscale large images to prevent OOM crashes
                val scaledBitmap = downscaleIfNeeded(imageBitmap, maxDimension = 1200)
                
                if (aiProvider == AIProvider.GEMINI && apiKey.isNotBlank()) {
                    loadingMessage = "Sending to Gemini AI..."
                    
                    val result = generateWithGemini(scaledBitmap, apiKey)
                    
                    usingAI = true
                    colorByNumberBitmap = result.first
                    colorPalette = result.second.map { parseHexColor(it.hexColor) }
                    colorNames = result.second.map { "${it.number}. ${it.colorName}" }
                    
                    // Analyze the returned image to create fillable regions
                    loadingMessage = "Analyzing coloring page..."
                    val analysisResult = analyzeColorByNumberImage(result.first, colorPalette)
                    regions = analysisResult
                    
                    displayBitmap = createDisplayBitmap(result.first, regions, colorPalette).asImageBitmap()
                } else {
                    // Use SLIC superpixel segmentation for local processing
                    // This works on ANY image type (photos, art, screenshots)
                    loadingMessage = "Analyzing image with SLIC..."
                    usingAI = false
                    
                    // Calculate superpixel count based on difficulty
                    val superpixelCount = getRecommendedSuperpixelCount(
                        scaledBitmap.width, 
                        scaledBitmap.height, 
                        difficulty
                    )
                    val compactness = getRecommendedCompactness(difficulty)
                    
                    loadingMessage = "Segmenting into regions..."
                    val slicResult = slicSuperpixels(
                        bitmap = scaledBitmap,
                        k = superpixelCount,
                        compactness = compactness,
                        maxIterations = 10
                    )
                    
                    loadingMessage = "Generating outlines..."
                    val lineArt = generateOutlinesFromSlic(slicResult, lineWidth = 2)
                    colorByNumberBitmap = lineArt
                    
                    loadingMessage = "Creating color palette..."
                    val result = slicToColorRegions(scaledBitmap, slicResult, numberOfColors)
                    colorPalette = result.first
                    
                    // Generate descriptive color names using our getColorName function
                    colorNames = result.first.mapIndexed { i, color -> 
                        val r = Color.red(color)
                        val g = Color.green(color)
                        val b = Color.blue(color)
                        "${i + 1}. ${getColorName(r, g, b)}"
                    }
                    regions = result.second
                    
                    displayBitmap = createDisplayBitmap(lineArt, regions, colorPalette).asImageBitmap()
                }
                
                isLoading = false
                
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    errorMessage = "Error: ${e.message}. Using local processing..."
                }
                
                // Fallback to SLIC local processing
                try {
                    val fallbackScaled = downscaleIfNeeded(imageBitmap, maxDimension = 1200)
                    
                    // Use SLIC for fallback as well
                    val superpixelCount = getRecommendedSuperpixelCount(
                        fallbackScaled.width, 
                        fallbackScaled.height, 
                        difficulty
                    )
                    val slicResult = slicSuperpixels(
                        bitmap = fallbackScaled,
                        k = superpixelCount,
                        compactness = getRecommendedCompactness(difficulty),
                        maxIterations = 10
                    )
                    
                    val lineArt = generateOutlinesFromSlic(slicResult, lineWidth = 2)
                    colorByNumberBitmap = lineArt
                    
                    val result = slicToColorRegions(fallbackScaled, slicResult, numberOfColors)
                    colorPalette = result.first
                    colorNames = result.first.mapIndexed { i, color -> 
                        val r = Color.red(color)
                        val g = Color.green(color)
                        val b = Color.blue(color)
                        "${i + 1}. ${getColorName(r, g, b)}"
                    }
                    regions = result.second
                    
                    displayBitmap = createDisplayBitmap(lineArt, regions, colorPalette).asImageBitmap()
                    isLoading = false
                } catch (e2: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed: ${e2.message}"
                        isLoading = false
                    }
                }
            }
        }
    }
    
    // Update display when regions are filled
    LaunchedEffect(regions) {
        if (regions.isNotEmpty() && !isLoading && colorByNumberBitmap != null) {
            withContext(Dispatchers.Default) {
                displayBitmap = createDisplayBitmap(colorByNumberBitmap!!, regions, colorPalette).asImageBitmap()
                isCompleted = regions.all { it.isFilled }
                if (isCompleted) {
                    onComplete()
                }
            }
        }
    }
    
    fun handleTap(tapOffset: Offset) {
        if (isCompleted || regions.isEmpty() || colorByNumberBitmap == null) return
        if (imageRect.width <= 0 || imageRect.height <= 0) return
        
        // pointerInput receives coordinates in the composable's LOCAL coordinate space
        // BEFORE graphicsLayer visual transformation is applied.
        // 
        // graphicsLayer transformation (with default center origin):
        // visualPoint = (canvasPoint - center) * scale + center + offset
        //
        // To reverse (tap is at visualPoint, we want canvasPoint):
        // canvasPoint = (tapPoint - center - offset) / scale + center
        //
        // But wait - pointerInput is placed AFTER graphicsLayer in modifier chain,
        // which means it receives taps in the TRANSFORMED visual space.
        // So tapOffset IS the visual position, and we need to reverse it.
        
        val centerX = canvasSize.first / 2f
        val centerY = canvasSize.second / 2f
        
        // The tap is in screen/visual space. Reverse the graphicsLayer transform.
        // Visual = (Canvas - Center) * Scale + Center + Offset
        // Canvas = (Visual - Center - Offset) / Scale + Center
        val canvasX = (tapOffset.x - centerX - offset.x) / scale + centerX
        val canvasY = (tapOffset.y - centerY - offset.y) / scale + centerY
        
        // Now convert canvas coordinates to image coordinates
        val relativeX = canvasX - imageRect.left
        val relativeY = canvasY - imageRect.top
        
        // Check if tap is within the image bounds
        if (relativeX < 0 || relativeY < 0 || relativeX > imageRect.width || relativeY > imageRect.height) {
            return // Tapped outside the image
        }
        
        // Convert to actual image pixel coordinates
        val imageX = (relativeX / imageRect.width * colorByNumberBitmap!!.width).toInt()
            .coerceIn(0, colorByNumberBitmap!!.width - 1)
        val imageY = (relativeY / imageRect.height * colorByNumberBitmap!!.height).toInt()
            .coerceIn(0, colorByNumberBitmap!!.height - 1)
        
        // Find which region was tapped (with larger tolerance when zoomed out)
        val searchRadius = (15 / scale).toInt().coerceIn(5, 25)
        val tappedRegion = regions.indexOfFirst { region ->
            Pair(imageX, imageY) in region.pixels ||
            (-searchRadius..searchRadius).any { dx ->
                (-searchRadius..searchRadius).any { dy ->
                    Pair(imageX + dx, imageY + dy) in region.pixels
                }
            }
        }
        
        if (tappedRegion >= 0) {
            val region = regions[tappedRegion]
            if (selectedColorIndex == region.colorIndex && !region.isFilled) {
                regions = regions.toMutableList().also {
                    it[tappedRegion] = region.copy(isFilled = true)
                }
            }
        }
    }
    
    fun resetGame() {
        regions = regions.map { it.copy(isFilled = false) }
        isCompleted = false
        selectedColorIndex = 0
    }
    
    // Settings Dialog
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Customize Your Coloring Page") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Preview thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(imageBitmap.width.toFloat() / imageBitmap.height),
                            contentScale = ContentScale.Fit
                        )
                    }
                    
                    // Number of colors slider
                    Column {
                        Text(
                            "Number of Colors: $numberOfColors",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Slider(
                            value = numberOfColors.toFloat(),
                            onValueChange = { numberOfColors = it.toInt() },
                            valueRange = 4f..24f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            when {
                                numberOfColors <= 8 -> "Simple - Great for beginners"
                                numberOfColors <= 16 -> "Medium - Good balance"
                                else -> "Detailed - More challenging"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Difficulty selection
                    Column {
                        Text(
                            "Difficulty:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Difficulty.entries.forEach { diff ->
                                FilterChip(
                                    selected = difficulty == diff,
                                    onClick = { difficulty = diff },
                                    label = { Text(diff.label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text(
                            when (difficulty) {
                                Difficulty.EASY -> "Larger regions, easier to fill"
                                Difficulty.MEDIUM -> "Balanced detail and simplicity"
                                Difficulty.HARD -> "Smaller regions, more precise"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettings = false
                        isLoading = true
                        processingStarted = true
                    }
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val filledCount = regions.count { it.isFilled }
                    Column {
                        Text(
                            if (isCompleted) "Complete! 🎉" 
                            else if (isLoading) loadingMessage
                            else "Color by Number ($filledCount/${regions.size})"
                        )
                        if (usingAI && !isLoading) {
                            Text(
                                "Powered by Gemini AI",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    IconButton(onClick = { resetGame() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(androidx.compose.ui.graphics.Color.White)
        ) {
            // Error/info message
            errorMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFFFF3CD)
                    )
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(8.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF856404),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Color palette
            if (!isLoading && colorPalette.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorPalette.forEachIndexed { index, color ->
                        val isSelected = selectedColorIndex == index
                        val regionsWithThisColor = regions.filter { it.colorIndex == index }
                        val allFilled = regionsWithThisColor.all { it.isFilled }
                        val count = regionsWithThisColor.size
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(color))
                                    .border(
                                        width = if (isSelected) 4.dp else 2.dp,
                                        color = if (isSelected) 
                                            androidx.compose.ui.graphics.Color.Black 
                                        else 
                                            androidx.compose.ui.graphics.Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorIndex = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isColorDark(color)) 
                                        androidx.compose.ui.graphics.Color.White 
                                    else 
                                        androidx.compose.ui.graphics.Color.Black,
                                    fontSize = 18.sp,
                                    style = LocalTextStyle.current.copy(
                                        shadow = Shadow(
                                            color = if (isColorDark(color)) 
                                                androidx.compose.ui.graphics.Color.Black 
                                            else 
                                                androidx.compose.ui.graphics.Color.White,
                                            blurRadius = 2f
                                        )
                                    )
                                )
                                
                                if (allFilled && count > 0) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Completed",
                                        tint = if (isColorDark(color)) 
                                            androidx.compose.ui.graphics.Color.White 
                                        else 
                                            androidx.compose.ui.graphics.Color.Black,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                            
                            // Show color name if available
                            if (index < colorNames.size) {
                                Text(
                                    text = if (count > 0) "$count" else "",
                                    fontSize = 10.sp,
                                    color = androidx.compose.ui.graphics.Color.Gray
                                )
                            }
                        }
                    }
                }
            }
            
            // Canvas with zoom and pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(loadingMessage)
                        if (aiProvider == AIProvider.GEMINI && apiKey.isBlank()) {
                            Text(
                                "No API key configured - using local processing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    displayBitmap?.let { bitmap ->
                        // Transformable state for zoom and pan
                        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                            scale = (scale * zoomChange).coerceIn(minScale, maxScale)
                            
                            // Calculate bounds for panning
                            val maxX = (canvasSize.first * (scale - 1)) / 2
                            val maxY = (canvasSize.second * (scale - 1)) / 2
                            
                            offset = Offset(
                                x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(scale, offset, imageRect) {
                                        detectTapGestures { tapOffset ->
                                            handleTap(tapOffset)
                                        }
                                    }
                                    .transformable(state = transformState)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                            ) {
                                canvasSize = Pair(size.width, size.height)
                                
                                // Calculate aspect-ratio-preserving image bounds
                                val imageAspect = bitmap.width.toFloat() / bitmap.height
                                val canvasAspect = size.width / size.height
                                
                                val (drawWidth, drawHeight) = if (imageAspect > canvasAspect) {
                                    // Image is wider than canvas - fit to width
                                    Pair(size.width, size.width / imageAspect)
                                } else {
                                    // Image is taller than canvas - fit to height
                                    Pair(size.height * imageAspect, size.height)
                                }
                                
                                val drawLeft = (size.width - drawWidth) / 2f
                                val drawTop = (size.height - drawHeight) / 2f
                                
                                // Update imageRect for tap detection
                                imageRect = androidx.compose.ui.geometry.Rect(
                                    left = drawLeft,
                                    top = drawTop,
                                    right = drawLeft + drawWidth,
                                    bottom = drawTop + drawHeight
                                )
                                
                                drawImage(
                                    image = bitmap,
                                    dstOffset = androidx.compose.ui.unit.IntOffset(
                                        drawLeft.toInt(),
                                        drawTop.toInt()
                                    ),
                                    dstSize = androidx.compose.ui.unit.IntSize(
                                        drawWidth.toInt(),
                                        drawHeight.toInt()
                                    )
                                )
                                
                                // Draw numbers on unfilled regions
                                if (!isCompleted && colorByNumberBitmap != null && imageRect.width > 0) {
                                    val imgScaleX = imageRect.width / colorByNumberBitmap!!.width
                                    val imgScaleY = imageRect.height / colorByNumberBitmap!!.height
                                    
                                    regions.filter { !it.isFilled }.forEach { region ->
                                        val centerX = imageRect.left + region.centerX * imgScaleX
                                        val centerY = imageRect.top + region.centerY * imgScaleY
                                        
                                        val textStyle = TextStyle(
                                            color = androidx.compose.ui.graphics.Color.Black,
                                            fontSize = 11.sp
                                        )
                                        val textLayout = textMeasurer.measure(
                                            text = "${region.colorIndex + 1}",
                                            style = textStyle
                                        )
                                        
                                        drawCircle(
                                            color = androidx.compose.ui.graphics.Color.White,
                                            radius = 12f,
                                            center = Offset(centerX, centerY)
                                        )
                                        drawCircle(
                                            color = androidx.compose.ui.graphics.Color.Black,
                                            radius = 12f,
                                            center = Offset(centerX, centerY),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                                        )
                                        
                                        drawText(
                                            textLayoutResult = textLayout,
                                            topLeft = Offset(
                                                centerX - textLayout.size.width / 2,
                                                centerY - textLayout.size.height / 2
                                            )
                                        )
                                    }
                                }
                            }
                            
                            // Zoom controls
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                // Zoom in button
                                FilledIconButton(
                                    onClick = {
                                        scale = (scale * 1.5f).coerceIn(minScale, maxScale)
                                    },
                                    modifier = Modifier.size(40.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Zoom In",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Zoom out button
                                FilledIconButton(
                                    onClick = {
                                        scale = (scale / 1.5f).coerceIn(minScale, maxScale)
                                        // Reset offset when zooming out to prevent going out of bounds
                                        val maxX = (canvasSize.first * (scale - 1)) / 2
                                        val maxY = (canvasSize.second * (scale - 1)) / 2
                                        offset = Offset(
                                            x = offset.x.coerceIn(-maxX, maxX),
                                            y = offset.y.coerceIn(-maxY, maxY)
                                        )
                                    },
                                    modifier = Modifier.size(40.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Text(
                                        text = "−",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                
                                // Reset zoom button (only visible when zoomed)
                                if (scale > 1.1f) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FilledIconButton(
                                        onClick = {
                                            scale = 1f
                                            offset = Offset.Zero
                                        },
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Reset Zoom",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                
                                // Zoom level indicator
                                if (scale > 1.1f) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        shadowElevation = 2.dp
                                    ) {
                                        Text(
                                            text = "${(scale * 100).toInt()}%",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Generate color-by-number using Gemini API
 * Uses Gemini for color analysis only, local processing for line art
 */
private suspend fun generateWithGemini(
    bitmap: Bitmap,
    apiKey: String
): Pair<Bitmap, List<GeminiColorInfo>> {
    // First, generate line art locally
    val lineArt = convertToLineArtLocal(bitmap)
    
    // Resize image if too large (Gemini has limits)
    val maxSize = 512
    val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
        val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
            )
        } else {
            bitmap
        }
        
        // Encode to base64
        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        
        // Gemini API request for color analysis only (text response)
        val prompt = """Analyze this image and identify the main colors for a color-by-number activity.

Return ONLY a JSON object with the main colors found in this image. Use this exact format:
{"colors": [{"number": 1, "name": "Sky Blue", "hex": "#87CEEB"}, {"number": 2, "name": "Grass Green", "hex": "#228B22"}]}

Rules:
- Include 5-10 distinct colors
- Use descriptive color names (e.g., "Sky Blue" not just "Blue")
- Provide accurate hex codes
- Order colors by prominence in the image
- Only output the JSON, no other text"""

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 500)
            })
        }
        
        Log.d("ColorByNumber", "Sending request to Gemini API...")
        
        // Use gemini-2.0-flash-001 model
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-001:generateContent?key=$apiKey")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        
        Log.d("ColorByNumber", "Response code: ${response.code}")
        
        if (!response.isSuccessful) {
            val errorDetail = try {
                JSONObject(responseBody ?: "").optJSONObject("error")?.optString("message") ?: responseBody
            } catch (e: Exception) { responseBody }
            Log.e("ColorByNumber", "API error: $errorDetail")
            throw Exception("API error ${response.code}: $errorDetail")
        }
        
        if (responseBody == null) {
            throw Exception("Empty response from Gemini")
        }
        
        Log.d("ColorByNumber", "Response received, parsing...")
        // Parse response
        val jsonResponse = JSONObject(responseBody)
        val candidates = jsonResponse.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val blockReason = jsonResponse.optJSONObject("promptFeedback")?.optString("blockReason")
            if (blockReason != null) {
                throw Exception("Request blocked: $blockReason")
            }
            throw Exception("No response from Gemini")
        }
        
        val content = candidates.getJSONObject(0).optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        
        var colorInfoList = mutableListOf<GeminiColorInfo>()
        
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text")
                
                if (text.isNotBlank()) {
                    // Try to extract JSON from text
                    val jsonMatch = Regex("""\{[\s\S]*"colors"[\s\S]*\}""").find(text)
                    if (jsonMatch != null) {
                        try {
                            val colorJson = JSONObject(jsonMatch.value)
                            val colors = colorJson.optJSONArray("colors")
                            if (colors != null) {
                                for (j in 0 until colors.length()) {
                                    val colorObj = colors.getJSONObject(j)
                                    colorInfoList.add(GeminiColorInfo(
                                        number = colorObj.optInt("number", j + 1),
                                        colorName = colorObj.optString("name", "Color ${j + 1}"),
                                        hexColor = colorObj.optString("hex", "#808080")
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("ColorByNumber", "JSON parsing error: ${e.message}")
                        }
                    }
                }
            }
        }
        
        Log.d("ColorByNumber", "Gemini returned ${colorInfoList.size} colors")
        
        // If no colors were extracted, create default palette based on image
        if (colorInfoList.isEmpty()) {
            Log.d("ColorByNumber", "No colors from Gemini, extracting from image")
            colorInfoList = extractColorsFromImage(bitmap).toMutableList()
        }
        
        return Pair(lineArt, colorInfoList)
}

/**
 * Extract dominant colors from image as fallback
 */
private fun extractColorsFromImage(bitmap: Bitmap): List<GeminiColorInfo> {
    val colorCounts = mutableMapOf<Int, Int>()
    val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 20)
    
    for (y in 0 until bitmap.height step step) {
        for (x in 0 until bitmap.width step step) {
            val pixel = bitmap.getPixel(x, y)
            // Quantize to reduce color variations
            val r = (Color.red(pixel) / 32) * 32
            val g = (Color.green(pixel) / 32) * 32
            val b = (Color.blue(pixel) / 32) * 32
            val quantized = Color.rgb(r, g, b)
            colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
        }
    }
    
    return colorCounts.entries
        .sortedByDescending { it.value }
        .take(8)
        .mapIndexed { index, entry ->
            val color = entry.key
            val hex = String.format("#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))
            val colorName = getColorName(Color.red(color), Color.green(color), Color.blue(color))
            GeminiColorInfo(index + 1, colorName, hex)
        }
}

/**
 * Get a descriptive name for an RGB color
 */
private fun getColorName(r: Int, g: Int, b: Int): String {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val brightness = (r + g + b) / 3
    
    // Check for grayscale colors
    if (max - min < 30) {
        return when {
            brightness < 50 -> "Black"
            brightness < 100 -> "Dark Gray"
            brightness < 180 -> "Gray"
            brightness < 230 -> "Light Gray"
            else -> "White"
        }
    }
    
    // Determine hue-based color
    val hue = when {
        max == r && g >= b -> 60f * (g - b) / (max - min)
        max == r && g < b -> 60f * (g - b) / (max - min) + 360f
        max == g -> 60f * (b - r) / (max - min) + 120f
        else -> 60f * (r - g) / (max - min) + 240f
    }
    
    val saturation = if (max == 0) 0f else (max - min).toFloat() / max
    
    val baseColor = when {
        hue < 15 || hue >= 345 -> "Red"
        hue < 45 -> "Orange"
        hue < 70 -> "Yellow"
        hue < 150 -> "Green"
        hue < 190 -> "Cyan"
        hue < 260 -> "Blue"
        hue < 290 -> "Purple"
        else -> "Pink"
    }
    
    // Add modifiers
    val prefix = when {
        saturation < 0.3 -> "Pale "
        brightness < 80 -> "Dark "
        brightness > 200 && saturation > 0.5 -> "Bright "
        else -> ""
    }
    
    return prefix + baseColor
}

private fun parseHexColor(hex: String): Int {
    return try {
        val cleanHex = hex.removePrefix("#")
        Color.parseColor("#$cleanHex")
    } catch (e: Exception) {
        Color.GRAY
    }
}

/**
 * Analyze a color-by-number image to find regions
 */
private fun analyzeColorByNumberImage(
    bitmap: Bitmap,
    palette: List<Int>
): List<ColorRegion> {
    val width = bitmap.width
    val height = bitmap.height
    val visited = Array(width) { BooleanArray(height) }
    val regions = mutableListOf<ColorRegion>()
    
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (!visited[x][y]) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                
                // Find white/light regions (areas to fill)
                if (brightness > 200) {
                    val regionPixels = mutableSetOf<Pair<Int, Int>>()
                    floodFillRegion(bitmap, x, y, visited, regionPixels, width, height)
                    
                    if (regionPixels.size > (width * height) / 500) {
                        val centerX = regionPixels.map { it.first }.average().toInt()
                        val centerY = regionPixels.map { it.second }.average().toInt()
                        
                        // Assign to a color based on position (for AI-generated images)
                        val colorIndex = regions.size % palette.size
                        
                        regions.add(ColorRegion(
                            colorIndex = colorIndex,
                            originalColor = palette.getOrElse(colorIndex) { Color.GRAY },
                            pixels = regionPixels,
                            centerX = centerX,
                            centerY = centerY
                        ))
                    }
                }
            }
        }
    }
    
    return regions
}

private fun floodFillRegion(
    bitmap: Bitmap,
    startX: Int,
    startY: Int,
    visited: Array<BooleanArray>,
    region: MutableSet<Pair<Int, Int>>,
    width: Int,
    height: Int
) {
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.add(Pair(startX, startY))
    
    while (stack.isNotEmpty()) {
        val (x, y) = stack.removeLast()
        
        if (x < 0 || x >= width || y < 0 || y >= height) continue
        if (visited[x][y]) continue
        
        val pixel = bitmap.getPixel(x, y)
        val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
        
        if (brightness <= 200) continue
        
        visited[x][y] = true
        region.add(Pair(x, y))
        
        stack.add(Pair(x + 1, y))
        stack.add(Pair(x - 1, y))
        stack.add(Pair(x, y + 1))
        stack.add(Pair(x, y - 1))
    }
}

/**
 * Apply bilateral filter for edge-preserving smoothing
 * This produces cleaner color regions similar to Davincified
 */
private fun applyBilateralFilter(bitmap: Bitmap, radius: Int = 5, sigmaColor: Float = 50f, sigmaSpace: Float = 50f): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    // Pre-calculate spatial Gaussian weights
    val spatialWeights = Array(2 * radius + 1) { FloatArray(2 * radius + 1) }
    for (dy in -radius..radius) {
        for (dx in -radius..radius) {
            spatialWeights[dy + radius][dx + radius] = exp(-(dx * dx + dy * dy) / (2 * sigmaSpace * sigmaSpace))
        }
    }
    
    for (y in 0 until height) {
        for (x in 0 until width) {
            val centerPixel = bitmap.getPixel(x, y)
            val centerR = Color.red(centerPixel)
            val centerG = Color.green(centerPixel)
            val centerB = Color.blue(centerPixel)
            
            var sumR = 0f
            var sumG = 0f
            var sumB = 0f
            var sumWeight = 0f
            
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, width - 1)
                    val ny = (y + dy).coerceIn(0, height - 1)
                    
                    val neighborPixel = bitmap.getPixel(nx, ny)
                    val nR = Color.red(neighborPixel)
                    val nG = Color.green(neighborPixel)
                    val nB = Color.blue(neighborPixel)
                    
                    // Color distance
                    val colorDist = sqrt(((nR - centerR) * (nR - centerR) +
                            (nG - centerG) * (nG - centerG) +
                            (nB - centerB) * (nB - centerB)).toFloat())
                    
                    // Combined weight
                    val colorWeight = exp(-colorDist * colorDist / (2 * sigmaColor * sigmaColor))
                    val weight = spatialWeights[dy + radius][dx + radius] * colorWeight
                    
                    sumR += nR * weight
                    sumG += nG * weight
                    sumB += nB * weight
                    sumWeight += weight
                }
            }
            
            val finalR = (sumR / sumWeight).toInt().coerceIn(0, 255)
            val finalG = (sumG / sumWeight).toInt().coerceIn(0, 255)
            val finalB = (sumB / sumWeight).toInt().coerceIn(0, 255)
            result.setPixel(x, y, Color.rgb(finalR, finalG, finalB))
        }
    }
    
    return result
}

/**
 * K-means clustering for professional color palette extraction
 * Similar to pbngen/Davincified approach
 */
private fun kMeansColorPalette(bitmap: Bitmap, numColors: Int, maxIterations: Int = 20): List<Int> {
    val width = bitmap.width
    val height = bitmap.height
    
    // Sample pixels from the image (every nth pixel for performance)
    val sampleStep = maxOf(1, minOf(width, height) / 80)
    val samples = mutableListOf<FloatArray>()
    
    for (y in 0 until height step sampleStep) {
        for (x in 0 until width step sampleStep) {
            val pixel = bitmap.getPixel(x, y)
            samples.add(floatArrayOf(
                Color.red(pixel).toFloat(),
                Color.green(pixel).toFloat(),
                Color.blue(pixel).toFloat()
            ))
        }
    }
    
    if (samples.size < numColors) {
        // Not enough samples, use fallback
        return generateDefaultPalette(numColors)
    }
    
    // Initialize centroids using k-means++ for better starting positions
    val centroids = mutableListOf<FloatArray>()
    val random = java.util.Random(42)
    
    // First centroid is random
    centroids.add(samples[random.nextInt(samples.size)].copyOf())
    
    // Choose remaining centroids with probability proportional to squared distance
    while (centroids.size < numColors) {
        val distances = samples.map { sample ->
            centroids.minOf { centroid ->
                val dr = sample[0] - centroid[0]
                val dg = sample[1] - centroid[1]
                val db = sample[2] - centroid[2]
                dr * dr + dg * dg + db * db
            }
        }
        
        val totalDist = distances.sum()
        if (totalDist <= 0) break
        
        var threshold = random.nextFloat() * totalDist
        var chosenIdx = 0
        for (i in distances.indices) {
            threshold -= distances[i]
            if (threshold <= 0) {
                chosenIdx = i
                break
            }
        }
        centroids.add(samples[chosenIdx].copyOf())
    }
    
    // K-means iterations
    val assignments = IntArray(samples.size)
    
    repeat(maxIterations) {
        // Assign each sample to nearest centroid
        samples.forEachIndexed { idx, sample ->
            assignments[idx] = centroids.indices.minByOrNull { c ->
                val dr = sample[0] - centroids[c][0]
                val dg = sample[1] - centroids[c][1]
                val db = sample[2] - centroids[c][2]
                dr * dr + dg * dg + db * db
            } ?: 0
        }
        
        // Update centroids
        val newCentroids = Array(numColors) { floatArrayOf(0f, 0f, 0f) }
        val counts = IntArray(numColors)
        
        samples.forEachIndexed { idx, sample ->
            val cluster = assignments[idx]
            newCentroids[cluster][0] += sample[0]
            newCentroids[cluster][1] += sample[1]
            newCentroids[cluster][2] += sample[2]
            counts[cluster]++
        }
        
        for (c in 0 until numColors) {
            if (counts[c] > 0) {
                centroids[c][0] = newCentroids[c][0] / counts[c]
                centroids[c][1] = newCentroids[c][1] / counts[c]
                centroids[c][2] = newCentroids[c][2] / counts[c]
            }
        }
    }
    
    // Convert centroids to colors, sorted by luminance for nicer palette display
    return centroids
        .filter { it[0] >= 0 && it[1] >= 0 && it[2] >= 0 }
        .map { Color.rgb(it[0].toInt().coerceIn(0, 255), it[1].toInt().coerceIn(0, 255), it[2].toInt().coerceIn(0, 255)) }
        .distinctBy { 
            // Group very similar colors
            val r = Color.red(it) / 20
            val g = Color.green(it) / 20
            val b = Color.blue(it) / 20
            r * 10000 + g * 100 + b
        }
        .sortedBy { 
            val r = Color.red(it)
            val g = Color.green(it)
            val b = Color.blue(it)
            0.299 * r + 0.587 * g + 0.114 * b 
        }
        .take(numColors)
        .ifEmpty { generateDefaultPalette(numColors) }
}

/**
 * Generate a default color palette as fallback
 */
private fun generateDefaultPalette(numColors: Int): List<Int> {
    val baseColors = listOf(
        Color.rgb(220, 53, 69),   // Red
        Color.rgb(255, 193, 7),   // Yellow
        Color.rgb(40, 167, 69),   // Green
        Color.rgb(0, 123, 255),   // Blue
        Color.rgb(111, 66, 193),  // Purple
        Color.rgb(255, 133, 27),  // Orange
        Color.rgb(32, 201, 151),  // Teal
        Color.rgb(232, 62, 140),  // Pink
        Color.rgb(108, 117, 125), // Gray
        Color.rgb(52, 58, 64),    // Dark Gray
        Color.rgb(23, 162, 184),  // Cyan
        Color.rgb(253, 126, 20),  // Light Orange
        Color.rgb(102, 16, 242),  // Indigo
        Color.rgb(111, 207, 151), // Light Green
        Color.rgb(245, 196, 148), // Peach
        Color.rgb(173, 181, 189)  // Silver
    )
    return baseColors.take(numColors)
}

/**
 * Quantize the image to use only palette colors (like pbngen)
 */
private fun quantizeImageToPalette(bitmap: Bitmap, palette: List<Int>): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    // Pre-convert palette to float arrays for faster comparison
    val paletteRGB = palette.map { 
        floatArrayOf(Color.red(it).toFloat(), Color.green(it).toFloat(), Color.blue(it).toFloat())
    }
    
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = bitmap.getPixel(x, y)
            val r = Color.red(pixel).toFloat()
            val g = Color.green(pixel).toFloat()
            val b = Color.blue(pixel).toFloat()
            
            // Find nearest palette color using Euclidean distance
            val nearestIdx = paletteRGB.indices.minByOrNull { idx ->
                val pr = paletteRGB[idx][0]
                val pg = paletteRGB[idx][1]
                val pb = paletteRGB[idx][2]
                (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
            } ?: 0
            
            result.setPixel(x, y, palette[nearestIdx])
        }
    }
    
    return result
}

/**
 * Convert image to line art using local edge detection
 * Now with edge-preserving pre-processing
 */
private fun convertToLineArtLocal(bitmap: Bitmap, edgeThreshold: Int = 30): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    val gray = Array(width) { IntArray(height) }
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = bitmap.getPixel(x, y)
            gray[x][y] = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
        }
    }
    
    // Apply Gaussian blur to reduce noise before edge detection
    val blurred = Array(width) { IntArray(height) }
    val kernel = arrayOf(
        intArrayOf(1, 4, 7, 4, 1),
        intArrayOf(4, 16, 26, 16, 4),
        intArrayOf(7, 26, 41, 26, 7),
        intArrayOf(4, 16, 26, 16, 4),
        intArrayOf(1, 4, 7, 4, 1)
    )
    val kernelSum = 273
    
    for (y in 2 until height - 2) {
        for (x in 2 until width - 2) {
            var sum = 0
            for (ky in -2..2) {
                for (kx in -2..2) {
                    sum += gray[x + kx][y + ky] * kernel[ky + 2][kx + 2]
                }
            }
            blurred[x][y] = sum / kernelSum
        }
    }
    
    // Sobel edge detection on blurred image
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val gx = -blurred[x-1][y-1] - 2*blurred[x-1][y] - blurred[x-1][y+1] +
                     blurred[x+1][y-1] + 2*blurred[x+1][y] + blurred[x+1][y+1]
            val gy = -blurred[x-1][y-1] - 2*blurred[x][y-1] - blurred[x+1][y-1] +
                     blurred[x-1][y+1] + 2*blurred[x][y+1] + blurred[x+1][y+1]
            
            val magnitude = sqrt((gx * gx + gy * gy).toFloat()).toInt().coerceIn(0, 255)
            val edgeValue = if (magnitude > edgeThreshold) 0 else 255
            result.setPixel(x, y, Color.rgb(edgeValue, edgeValue, edgeValue))
        }
    }
    
    // Set borders to white
    for (x in 0 until width) {
        result.setPixel(x, 0, Color.WHITE)
        result.setPixel(x, height - 1, Color.WHITE)
    }
    for (y in 0 until height) {
        result.setPixel(0, y, Color.WHITE)
        result.setPixel(width - 1, y, Color.WHITE)
    }
    
    return result
}

/**
 * Professional paint-by-number analysis using K-means clustering
 * Based on techniques from pbngen and similar to Davincified
 */
private fun analyzeImageForColorByNumber(
    original: Bitmap,
    lineArt: Bitmap,
    targetColors: Int = 12,
    difficulty: Difficulty = Difficulty.MEDIUM
): Pair<List<Int>, List<ColorRegion>> {
    val width = original.width
    val height = original.height
    
    // Step 1: Apply bilateral filter for edge-preserving smoothing
    // This creates cleaner color regions like professional tools
    val smoothed = applyBilateralFilter(original, radius = 3, sigmaColor = 40f, sigmaSpace = 40f)
    
    // Step 2: Extract color palette using K-means clustering (like pbngen)
    val palette = kMeansColorPalette(smoothed, targetColors)
    
    // Step 3: Quantize the image to use only palette colors
    val quantized = quantizeImageToPalette(smoothed, palette)
    
    // Step 4: Find connected regions based on both line art and quantized colors
    val visited = Array(width) { BooleanArray(height) }
    val regionsList = mutableListOf<MutableSet<Pair<Int, Int>>>()
    val regionColors = mutableListOf<Int>()  // Track the quantized color of each region
    
    // Calculate minimum region size based on difficulty
    val minRegionSize = (width * height * difficulty.minRegionSize).toInt()
    
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (!visited[x][y]) {
                val lineArtPixel = lineArt.getPixel(x, y)
                val brightness = (Color.red(lineArtPixel) + Color.green(lineArtPixel) + Color.blue(lineArtPixel)) / 3
                
                // Only process white areas (non-edge pixels)
                if (brightness > 200) {
                    val region = mutableSetOf<Pair<Int, Int>>()
                    val regionColor = quantized.getPixel(x, y)
                    
                    // Color-aware flood fill - stay within same quantized color AND line art boundaries
                    colorAwareFloodFill(lineArt, quantized, x, y, regionColor, visited, region, width, height)
                    
                    if (region.size > minRegionSize) {
                        regionsList.add(region)
                        regionColors.add(regionColor)
                    }
                }
            }
        }
    }
    
    // Step 5: Create ColorRegion objects
    val regions = regionsList.mapIndexedNotNull { idx, pixels ->
        if (pixels.isEmpty()) return@mapIndexedNotNull null
        
        // Find which palette index this region's color corresponds to
        val regionColor = regionColors[idx]
        val colorIndex = palette.indices.minByOrNull { paletteIdx ->
            colorDistance(regionColor, palette[paletteIdx])
        } ?: 0
        
        // Calculate centroid - use visual center for better label placement
        val sortedX = pixels.map { it.first }.sorted()
        val sortedY = pixels.map { it.second }.sorted()
        val centerX = sortedX[sortedX.size / 2]  // Median gives more stable center
        val centerY = sortedY[sortedY.size / 2]
        
        ColorRegion(
            colorIndex = colorIndex,
            originalColor = palette.getOrElse(colorIndex) { Color.GRAY },
            pixels = pixels,
            centerX = centerX,
            centerY = centerY
        )
    }
    
    return Pair(palette, regions)
}

/**
 * Color-aware flood fill that respects both line art boundaries and color regions
 */
private fun colorAwareFloodFill(
    lineArt: Bitmap,
    quantized: Bitmap,
    startX: Int,
    startY: Int,
    targetColor: Int,
    visited: Array<BooleanArray>,
    region: MutableSet<Pair<Int, Int>>,
    width: Int,
    height: Int
) {
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.add(Pair(startX, startY))
    
    // Color tolerance for matching (to handle slight quantization differences)
    val tolerance = 30
    
    while (stack.isNotEmpty()) {
        val (x, y) = stack.removeLast()
        
        if (x < 0 || x >= width || y < 0 || y >= height) continue
        if (visited[x][y]) continue
        
        // Check line art - must be a white (non-edge) pixel
        val lineArtPixel = lineArt.getPixel(x, y)
        val brightness = (Color.red(lineArtPixel) + Color.green(lineArtPixel) + Color.blue(lineArtPixel)) / 3
        if (brightness <= 200) continue
        
        // Check color - must match target color within tolerance
        val currentColor = quantized.getPixel(x, y)
        if (colorDistance(currentColor, targetColor) > tolerance) continue
        
        visited[x][y] = true
        region.add(Pair(x, y))
        
        // 4-connectivity (same as pbngen uses)
        stack.add(Pair(x + 1, y))
        stack.add(Pair(x - 1, y))
        stack.add(Pair(x, y + 1))
        stack.add(Pair(x, y - 1))
    }
}

/**
 * Extract a color palette from the image using K-means (legacy wrapper)
 */
private fun extractColorPalette(bitmap: Bitmap, numColors: Int): List<Int> {
    // Delegate to K-means for better results
    return kMeansColorPalette(bitmap, numColors)
}

private fun quantizeColor(color: Int, levels: Int): Int {
    val step = 256 / levels
    val r = ((Color.red(color) / step) * step).coerceIn(0, 255)
    val g = ((Color.green(color) / step) * step).coerceIn(0, 255)
    val b = ((Color.blue(color) / step) * step).coerceIn(0, 255)
    return Color.rgb(r, g, b)
}

private fun colorDistance(c1: Int, c2: Int): Int {
    val dr = Color.red(c1) - Color.red(c2)
    val dg = Color.green(c1) - Color.green(c2)
    val db = Color.blue(c1) - Color.blue(c2)
    return sqrt((dr * dr + dg * dg + db * db).toFloat()).toInt()
}

private fun findClosestColorIndex(color: Int, palette: List<Int>): Int {
    return palette.indices.minByOrNull { colorDistance(color, palette[it]) } ?: 0
}

private fun createDisplayBitmap(
    lineArt: Bitmap,
    regions: List<ColorRegion>,
    palette: List<Int>
): Bitmap {
    val width = lineArt.width
    val height = lineArt.height
    val result = lineArt.copy(Bitmap.Config.ARGB_8888, true)
    
    regions.forEach { region ->
        if (region.isFilled) {
            val fillColor = palette.getOrElse(region.colorIndex) { Color.GRAY }
            region.pixels.forEach { (x, y) ->
                if (x in 0 until width && y in 0 until height) {
                    val currentPixel = result.getPixel(x, y)
                    val brightness = (Color.red(currentPixel) + Color.green(currentPixel) + Color.blue(currentPixel)) / 3
                    if (brightness > 100) {
                        result.setPixel(x, y, fillColor)
                    }
                }
            }
        }
    }
    
    return result
}

private fun isColorDark(color: Int): Boolean {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    val brightness = (r * 299 + g * 587 + b * 114) / 1000
    return brightness < 128
}
