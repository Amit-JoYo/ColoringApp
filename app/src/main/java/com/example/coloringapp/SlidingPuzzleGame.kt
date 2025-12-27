package com.example.coloringapp

import android.graphics.Bitmap
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Data class representing a tile in the sliding puzzle.
 */
data class SlidingTile(
    val id: Int, // Original position (0 to gridSize^2 - 1), -1 for empty
    val currentPosition: Int, // Current position in the grid
    val bitmap: Bitmap? // The tile image
)

/**
 * Sliding puzzle game composable.
 *
 * @param bitmap The source image to create the puzzle from.
 * @param gridSize The size of the grid (3 for 3x3, 4 for 4x4, etc.).
 * @param onPuzzleSolved Called when the puzzle is solved.
 */
@Composable
fun SlidingPuzzleGame(
    bitmap: Bitmap,
    gridSize: Int,
    onPuzzleSolved: () -> Unit
) {
    var tiles by remember { mutableStateOf(createSlidingPuzzleTiles(bitmap, gridSize)) }
    var moveCount by remember { mutableStateOf(0) }
    var isSolved by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    val density = LocalDensity.current

    // Check if puzzle is solved after each move
    LaunchedEffect(tiles) {
        if (checkSlidingPuzzleSolved(tiles, gridSize)) {
            isSolved = true
            onPuzzleSolved()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Move counter
        Text(
            text = "Moves: $moveCount",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )

        // Puzzle grid - use weight to take available space while staying centered
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxSize = minOf(maxWidth, maxHeight)
            
            Box(
                modifier = Modifier
                    .size(maxSize)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .onSizeChanged { containerSize = it },
                contentAlignment = Alignment.TopStart
            ) {
                if (containerSize.width > 0 && containerSize.height > 0) {
                    val tileSize = containerSize.width / gridSize
                    
                    tiles.filter { it.id != -1 }.forEach { tile ->
                        val row = tile.currentPosition / gridSize
                        val col = tile.currentPosition % gridSize
                        
                        val targetOffset = Offset(
                            x = col * tileSize.toFloat(),
                        y = row * tileSize.toFloat()
                    )
                    
                    val animatedOffset by animateOffsetAsState(
                        targetValue = targetOffset,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                        label = "tileOffset"
                    )

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(animatedOffset.x.roundToInt(), animatedOffset.y.roundToInt()) }
                            .size(with(density) { tileSize.toDp() })
                            .padding(1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = !isSolved) {
                                val emptyTile = tiles.find { it.id == -1 }
                                if (emptyTile != null && canMoveTile(tile.currentPosition, emptyTile.currentPosition, gridSize)) {
                                    tiles = moveTile(tiles, tile, emptyTile)
                                    moveCount++
                                }
                            }
                    ) {
                        tile.bitmap?.let { tileBitmap ->
                            Image(
                                bitmap = tileBitmap.asImageBitmap(),
                                contentDescription = "Puzzle tile ${tile.id}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                        
                        // Optional: Show tile numbers for debugging/assistance
                        // Text(
                        //     text = "${tile.id + 1}",
                        //     color = Color.White,
                        //     modifier = Modifier.align(Alignment.Center)
                        // )
                    }
                }
            }
        }
        }

        // Solved message
        if (isSolved) {
            Text(
                text = "🎉 Congratulations! 🎉\nPuzzle solved in $moveCount moves!",
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
 * Creates the initial tiles for a sliding puzzle.
 * Uses precise pixel calculations to avoid cutting off any part of the image.
 */
fun createSlidingPuzzleTiles(bitmap: Bitmap, gridSize: Int): List<SlidingTile> {
    val tiles = mutableListOf<SlidingTile>()
    
    for (i in 0 until gridSize * gridSize - 1) {
        val row = i / gridSize
        val col = i % gridSize
        
        // Calculate precise pixel boundaries to avoid gaps or overlaps
        val startX = (col * bitmap.width) / gridSize
        val endX = ((col + 1) * bitmap.width) / gridSize
        val startY = (row * bitmap.height) / gridSize
        val endY = ((row + 1) * bitmap.height) / gridSize
        
        val tileWidth = endX - startX
        val tileHeight = endY - startY
        
        val tileBitmap = Bitmap.createBitmap(
            bitmap,
            startX,
            startY,
            tileWidth,
            tileHeight
        )
        
        tiles.add(SlidingTile(id = i, currentPosition = i, bitmap = tileBitmap))
    }
    
    // Add empty tile at the last position
    tiles.add(SlidingTile(id = -1, currentPosition = gridSize * gridSize - 1, bitmap = null))
    
    // Shuffle the tiles
    return shuffleSlidingPuzzle(tiles, gridSize)
}

/**
 * Shuffles the puzzle tiles while ensuring the puzzle remains solvable.
 */
fun shuffleSlidingPuzzle(tiles: List<SlidingTile>, gridSize: Int): List<SlidingTile> {
    val mutableTiles = tiles.toMutableList()
    val totalTiles = gridSize * gridSize
    
    // Perform random valid moves to shuffle
    repeat(totalTiles * 20) {
        val emptyTile = mutableTiles.find { it.id == -1 } ?: return@repeat
        val emptyPos = emptyTile.currentPosition
        
        // Find adjacent positions
        val adjacentPositions = getAdjacentPositions(emptyPos, gridSize)
        
        if (adjacentPositions.isNotEmpty()) {
            val randomAdjacentPos = adjacentPositions.random()
            val tileToMove = mutableTiles.find { it.currentPosition == randomAdjacentPos }
            
            if (tileToMove != null) {
                // Swap positions
                val emptyIndex = mutableTiles.indexOf(emptyTile)
                val tileIndex = mutableTiles.indexOf(tileToMove)
                
                mutableTiles[emptyIndex] = emptyTile.copy(currentPosition = tileToMove.currentPosition)
                mutableTiles[tileIndex] = tileToMove.copy(currentPosition = emptyPos)
            }
        }
    }
    
    return mutableTiles
}

/**
 * Gets adjacent positions for a given position in the grid.
 */
fun getAdjacentPositions(position: Int, gridSize: Int): List<Int> {
    val row = position / gridSize
    val col = position % gridSize
    val adjacent = mutableListOf<Int>()
    
    if (row > 0) adjacent.add(position - gridSize) // Up
    if (row < gridSize - 1) adjacent.add(position + gridSize) // Down
    if (col > 0) adjacent.add(position - 1) // Left
    if (col < gridSize - 1) adjacent.add(position + 1) // Right
    
    return adjacent
}

/**
 * Checks if a tile can be moved to the empty position.
 */
fun canMoveTile(tilePosition: Int, emptyPosition: Int, gridSize: Int): Boolean {
    val tileRow = tilePosition / gridSize
    val tileCol = tilePosition % gridSize
    val emptyRow = emptyPosition / gridSize
    val emptyCol = emptyPosition % gridSize
    
    // Tile must be adjacent to empty space (same row/col, 1 step away)
    return (tileRow == emptyRow && abs(tileCol - emptyCol) == 1) ||
           (tileCol == emptyCol && abs(tileRow - emptyRow) == 1)
}

/**
 * Moves a tile to the empty position.
 */
fun moveTile(tiles: List<SlidingTile>, tile: SlidingTile, emptyTile: SlidingTile): List<SlidingTile> {
    return tiles.map { t ->
        when (t.id) {
            tile.id -> t.copy(currentPosition = emptyTile.currentPosition)
            emptyTile.id -> t.copy(currentPosition = tile.currentPosition)
            else -> t
        }
    }
}

/**
 * Checks if the sliding puzzle is solved.
 */
fun checkSlidingPuzzleSolved(tiles: List<SlidingTile>, gridSize: Int): Boolean {
    return tiles.all { tile ->
        if (tile.id == -1) {
            tile.currentPosition == gridSize * gridSize - 1
        } else {
            tile.currentPosition == tile.id
        }
    }
}
