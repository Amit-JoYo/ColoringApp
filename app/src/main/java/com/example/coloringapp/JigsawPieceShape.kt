package com.example.coloringapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import kotlin.random.Random

/**
 * Enum representing the type of edge for a jigsaw piece.
 */
enum class EdgeType {
    FLAT,   // Edge of the puzzle (no tab or slot)
    TAB,    // Protrudes outward
    SLOT    // Goes inward
}

/**
 * Data class representing the edge configuration for a jigsaw piece.
 */
data class PieceEdges(
    val top: EdgeType,
    val right: EdgeType,
    val bottom: EdgeType,
    val left: EdgeType
)

/**
 * Generates the edge pattern for the entire puzzle grid.
 * Rules:
 * - FLAT edges ONLY on puzzle borders (first/last row/column)
 * - Interior edges are always TAB or SLOT
 * - Adjacent pieces must have matching TAB↔SLOT pairs
 * 
 * @param numRows Number of rows in the grid
 * @param numCols Number of columns in the grid
 */
fun generatePuzzleEdgePattern(numRows: Int, numCols: Int, seed: Long = System.currentTimeMillis()): Array<Array<PieceEdges>> {
    val random = Random(seed)
    val pattern = Array(numRows) { Array(numCols) { PieceEdges(EdgeType.FLAT, EdgeType.FLAT, EdgeType.FLAT, EdgeType.FLAT) } }
    
    // For each piece, determine its edges
    for (row in 0 until numRows) {
        for (col in 0 until numCols) {
            // Top edge: FLAT only if first row, otherwise match piece above
            val top = when {
                row == 0 -> EdgeType.FLAT  // Top border of puzzle
                else -> {
                    // Must be opposite of piece above's bottom edge
                    when (pattern[row - 1][col].bottom) {
                        EdgeType.TAB -> EdgeType.SLOT
                        EdgeType.SLOT -> EdgeType.TAB
                        EdgeType.FLAT -> EdgeType.FLAT // Should never happen for interior
                    }
                }
            }
            
            // Left edge: FLAT only if first column, otherwise match piece to left
            val left = when {
                col == 0 -> EdgeType.FLAT  // Left border of puzzle
                else -> {
                    // Must be opposite of left piece's right edge
                    when (pattern[row][col - 1].right) {
                        EdgeType.TAB -> EdgeType.SLOT
                        EdgeType.SLOT -> EdgeType.TAB
                        EdgeType.FLAT -> EdgeType.FLAT // Should never happen for interior
                    }
                }
            }
            
            // Right edge: FLAT only if last column, otherwise random TAB or SLOT
            val right = when {
                col == numCols - 1 -> EdgeType.FLAT  // Right border of puzzle
                else -> if (random.nextBoolean()) EdgeType.TAB else EdgeType.SLOT
            }
            
            // Bottom edge: FLAT only if last row, otherwise random TAB or SLOT
            val bottom = when {
                row == numRows - 1 -> EdgeType.FLAT  // Bottom border of puzzle
                else -> if (random.nextBoolean()) EdgeType.TAB else EdgeType.SLOT
            }
            
            pattern[row][col] = PieceEdges(top, right, bottom, left)
        }
    }
    
    // Validate and log the pattern
    for (row in 0 until numRows) {
        for (col in 0 until numCols) {
            val edges = pattern[row][col]
            val isTopBorder = row == 0
            val isBottomBorder = row == numRows - 1
            val isLeftBorder = col == 0
            val isRightBorder = col == numCols - 1
            
            // Log any invalid pieces (interior pieces with FLAT edges)
            if (!isTopBorder && edges.top == EdgeType.FLAT) {
                Log.e("JigsawPuzzle", "Invalid: Piece[$row][$col] has FLAT top but is not on top border")
            }
            if (!isBottomBorder && edges.bottom == EdgeType.FLAT) {
                Log.e("JigsawPuzzle", "Invalid: Piece[$row][$col] has FLAT bottom but is not on bottom border")
            }
            if (!isLeftBorder && edges.left == EdgeType.FLAT) {
                Log.e("JigsawPuzzle", "Invalid: Piece[$row][$col] has FLAT left but is not on left border")
            }
            if (!isRightBorder && edges.right == EdgeType.FLAT) {
                Log.e("JigsawPuzzle", "Invalid: Piece[$row][$col] has FLAT right but is not on right border")
            }
            
            Log.d("JigsawPuzzle", "Piece[$row][$col]: T=${edges.top} R=${edges.right} B=${edges.bottom} L=${edges.left}")
        }
    }
    
    return pattern
}

