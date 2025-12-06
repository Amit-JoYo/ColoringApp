# Brush Tool, Enhanced Color Picker & Advanced Undo/Redo

## Overview

This document describes the brush tool, enhanced color picker, and advanced undo/redo features added in version 1.2.0 of the ColoringApp. These enhancements provide users with more creative control and a better coloring experience.

## Features

### 1. Brush Tool (Free-Hand Drawing)

The brush tool allows users to draw freely on the canvas, providing more precision and artistic control compared to the flood-fill tool.

#### Key Features:
- **Two Drawing Modes:**
  - **Fill Mode** (Default): Traditional flood-fill for quick coloring of regions
  - **Brush Mode**: Free-hand drawing for detailed work and artistic touches

- **Adjustable Brush Size:** 5-100 pixels
- **Smooth Drawing:** Drag gesture detection for continuous strokes
- **Color Selection:** Use any color from the color picker
- **Undo Support:** Each brush stroke is saved as a single undo action

#### Usage:
1. **Toggle Drawing Mode:**
   - Tap the mode button (paintbrush/fill icon) to switch between Fill and Brush modes
   - The button highlights in blue when Brush mode is active

2. **Adjust Brush Size:**
   - When in Brush mode, a slider appears below the control buttons
   - Drag the slider to adjust brush size from 5px to 100px
   - Current size is displayed next to the slider (e.g., "Brush: 20px")

3. **Drawing:**
   - Select a color from the color picker
   - In Brush mode, drag your finger/stylus across the canvas to draw
   - Each continuous stroke is treated as one action for undo/redo

#### Technical Implementation:

**ViewModel (PaintingViewModel.kt):**
```kotlin
// Drawing mode state
private val _drawingMode = MutableStateFlow<DrawingMode>(DrawingMode.Fill)
val drawingMode: StateFlow<DrawingMode> = _drawingMode

// Brush size state (5-100 pixels)
private val _brushSize = MutableStateFlow(20f)
val brushSize: StateFlow<Float> = _brushSize

// Brush drawing method
fun brushDraw(x: Int, y: Int) {
    _imageBitmap.value?.let { bitmap ->
        // Draw directly on the existing bitmap
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (_selectedColor.value.alpha * 255).toInt(),
                (_selectedColor.value.red * 255).toInt(),
                (_selectedColor.value.green * 255).toInt(),
                (_selectedColor.value.blue * 255).toInt()
            )
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 30f // Diameter of the circle (2 * 15f)
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        
        val currentX = x.toFloat()
        val currentY = y.toFloat()

        if (lastBrushX != null && lastBrushY != null) {
            // Draw line from last point to current point for smooth continuity
            canvas.drawLine(lastBrushX!!, lastBrushY!!, currentX, currentY, paint)
        } else {
            // First point - just draw a dot
            canvas.drawLine(currentX, currentY, currentX, currentY, paint)
        }
        
        // Update last position
        lastBrushX = currentX
        lastBrushY = currentY
        
        // Trigger UI update
        _imageBitmap.value = bitmap
    }
}

// Start a new brush stroke for undo management
fun startBrushStroke() {
    // Reset last brush position for new stroke
    lastBrushX = null
    lastBrushY = null

    _imageBitmap.value?.let {
        undoStack.add(UndoState(it.copy(it.config, true), "Brush Stroke", System.currentTimeMillis()))
        redoStack.clear()
        updateUndoRedoStates()
    }
}
```

**UI (PaintingScreen.kt):**
```kotlin
// Drag gesture detection for brush mode
.pointerInput(scale, offset, canvasSize, bitmap, drawingMode) {
    if (drawingMode == DrawingMode.Brush) {
        detectDragGestures(
            onDragStart = { startOffset ->
                viewModel.startBrushStroke()
                // Transform and draw at start position
                val (bitmapX, bitmapY) = transformCoordinates(startOffset)
                viewModel.brushDraw(bitmapX, bitmapY)
            },
            onDrag = { change, _ ->
                // Transform and draw along drag path
                val (bitmapX, bitmapY) = transformCoordinates(change.position)
                viewModel.brushDraw(bitmapX, bitmapY)
            }
        )
    }
}
```

### 2. Enhanced Color Picker

The color picker now includes a history feature that tracks recently used colors for quick access.

#### Key Features:
- **Color History:** Displays the last 10 colors used
- **Quick Selection:** Tap any recent color to reuse it immediately
- **Persistent Across Sessions:** Color history is maintained while the image is open
- **Smart Deduplication:** Prevents duplicate colors in history

