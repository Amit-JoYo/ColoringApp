package com.example.coloringapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coloringapp.ui.components.*
import com.example.coloringapp.ui.theme.FredokaFont
import com.example.coloringapp.ui.theme.GameColors

/**
 * Get localized display name for a PuzzleType
 */
@Composable
fun PuzzleType.localizedName(): String {
    return when (this) {
        PuzzleType.FREE_DRAWING -> stringResource(R.string.free_drawing)
        PuzzleType.PAINTING -> stringResource(R.string.free_paint)
        PuzzleType.SLIDING -> stringResource(R.string.sliding_puzzle)
        PuzzleType.JIGSAW -> stringResource(R.string.jigsaw_puzzle)
        PuzzleType.COLOR_BY_NUMBER -> stringResource(R.string.color_by_number)
        PuzzleType.MEMORY_MATCH -> stringResource(R.string.memory_match)
    }
}

/**
 * Get localized description for a PuzzleType
 */
@Composable
fun PuzzleType.localizedDescription(): String {
    return when (this) {
        PuzzleType.FREE_DRAWING -> stringResource(R.string.free_drawing_desc)
        PuzzleType.PAINTING -> stringResource(R.string.free_paint_desc)
        PuzzleType.SLIDING -> stringResource(R.string.sliding_puzzle_desc)
        PuzzleType.JIGSAW -> stringResource(R.string.jigsaw_puzzle_desc)
        PuzzleType.COLOR_BY_NUMBER -> stringResource(R.string.color_by_number_desc)
        PuzzleType.MEMORY_MATCH -> stringResource(R.string.memory_match_desc)
    }
}

/**
 * Game mode configuration for the hub
 */
data class GameMode(
    val type: PuzzleType,
    val emoji: String,
    val color: Color,
    val suggestionResourceIds: List<Int>  // String resource IDs for localized suggestions
)

private val gameModes = listOf(
    GameMode(
        type = PuzzleType.FREE_DRAWING,
        emoji = "✏️",
        color = GameColors.ButtonGreen,
        suggestionResourceIds = emptyList()  // No suggestions needed - blank canvas
    ),
    GameMode(
        type = PuzzleType.PAINTING,
        emoji = "🖌️",
        color = GameColors.ColorByNumber,
        suggestionResourceIds = listOf(R.string.suggestion_mandala, R.string.suggestion_coloring_page, R.string.suggestion_line_art, R.string.suggestion_sketch, R.string.suggestion_drawing)
    ),
    GameMode(
        type = PuzzleType.SLIDING,
        emoji = "🧩",
        color = GameColors.SlidingPuzzle,
        suggestionResourceIds = listOf(R.string.suggestion_landscape, R.string.suggestion_famous_painting, R.string.suggestion_cityscape, R.string.suggestion_nature, R.string.suggestion_sunset)
    ),
    GameMode(
        type = PuzzleType.JIGSAW,
        emoji = "🖼️",
        color = GameColors.JigsawPuzzle,
        suggestionResourceIds = listOf(R.string.suggestion_scenery, R.string.suggestion_animals, R.string.suggestion_flowers, R.string.suggestion_architecture, R.string.suggestion_art)
    ),
    GameMode(
        type = PuzzleType.COLOR_BY_NUMBER,
        emoji = "🎨",
        color = GameColors.ButtonOrange,
        suggestionResourceIds = listOf(R.string.suggestion_cartoon, R.string.suggestion_mandala, R.string.suggestion_simple_art, R.string.suggestion_portrait, R.string.suggestion_illustration)
    ),
    GameMode(
        type = PuzzleType.MEMORY_MATCH,
        emoji = "🃏",
        color = GameColors.MemoryMatch,
        suggestionResourceIds = listOf(R.string.suggestion_icons, R.string.suggestion_animals, R.string.suggestion_objects, R.string.suggestion_emoji, R.string.suggestion_patterns)
    )
)

/**
 * Step in the game setup flow
 */
enum class GameSetupStep {
    SELECT_MODE,
    SELECT_IMAGE,
    CONFIGURE_GAME
}

