package com.example.coloringapp

import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebImageSearchScreen(
    searchQuery: String,
    onImageSelected: (Bitmap) -> Unit,
    onBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var imageUrlToDownload by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    val searchUrl = remember(searchQuery) {
        "https://www.google.com/search?q=${android.net.Uri.encode(searchQuery)}+coloring+page+black+and+white&tbm=isch&tbs=ic:gray"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Search: $searchQuery")
                        if (currentUrl.isNotEmpty()) {
                            Text(
                                text = currentUrl.take(50) + if (currentUrl.length > 50) "..." else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // WebView
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                url?.let { currentUrl = it }
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                            
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }
                        
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                isLoading = newProgress < 100
                            }
                        }
                        
                        // Set long press listener to capture image URLs
                        setOnLongClickListener {
                            val result = hitTestResult
                            when (result.type) {
                                WebView.HitTestResult.IMAGE_TYPE,
                                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                    result.extra?.let { imageUrl ->
                                        imageUrlToDownload = imageUrl
                                        showDownloadDialog = true
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        
                        loadUrl(searchUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // Instructions card
            if (!isLoading && currentUrl.contains("google.com/search")) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 Long-press any image to download",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Error snackbar
            errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { errorMessage = null }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
    
    // Download confirmation dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (!downloadProgress) {
                    showDownloadDialog = false 
                }
            },
            title = { Text("Download Image") },
            text = {
                Column {
                    if (downloadProgress) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                            Text("Downloading and processing image...")
                        }
                    } else {
                        Text("Download this image and convert it to a coloring page?")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = imageUrlToDownload.takeLast(60),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                if (!downloadProgress) {
                    Button(
                        onClick = {
                            downloadProgress = true
                            coroutineScope.launch {
                                try {
                                    val bitmap = downloadImage(imageUrlToDownload)
                                    if (bitmap != null) {
                                        onImageSelected(bitmap)
                                        showDownloadDialog = false
                                        downloadProgress = false
                                    } else {
                                        errorMessage = "Failed to download image. Please try another one."
                                        showDownloadDialog = false
                                        downloadProgress = false
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Error: ${e.message}"
                                    showDownloadDialog = false
                                    downloadProgress = false
                                }
                            }
                        }
                    ) {
                        Text("Download")
                    }
                }
            },
            dismissButton = {
                if (!downloadProgress) {
                    TextButton(
                        onClick = { showDownloadDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

/**
 * Downloads an image from a URL and returns a Bitmap
 */
private suspend fun downloadImage(imageUrl: String): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            // Handle Google proxy URLs
            val actualUrl = if (imageUrl.contains("google.com")) {
                // Extract actual image URL from Google proxy
                android.net.Uri.parse(imageUrl).getQueryParameter("imgurl") ?: imageUrl
            } else {
                imageUrl
            }
            
            val url = URL(actualUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            
            val inputStream = connection.getInputStream()
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("WebImageSearch", "Error downloading image: ${e.message}")
            null
        }
    }
}