/**
 * Creates a jigsaw-shaped bitmap from a source region with the specified edges.
 * The tab size is proportional to the piece size.
 *
 * @param sourceBitmap The full source image
 * @param pieceX The x coordinate of the piece in the grid (column index)
 * @param pieceY The y coordinate of the piece in the grid (row index)
 * @param pieceWidth The base width of each piece in pixels (sourceBitmap.width / numCols)
 * @param pieceHeight The base height of each piece in pixels (sourceBitmap.height / numRows)
 * @param edges The edge configuration for this piece
 * @return A bitmap containing just this piece with jigsaw edges, with transparency for areas outside the piece
 */
fun createJigsawPieceBitmap(
    sourceBitmap: Bitmap,
    pieceX: Int,
    pieceY: Int,
    pieceWidth: Int,
    pieceHeight: Int,
    edges: PieceEdges
): Bitmap {
    // Tab size - 15% of smaller dimension
    val tabSize = (minOf(pieceWidth, pieceHeight) * 0.15f).toInt()
    
    // Calculate expanded dimensions to include tabs
    // Add tabSize on each side that has a TAB edge
    val expandLeft = if (edges.left == EdgeType.TAB) tabSize else 0
    val expandRight = if (edges.right == EdgeType.TAB) tabSize else 0
    val expandTop = if (edges.top == EdgeType.TAB) tabSize else 0
    val expandBottom = if (edges.bottom == EdgeType.TAB) tabSize else 0
    
    val totalWidth = pieceWidth + expandLeft + expandRight
    val totalHeight = pieceHeight + expandTop + expandBottom
    
    // Calculate the base position of this piece in the source bitmap
    // Adjust source position to include tab areas from neighboring pieces
    val baseSrcX = pieceX * pieceWidth - expandLeft
    val baseSrcY = pieceY * pieceHeight - expandTop
    
    // Calculate how much we can actually read from source (clamp to source bounds)
    val srcLeft = maxOf(0, baseSrcX)
    val srcTop = maxOf(0, baseSrcY)
    val srcRight = minOf(sourceBitmap.width, baseSrcX + totalWidth)
    val srcBottom = minOf(sourceBitmap.height, baseSrcY + totalHeight)
    
    val srcWidth = srcRight - srcLeft
    val srcHeight = srcBottom - srcTop
    
    if (srcWidth <= 0 || srcHeight <= 0) {
        val errorBitmap = Bitmap.createBitmap(pieceWidth, pieceHeight, Bitmap.Config.ARGB_8888)
        Canvas(errorBitmap).drawColor(android.graphics.Color.RED)
        return errorBitmap
    }
    
    // Create result bitmap with expanded size
    val resultBitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultBitmap)
    
    // Calculate where to draw the source in the result bitmap
    val dstLeft = srcLeft - baseSrcX
    val dstTop = srcTop - baseSrcY
    
    // Draw the source region onto result
    val srcRect = android.graphics.Rect(srcLeft, srcTop, srcRight, srcBottom)
    val dstRect = android.graphics.Rect(dstLeft, dstTop, dstLeft + srcWidth, dstTop + srcHeight)
    canvas.drawBitmap(sourceBitmap, srcRect, dstRect, null)
    
    // Create the jigsaw path with tabs and slots
    // The path is relative to the expanded bitmap
    val path = createJigsawPathWithTabsAndSlots(
        totalWidth.toFloat(),
        totalHeight.toFloat(),
        tabSize.toFloat(),
        edges,
        expandLeft.toFloat(),
        expandTop.toFloat()
    )
    
    // Create mask bitmap with same size
    val maskBitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val maskCanvas = Canvas(maskBitmap)
    maskCanvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = android.graphics.Color.WHITE 
    })
    
    // Apply mask using DST_IN
    canvas.drawBitmap(maskBitmap, 0f, 0f, Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    })
    maskBitmap.recycle()
    
    return resultBitmap
}

