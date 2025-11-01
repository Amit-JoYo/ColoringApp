package com.example.coloringapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope

import androidx.activity.result.PickVisualMediaRequest

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import android.widget.Toast

@Composable
fun PaintingScreen(viewModel: PaintingViewModel = viewModel()) {
    val imageBitmap by viewModel.imageBitmap.collectAsState()
    val imageSessionId by viewModel.imageSessionId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Hoisted state for scale and offset
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(it)
                .allowHardware(false) // IMPORTANT: To work with bitmaps
                .build()

            loader.enqueue(request).job.invokeOnCompletion { throwable ->
                if (throwable == null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val imageResult = loader.execute(request)
                        if (imageResult is SuccessResult) {
                            val bitmap = (imageResult.drawable).toBitmap()
                            viewModel.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap == null) {
            ImageSelectionScreen(
                viewModel = viewModel, 
                onImageSelected = { },
                onWebSearchRequested = { query ->
                    viewModel.startWebSearch(query)
                }
            )
        } else {
            PaintingCanvas(
                bitmap = imageBitmap!!,
                imageSessionId = imageSessionId,
                viewModel = viewModel,
                scale = scale,
                offset = offset,
                onScaleChange = { scale = it },
                onOffsetChange = { offset = it }
            )
        }
        if (isLoading) {
            CircularProgressIndicator()
        }
    }
}

/**
 * A composable that displays the painting canvas and its controls.
 *
 * @param bitmap The bitmap image to be displayed and colored.
 * @param viewModel The view model that manages the state of the painting screen.
 * @param scale The current scale of the canvas.
 * @param offset The current offset of the canvas.
 * @param onScaleChange A lambda to be called when the scale changes.
 * @param onOffsetChange A lambda to be called when the offset changes.
 */
@Composable
fun PaintingCanvas(
    bitmap: Bitmap,
    imageSessionId: Int,
    viewModel: PaintingViewModel,
    scale: Float,
    offset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit
) {
    // Context for accessing system services
    val context = LocalContext.current
    
    // State for showing and hiding the color picker.
    val showColorPicker = remember { mutableStateOf(false) }

    // State for the size of the canvas, used for calculating the fit-to-screen scale.
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // State for enabling and disabling the undo and redo buttons.
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    
    // Observe drawing mode and color history
    val drawingMode by viewModel.drawingMode.collectAsState()
    val colorHistory by viewModel.colorHistory.collectAsState()
    
    // Observe save status
    val saveStatus by viewModel.saveStatus.collectAsState()
    
    // Handle save status changes
    LaunchedEffect(saveStatus) {
        when (saveStatus) {
            is SaveStatus.Success -> {
                Toast.makeText(context, "Image saved successfully!", Toast.LENGTH_SHORT).show()
                viewModel.resetSaveStatus()
            }
            is SaveStatus.Error -> {
                Toast.makeText(context, "Error: ${(saveStatus as SaveStatus.Error).message}", Toast.LENGTH_SHORT).show()
                viewModel.resetSaveStatus()
            }
            else -> { /* Do nothing */ }
        }
    }

    // A function that calculates the scale and offset to fit the bitmap to the screen.
    val fitToScreen = {
        if (canvasSize != Size.Zero) {
            val canvasWidth = canvasSize.width
            val canvasHeight = canvasSize.height
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()

            val canvasRatio = canvasWidth / canvasHeight
            val bitmapRatio = bitmapWidth / bitmapHeight

            onScaleChange(
                if (canvasRatio > bitmapRatio) {
                    canvasHeight / bitmapHeight
                } else {
                    canvasWidth / bitmapWidth
                }
            )
            onOffsetChange(Offset.Zero)
        }
    }

    // Effect to fit the image to the screen when a new image is loaded
    LaunchedEffect(imageSessionId) {
        if (canvasSize != Size.Zero) {
            fitToScreen()
        }
    }
    
    // Effect to fit the image when canvas size is first initialized
    LaunchedEffect(canvasSize) {
        if (canvasSize != Size.Zero && scale == 1f && offset == Offset.Zero) {
            fitToScreen()
        }
    }

    // A state for the transformable gesture, used for zooming and panning.
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        onScaleChange(scale * zoomChange)
        onOffsetChange(offset + offsetChange)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .onSizeChanged {
                    canvasSize = it.toSize()
                }
                .transformable(state = transformableState)
                .pointerInput(scale, offset, canvasSize, bitmap, drawingMode) {
                    if (drawingMode == DrawingMode.Fill) {
                        detectTapGestures { tapOffset ->
                            // Transform tap coordinates from screen space to bitmap space
                            val canvasCenter = Offset(canvasSize.width / 2, canvasSize.height / 2)
                            
                            val afterTranslation = Offset(
                                tapOffset.x - offset.x,
                                tapOffset.y - offset.y
                            )
                            
                            val afterScale = Offset(
                                canvasCenter.x + (afterTranslation.x - canvasCenter.x) / scale,
                                canvasCenter.y + (afterTranslation.y - canvasCenter.y) / scale
                            )
                            
                            val bitmapTopLeft = Offset(
                                (canvasSize.width - bitmap.width) / 2,
                                (canvasSize.height - bitmap.height) / 2
                            )
                            
                            val bitmapCoords = Offset(
                                afterScale.x - bitmapTopLeft.x,
                                afterScale.y - bitmapTopLeft.y
                            )
                            
                            val bitmapX = bitmapCoords.x.toInt().coerceIn(0, bitmap.width - 1)
                            val bitmapY = bitmapCoords.y.toInt().coerceIn(0, bitmap.height - 1)
                            
                            viewModel.startFloodFill(bitmapX, bitmapY)
                        }
                    } else {
                        // Brush mode - detect drag gestures for drawing
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                viewModel.startBrushStroke()
                                
                                // Transform coordinates for initial touch
                                val canvasCenter = Offset(canvasSize.width / 2, canvasSize.height / 2)
                                val afterTranslation = Offset(
                                    startOffset.x - offset.x,
                                    startOffset.y - offset.y
                                )
                                val afterScale = Offset(
                                    canvasCenter.x + (afterTranslation.x - canvasCenter.x) / scale,
                                    canvasCenter.y + (afterTranslation.y - canvasCenter.y) / scale
                                )
                                val bitmapTopLeft = Offset(
                                    (canvasSize.width - bitmap.width) / 2,
                                    (canvasSize.height - bitmap.height) / 2
                                )
                                val bitmapCoords = Offset(
                                    afterScale.x - bitmapTopLeft.x,
                                    afterScale.y - bitmapTopLeft.y
                                )
                                val bitmapX = bitmapCoords.x.toInt().coerceIn(0, bitmap.width - 1)
                                val bitmapY = bitmapCoords.y.toInt().coerceIn(0, bitmap.height - 1)
                                
                                viewModel.brushDraw(bitmapX, bitmapY)
                            },
                            onDrag = { change, _ ->
                                // Transform drag coordinates
                                val canvasCenter = Offset(canvasSize.width / 2, canvasSize.height / 2)
                                val afterTranslation = Offset(
                                    change.position.x - offset.x,
                                    change.position.y - offset.y
                                )
                                val afterScale = Offset(
                                    canvasCenter.x + (afterTranslation.x - canvasCenter.x) / scale,
                                    canvasCenter.y + (afterTranslation.y - canvasCenter.y) / scale
                                )
                                val bitmapTopLeft = Offset(
                                    (canvasSize.width - bitmap.width) / 2,
                                    (canvasSize.height - bitmap.height) / 2
                                )
                                val bitmapCoords = Offset(
                                    afterScale.x - bitmapTopLeft.x,
                                    afterScale.y - bitmapTopLeft.y
                                )
                                val bitmapX = bitmapCoords.x.toInt().coerceIn(0, bitmap.width - 1)
                                val bitmapY = bitmapCoords.y.toInt().coerceIn(0, bitmap.height - 1)
                                
                                viewModel.brushDraw(bitmapX, bitmapY)
                            }
                        )
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                // Draw the image centered in the canvas
                val bitmapWidth = bitmap.width.toFloat()
                val bitmapHeight = bitmap.height.toFloat()
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                drawImage(
                    image = bitmap.asImageBitmap(),
                    topLeft = Offset(
                        (canvasWidth - bitmapWidth) / 2,
                        (canvasHeight - bitmapHeight) / 2
                    )
                )
            }
            PaintingControls(
                viewModel = viewModel,
                onShowColorPicker = { showColorPicker.value = !showColorPicker.value },
                onFitToScreen = { fitToScreen() },
                onSave = { viewModel.saveImageToGallery(context) },
                onShare = {
                    viewModel.shareImage(context)?.let { shareIntent ->
                        context.startActivity(Intent.createChooser(shareIntent, "Share your artwork"))
                    }
                },
                canUndo = canUndo,
                canRedo = canRedo,
                isSaving = saveStatus is SaveStatus.Saving,
                drawingMode = drawingMode,
                onDrawingModeChange = { viewModel.setDrawingMode(it) }
            )
        }
        AnimatedVisibility(visible = showColorPicker.value) {
            Column {
                // Show color history if available
                if (colorHistory.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Recent:",
                            modifier = Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.labelSmall
                        )
                        colorHistory.forEach { recentColor ->
                            Surface(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            viewModel.setSelectedColor(recentColor)
                                            showColorPicker.value = false
                                        }
                                    },
                                color = recentColor,
                                shape = CircleShape
                            ) {}
                        }
                    }
                }
                HoneycombColorPicker(
                    onColorSelected = { color ->
                        viewModel.setSelectedColor(color)
                        showColorPicker.value = false
                    },
                    currentBitmap = bitmap
                )
            }
        }
    }
}

