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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

import androidx.activity.result.PickVisualMediaRequest

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource

@Composable
fun PaintingScreen(viewModel: PaintingViewModel = viewModel()) {
    val imageBitmap by viewModel.imageBitmap.collectAsState()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(it)
                .allowHardware(false) // IMPORTANT: To work with bitmaps
                .build()

            loader.enqueue(request).job.invokeOnCompletion { throwable ->
                if (throwable == null) {
                    GlobalScope.launch(Dispatchers.IO) {
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
        PaintingCanvas(imageBitmap!!, viewModel)
    }
}

@Composable
fun PaintingCanvas(bitmap: Bitmap, viewModel: PaintingViewModel) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val showColorPicker = remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale *= zoomChange
        offset += offsetChange
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it.toSize() }
            .pointerInput(Unit) {
                detectTapGestures {
                    val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
                    val transformedOffset = (it - offset - center) / scale + center
                    viewModel.startFloodFill(transformedOffset.x.toInt(), transformedOffset.y.toInt())
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
                .transformable(state = transformableState)
        ) {
            drawImage(bitmap.asImageBitmap())
        }
        Row {
            Button(onClick = { viewModel.clearImage() }) {
                Text("Back")
            }
            IconButton(onClick = { showColorPicker.value = !showColorPicker.value }) {
                Icon(painter = painterResource(id = R.drawable.ic_brush), contentDescription = "Select Color", tint = viewModel.selectedColor.collectAsState().value)
            }
        }
        if (showColorPicker.value) {
            HoneycombColorPicker(onColorSelected = { color ->
                viewModel.setSelectedColor(color)
                showColorPicker.value = false
            })
        }
    }
}