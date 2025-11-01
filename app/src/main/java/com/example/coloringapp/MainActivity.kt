package com.example.coloringapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.coloringapp.ui.theme.ColoringAppTheme

import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opencv.android.OpenCVLoader
import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV!")
        } else {
            Log.d("OpenCV", "OpenCV loaded Successfully!")
        }
        setContent {
            ColoringAppTheme {
                val viewModel: PaintingViewModel = viewModel()
                val isPaintingScreen by viewModel.imageBitmap.collectAsState()
                val showAdjustment by viewModel.showAdjustment.collectAsState()
                val originalBitmap by viewModel.originalBitmap.collectAsState()
                val webSearchQuery by viewModel.webSearchQuery.collectAsState()

                BackHandler(enabled = isPaintingScreen != null || showAdjustment || webSearchQuery != null) {
                    when {
                        webSearchQuery != null -> viewModel.cancelWebSearch()
                        showAdjustment -> viewModel.cancelAdjustment()
                        else -> viewModel.clearImage()
                    }
                }

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        webSearchQuery != null -> {
                            WebImageSearchScreen(
                                searchQuery = webSearchQuery!!,
                                onImageSelected = { bitmap ->
                                    viewModel.cancelWebSearch()
                                    viewModel.setImageBitmap(bitmap)
                                },
                                onBack = {
                                    viewModel.cancelWebSearch()
                                }
                            )
                        }
                        showAdjustment && originalBitmap != null -> {
                            ImageAdjustmentScreen(
                                originalBitmap = originalBitmap!!,
                                onApply = { adjustedBitmap ->
                                    viewModel.applyAdjustedBitmap(adjustedBitmap)
                                },
                                onCancel = {
                                    viewModel.cancelAdjustment()
                                }
                            )
                        }
                        else -> {
                            PaintingScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}