/**
 * A composable that displays the controls for the painting canvas.
 *
 * @param viewModel The view model that manages the state of the painting screen.
 * @param onShowColorPicker A lambda function that is called when the color picker button is clicked.
 * @param onFitToScreen A lambda function that is called when the fit-to-screen button is clicked.
 * @param onSave A lambda function that is called when the save button is clicked.
 * @param onShare A lambda function that is called when the share button is clicked.
 * @param canUndo A boolean that indicates whether the undo button should be enabled.
 * @param canRedo A boolean that indicates whether the redo button should be enabled.
 * @param isSaving A boolean that indicates whether a save operation is in progress.
 */
@Composable
fun PaintingControls(
    viewModel: PaintingViewModel,
    onShowColorPicker: () -> Unit,
    onFitToScreen: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    isSaving: Boolean = false,
    drawingMode: DrawingMode,
    onDrawingModeChange: (DrawingMode) -> Unit
) {
    Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Button(onClick = { viewModel.clearImage() }) {
            Text("Back")
        }
        IconButton(
            onClick = {
                onDrawingModeChange(
                    if (drawingMode == DrawingMode.Fill) DrawingMode.Brush else DrawingMode.Fill
                )
            }
        ) {
            Icon(
                painter = painterResource(
                    id = if (drawingMode == DrawingMode.Fill) R.drawable.ic_brush else R.drawable.ic_brush_draw
                ),
                contentDescription = if (drawingMode == DrawingMode.Fill) "Switch to Brush" else "Switch to Fill",
                tint = if (drawingMode == DrawingMode.Brush) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onShowColorPicker) {
            Icon(
                painter = painterResource(id = R.drawable.ic_brush),
                contentDescription = "Select Color",
                tint = viewModel.selectedColor.collectAsState().value
            )
        }
        IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
            Icon(painter = painterResource(id = R.drawable.ic_undo), contentDescription = "Undo")
        }
        IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
            Icon(painter = painterResource(id = R.drawable.ic_redo), contentDescription = "Redo")
        }
        IconButton(onClick = onFitToScreen) {
            Icon(
                painter = painterResource(id = R.drawable.ic_fit_to_screen),
                contentDescription = "Fit to Screen"
            )
        }
        IconButton(onClick = onSave, enabled = !isSaving) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_save),
                    contentDescription = "Save to Gallery"
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(
                painter = painterResource(id = R.drawable.ic_share),
                contentDescription = "Share"
            )
        }
    }
}