/**
 * Main Game Hub Screen - The new entry point for all puzzle games
 * 
 * Flow:
 * 1. Select game mode (all visible as cards)
 * 2. Select image (with smart suggestions based on mode)
 * 3. Configure game settings
 * 4. Start playing!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHubScreen(
    initialBitmap: Bitmap? = null,
    onBack: () -> Unit,
    onSettings: (() -> Unit)? = null,
    puzzleViewModel: PuzzleViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // State
    var currentStep by remember { mutableStateOf(GameSetupStep.SELECT_MODE) }
    var selectedMode by remember { mutableStateOf<GameMode?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(initialBitmap) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var webSearchQuery by remember { mutableStateOf<String?>(null) }
    
    // Puzzle state from ViewModel
    val isPlaying by puzzleViewModel.isPlaying.collectAsState()
    val puzzleConfig by puzzleViewModel.puzzleConfig.collectAsState()
    val isSolved by puzzleViewModel.isSolved.collectAsState()
    val puzzleBitmap by puzzleViewModel.puzzleBitmap.collectAsState()
    val isLoading by puzzleViewModel.isLoading.collectAsState()
    
    // Set initial bitmap if provided
    LaunchedEffect(initialBitmap) {
        if (initialBitmap != null && selectedBitmap == null) {
            selectedBitmap = initialBitmap
            puzzleViewModel.setBitmap(initialBitmap)
            currentStep = GameSetupStep.SELECT_MODE
        }
    }
    
    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    // Convert to coloring page for Free Paint mode
                    val processedBitmap = if (selectedMode?.type == PuzzleType.PAINTING) {
                        convertToColoringPage(bitmap)
                    } else {
                        bitmap
                    }
                    selectedBitmap = processedBitmap
                    puzzleViewModel.setBitmap(processedBitmap)
                    currentStep = GameSetupStep.CONFIGURE_GAME
                }
            } catch (e: Exception) {
                android.util.Log.e("GameHub", "Error loading image: ${e.message}")
            }
        }
    }
    
    // Camera launcher
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(cameraImageUri!!)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    // Convert to coloring page for Free Paint mode
                    val processedBitmap = if (selectedMode?.type == PuzzleType.PAINTING) {
                        convertToColoringPage(bitmap)
                    } else {
                        bitmap
                    }
                    selectedBitmap = processedBitmap
                    puzzleViewModel.setBitmap(processedBitmap)
                    currentStep = GameSetupStep.CONFIGURE_GAME
                }
            } catch (e: Exception) {
                android.util.Log.e("GameHub", "Error loading camera image: ${e.message}")
            }
        }
    }
    
    // Camera permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Create temp file for camera
            val file = java.io.File.createTempFile(
                "puzzle_camera_", ".jpg",
                context.cacheDir
            )
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            cameraLauncher.launch(cameraImageUri!!)
        }
    }
    
    // Handle system back button
    BackHandler(enabled = currentStep != GameSetupStep.SELECT_MODE || isPlaying || webSearchQuery != null) {
        when {
            webSearchQuery != null -> {
                webSearchQuery = null
            }
            isPlaying -> {
                puzzleViewModel.backToConfig()
                currentStep = GameSetupStep.SELECT_MODE
                selectedMode = null
                selectedBitmap = null
            }
            currentStep == GameSetupStep.CONFIGURE_GAME -> {
                // For Free Drawing, skip image selection when going back
                if (selectedMode?.type == PuzzleType.FREE_DRAWING) {
                    selectedBitmap = null
                    currentStep = GameSetupStep.SELECT_MODE
                } else {
                    currentStep = GameSetupStep.SELECT_IMAGE
                }
            }
            currentStep == GameSetupStep.SELECT_IMAGE -> {
                currentStep = GameSetupStep.SELECT_MODE
            }
        }
    }
    
    // Background with gradient
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GameColors.BackgroundSky,
                        GameColors.BackgroundGreen
                    )
                )
            )
    ) {
        // Debug logging
        android.util.Log.d("GameHub", "Recomposing - webSearchQuery: $webSearchQuery, isLoading: $isLoading, isPlaying: $isPlaying")
        
        when {
            // Web search screen
            webSearchQuery != null -> {
                android.util.Log.d("GameHub", "Showing WebImageSearchScreen for: $webSearchQuery")
                // Use COLORING_PAGE mode for painting (to find black & white images), PHOTO for other games
                val searchMode = if (selectedMode?.type == PuzzleType.PAINTING) {
                    ImageSearchMode.COLORING_PAGE
                } else {
                    ImageSearchMode.PHOTO
                }
                WebImageSearchScreen(
                    searchQuery = webSearchQuery!!,
                    searchMode = searchMode,
                    onImageSelected = { bitmap ->
                        webSearchQuery = null
                        // For Free Paint mode, convert colorful images to black & white line art
                        val processedBitmap = if (selectedMode?.type == PuzzleType.PAINTING) {
                            convertToColoringPage(bitmap)
                        } else {
                            bitmap
                        }
                        selectedBitmap = processedBitmap
                        puzzleViewModel.setBitmap(processedBitmap)
                        currentStep = GameSetupStep.CONFIGURE_GAME
                    },
                    onBack = { webSearchQuery = null }
                )
            }
            
            // Loading state
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = GameColors.WoodDark,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loading),
                            fontFamily = FredokaFont,
                            fontSize = 20.sp,
                            color = GameColors.TextDark
                        )
                    }
                }
            }
            
            // Playing the game
            isPlaying && puzzleBitmap != null && puzzleConfig != null -> {
                PuzzleGameContent(
                    puzzleBitmap = puzzleBitmap!!,
                    puzzleConfig = puzzleConfig!!,
                    isSolved = isSolved,
                    onPuzzleSolved = { puzzleViewModel.onPuzzleSolved() },
                    onBack = { 
                        puzzleViewModel.backToConfig()
                        currentStep = GameSetupStep.SELECT_MODE
                    },
                    onPlayAgain = {
                        puzzleViewModel.backToConfig()
                        currentStep = GameSetupStep.CONFIGURE_GAME
                    },
                    onExit = {
                        puzzleViewModel.backToConfig()
                        currentStep = GameSetupStep.SELECT_MODE
                        selectedMode = null
                        selectedBitmap = null
                    }
                )
            }
            
            // Setup flow
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top bar
                    GameHubTopBar(
                        currentStep = currentStep,
                        onBack = {
                            when (currentStep) {
                                GameSetupStep.SELECT_MODE -> onBack()
                                GameSetupStep.SELECT_IMAGE -> {
                                    currentStep = GameSetupStep.SELECT_MODE
                                }
                                GameSetupStep.CONFIGURE_GAME -> {
                                    // For Free Drawing, skip image selection when going back
                                    if (selectedMode?.type == PuzzleType.FREE_DRAWING) {
                                        selectedBitmap = null
                                        currentStep = GameSetupStep.SELECT_MODE
                                    } else {
                                        currentStep = GameSetupStep.SELECT_IMAGE
                                    }
                                }
                            }
                        },
                        onSettings = onSettings
                    )
                    
                    // Step content
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> -width } + fadeOut()
                        },
                        modifier = Modifier.weight(1f),
                        label = "step_transition"
                    ) { step ->
                        when (step) {
                            GameSetupStep.SELECT_MODE -> {
                                GameModeSelectionContent(
                                    selectedMode = selectedMode,
                                    onModeSelected = { mode ->
                                        selectedMode = mode
                                        // Clear any previous bitmap when switching modes
                                        selectedBitmap = null
                                        puzzleViewModel.clearBitmap()
                                        
                                        // For Free Drawing, skip image selection - create blank canvas
                                        if (mode.type == PuzzleType.FREE_DRAWING) {
                                            // Create a white canvas bitmap
                                            val blankBitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                                            blankBitmap.eraseColor(android.graphics.Color.WHITE)
                                            selectedBitmap = blankBitmap
                                            puzzleViewModel.setBitmap(blankBitmap)
                                            currentStep = GameSetupStep.CONFIGURE_GAME
                                        } else {
                                            // Auto-advance to image selection
                                            currentStep = GameSetupStep.SELECT_IMAGE
                                        }
                                    }
                                )
                            }
                            
                            GameSetupStep.SELECT_IMAGE -> {
                                ImageSelectionContent(
                                    selectedMode = selectedMode!!,
                                    preloadedImages = puzzleViewModel.preloadedImages,
                                    onImageSelected = { drawableRes ->
                                        // This is async - just trigger the load, the bitmap will update via StateFlow
                                        // For Free Paint mode, convert to coloring page (black & white)
                                        if (selectedMode?.type == PuzzleType.PAINTING) {
                                            puzzleViewModel.setBitmapFromDrawableAsColoringPage(context, drawableRes)
                                        } else {
                                            puzzleViewModel.setBitmapFromDrawable(context, drawableRes)
                                        }
                                        currentStep = GameSetupStep.CONFIGURE_GAME
                                    },
                                    onGalleryClick = { imagePickerLauncher.launch("image/*") },
                                    onCameraClick = {
                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            val file = java.io.File.createTempFile(
                                                "puzzle_camera_", ".jpg",
                                                context.cacheDir
                                            )
                                            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            cameraLauncher.launch(cameraImageUri!!)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    onSearchClick = { showSearchDialog = true },
                                    onRandomSuggestionClick = { query ->
                                        android.util.Log.d("GameHub", "Setting webSearchQuery to: $query")
                                        webSearchQuery = query
                                    }
                                )
                            }
                            
                            GameSetupStep.CONFIGURE_GAME -> {
                                GameConfigContent(
                                    selectedMode = selectedMode!!,
                                    previewBitmap = puzzleBitmap,
                                    onStartGame = { config ->
                                        puzzleViewModel.startPuzzle(config)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Search dialog
    if (showSearchDialog) {
        SearchImageDialog(
            searchQuery = searchQuery,
            onQueryChange = { searchQuery = it },
            suggestionResourceIds = selectedMode?.suggestionResourceIds ?: emptyList(),
            onSearch = {
                if (searchQuery.isNotBlank()) {
                    webSearchQuery = searchQuery
                    showSearchDialog = false
                    searchQuery = ""
                }
            },
            onSuggestionClick = { term ->
                webSearchQuery = term
                showSearchDialog = false
            },
            onDismiss = {
                showSearchDialog = false
                searchQuery = ""
            }
        )
    }
}

@Composable
private fun GameHubTopBar(
    currentStep: GameSetupStep,
    onBack: () -> Unit,
    onSettings: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button (only show if not on first step)
        if (currentStep != GameSetupStep.SELECT_MODE) {
            WoodenButton(
                text = "",
                onClick = onBack,
                modifier = Modifier.size(48.dp),
                buttonColor = GameColors.WoodDark
            )
            
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .offset(x = (-48).dp)
                    .size(48.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = GameColors.TextOnWood
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        // Title
        val title = when (currentStep) {
            GameSetupStep.SELECT_MODE -> "🎮 " + stringResource(R.string.choose_your_game)
            GameSetupStep.SELECT_IMAGE -> "🖼️ " + stringResource(R.string.pick_an_image)
            GameSetupStep.CONFIGURE_GAME -> "⚙️ " + stringResource(R.string.game_settings)
        }
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFont,
            color = GameColors.TextDark,
            modifier = Modifier.weight(1f)
        )
        
        // Settings button (only on first step)
        if (currentStep == GameSetupStep.SELECT_MODE && onSettings != null) {
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = GameColors.TextDark,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        // Step indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                val isActive = index <= currentStep.ordinal
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) GameColors.WoodDark else GameColors.WoodLight
                        )
                )
            }
        }
    }
}

@Composable
private fun GameModeSelectionContent(
    selectedMode: GameMode?,
    onModeSelected: (GameMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Game mode cards in 2x2 grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(gameModes) { mode ->
                val localizedTitle = mode.type.localizedName()
                val localizedDesc = mode.type.localizedDescription()
                GameModeCard(
                    title = localizedTitle,
                    emoji = mode.emoji,
                    description = localizedDesc,
                    backgroundColor = mode.color,
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier
                        .aspectRatio(0.9f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ImageSelectionContent(
    selectedMode: GameMode,
    preloadedImages: List<Int>,
    onImageSelected: (Int) -> Unit,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRandomSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Random suggestions for this mode
        WoodenPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column {
                val modeName = selectedMode.type.localizedName()
                Text(
                    text = "✨ " + stringResource(R.string.suggested_for, modeName),
                    fontFamily = FredokaFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = GameColors.TextDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Random suggestion chips - use resource IDs for localization
                val suggestionIds = remember(selectedMode) {
                    selectedMode.suggestionResourceIds.shuffled().take(3)
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestionIds.forEach { resId ->
                        val term = stringResource(resId)
                        SuggestionChip(
                            onClick = { 
                                onRandomSuggestionClick(term) 
                            },
                            label = {
                                Text(
                                    text = term,
                                    fontFamily = FredokaFont,
                                    color = GameColors.TextDark
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = selectedMode.color.copy(alpha = 0.4f),
                                labelColor = GameColors.TextDark
                            )
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action buttons
        WoodenSectionHeader(text = stringResource(R.string.get_an_image))
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Gallery - icon only
            WoodenButton(
                text = "",
                onClick = onGalleryClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                buttonColor = GameColors.ButtonBlue,
                icon = Icons.Default.Face  // Gallery icon
            )
            
            // Camera - icon only  
            WoodenButton(
                text = "",
                onClick = onCameraClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                buttonColor = GameColors.ButtonOrange,
                icon = Icons.Default.AccountBox  // Camera icon
            )
            
            // Search - icon only
            WoodenButton(
                text = "",
                onClick = onSearchClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                buttonColor = GameColors.ButtonPurple,
                icon = Icons.Default.Search
            )
        }
        
        // Labels under buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.gallery),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontFamily = FredokaFont,
                fontSize = 12.sp,
                color = GameColors.TextDark
            )
            Text(
                text = stringResource(R.string.camera),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontFamily = FredokaFont,
                fontSize = 12.sp,
                color = GameColors.TextDark
            )
            Text(
                text = stringResource(R.string.search),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontFamily = FredokaFont,
                fontSize = 12.sp,
                color = GameColors.TextDark
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Sample images
        WoodenSectionHeader(text = stringResource(R.string.sample_images))
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(300.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(preloadedImages) { drawableRes ->
                Card(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onImageSelected(drawableRes) },
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Image(
                        painter = painterResource(id = drawableRes),
                        contentDescription = "Sample image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GameConfigContent(
    selectedMode: GameMode,
    previewBitmap: Bitmap?,
    onStartGame: (PuzzleConfig) -> Unit
) {
    var gridSize by remember { mutableFloatStateOf(4f) }
    // Color by Number specific settings
    var numberOfColors by remember { mutableFloatStateOf(12f) }
    var cbnDifficulty by remember { mutableStateOf(ColorByNumberDifficulty.MEDIUM) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),  // Make scrollable
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preview - smaller for more room
        if (previewBitmap != null) {
            WoodenPanel(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Selected image",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Game mode info panel
        val gameModeName = selectedMode.type.localizedName()
        val gameModeDesc = selectedMode.type.localizedDescription()
        WoodenPanel(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🎮 " + gameModeName,
                    fontFamily = FredokaFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = GameColors.TextDark
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = gameModeDesc,
                    fontFamily = FredokaFont,
                    fontSize = 14.sp,
                    color = GameColors.TextDark.copy(alpha = 0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Grid size slider (for applicable modes)
        if (selectedMode.type.needsGridSize) {
            WoodenPanel(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Explain what the setting does for this game type
                val settingExplanation = when (selectedMode.type) {
                    PuzzleType.SLIDING -> "Puzzle grid: ${gridSize.toInt()}×${gridSize.toInt()} = ${gridSize.toInt() * gridSize.toInt() - 1} tiles to slide"
                    PuzzleType.JIGSAW -> "Puzzle pieces: ${gridSize.toInt()}×${gridSize.toInt()} = ${gridSize.toInt() * gridSize.toInt()} pieces"
                    PuzzleType.MEMORY_MATCH -> "Card grid: ${gridSize.toInt()}×${gridSize.toInt()} = ${(gridSize.toInt() * gridSize.toInt()) / 2} pairs to match"
                    else -> "Grid: ${gridSize.toInt()}×${gridSize.toInt()}"
                }
                
                Text(
                    text = "⚙️ " + stringResource(R.string.difficulty),
                    fontFamily = FredokaFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = GameColors.TextDark
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = settingExplanation,
                    fontFamily = FredokaFont,
                    fontSize = 14.sp,
                    color = GameColors.TextDark
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = getDifficultyLabel(gridSize.toInt()),
                    fontFamily = FredokaFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = selectedMode.color
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Slider(
                    value = gridSize,
                    onValueChange = { gridSize = it },
                    valueRange = 3f..8f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = GameColors.WoodDark,
                        activeTrackColor = selectedMode.color
                    )
                )
                
                // Visual difficulty indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.easy),
                        fontFamily = FredokaFont,
                        fontSize = 12.sp,
                        color = GameColors.TextDark.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.hard),
                        fontFamily = FredokaFont,
                        fontSize = 12.sp,
                        color = GameColors.TextDark.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            // Settings for modes without grid (Free Drawing, Painting, Color by Number)
            WoodenPanel(
                modifier = Modifier.fillMaxWidth()
            ) {
                when (selectedMode.type) {
                    PuzzleType.FREE_DRAWING -> {
                        Text(
                            text = "✏️ " + stringResource(R.string.free_drawing),
                            fontFamily = FredokaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = GameColors.TextDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Draw on a blank white canvas\n• Multiple brush sizes available\n• Full color palette\n• Undo/Redo and save your art",
                            fontFamily = FredokaFont,
                            fontSize = 14.sp,
                            color = GameColors.TextDark.copy(alpha = 0.8f)
                        )
                    }
                    PuzzleType.PAINTING -> {
                        Text(
                            text = "🎨 " + stringResource(R.string.free_paint),
                            fontFamily = FredokaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = GameColors.TextDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Choose from multiple brush sizes\n• Pick any color from the palette\n• Undo/Redo your strokes\n• Save and share your artwork",
                            fontFamily = FredokaFont,
                            fontSize = 14.sp,
                            color = GameColors.TextDark.copy(alpha = 0.8f)
                        )
                    }
                    PuzzleType.COLOR_BY_NUMBER -> {
                        Text(
                            text = "🔢 " + stringResource(R.string.color_by_number),
                            fontFamily = FredokaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = GameColors.TextDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Number of colors slider
                        Text(
                            text = "🎨 " + stringResource(R.string.number_of_colors, numberOfColors.toInt()),
                            fontFamily = FredokaFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = GameColors.TextDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = numberOfColors,
                            onValueChange = { numberOfColors = it },
                            valueRange = 6f..24f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = GameColors.WoodDark,
                                activeTrackColor = selectedMode.color
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "6 (" + stringResource(R.string.simple) + ")",
                                fontFamily = FredokaFont,
                                fontSize = 11.sp,
                                color = GameColors.TextDark.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "24 (" + stringResource(R.string.detailed) + ")",
                                fontFamily = FredokaFont,
                                fontSize = 11.sp,
                                color = GameColors.TextDark.copy(alpha = 0.5f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Difficulty selection - localized labels
                        val diffLabel = when (cbnDifficulty) {
                            ColorByNumberDifficulty.EASY -> stringResource(R.string.easy)
                            ColorByNumberDifficulty.MEDIUM -> stringResource(R.string.medium)
                            ColorByNumberDifficulty.HARD -> stringResource(R.string.hard)
                        }
                        Text(
                            text = "📊 " + stringResource(R.string.region_size, diffLabel),
                            fontFamily = FredokaFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = GameColors.TextDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ColorByNumberDifficulty.entries.forEach { diff ->
                                val isSelected = cbnDifficulty == diff
                                val localizedLabel = when (diff) {
                                    ColorByNumberDifficulty.EASY -> stringResource(R.string.easy)
                                    ColorByNumberDifficulty.MEDIUM -> stringResource(R.string.medium)
                                    ColorByNumberDifficulty.HARD -> stringResource(R.string.hard)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) selectedMode.color.copy(alpha = 0.8f) 
                                            else GameColors.WoodLight.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 2.dp,
                                            color = if (isSelected) GameColors.WoodDark else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { cbnDifficulty = diff }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = localizedLabel,
                                        fontFamily = FredokaFont,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else GameColors.TextDark
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (cbnDifficulty) {
                                ColorByNumberDifficulty.EASY -> stringResource(R.string.easy_regions_desc)
                                ColorByNumberDifficulty.MEDIUM -> stringResource(R.string.medium_regions_desc)
                                ColorByNumberDifficulty.HARD -> stringResource(R.string.hard_regions_desc)
                            },
                            fontFamily = FredokaFont,
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = GameColors.TextDark.copy(alpha = 0.6f)
                        )
                    }
                    else -> {
                        Text(
                            text = "✨ " + stringResource(R.string.ready_to_start),
                            fontFamily = FredokaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = GameColors.TextDark
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Start button
        WoodenButton(
            text = "🎮 " + stringResource(R.string.start_game),
            onClick = {
                onStartGame(PuzzleConfig(
                    type = selectedMode.type,
                    gridSize = gridSize.toInt(),
                    numberOfColors = numberOfColors.toInt(),
                    colorByNumberDifficulty = cbnDifficulty
                ))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            buttonColor = GameColors.ButtonGreen,
            fontSize = 22.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PuzzleGameContent(
    puzzleBitmap: Bitmap,
    puzzleConfig: PuzzleConfig,
    isSolved: Boolean,
    onPuzzleSolved: () -> Unit,
    onBack: () -> Unit,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (puzzleConfig.type) {
            PuzzleType.FREE_DRAWING -> {
                // Free drawing mode - blank canvas with drawing tools
                FreeDrawingCanvas(
                    onBack = onBack
                )
            }
            PuzzleType.PAINTING -> {
                // Free painting mode - paint on the selected image
                FreePaintCanvas(
                    backgroundBitmap = puzzleBitmap,
                    onComplete = onPuzzleSolved,
                    onBack = onBack
                )
            }
            PuzzleType.SLIDING -> {
                SlidingPuzzleGame(
                    bitmap = puzzleBitmap,
                    gridSize = puzzleConfig.gridSize,
                    onPuzzleSolved = onPuzzleSolved
                )
            }
            PuzzleType.JIGSAW -> {
                JigsawPuzzleGame(
                    bitmap = puzzleBitmap,
                    gridSize = puzzleConfig.gridSize,
                    onPuzzleSolved = onPuzzleSolved
                )
            }
            PuzzleType.COLOR_BY_NUMBER -> {
                ColorByNumberGame(
                    imageBitmap = puzzleBitmap,
                    onComplete = onPuzzleSolved,
                    onBack = onBack,
                    configuredNumberOfColors = puzzleConfig.numberOfColors,
                    configuredDifficulty = puzzleConfig.colorByNumberDifficulty
                )
            }
            PuzzleType.MEMORY_MATCH -> {
                MemoryMatchGame(
                    bitmap = puzzleBitmap,
                    gridSize = puzzleConfig.gridSize,
                    onPuzzleSolved = onPuzzleSolved
                )
            }
        }
        
        // Victory dialog
        if (isSolved) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                WoodenPanel(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "🎉",
                            fontSize = 64.sp
                        )
                        Text(
                            text = stringResource(R.string.puzzle_complete),
                            fontFamily = FredokaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = GameColors.TextDark
                        )
                        Text(
                            text = stringResource(R.string.great_job),
                            fontFamily = FredokaFont,
                            fontSize = 18.sp,
                            color = GameColors.TextDark.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        WoodenButton(
                            text = stringResource(R.string.play_again),
                            onClick = onPlayAgain,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            buttonColor = GameColors.ButtonGreen
                        )
                        
                        WoodenButton(
                            text = stringResource(R.string.exit),
                            onClick = onExit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            buttonColor = GameColors.WoodMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchImageDialog(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    suggestionResourceIds: List<Int>,
    onSearch: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🔍 " + stringResource(R.string.search_images),
                fontFamily = FredokaFont,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.search_keywords)) },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.quick_suggestions),
                    fontFamily = FredokaFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Suggestion chips - localized
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestionResourceIds) { resId ->
                        val term = stringResource(resId)
                        SuggestionChip(
                            onClick = { onSuggestionClick(term) },
                            label = { 
                                Text(
                                    text = term, 
                                    fontFamily = FredokaFont,
                                    color = GameColors.TextDark
                                ) 
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSearch,
                enabled = searchQuery.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GameColors.ButtonGreen
                )
            ) {
                Text(stringResource(R.string.search), fontFamily = FredokaFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), fontFamily = FredokaFont)
            }
        }
    )
}

private fun getDifficultyLabel(gridSize: Int): String {
    return when {
        gridSize <= 3 -> "Easy - Perfect for beginners"
        gridSize <= 4 -> "Medium - A nice challenge"
        gridSize <= 6 -> "Hard - For puzzle pros"
        else -> "Expert - Ultimate challenge!"
    }
}

/**
 * Check if an image is already mostly black and white (low saturation).
 */
