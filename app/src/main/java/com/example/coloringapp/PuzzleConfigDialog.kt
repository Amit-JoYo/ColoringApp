package com.example.coloringapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Enum representing the type of puzzle game.
 */
enum class PuzzleType(val displayName: String, val description: String, val needsGridSize: Boolean = true, val needsImage: Boolean = true) {
    FREE_DRAWING("Free Drawing", "Draw on a blank canvas", false, false),
    PAINTING("Free Paint", "Paint and color freely on your image", false, true),
    SLIDING("Sliding Puzzle", "Slide tiles to solve the puzzle", true, true),
    JIGSAW("Jigsaw Puzzle", "Drag pieces to their correct positions", true, true),
    COLOR_BY_NUMBER("Color by Number", "AI creates a coloring page - fill each region", false, true),
    MEMORY_MATCH("Memory Match", "Find matching pairs of image tiles", true, true)
}

/**
 * Difficulty levels for Color by Number game
 */
enum class ColorByNumberDifficulty(val label: String, val minRegionSize: Float, val edgeThreshold: Int) {
    EASY("Easy", 0.005f, 40),      // Fewer, larger regions
    MEDIUM("Medium", 0.002f, 30),   // Balanced
    HARD("Hard", 0.001f, 20)        // More, smaller regions
}

/**
 * Data class containing puzzle configuration.
 */
data class PuzzleConfig(
    val type: PuzzleType,
    val gridSize: Int,
    // Color by Number specific settings
    val numberOfColors: Int = 12,
    val colorByNumberDifficulty: ColorByNumberDifficulty = ColorByNumberDifficulty.MEDIUM
) {
    val pieceCount: Int get() = if (type == PuzzleType.SLIDING) gridSize * gridSize - 1 else gridSize * gridSize
    
    val difficultyLabel: String get() = when {
        gridSize <= 4 -> "Easy"
        gridSize <= 6 -> "Medium"
        gridSize <= 10 -> "Hard"
        gridSize <= 16 -> "Expert"
        else -> "Master"
    }
}

/**
 * A dialog for configuring puzzle settings before starting a puzzle game.
 *
 * @param onDismiss Called when the dialog is dismissed.
 * @param onStartPuzzle Called when the user confirms their selection with the chosen configuration.
 */
@Composable
fun PuzzleConfigDialog(
    onDismiss: () -> Unit,
    onStartPuzzle: (PuzzleConfig) -> Unit
) {
    var selectedType by remember { mutableStateOf(PuzzleType.SLIDING) }
    var gridSize by remember { mutableFloatStateOf(4f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Puzzle",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Puzzle Type Selection
                Text(
                    text = "Puzzle Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PuzzleType.entries.forEach { type ->
                        PuzzleOptionCard(
                            title = type.displayName,
                            description = type.description,
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Grid Size Slider - only show for puzzle types that need it
                if (selectedType.needsGridSize) {
                    Text(
                        text = "Grid Size",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    val currentGridSize = gridSize.roundToInt()
                    val pieceCount = when (selectedType) {
                        PuzzleType.SLIDING -> currentGridSize * currentGridSize - 1
                        PuzzleType.MEMORY_MATCH -> (currentGridSize * currentGridSize / 2) * 2 // Pairs (even number)
                        else -> currentGridSize * currentGridSize
                    }
                    val difficultyLabel = when {
                        currentGridSize <= 4 -> "Easy"
                        currentGridSize <= 6 -> "Medium"
                        currentGridSize <= 10 -> "Hard"
                        currentGridSize <= 16 -> "Expert"
                        else -> "Master"
                    }
                    
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentGridSize}×${currentGridSize}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = difficultyLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = if (selectedType == PuzzleType.MEMORY_MATCH) "${pieceCount / 2} pairs" else "$pieceCount pieces",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Slider(
                            value = gridSize,
                            onValueChange = { gridSize = it },
                            valueRange = if (selectedType == PuzzleType.MEMORY_MATCH) 2f..6f else 3f..25f,
                            steps = if (selectedType == PuzzleType.MEMORY_MATCH) 3 else 21,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (selectedType == PuzzleType.MEMORY_MATCH) "2×2" else "3×3",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (selectedType == PuzzleType.MEMORY_MATCH) "6×6" else "25×25",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStartPuzzle(PuzzleConfig(selectedType, gridSize.roundToInt()))
                }
            ) {
                Text("Start Puzzle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * A selectable card for puzzle options.
 */
@Composable
private fun PuzzleOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null // Handled by selectable modifier
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
