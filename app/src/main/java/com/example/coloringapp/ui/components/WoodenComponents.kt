package com.example.coloringapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coloringapp.ui.theme.FredokaFont
import com.example.coloringapp.ui.theme.GameColors

/**
 * A wooden-styled button that looks like it's made from wood planks
 */
@Composable
fun WoodenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonColor: Color = GameColors.WoodMedium,
    textColor: Color = GameColors.TextOnWood,
    fontSize: TextUnit = 18.sp,
    icon: ImageVector? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val pressOffset by animateFloatAsState(
        targetValue = if (isPressed) 4f else 0f,
        label = "press"
    )
    
    val shadowOffset by animateFloatAsState(
        targetValue = if (isPressed) 2f else 6f,
        label = "shadow"
    )
    
    Box(
        modifier = modifier
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                            onClick()
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            drawWoodenButton(
                color = buttonColor,
                pressOffset = pressOffset,
                shadowOffset = shadowOffset,
                enabled = enabled
            )
        }
        
        Row(
            modifier = Modifier
                .padding(horizontal = if (text.isEmpty()) 8.dp else 16.dp, vertical = 8.dp)
                .offset(y = pressOffset.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) textColor else textColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(if (text.isEmpty()) 28.dp else 20.dp)
                )
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    color = if (enabled) textColor else textColor.copy(alpha = 0.5f),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FredokaFont,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * A large game mode selection card with wooden frame styling
 */
@Composable
fun GameModeCard(
    title: String,
    emoji: String,
    description: String,
    backgroundColor: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "scale"
    )
    
    val borderWidth by animateFloatAsState(
        targetValue = if (selected) 6f else 3f,
        label = "border"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            // Shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.3f),
                topLeft = Offset(4f, 6f),
                size = Size(size.width - 4f, size.height - 4f),
                cornerRadius = CornerRadius(20f, 20f)
            )
            
            // Main card background
            drawRoundRect(
                color = backgroundColor,
                cornerRadius = CornerRadius(20f, 20f)
            )
            
            // Highlight gradient (top)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    endY = size.height * 0.5f
                ),
                cornerRadius = CornerRadius(20f, 20f)
            )
            
            // Wooden frame border
            drawRoundRect(
                color = if (selected) GameColors.TextGold else GameColors.WoodDark,
                cornerRadius = CornerRadius(20f, 20f),
                style = Stroke(width = borderWidth)
            )
            
            // Inner wood grain lines (decorative)
            val grainColor = GameColors.WoodDark.copy(alpha = 0.2f)
            for (i in 0..2) {
                drawLine(
                    color = grainColor,
                    start = Offset(size.width * 0.1f, size.height * (0.3f + i * 0.2f)),
                    end = Offset(size.width * 0.9f, size.height * (0.32f + i * 0.2f)),
                    strokeWidth = 2f
                )
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FredokaFont,
                color = GameColors.TextDark,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                fontFamily = FredokaFont,
                color = GameColors.TextDark.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
        
        // Selection indicator
        if (selected) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
            ) {
                drawCircle(
                    color = GameColors.ButtonGreen,
                    radius = size.minDimension / 2
                )
                drawCircle(
                    color = GameColors.WoodDark,
                    radius = size.minDimension / 2,
                    style = Stroke(width = 2f)
                )
                // Checkmark
                val path = Path().apply {
                    moveTo(size.width * 0.25f, size.height * 0.5f)
                    lineTo(size.width * 0.45f, size.height * 0.7f)
                    lineTo(size.width * 0.75f, size.height * 0.3f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

/**
 * Wooden panel/frame for containing content
 */
@Composable
fun WoodenPanel(
    modifier: Modifier = Modifier,
    backgroundColor: Color = GameColors.BackgroundParchment,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .drawBehind {
                // Outer shadow
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(4f, 6f),
                    size = Size(size.width - 4f, size.height - 4f),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                
                // Wood frame (outer)
                drawRoundRect(
                    color = GameColors.WoodDark,
                    cornerRadius = CornerRadius(16f, 16f)
                )
                
                // Wood frame (inner bevel)
                drawRoundRect(
                    color = GameColors.WoodMedium,
                    topLeft = Offset(6f, 6f),
                    size = Size(size.width - 12f, size.height - 12f),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                
                // Content background
                drawRoundRect(
                    color = backgroundColor,
                    topLeft = Offset(12f, 12f),
                    size = Size(size.width - 24f, size.height - 24f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                
                // Wood grain decorations on frame
                val grainColor = GameColors.WoodLight.copy(alpha = 0.3f)
                // Top grain
                drawLine(
                    color = grainColor,
                    start = Offset(20f, 4f),
                    end = Offset(size.width - 20f, 4f),
                    strokeWidth = 2f
                )
                // Bottom grain
                drawLine(
                    color = GameColors.WoodDark.copy(alpha = 0.5f),
                    start = Offset(20f, size.height - 4f),
                    end = Offset(size.width - 20f, size.height - 4f),
                    strokeWidth = 2f
                )
            }
            .padding(16.dp),
        content = content
    )
}

/**
 * Section header with wooden styling
 */
@Composable
fun WoodenSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // Wood plank background
            drawRoundRect(
                color = GameColors.WoodMedium,
                cornerRadius = CornerRadius(8f, 8f)
            )
            
            // Highlight on top
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GameColors.WoodLight.copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    endY = size.height * 0.5f
                ),
                cornerRadius = CornerRadius(8f, 8f)
            )
            
            // Border
            drawRoundRect(
                color = GameColors.WoodDark,
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 2f)
            )
            
            // Nail decorations
            val nailRadius = 4f
            listOf(12f, size.width - 12f).forEach { x ->
                drawCircle(
                    color = GameColors.WoodDark,
                    radius = nailRadius,
                    center = Offset(x, size.height / 2)
                )
                drawCircle(
                    color = GameColors.WoodLight.copy(alpha = 0.5f),
                    radius = nailRadius * 0.5f,
                    center = Offset(x - 1f, size.height / 2 - 1f)
                )
            }
        }
        
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFont,
            color = GameColors.TextOnWood
        )
    }
}

