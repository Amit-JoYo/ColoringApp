package com.example.coloringapp

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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

@Composable
fun PaintingScreen(viewModel: PaintingViewModel = viewModel()) {
    val imageBitmap by viewModel.imageBitmap.collectAsState()
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
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ImageSelectionScreen(viewModel = viewModel, onImageSelected = { })
                }
                Button(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text("Select Image from Gallery")
                }
            }
        } else {
            PaintingCanvas(
                bitmap = imageBitmap!!,
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
    viewModel: PaintingViewModel,
    scale: Float,
    offset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit
) {
    // State for showing and hiding the color picker.
    val showColorPicker = remember { mutableStateOf(false) }

    // State for the size of the canvas, used for calculating the fit-to-screen scale.
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // State for enabling and disabling the undo and redo buttons.
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

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

    // Effect to fit the image to the screen when the bitmap or canvas size changes.
    LaunchedEffect(bitmap, canvasSize) {
        fitToScreen()
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
                .pointerInput(Unit) {
                    detectTapGestures {
                        // Transform the tap coordinates to the bitmap's coordinate system.
                        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
                        val transformedOffset = (it - offset - center) / scale + center
                        viewModel.startFloodFill(
                            transformedOffset.x.toInt(),
                            transformedOffset.y.toInt()
                        )
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        canvasSize = it.toSize()
                        fitToScreen()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            // Transform the tap coordinates to the bitmap's coordinate system.
                            val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
                            val transformedOffset = (it - offset - center) / scale + center
                            viewModel.startFloodFill(
                                transformedOffset.x.toInt(),
                                transformedOffset.y.toInt()
                            )
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = transformableState)
            ) {
                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, pivot = center)
                }) {
                    drawImage(bitmap.asImageBitmap())
                }
            }
            PaintingControls(
                viewModel = viewModel,
                onShowColorPicker = { showColorPicker.value = !showColorPicker.value },
                onFitToScreen = { fitToScreen() },
                canUndo = canUndo,
                canRedo = canRedo
            )
        }
        AnimatedVisibility(visible = showColorPicker.value) {
            HoneycombColorPicker(onColorSelected = { color ->
                viewModel.setSelectedColor(color)
                showColorPicker.value = false
            })
        }
    }
}

/**
 * A composable that displays the controls for the painting canvas.
 *
 * @param viewModel The view model that manages the state of the painting screen.
 * @param onShowColorPicker A lambda function that is called when the color picker button is clicked.
 * @param onFitToScreen A lambda function that is called when the fit-to-screen button is clicked.
 * @param canUndo A boolean that indicates whether the undo button should be enabled.
 * @param canRedo A boolean that indicates whether the redo button should be enabled.
 */
@Composable
fun PaintingControls(
    viewModel: PaintingViewModel,
    onShowColorPicker: () -> Unit,
    onFitToScreen: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean
) {
    Row {
        Button(onClick = { viewModel.clearImage() }) {
            Text("Back")
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
    }
}