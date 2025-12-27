package com.example.coloringapp

import android.graphics.Bitmap
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Data class representing a piece in the jigsaw puzzle.
 */
data class JigsawPiece(
    val id: Int, // Original position (0 to gridSize^2 - 1)
    val row: Int, // Row in grid
    val col: Int, // Column in grid
    val targetPosition: Offset, // Where the piece should be placed (accounting for tabs)
    val currentOffset: Offset, // Current position on screen
    val bitmap: Bitmap, // The piece image (with jigsaw edges)
    val edges: PieceEdges, // The edge configuration
    val tabSize: Int, // Size of tabs in pixels
    val isPlaced: Boolean = false // Whether piece is in correct position
)

/**
 * Calculates optimal number of rows and columns to keep pieces approximately square
 * while matching the target piece count.
 * 
 * @param imageAspectRatio Width / Height of the image
 * @param targetPieces Target total number of pieces (from gridSize slider)
 * @return Pair of (numRows, numCols)
 */
fun calculateGridDimensions(imageAspectRatio: Float, targetPieces: Int): Pair<Int, Int> {
    // For approximately square pieces, we want:
    // numCols / numRows ≈ imageAspectRatio
    // numRows * numCols ≈ targetPieces
    // 
    // From these: numRows = sqrt(targetPieces / aspectRatio)
    //             numCols = sqrt(targetPieces * aspectRatio)
    
    val numRows = maxOf(2, kotlin.math.sqrt(targetPieces / imageAspectRatio).roundToInt())
    val numCols = maxOf(2, kotlin.math.sqrt(targetPieces * imageAspectRatio).roundToInt())
    
    return Pair(numRows, numCols)
}

/**
 * Jigsaw puzzle game composable.
 *
 * @param bitmap The source image to create the puzzle from.
 * @param gridSize The approximate number of pieces (used to calculate rows/cols).
 * @param onPuzzleSolved Called when the puzzle is solved.
 */
