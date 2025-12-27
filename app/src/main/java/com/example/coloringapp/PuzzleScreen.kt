package com.example.coloringapp

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Main puzzle screen that handles the entire puzzle flow:
 * 1. Image selection (if no bitmap provided)
 * 2. Puzzle configuration
 * 3. Puzzle game
 *
 * @param initialBitmap Optional bitmap to use for the puzzle (e.g., from painting screen).
 * @param onBack Called when the user wants to go back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    initialBitmap: Bitmap? = null,
    onBack: () -> Unit,
    puzzleViewModel: PuzzleViewModel = viewModel()
) {
    val context = LocalContext.current
    
    val puzzleBitmap by puzzleViewModel.puzzleBitmap.collectAsState()
    val puzzleConfig by puzzleViewModel.puzzleConfig.collectAsState()
    val isPlaying by puzzleViewModel.isPlaying.collectAsState()
    val isSolved by puzzleViewModel.isSolved.collectAsState()
    val isLoading by puzzleViewModel.isLoading.collectAsState()

    var showConfigDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var webSearchQuery by remember { mutableStateOf<String?>(null) }

    // Set initial bitmap if provided
    LaunchedEffect(initialBitmap) {
        if (initialBitmap != null && puzzleBitmap == null) {
            puzzleViewModel.setBitmap(initialBitmap)
            showConfigDialog = true
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    puzzleViewModel.setBitmap(bitmap)
                    showConfigDialog = true
                }
            } catch (e: Exception) {
                android.util.Log.e("PuzzleScreen", "Error loading image: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when {
                            isPlaying && puzzleConfig != null -> "${puzzleConfig!!.type.displayName} - ${puzzleConfig!!.gridSize}×${puzzleConfig!!.gridSize}"
                            puzzleBitmap != null -> "Configure Puzzle"
                            else -> "Make a Puzzle"
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            isPlaying -> puzzleViewModel.backToConfig()
                            puzzleBitmap != null -> puzzleViewModel.clearPuzzle()
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isPlaying) {
                        // Shuffle/Reset button
                        IconButton(onClick = {
                            puzzleViewModel.backToConfig()
                            showConfigDialog = true
                        }) {
                            Icon(Icons.Default.Refresh, "Restart")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }
                webSearchQuery != null -> {
                    // Show web search screen
                    WebImageSearchScreen(
                        searchQuery = webSearchQuery!!,
                        onImageSelected = { bitmap ->
                            webSearchQuery = null
                            puzzleViewModel.setBitmap(bitmap)
                            showConfigDialog = true
                        },
                        onBack = {
                            webSearchQuery = null
                        }
                    )
                }
                isPlaying && puzzleBitmap != null && puzzleConfig != null -> {
                    // Show the puzzle game
                    when (puzzleConfig!!.type) {
                        PuzzleType.SLIDING -> {
                            SlidingPuzzleGame(
                                bitmap = puzzleBitmap!!,
                                gridSize = puzzleConfig!!.gridSize,
                                onPuzzleSolved = { puzzleViewModel.onPuzzleSolved() }
                            )
                        }
                        PuzzleType.JIGSAW -> {
                            JigsawPuzzleGame(
                                bitmap = puzzleBitmap!!,
                                gridSize = puzzleConfig!!.gridSize,
                                onPuzzleSolved = { puzzleViewModel.onPuzzleSolved() }
                            )
                        }
                    }

                    // Show play again dialog when solved
                    if (isSolved) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("🎉 Puzzle Complete!") },
                            text = { Text("Great job! Would you like to play again?") },
                            confirmButton = {
                                Button(onClick = {
                                    puzzleViewModel.backToConfig()
                                    showConfigDialog = true
                                }) {
                                    Text("Play Again")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = onBack) {
                                    Text("Exit")
                                }
                            }
                        )
                    }
                }
                puzzleBitmap != null -> {
                    // Image selected, show preview and prompt to configure
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Image Selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .aspectRatio(1f)
                                .padding(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Image(
                                bitmap = puzzleBitmap!!.asImageBitmap(),
                                contentDescription = "Selected image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { showConfigDialog = true },
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("Configure Puzzle")
                        }
                    }
                }
                else -> {
                    // Image selection screen for puzzle
                    PuzzleImageSelectionContent(
                        preloadedImages = puzzleViewModel.preloadedImages,
                        onImageSelected = { drawableRes ->
                            puzzleViewModel.setBitmapFromDrawable(context, drawableRes)
                            showConfigDialog = true
                        },
                        onGalleryClick = { imagePickerLauncher.launch("image/*") },
                        onSearchClick = { showSearchDialog = true }
                    )
                }
            }
        }
    }

    // Puzzle configuration dialog
    if (showConfigDialog && puzzleBitmap != null) {
        PuzzleConfigDialog(
            onDismiss = { showConfigDialog = false },
            onStartPuzzle = { config ->
                showConfigDialog = false
                puzzleViewModel.startPuzzle(config)
            }
        )
    }
    
    // Web search dialog
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search for Puzzle Images") },
            text = {
                Column {
                    Text(
                        text = "Enter keywords to search for images online:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search keywords") },
                        placeholder = { Text("e.g., landscape, animals, art") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Popular searches:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Nature: sunset, mountains, ocean\n" +
                               "• Animals: cat, dog, wildlife\n" +
                               "• Art: famous paintings, abstract",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            webSearchQuery = searchQuery
                            showSearchDialog = false
                            searchQuery = ""
                        }
                    },
                    enabled = searchQuery.isNotBlank()
                ) {
                    Text("Search")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showSearchDialog = false
                    searchQuery = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Content for selecting an image for the puzzle.
 */
@Composable
private fun PuzzleImageSelectionContent(
    preloadedImages: List<Int>,
    onImageSelected: (Int) -> Unit,
    onGalleryClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Instructions card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Create a Puzzle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Choose a pre-loaded image below\n" +
                           "• Search online for images\n" +
                           "• Or import your own image from gallery",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Pre-loaded images grid
        Text(
            text = "Pre-loaded Images",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(preloadedImages) { image ->
                Card(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onImageSelected(image) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Image(
                        painter = painterResource(id = image),
                        contentDescription = "Puzzle image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Action buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search button
            OutlinedButton(
                onClick = onSearchClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search Online")
            }
            
            // Gallery button
            Button(
                onClick = onGalleryClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gallery")
            }
        }
    }
}