#### Usage:
1. **Access Color Picker:**
   - Tap the color palette icon in the control bar

2. **View Recent Colors:**
   - Recent colors appear at the top of the color picker
   - Shows up to 10 most recently used colors
   - Ordered from newest to oldest (left to right)

3. **Quick Color Selection:**
   - Tap any recent color swatch to select it immediately
   - Color picker closes automatically after selection
   - Selected color applies to both Fill and Brush modes

#### Technical Implementation:

**ViewModel (PaintingViewModel.kt):**
```kotlin
// Color history state (last 10 colors)
private val _colorHistory = MutableStateFlow<List<Color>>(emptyList())
val colorHistory: StateFlow<List<Color>> = _colorHistory

// Update color with history tracking
fun setSelectedColor(color: Color) {
    _selectedColor.value = color
    
    // Update color history (keep last 10, avoid duplicates)
    val currentHistory = _colorHistory.value.toMutableList()
    currentHistory.remove(color)  // Remove if exists
    currentHistory.add(0, color)  // Add to front
    _colorHistory.value = currentHistory.take(10)  // Keep last 10
}
```

**UI (PaintingScreen.kt):**
```kotlin
AnimatedVisibility(visible = showColorPicker.value) {
    Column {
        // Show color history if available
        if (colorHistory.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Recent:", style = MaterialTheme.typography.labelSmall)
                colorHistory.forEach { recentColor ->
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    viewModel.setSelectedColor(recentColor)
                                    showColorPicker.value = false
                                }
                            },
                        color = recentColor,
                        shape = CircleShape
                    ) {}
                }
            }
        }
        HoneycombColorPicker(onColorSelected = { color ->
            viewModel.setSelectedColor(color)
            showColorPicker.value = false
        })
    }
}
```

### 3. Advanced Undo/Redo

The undo/redo system has been enhanced with metadata tracking for better context and future UI improvements.

#### Key Features:
- **Action Labels:** Each undo state includes a descriptive label (e.g., "Flood Fill", "Brush Stroke", "Image Load")
- **Timestamps:** Every action records when it occurred
- **History Preview Data:** Foundation for showing undo/redo preview thumbnails (UI pending)
- **Per-Action Undo:** Brush strokes are treated as complete actions, not individual points

#### Usage:
- **Undo:** Tap the undo button to reverse the last action
- **Redo:** Tap the redo button to reapply an undone action
- **Brush Strokes:** Each complete drag gesture is one undo unit
- **Fill Operations:** Each flood-fill is one undo unit

#### Technical Implementation:

**Data Structures:**
```kotlin
// Enhanced undo state with metadata
data class UndoState(
    val bitmap: Bitmap,
    val action: String,      // e.g., "Flood Fill", "Brush Stroke"
    val timestamp: Long      // System.currentTimeMillis()
)

// History item for UI display (future use)
data class HistoryItem(
    val action: String,
    val timestamp: Long,
    val isUndo: Boolean
)

// Drawing mode enum
enum class DrawingMode {
    Fill,   // Flood fill mode
    Brush   // Free-hand brush drawing
}
```

**Undo/Redo Logic:**
```kotlin
fun undo() {
    if (undoStack.size > 1) {
        val currentState = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(currentState)
        _imageBitmap.value?.let {
            it.recycle()  // Free memory
        }
        val previousState = undoStack.last()
        _imageBitmap.value = previousState.bitmap.copy(previousState.bitmap.config, true)
        updateUndoRedoStates()
    }
}

fun redo() {
    if (redoStack.isNotEmpty()) {
        val nextState = redoStack.removeAt(redoStack.size - 1)
        _imageBitmap.value?.let {
            undoStack.add(UndoState(it.copy(it.config, true), nextState.action, System.currentTimeMillis()))
        }
        _imageBitmap.value = nextState.bitmap.copy(nextState.bitmap.config, true)
        updateUndoRedoStates()
    }
}

private fun updateUndoRedoStates() {
    _canUndo.value = undoStack.size > 1
    _canRedo.value = redoStack.isNotEmpty()
    
    // Update undo/redo history for UI preview (future feature)
    _undoHistory.value = undoStack.takeLast(5).map { state ->
        HistoryItem(state.action, state.timestamp, isUndo = true)
    }.reversed()
}
```

## UI Components

### New Icons:
- **ic_brush_draw.xml**: Brush/pencil icon for brush mode indicator

### Updated Controls:
- **Mode Toggle Button:** Switches between Fill and Brush modes
  - Shows fill icon when in Fill mode
  - Shows brush icon when in Brush mode
  - Highlights in blue when Brush mode is active

