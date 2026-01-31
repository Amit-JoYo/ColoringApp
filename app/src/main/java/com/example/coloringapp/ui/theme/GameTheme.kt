package com.example.coloringapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.coloringapp.R

/**
 * Game Theme - Fun, playful colors and fonts for the puzzle game UI
 */

// Fredoka font - bundled locally for reliability
val FredokaFont = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Medium)
)

// Wooden/Game color palette
object GameColors {
    // Wood tones
    val WoodDark = Color(0xFF5D4037)
    val WoodMedium = Color(0xFF8D6E63)
    val WoodLight = Color(0xFFBCAAA4)
    val WoodHighlight = Color(0xFFD7CCC8)
    
    // Button colors
    val ButtonGreen = Color(0xFF4CAF50)
    val ButtonGreenDark = Color(0xFF388E3C)
    val ButtonBlue = Color(0xFF2196F3)
    val ButtonBlueDark = Color(0xFF1976D2)
    val ButtonOrange = Color(0xFFFF9800)
    val ButtonOrangeDark = Color(0xFFF57C00)
    val ButtonRed = Color(0xFFF44336)
    val ButtonRedDark = Color(0xFFD32F2F)
    val ButtonPurple = Color(0xFF9C27B0)
    val ButtonPurpleDark = Color(0xFF7B1FA2)
    val ButtonYellow = Color(0xFFFFEB3B)
    val ButtonYellowDark = Color(0xFFFBC02D)
    
    // Background colors
    val BackgroundGreen = Color(0xFF81C784)
    val BackgroundSky = Color(0xFF87CEEB)
    val BackgroundParchment = Color(0xFFFFF8E1)
    
    // Text colors
    val TextOnWood = Color(0xFFFFFFFF)
    val TextDark = Color(0xFF3E2723)
    val TextGold = Color(0xFFFFD700)
    
    // Game mode card colors
    val SlidingPuzzle = Color(0xFF42A5F5)
    val JigsawPuzzle = Color(0xFF66BB6A)
    val ColorByNumber = Color(0xFFFFCA28)
    val MemoryMatch = Color(0xFFAB47BC)
}