/**
 * Creates a jigsaw path that includes both TAB protrusions and SLOT cutouts.
 * Uses quadratic bezier curves for smooth, reliable shapes.
 */
private fun createJigsawPathWithTabsAndSlots(
    totalWidth: Float,
    totalHeight: Float,
    tabSize: Float,
    edges: PieceEdges,
    expandLeft: Float,
    expandTop: Float
): Path {
    val path = Path()
    val tabDepth = tabSize  // How far tab extends / slot cuts
    val tabWidth = tabSize  // Width of the tab/slot opening
    
    // Calculate the base rectangle boundaries
    val left = expandLeft
    val top = expandTop
    val right = if (edges.right == EdgeType.TAB) totalWidth - tabSize else totalWidth
    val bottom = if (edges.bottom == EdgeType.TAB) totalHeight - tabSize else totalHeight
    val baseWidth = right - left
    val baseHeight = bottom - top
    
    // Start at top-left of base rectangle
    path.moveTo(left, top)
    
    // TOP EDGE (going left to right)
    val topCenterX = left + baseWidth / 2
    when (edges.top) {
        EdgeType.TAB -> {
            // Tab bulges upward
            path.lineTo(topCenterX - tabWidth/2, top)
            path.quadTo(topCenterX - tabWidth/2, top - tabDepth, topCenterX, top - tabDepth)
            path.quadTo(topCenterX + tabWidth/2, top - tabDepth, topCenterX + tabWidth/2, top)
        }
        EdgeType.SLOT -> {
            // Slot cuts downward into piece
            path.lineTo(topCenterX - tabWidth/2, top)
            path.quadTo(topCenterX - tabWidth/2, top + tabDepth, topCenterX, top + tabDepth)
            path.quadTo(topCenterX + tabWidth/2, top + tabDepth, topCenterX + tabWidth/2, top)
        }
        EdgeType.FLAT -> { /* just continue to corner */ }
    }
    path.lineTo(right, top)
    
    // RIGHT EDGE (going top to bottom)
    val rightCenterY = top + baseHeight / 2
    when (edges.right) {
        EdgeType.TAB -> {
            // Tab bulges rightward
            path.lineTo(right, rightCenterY - tabWidth/2)
            path.quadTo(right + tabDepth, rightCenterY - tabWidth/2, right + tabDepth, rightCenterY)
            path.quadTo(right + tabDepth, rightCenterY + tabWidth/2, right, rightCenterY + tabWidth/2)
        }
        EdgeType.SLOT -> {
            // Slot cuts leftward into piece
            path.lineTo(right, rightCenterY - tabWidth/2)
            path.quadTo(right - tabDepth, rightCenterY - tabWidth/2, right - tabDepth, rightCenterY)
            path.quadTo(right - tabDepth, rightCenterY + tabWidth/2, right, rightCenterY + tabWidth/2)
        }
        EdgeType.FLAT -> { /* just continue to corner */ }
    }
    path.lineTo(right, bottom)
    
    // BOTTOM EDGE (going right to left)
    val bottomCenterX = left + baseWidth / 2
    when (edges.bottom) {
        EdgeType.TAB -> {
            // Tab bulges downward
            path.lineTo(bottomCenterX + tabWidth/2, bottom)
            path.quadTo(bottomCenterX + tabWidth/2, bottom + tabDepth, bottomCenterX, bottom + tabDepth)
            path.quadTo(bottomCenterX - tabWidth/2, bottom + tabDepth, bottomCenterX - tabWidth/2, bottom)
        }
        EdgeType.SLOT -> {
            // Slot cuts upward into piece
            path.lineTo(bottomCenterX + tabWidth/2, bottom)
            path.quadTo(bottomCenterX + tabWidth/2, bottom - tabDepth, bottomCenterX, bottom - tabDepth)
            path.quadTo(bottomCenterX - tabWidth/2, bottom - tabDepth, bottomCenterX - tabWidth/2, bottom)
        }
        EdgeType.FLAT -> { /* just continue to corner */ }
    }
    path.lineTo(left, bottom)
    
    // LEFT EDGE (going bottom to top)
    val leftCenterY = top + baseHeight / 2
    when (edges.left) {
        EdgeType.TAB -> {
            // Tab bulges leftward
            path.lineTo(left, leftCenterY + tabWidth/2)
            path.quadTo(left - tabDepth, leftCenterY + tabWidth/2, left - tabDepth, leftCenterY)
            path.quadTo(left - tabDepth, leftCenterY - tabWidth/2, left, leftCenterY - tabWidth/2)
        }
        EdgeType.SLOT -> {
            // Slot cuts rightward into piece
            path.lineTo(left, leftCenterY + tabWidth/2)
            path.quadTo(left + tabDepth, leftCenterY + tabWidth/2, left + tabDepth, leftCenterY)
            path.quadTo(left + tabDepth, leftCenterY - tabWidth/2, left, leftCenterY - tabWidth/2)
        }
        EdgeType.FLAT -> { /* just continue to corner */ }
    }
    path.lineTo(left, top)
    
    path.close()
    return path
}

