package com.example.coloringapp

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAdjustmentScreen(
    originalBitmap: Bitmap,
    onApply: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var edgeThreshold by remember { mutableFloatStateOf(30f) }
    var edgeThickness by remember { mutableFloatStateOf(2f) }
    var blurAmount by remember { mutableFloatStateOf(3f) }
    var detailEnhancement by remember { mutableFloatStateOf(75f) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Initial processing
    LaunchedEffect(Unit) {
        isProcessing = true
        previewBitmap = withContext(Dispatchers.Default) {
            processImageWithParameters(
                originalBitmap, 
                edgeThreshold, 
                edgeThickness, 
                blurAmount,
                detailEnhancement
            )
        }
        isProcessing = false
    }

    // Update preview when any parameter changes
    LaunchedEffect(edgeThreshold, edgeThickness, blurAmount, detailEnhancement) {
        isProcessing = true
        previewBitmap = withContext(Dispatchers.Default) {
            processImageWithParameters(
                originalBitmap, 
                edgeThreshold, 
                edgeThickness, 
                blurAmount,
                detailEnhancement
            )
        }
        isProcessing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adjust Edge Detection") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.ArrowBack, "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            previewBitmap?.let { onApply(it) }
                        },
                        enabled = !isProcessing && previewBitmap != null
                    ) {
                        Icon(Icons.Default.Check, "Apply")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator()
                } else {
                    previewBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Controls area - Scrollable for multiple parameters
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Edge Detection Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Edge Sensitivity
                    Text(
                        text = "Detail Level: ${edgeThreshold.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Less",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.width(40.dp)
                        )
                        
                        Slider(
                            value = edgeThreshold,
                            onValueChange = { edgeThreshold = it },
                            valueRange = 10f..80f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            enabled = !isProcessing
                        )
                        
                        Text(
                            text = "More",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                    Text(
                        text = "Lower = more details captured",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Line Thickness
                    Text(
                        text = "Line Thickness: ${edgeThickness.toInt()}px",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thin",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.width(40.dp)
                        )
                        
                        Slider(
                            value = edgeThickness,
                            onValueChange = { edgeThickness = it },
                            valueRange = 1f..5f,
                            steps = 3,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            enabled = !isProcessing
                        )
                        
                        Text(
                            text = "Thick",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                    Text(
                        text = "Makes lines more visible",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Blur/Smoothing
                    Text(
                        text = "Smoothing: ${blurAmount.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sharp",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.width(40.dp)
                        )
                        
                        Slider(
                            value = blurAmount,
                            onValueChange = { blurAmount = it },
                            valueRange = 1f..7f,
                            steps = 2,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            enabled = !isProcessing
                        )
                        
                        Text(
                            text = "Smooth",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                    Text(
                        text = "Reduces noise, smoother lines",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Detail Enhancement (bilateral filter strength)
                    Text(
                        text = "Edge Preservation: ${detailEnhancement.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Low",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.width(40.dp)
                        )
                        
                        Slider(
                            value = detailEnhancement,
                            onValueChange = { detailEnhancement = it },
                            valueRange = 50f..150f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            enabled = !isProcessing
                        )
                        
                        Text(
                            text = "High",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                    Text(
                        text = "Keeps important edges sharp",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { 
                                edgeThreshold = 30f
                                edgeThickness = 2f
                                blurAmount = 3f
                                detailEnhancement = 75f
                            },
                            enabled = !isProcessing
                        ) {
                            Text("Reset All")
                        }
                        
                        TextButton(
                            onClick = { 
                                edgeThreshold = 15f
                                edgeThickness = 3f
                                blurAmount = 3f
                                detailEnhancement = 100f
                            },
                            enabled = !isProcessing
                        ) {
                            Text("Max Details")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Process image with custom parameters for edge detection
 */
private suspend fun processImageWithParameters(
    bitmap: Bitmap, 
    edgeThreshold: Float,
    edgeThickness: Float,
    blurAmount: Float,
    detailEnhancement: Float
): Bitmap {
    return withContext(Dispatchers.Default) {
        try {
            val mat = org.opencv.core.Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, mat)
            
            // Convert to grayscale
            val grayMat = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.cvtColor(mat, grayMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
            
            // Apply bilateral filter with adjustable strength for detail preservation
            val filtered = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.bilateralFilter(
                grayMat, 
                filtered, 
                9, 
                detailEnhancement.toDouble(), 
                detailEnhancement.toDouble()
            )
            
            // Apply Gaussian blur with adjustable amount
            val blurred = org.opencv.core.Mat()
            val kernelSize = (blurAmount.toInt() * 2 - 1).toDouble() // 1, 3, 5, 7, etc.
            org.opencv.imgproc.Imgproc.GaussianBlur(
                filtered, 
                blurred, 
                org.opencv.core.Size(kernelSize, kernelSize), 
                0.0
            )
            
            // Canny edge detection with custom threshold
            val edges = org.opencv.core.Mat()
            val lowerThreshold = edgeThreshold.toDouble()
            val upperThreshold = lowerThreshold * 2.5
            org.opencv.imgproc.Imgproc.Canny(blurred, edges, lowerThreshold, upperThreshold)
            
            // Dilate edges with adjustable thickness
            val kernelSizeInt = edgeThickness.toInt()
            val kernel = org.opencv.imgproc.Imgproc.getStructuringElement(
                org.opencv.imgproc.Imgproc.MORPH_RECT, 
                org.opencv.core.Size(kernelSizeInt.toDouble(), kernelSizeInt.toDouble())
            )
            val dilated = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.dilate(edges, dilated, kernel)
            
            // Invert to get black lines on white
            val inverted = org.opencv.core.Mat()
            org.opencv.core.Core.bitwise_not(dilated, inverted)
            
            // Threshold for pure white/black
            val thresholded = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.threshold(
                inverted, 
                thresholded, 
                240.0, 
                255.0, 
                org.opencv.imgproc.Imgproc.THRESH_BINARY
            )
            
            // Convert back to RGBA
            val resultMat = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.cvtColor(thresholded, resultMat, org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA)
            
            // Convert to bitmap
            val resultBitmap = Bitmap.createBitmap(
                bitmap.width, 
                bitmap.height, 
                Bitmap.Config.ARGB_8888
            )
            org.opencv.android.Utils.matToBitmap(resultMat, resultBitmap)
            
            // Clean up
            mat.release()
            grayMat.release()
            filtered.release()
            blurred.release()
            edges.release()
            kernel.release()
            dilated.release()
            inverted.release()
            thresholded.release()
            resultMat.release()
            
            resultBitmap
        } catch (e: Exception) {
            android.util.Log.e("ImageAdjustment", "Error processing image: ${e.message}")
            bitmap
        }
    }
}
