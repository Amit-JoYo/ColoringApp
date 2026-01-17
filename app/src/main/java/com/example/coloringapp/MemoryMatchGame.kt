package com.example.coloringapp

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Data class representing a card in the memory match game.
 */
data class MemoryCard(
    val id: Int,           // Unique card ID
    val pairId: Int,       // ID of the matching pair (shared with another card)
    val bitmap: Bitmap,    // The image on this card
    var isFlipped: Boolean = false,
    var isMatched: Boolean = false
)

/**
 * Memory Match puzzle game composable.
 * 
 * Creates pairs of tiles from the image. Player flips cards to find matching pairs.
 *
 * @param bitmap The source image to create cards from.
 * @param gridSize The grid size (2x2 to 6x6).
 * @param onPuzzleSolved Called when all pairs are matched.
 */
@Composable
fun MemoryMatchGame(
    bitmap: Bitmap,
    gridSize: Int,
    onPuzzleSolved: () -> Unit
) {
    // Ensure even number of cards
    val totalCards = gridSize * gridSize
    val numPairs = totalCards / 2
    
    // Generate memory cards from image
    val cards = remember(bitmap, gridSize) { 
        createMemoryCards(bitmap, numPairs) 
    }
    
    var cardsState by remember { mutableStateOf(cards) }
    var firstFlipped by remember { mutableStateOf<Int?>(null) }
    var secondFlipped by remember { mutableStateOf<Int?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var moves by remember { mutableStateOf(0) }
    var matchedPairs by remember { mutableStateOf(0) }
    var isSolved by remember { mutableStateOf(false) }
    
    // Check for matches after flipping two cards
    LaunchedEffect(secondFlipped) {
        if (firstFlipped != null && secondFlipped != null && !isChecking) {
            isChecking = true
            moves++
            
            val firstCard = cardsState.find { it.id == firstFlipped }
            val secondCard = cardsState.find { it.id == secondFlipped }
            
            delay(1000) // Show cards for 1 second
            
            if (firstCard != null && secondCard != null && firstCard.pairId == secondCard.pairId) {
                // Match found!
                cardsState = cardsState.map { card ->
                    if (card.id == firstFlipped || card.id == secondFlipped) {
                        card.copy(isMatched = true)
                    } else {
                        card
                    }
                }
                matchedPairs++
            } else {
                // No match - flip cards back
                cardsState = cardsState.map { card ->
                    if (card.id == firstFlipped || card.id == secondFlipped) {
                        card.copy(isFlipped = false)
                    } else {
                        card
                    }
                }
            }
            
            firstFlipped = null
            secondFlipped = null
            isChecking = false
        }
    }
    
    // Check if puzzle is solved
    LaunchedEffect(matchedPairs) {
        if (matchedPairs == numPairs && numPairs > 0 && !isSolved) {
            isSolved = true
            onPuzzleSolved()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "Moves: $moves",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Pairs: $matchedPairs / $numPairs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Game grid
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxSize = minOf(maxWidth, maxHeight)
            val cardSize = (maxSize - ((gridSize + 1) * 4).dp) / gridSize
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridSize),
                modifier = Modifier.size(maxSize),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(cardsState) { card ->
                    MemoryCardView(
                        card = card,
                        size = cardSize,
                        onClick = {
                            if (!isChecking && !card.isFlipped && !card.isMatched) {
                                cardsState = cardsState.map { c ->
                                    if (c.id == card.id) c.copy(isFlipped = true) else c
                                }
                                
                                if (firstFlipped == null) {
                                    firstFlipped = card.id
                                } else if (secondFlipped == null) {
                                    secondFlipped = card.id
                                }
                            }
                        }
                    )
                }
            }
        }
        
        // Completion message
        if (isSolved) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🧠 All Pairs Found!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Completed in $moves moves",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/**
 * Individual memory card composable with flip animation.
 */
@Composable
private fun MemoryCardView(
    card: MemoryCard,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (card.isFlipped || card.isMatched) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "flip"
    )
    
    Card(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(enabled = !card.isFlipped && !card.isMatched) { onClick() },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (card.isMatched) 0.dp else 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                card.isMatched -> Color(0xFF81C784) // Green for matched
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (rotation > 90f) {
                // Show card face (image)
                Image(
                    bitmap = card.bitmap.asImageBitmap(),
                    contentDescription = "Card ${card.id}",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f } // Counter-rotate to show correctly
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                // Matched overlay
                if (card.isMatched) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x4081C784))
                            .graphicsLayer { rotationY = 180f },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Matched",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else {
                // Show card back
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF5C6BC0),
                                    Color(0xFF3949AB)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "?",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Creates memory cards by splitting the image into tiles and duplicating them for pairs.
 */
private fun createMemoryCards(bitmap: Bitmap, numPairs: Int): List<MemoryCard> {
    val cards = mutableListOf<MemoryCard>()
    
    // Calculate grid dimensions for extracting tiles
    val tilesAcross = kotlin.math.ceil(kotlin.math.sqrt(numPairs.toDouble())).toInt()
    val tilesDown = kotlin.math.ceil(numPairs.toDouble() / tilesAcross).toInt()
    
    val tileWidth = bitmap.width / tilesAcross
    val tileHeight = bitmap.height / tilesDown
    
    var cardId = 0
    var pairId = 0
    
    // Create pairs of cards
    for (row in 0 until tilesDown) {
        for (col in 0 until tilesAcross) {
            if (pairId >= numPairs) break
            
            val x = col * tileWidth
            val y = row * tileHeight
            
            // Ensure we don't go out of bounds
            val width = minOf(tileWidth, bitmap.width - x)
            val height = minOf(tileHeight, bitmap.height - y)
            
            if (width > 0 && height > 0) {
                val tileBitmap = Bitmap.createBitmap(bitmap, x, y, width, height)
                
                // Create two cards with the same image (a pair)
                cards.add(MemoryCard(id = cardId++, pairId = pairId, bitmap = tileBitmap))
                cards.add(MemoryCard(id = cardId++, pairId = pairId, bitmap = tileBitmap))
                pairId++
            }
        }
    }
    
    // Shuffle the cards
    return cards.shuffled()
}