/**
 * Creates a path with only SLOT cutouts - pieces stay same size, just have notches cut in
 */
private fun createSlotOnlyPath(
    width: Float,
    height: Float,
    tabSize: Float,
    edges: PieceEdges
): Path {
    val path = Path()
    val neckWidth = tabSize * 0.5f  // Width of the connector
    val bulgeSize = tabSize * 0.7f  // How far the slot goes into the piece
    
    // Start at top-left corner
    path.moveTo(0f, 0f)
    
    // TOP EDGE (going left to right)
    if (edges.top == EdgeType.SLOT) {
        val centerX = width / 2
        path.lineTo(centerX - neckWidth, 0f)
        // Draw slot going DOWN into the piece
        path.lineTo(centerX - neckWidth, bulgeSize * 0.3f)
        path.arcTo(
            centerX - neckWidth - bulgeSize/2, bulgeSize * 0.3f,
            centerX + neckWidth + bulgeSize/2, bulgeSize * 0.3f + bulgeSize,
            180f, -180f, false
        )
        path.lineTo(centerX + neckWidth, 0f)
    }
    path.lineTo(width, 0f)
    
    // RIGHT EDGE (going top to bottom)
    if (edges.right == EdgeType.SLOT) {
        val centerY = height / 2
        path.lineTo(width, centerY - neckWidth)
        // Draw slot going LEFT into the piece
        path.lineTo(width - bulgeSize * 0.3f, centerY - neckWidth)
        path.arcTo(
            width - bulgeSize * 0.3f - bulgeSize, centerY - neckWidth - bulgeSize/2,
            width - bulgeSize * 0.3f, centerY + neckWidth + bulgeSize/2,
            270f, -180f, false
        )
        path.lineTo(width, centerY + neckWidth)
    }
    path.lineTo(width, height)
    
    // BOTTOM EDGE (going right to left)
    if (edges.bottom == EdgeType.SLOT) {
        val centerX = width / 2
        path.lineTo(centerX + neckWidth, height)
        // Draw slot going UP into the piece
        path.lineTo(centerX + neckWidth, height - bulgeSize * 0.3f)
        path.arcTo(
            centerX - neckWidth - bulgeSize/2, height - bulgeSize * 0.3f - bulgeSize,
            centerX + neckWidth + bulgeSize/2, height - bulgeSize * 0.3f,
            0f, -180f, false
        )
        path.lineTo(centerX - neckWidth, height)
    }
    path.lineTo(0f, height)
    
    // LEFT EDGE (going bottom to top)
    if (edges.left == EdgeType.SLOT) {
        val centerY = height / 2
        path.lineTo(0f, centerY + neckWidth)
        // Draw slot going RIGHT into the piece
        path.lineTo(bulgeSize * 0.3f, centerY + neckWidth)
        path.arcTo(
            bulgeSize * 0.3f, centerY - neckWidth - bulgeSize/2,
            bulgeSize * 0.3f + bulgeSize, centerY + neckWidth + bulgeSize/2,
            90f, -180f, false
        )
        path.lineTo(0f, centerY - neckWidth)
    }
    path.lineTo(0f, 0f)
    
    path.close()
    return path
}