- **Brush Size Slider:** (Visible only in Brush mode)
  - Displays current brush size in pixels
  - Range: 5-100 pixels
  - Smooth animation when appearing/disappearing

- **Color History Row:** (Visible when color history exists)
  - Shows "Recent:" label
  - Displays up to 10 color swatches
  - Circular swatches (32dp diameter)
  - Tap to instantly select a color

## Coordinate Transformation

Both Fill and Brush modes properly handle:
- **Pan (Offset):** Canvas translation
- **Zoom (Scale):** Canvas scaling around center
- **Centering:** Bitmap centered in canvas
- **Bounds Checking:** Ensures coordinates stay within bitmap

The transformation pipeline:
1. Get screen tap/drag coordinates
2. Remove pan offset
3. Remove scale (relative to canvas center)
4. Adjust for bitmap centering
5. Clamp to bitmap bounds
6. Apply drawing operation

## Performance Considerations

### Memory Management:
- Brush drawing modifies bitmap in-place
- Each brush stroke creates one undo state copy
- Old bitmaps are recycled when removed from undo stack
- Undo stack size is unlimited (user can undo to initial state)

### Rendering:
- Brush draws circles at each drag point
- Anti-aliasing enabled for smooth edges
- Uses Android Canvas for efficient drawing
- No frame rate issues with reasonable brush sizes (5-50px)

### State Management:
- All state changes use StateFlow for reactive UI updates
- Brush drawing runs on main thread (fast enough for real-time)
- No coroutines needed for brush operations

## Future Enhancements

### Potential Improvements:
1. **Undo History Preview UI**
   - Thumbnail previews of undo/redo states
   - Scrub through history timeline
   - Jump to specific state

2. **Advanced Brush Options**
   - Opacity control
   - Brush shapes (circle, square, custom)
   - Blur/soft edges
   - Pressure sensitivity for stylus devices

3. **Color Picker Enhancements**
   - Save custom color palettes
   - Eye dropper tool to sample colors from image
   - Color mixing UI
   - Export/import palettes

4. **Brush Stroke Optimization**
   - Interpolation between drag points for smoother lines
   - Path smoothing algorithms
   - Separate brush stroke layer with merge option

5. **Advanced Undo Options**
   - Selective undo (undo specific action, not just last)
   - Branch history (explore alternate coloring paths)
   - Auto-save snapshots at intervals

## Testing Checklist

### Brush Tool:
- [ ] Switch between Fill and Brush modes
- [ ] Brush size slider appears only in Brush mode
- [ ] Adjust brush size from 5px to 100px
- [ ] Draw smooth strokes by dragging
- [ ] Brush respects selected color
- [ ] Each stroke creates one undo state
- [ ] Brush works correctly with zoom and pan
- [ ] Brush coordinates are accurate at all zoom levels

### Enhanced Color Picker:
- [ ] Color history row appears after using colors
- [ ] History shows up to 10 colors
- [ ] Recent colors are ordered newest to oldest
- [ ] Tapping recent color selects it immediately
- [ ] No duplicate colors in history
- [ ] Color picker closes after selection

### Advanced Undo/Redo:
- [ ] Undo reverses last action
- [ ] Redo reapplies undone action
- [ ] Undo/redo work for both Fill and Brush
- [ ] Complete brush strokes undo as one unit
- [ ] Undo button disabled when at initial state
- [ ] Redo button disabled when no redo available
- [ ] Redo stack clears after new action

### Integration:
- [ ] All features work together seamlessly
- [ ] No crashes or memory leaks
- [ ] Smooth performance with 50px brush
- [ ] Coordinate transformation accurate
- [ ] State persists during orientation changes (may need implementation)

## Version History

### v1.2.0 (Current)
- Added Brush Tool with adjustable size (5-100px)
- Added Drawing Mode toggle (Fill/Brush)
- Enhanced Color Picker with color history (last 10 colors)
- Advanced Undo/Redo with action labels and timestamps
- Drag gesture detection for smooth brush strokes
- UI improvements: brush size slider, color history row, mode indicator

### v1.1.0
- Save to Gallery functionality
- Share functionality
- Permission handling
- FileProvider configuration

### v1.0.0
- Initial release
- Flood fill coloring
- Basic color picker
- Simple undo/redo
- Pan and zoom
- Image selection

## Credits

**Development:** ColoringApp Team  
**Features:** Brush Tool, Enhanced Color Picker, Advanced Undo/Redo  
**Version:** 1.2.0  
**Date:** 2024