private fun isAlreadyBlackAndWhite(bitmap: Bitmap): Boolean {
    val width = bitmap.width
    val height = bitmap.height
    
    // Sample pixels (check every 10th pixel for performance)
    var colorfulPixels = 0
    var totalSamples = 0
    
    for (y in 0 until height step 10) {
        for (x in 0 until width step 10) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            // Calculate saturation - if R, G, B are close together, it's grayscale
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val diff = max - min
            
            // If difference is significant, it's colorful
            if (diff > 30) {
                colorfulPixels++
            }
            totalSamples++
        }
    }
    
    // If less than 10% of pixels are colorful, consider it black & white
    return colorfulPixels < totalSamples * 0.1
}

/**
 * Convert a colorful image to a black and white coloring page (line art style).
 * This uses edge detection to extract outlines suitable for coloring.
 * If the image is already black and white, it returns the original.
 */
private fun convertToColoringPage(bitmap: Bitmap): Bitmap {
    // Skip conversion if already black and white
    if (isAlreadyBlackAndWhite(bitmap)) {
        return bitmap
    }
    
    val width = bitmap.width
    val height = bitmap.height
    
    // Create output bitmap
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    // Get pixels
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    
    // Convert to grayscale first
    val grayscale = IntArray(width * height)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // Luminosity method for grayscale
        val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        grayscale[i] = gray
    }
    
    // Apply edge detection (Sobel-like filter)
    val output = IntArray(width * height)
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val idx = y * width + x
            
            // Sobel kernels
            val gx = (-grayscale[(y - 1) * width + (x - 1)] + grayscale[(y - 1) * width + (x + 1)]
                    - 2 * grayscale[y * width + (x - 1)] + 2 * grayscale[y * width + (x + 1)]
                    - grayscale[(y + 1) * width + (x - 1)] + grayscale[(y + 1) * width + (x + 1)])
            
            val gy = (-grayscale[(y - 1) * width + (x - 1)] - 2 * grayscale[(y - 1) * width + x] - grayscale[(y - 1) * width + (x + 1)]
                    + grayscale[(y + 1) * width + (x - 1)] + 2 * grayscale[(y + 1) * width + x] + grayscale[(y + 1) * width + (x + 1)])
            
            // Magnitude of gradient
            val magnitude = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt()
            
            // Invert and threshold to get black lines on white background
            val edgeValue = if (magnitude > 30) 0 else 255  // Black lines, white background
            output[idx] = (0xFF shl 24) or (edgeValue shl 16) or (edgeValue shl 8) or edgeValue
        }
    }
    
    // Fill edges with white
    for (x in 0 until width) {
        output[x] = 0xFFFFFFFF.toInt()
        output[(height - 1) * width + x] = 0xFFFFFFFF.toInt()
    }
    for (y in 0 until height) {
        output[y * width] = 0xFFFFFFFF.toInt()
        output[y * width + width - 1] = 0xFFFFFFFF.toInt()
    }
    
    result.setPixels(output, 0, width, 0, 0, width, height)
    return result
}