// Helper function to draw wooden button
private fun DrawScope.drawWoodenButton(
    color: Color,
    pressOffset: Float,
    shadowOffset: Float,
    enabled: Boolean
) {
    val cornerRadius = CornerRadius(12f, 12f)
    val actualColor = if (enabled) color else color.copy(alpha = 0.5f)
    
    // Drop shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.4f),
        topLeft = Offset(2f, shadowOffset),
        size = Size(size.width - 4f, size.height - 4f),
        cornerRadius = cornerRadius
    )
    
    // Button base (darker)
    drawRoundRect(
        color = darkenColor(actualColor, 0.3f),
        topLeft = Offset(0f, pressOffset),
        size = size,
        cornerRadius = cornerRadius
    )
    
    // Button face
    drawRoundRect(
        color = actualColor,
        topLeft = Offset(0f, pressOffset),
        size = Size(size.width, size.height - 4f),
        cornerRadius = cornerRadius
    )
    
    // Top highlight
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.3f),
                Color.Transparent
            ),
            endY = size.height * 0.4f
        ),
        topLeft = Offset(0f, pressOffset),
        size = Size(size.width, size.height - 4f),
        cornerRadius = cornerRadius
    )
    
    // Wood grain lines
    val grainColor = darkenColor(actualColor, 0.15f)
    for (i in 1..3) {
        val y = (size.height * 0.2f * i) + pressOffset
        drawLine(
            color = grainColor,
            start = Offset(size.width * 0.1f, y),
            end = Offset(size.width * 0.9f, y + 2f),
            strokeWidth = 1f
        )
    }
    
    // Border
    drawRoundRect(
        color = darkenColor(actualColor, 0.4f),
        topLeft = Offset(0f, pressOffset),
        size = Size(size.width, size.height - 4f),
        cornerRadius = cornerRadius,
        style = Stroke(width = 2f)
    )
}

private fun darkenColor(color: Color, factor: Float): Color {
    return Color(
        red = (color.red * (1 - factor)).coerceIn(0f, 1f),
        green = (color.green * (1 - factor)).coerceIn(0f, 1f),
        blue = (color.blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}