/**
 * Creates a simple jigsaw path where:
 * - FLAT edges are straight lines
 * - SLOT edges have a semicircle cut INTO the piece
 * - TAB edges are straight (tabs will be visual only, neighbor's slot receives them)
 */
private fun createSimpleJigsawPath(
    width: Float,
    height: Float,
    tabSize: Float,
    edges: PieceEdges
): Path {
    val path = Path()
    val halfTab = tabSize / 2
    
    // Start at top-left
    path.moveTo(0f, 0f)
    
    // Top edge (left to right)
    if (edges.top == EdgeType.SLOT) {
        // Slot cuts into the piece (semicircle going down into piece)
        path.lineTo(width / 2 - halfTab, 0f)
        path.arcTo(
            width / 2 - halfTab, 0f,
            width / 2 + halfTab, tabSize,
            180f, 180f, false
        )
        path.lineTo(width, 0f)
    } else {
        // FLAT or TAB - straight line
        path.lineTo(width, 0f)
    }
    
    // Right edge (top to bottom)
    if (edges.right == EdgeType.SLOT) {
        path.lineTo(width, height / 2 - halfTab)
        path.arcTo(
            width - tabSize, height / 2 - halfTab,
            width, height / 2 + halfTab,
            270f, 180f, false
        )
        path.lineTo(width, height)
    } else {
        path.lineTo(width, height)
    }
    
    // Bottom edge (right to left)
    if (edges.bottom == EdgeType.SLOT) {
        path.lineTo(width / 2 + halfTab, height)
        path.arcTo(
            width / 2 - halfTab, height - tabSize,
            width / 2 + halfTab, height,
            0f, 180f, false
        )
        path.lineTo(0f, height)
    } else {
        path.lineTo(0f, height)
    }
    
    // Left edge (bottom to top)
    if (edges.left == EdgeType.SLOT) {
        path.lineTo(0f, height / 2 + halfTab)
        path.arcTo(
            0f, height / 2 - halfTab,
            tabSize, height / 2 + halfTab,
            90f, 180f, false
        )
        path.lineTo(0f, 0f)
    } else {
        path.lineTo(0f, 0f)
    }
    
    path.close()
    return path
}

/**
 * Creates a Path representing the jigsaw piece shape.
 */
private fun createJigsawPath(
    pieceWidth: Float,
    pieceHeight: Float,
    tabSize: Float,
    edges: PieceEdges,
    offsetX: Float,
    offsetY: Float
): Path {
    val path = Path()
    
    // Start at top-left corner
    path.moveTo(offsetX, offsetY)
    
    // Top edge
    drawEdge(path, edges.top, offsetX, offsetY, pieceWidth, true, tabSize)
    
    // Right edge (going down)
    drawEdge(path, edges.right, offsetX + pieceWidth, offsetY, pieceHeight, false, tabSize)
    
    // Bottom edge (going left)
    drawEdgeReverse(path, edges.bottom, offsetX + pieceWidth, offsetY + pieceHeight, pieceWidth, true, tabSize)
    
    // Left edge (going up)
    drawEdgeReverse(path, edges.left, offsetX, offsetY + pieceHeight, pieceHeight, false, tabSize)
    
    path.close()
    return path
}

/**
 * Draws an edge of the jigsaw piece with optional tab or slot.
 */