@Composable
fun JigsawPuzzleGame(
    bitmap: Bitmap,
    gridSize: Int,
    onPuzzleSolved: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var puzzleAreaSize by remember { mutableStateOf(IntSize.Zero) }
    var pieces by remember { mutableStateOf<List<JigsawPiece>>(emptyList()) }
    var draggedPieceId by remember { mutableStateOf<Int?>(null) }
    var isSolved by remember { mutableStateOf(false) }
    
    val density = LocalDensity.current
    val snapThreshold = with(density) { 40.dp.toPx() } // Increased for jigsaw pieces
    
    // Calculate image aspect ratio and optimal grid dimensions
    val imageAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val (numRows, numCols) = remember(gridSize, imageAspectRatio) {
        calculateGridDimensions(imageAspectRatio, gridSize * gridSize)
    }

    // Initialize pieces when container size is known
    LaunchedEffect(containerSize, puzzleAreaSize, numRows, numCols) {
        if (containerSize.width > 0 && puzzleAreaSize.width > 0 && pieces.isEmpty()) {
            pieces = createJigsawPieces(bitmap, numRows, numCols, puzzleAreaSize, containerSize)
        }
    }

    // Check if puzzle is solved
    LaunchedEffect(pieces) {
        if (pieces.isNotEmpty() && pieces.all { it.isPlaced }) {
            isSolved = true
            onPuzzleSolved()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        val placedCount = pieces.count { it.isPlaced }
        Text(
            text = "Placed: $placedCount / ${pieces.size} (${numRows}x${numCols})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        // Main game area - contains both puzzle target and pieces
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Puzzle target area - centered with proper aspect ratio
            // Outer box with padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio)
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                // Inner box measures actual puzzle content size
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { puzzleAreaSize = it }
                ) {
                    // Semi-transparent reference image
                    if (puzzleAreaSize.width > 0) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Reference",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 0.15f },
                            contentScale = ContentScale.FillBounds
                        )
                    }
                }
            }

            // Puzzle pieces - positioned relative to puzzle area
            // Calculate puzzle area offset (16dp padding)
            val puzzleAreaPadding = with(density) { 16.dp.toPx() }
            val puzzleAreaOffset = Offset(puzzleAreaPadding, puzzleAreaPadding)
            
            pieces.forEachIndexed { _, piece ->
                val isDragging = draggedPieceId == piece.id
                
                val animatedOffset by animateOffsetAsState(
                    targetValue = piece.currentOffset,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                    label = "pieceOffset"
                )

                val displayOffset = if (isDragging) piece.currentOffset else animatedOffset
                
                // Use actual bitmap dimensions for display to avoid stretching
                val displayWidth = piece.bitmap.width
                val displayHeight = piece.bitmap.height
                
                // Actual target position includes puzzle area offset
                val actualTargetPosition = Offset(
                    x = puzzleAreaOffset.x + piece.targetPosition.x,
                    y = puzzleAreaOffset.y + piece.targetPosition.y
                )

                Box(
                    modifier = Modifier
                        .offset { IntOffset(displayOffset.x.roundToInt(), displayOffset.y.roundToInt()) }
                        .size(
                            width = with(density) { displayWidth.toDp() },
                            height = with(density) { displayHeight.toDp() }
                        )
                        .zIndex(if (isDragging) 100f else if (piece.isPlaced) 0f else 1f)
                        .then(
                            if (!piece.isPlaced && !isSolved) {
                                Modifier.pointerInput(piece.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedPieceId = piece.id
                                        },
                                        onDragEnd = {
                                            draggedPieceId = null
                                            // Check if piece should snap to target
                                            pieces = pieces.map { p ->
                                                if (p.id == piece.id) {
                                                    val distance = calculateDistance(p.currentOffset, actualTargetPosition)
                                                    if (distance < snapThreshold) {
                                                        p.copy(currentOffset = actualTargetPosition, isPlaced = true)
                                                    } else {
                                                        p
                                                    }
                                                } else {
                                                    p
                                                }
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            pieces = pieces.map { p ->
                                                if (p.id == piece.id) {
                                                    p.copy(
                                                        currentOffset = Offset(
                                                            x = (p.currentOffset.x + dragAmount.x).coerceIn(
                                                                0f,
                                                                (containerSize.width - displayWidth).toFloat().coerceAtLeast(0f)
                                                            ),
                                                            y = (p.currentOffset.y + dragAmount.y).coerceIn(
                                                                0f,
                                                                (containerSize.height - displayHeight).toFloat().coerceAtLeast(0f)
                                                            )
                                                        )
                                                    )
                                                } else {
                                                    p
                                                }
                                            }
                                        }
                                    )
                                }
                            } else Modifier
                        )
                        .shadow(if (isDragging) 8.dp else if (piece.isPlaced) 0.dp else 2.dp)
                ) {
                    Image(
                        bitmap = piece.bitmap.asImageBitmap(),
                        contentDescription = "Puzzle piece ${piece.id}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.None
                    )
                }
            }
        }

        // Solved message
        if (isSolved) {
            Text(
                text = "🎉 Congratulations! 🎉\nPuzzle completed!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * Creates jigsaw puzzle pieces with randomized starting positions.
 * Uses jigsaw-shaped pieces with interlocking tabs and slots.
 * 
 * @param bitmap Source image
 * @param numRows Number of rows in the puzzle grid
 * @param numCols Number of columns in the puzzle grid
 * @param puzzleAreaSize Size of the puzzle area on screen
 * @param containerSize Size of the full container for random positioning
 */
fun createJigsawPieces(
    bitmap: Bitmap,
    numRows: Int,
    numCols: Int,
    puzzleAreaSize: IntSize,
    containerSize: IntSize
): List<JigsawPiece> {
    // Calculate piece dimensions that exactly cover the puzzle area
    // Use integer division but make sure the last piece covers any remainder
    val basePieceWidth = puzzleAreaSize.width / numCols
    val basePieceHeight = puzzleAreaSize.height / numRows
    
    // Scale the source bitmap to match exactly numCols * basePieceWidth and numRows * basePieceHeight
    // This ensures pieces fit perfectly without gaps
    val scaledWidth = basePieceWidth * numCols
    val scaledHeight = basePieceHeight * numRows
    
    val scaledBitmap = Bitmap.createScaledBitmap(
        bitmap,
        scaledWidth,
        scaledHeight,
        true
    )
    
    // Generate edge patterns for the entire puzzle
    val edgePattern = generatePuzzleEdgePattern(numRows, numCols)
    
    // Calculate tab size - use same 20% ratio for both bitmap and screen
    // Screen tab size for positioning and display
    val screenTabSize = maxOf(10, (minOf(basePieceWidth, basePieceHeight) * 0.20f).toInt())
    
    val pieces = mutableListOf<JigsawPiece>()
    
    val totalPieces = numRows * numCols
    for (i in 0 until totalPieces) {
        val row = i / numCols
        val col = i % numCols
        
        val edges = edgePattern[row][col]
        
        // Create jigsaw-shaped bitmap from the scaled source
        val pieceBitmap = createJigsawPieceBitmap(
            sourceBitmap = scaledBitmap,
            pieceX = col,
            pieceY = row,
            pieceWidth = basePieceWidth,
            pieceHeight = basePieceHeight,
            edges = edges
        )
        
        // Calculate tab size used in bitmap creation (15% of smaller dimension)
        val bitmapTabSize = (minOf(basePieceWidth, basePieceHeight) * 0.15f)
        
        // Target position - account for tabs protruding from top/left edges
        // When a piece has a left tab, the bitmap is expanded, so we need to offset
        // the piece leftward so the base rectangle aligns with the grid
        val tabOffsetX = if (edges.left == EdgeType.TAB) bitmapTabSize else 0f
        val tabOffsetY = if (edges.top == EdgeType.TAB) bitmapTabSize else 0f
        
        val targetPosition = Offset(
            x = col * basePieceWidth.toFloat() - tabOffsetX,
            y = row * basePieceHeight.toFloat() - tabOffsetY
        )
        
        // Use actual bitmap size for display
        val displayWidth = pieceBitmap.width
        val displayHeight = pieceBitmap.height
        
        // Randomize starting position - scatter pieces below the puzzle area
        // Pieces should start in the lower portion of the screen
        val puzzleAreaHeight = scaledHeight + 32 // Account for padding
        val availableHeight = containerSize.height - puzzleAreaHeight
        val trayAreaStartY = puzzleAreaHeight + 16f // Start below puzzle area
        
        val randomOffset = Offset(
            x = (Math.random() * (containerSize.width - displayWidth).coerceAtLeast(1)).toFloat()
                .coerceIn(0f, (containerSize.width - displayWidth).toFloat().coerceAtLeast(0f)),
            y = if (availableHeight > displayHeight) {
                // Place in tray area below puzzle
                (trayAreaStartY + Math.random() * (availableHeight - displayHeight).coerceAtLeast(1)).toFloat()
                    .coerceIn(0f, (containerSize.height - displayHeight).toFloat().coerceAtLeast(0f))
            } else {
                // Not enough space below, scatter across screen
                (Math.random() * (containerSize.height - displayHeight).coerceAtLeast(1)).toFloat()
                    .coerceIn(0f, (containerSize.height - displayHeight).toFloat().coerceAtLeast(0f))
            }
        )
        
        pieces.add(
            JigsawPiece(
                id = i,
                row = row,
                col = col,
                targetPosition = targetPosition,
                currentOffset = randomOffset,
                bitmap = pieceBitmap,
                edges = edges,
                tabSize = screenTabSize,
                isPlaced = false
            )
        )
    }
    
    // DON'T recycle scaledBitmap here - piece bitmaps are independent copies
    // The pieces have their own bitmaps created via Bitmap.createBitmap
    
    return pieces.shuffled()
}

/**
 * Calculates the distance between two offsets.
 */
fun calculateDistance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