private fun drawEdge(
    path: Path,
    edgeType: EdgeType,
    startX: Float,
    startY: Float,
    length: Float,
    isHorizontal: Boolean,
    tabSize: Float
) {
    val tabWidth = length * 0.3f  // Tab takes up 30% of the edge
    val tabStart = (length - tabWidth) / 2
    val tabEnd = tabStart + tabWidth
    
    when (edgeType) {
        EdgeType.FLAT -> {
            if (isHorizontal) {
                path.lineTo(startX + length, startY)
            } else {
                path.lineTo(startX, startY + length)
            }
        }
        EdgeType.TAB, EdgeType.SLOT -> {
            // TAB protrudes outward from the piece, SLOT indents inward
            // For top edge: TAB goes UP (negative Y), SLOT goes DOWN (positive Y)
            // For right edge: TAB goes RIGHT (positive X), SLOT goes LEFT (negative X)
            val outwardSize = tabSize * (if (edgeType == EdgeType.TAB) 1f else -1f)
            
            if (isHorizontal) {
                // Top edge: outward = negative Y (up), inward = positive Y (down)
                val curveY = -outwardSize
                
                // Draw to start of tab/slot
                path.lineTo(startX + tabStart, startY)
                // Draw tab/slot using bezier curves for smooth shape
                path.cubicTo(
                    startX + tabStart, startY + curveY * 0.5f,
                    startX + tabStart + tabWidth * 0.2f, startY + curveY,
                    startX + tabStart + tabWidth * 0.5f, startY + curveY
                )
                path.cubicTo(
                    startX + tabStart + tabWidth * 0.8f, startY + curveY,
                    startX + tabEnd, startY + curveY * 0.5f,
                    startX + tabEnd, startY
                )
                // Continue to end
                path.lineTo(startX + length, startY)
            } else {
                // Right edge (going down): outward = positive X (right), inward = negative X (left)
                val curveX = outwardSize
                
                // Vertical edge
                path.lineTo(startX, startY + tabStart)
                path.cubicTo(
                    startX + curveX * 0.5f, startY + tabStart,
                    startX + curveX, startY + tabStart + tabWidth * 0.2f,
                    startX + curveX, startY + tabStart + tabWidth * 0.5f
                )
                path.cubicTo(
                    startX + curveX, startY + tabStart + tabWidth * 0.8f,
                    startX + curveX * 0.5f, startY + tabEnd,
                    startX, startY + tabEnd
                )
                path.lineTo(startX, startY + length)
            }
        }
    }
}

/**
 * Draws an edge in reverse direction (for bottom and left edges).
 */
private fun drawEdgeReverse(
    path: Path,
    edgeType: EdgeType,
    startX: Float,
    startY: Float,
    length: Float,
    isHorizontal: Boolean,
    tabSize: Float
) {
    val tabWidth = length * 0.3f
    val tabStart = (length - tabWidth) / 2
    val tabEnd = tabStart + tabWidth
    
    when (edgeType) {
        EdgeType.FLAT -> {
            if (isHorizontal) {
                path.lineTo(startX - length, startY)
            } else {
                path.lineTo(startX, startY - length)
            }
        }
        EdgeType.TAB, EdgeType.SLOT -> {
            // TAB protrudes outward from the piece, SLOT indents inward
            // For bottom edge: TAB goes DOWN (positive Y), SLOT goes UP (negative Y)
            // For left edge: TAB goes LEFT (negative X), SLOT goes RIGHT (positive X)
            val outwardSize = tabSize * (if (edgeType == EdgeType.TAB) 1f else -1f)
            
            if (isHorizontal) {
                // Bottom edge (going right to left): outward = positive Y (down)
                val curveY = outwardSize
                
                path.lineTo(startX - tabStart, startY)
                path.cubicTo(
                    startX - tabStart, startY + curveY * 0.5f,
                    startX - tabStart - tabWidth * 0.2f, startY + curveY,
                    startX - tabStart - tabWidth * 0.5f, startY + curveY
                )
                path.cubicTo(
                    startX - tabStart - tabWidth * 0.8f, startY + curveY,
                    startX - tabEnd, startY + curveY * 0.5f,
                    startX - tabEnd, startY
                )
                path.lineTo(startX - length, startY)
            } else {
                // Left edge (going bottom to top): outward = negative X (left)
                val curveX = -outwardSize
                
                path.lineTo(startX, startY - tabStart)
                path.cubicTo(
                    startX + curveX * 0.5f, startY - tabStart,
                    startX + curveX, startY - tabStart - tabWidth * 0.2f,
                    startX + curveX, startY - tabStart - tabWidth * 0.5f
                )
                path.cubicTo(
                    startX + curveX, startY - tabStart - tabWidth * 0.8f,
                    startX + curveX * 0.5f, startY - tabEnd,
                    startX, startY - tabEnd
                )
                path.lineTo(startX, startY - length)
            }
        }
    }
}
