# ColoringApp API Documentation

## Table of Contents
- [Overview](#overview)
- [Core Classes](#core-classes)
- [UI Components](#ui-components)
- [Image Processing](#image-processing)
- [Utilities](#utilities)
- [Data Models](#data-models)
- [Constants](#constants)
- [Extension Functions](#extension-functions)

## Overview

This document provides comprehensive API documentation for the ColoringApp. The API is organized around the MVVM architecture pattern with clear separation between UI components, business logic, and data processing.

## Core Classes

### PaintingViewModel

The main state management class that handles all painting-related operations and state.

#### Properties

```kotlin
class PaintingViewModel : ViewModel() {
    
    // Current image being displayed and colored
    val imageBitmap: StateFlow<Bitmap?>
    
    // Unique identifier for the current image session
    val imageSessionId: StateFlow<Int>
    
    // Loading state for image processing operations
    val isLoading: StateFlow<Boolean>
    
    // Currently selected color for painting
    val selectedColor: StateFlow<Color>
    
    // Whether undo operation is available
    val canUndo: StateFlow<Boolean>
    
    // Whether redo operation is available
    val canRedo: StateFlow<Boolean>
    
    // List of pre-loaded coloring page resource IDs
    val initialImages: List<Int>
}
```

#### Methods

```kotlin
/**
 * Sets a new image bitmap after processing it for coloring.
 * 
 * @param bitmap The bitmap image to process and set
 * 
 * This method:
 * 1. Processes the image using K-means segmentation
 * 2. Clears undo/redo history
 * 3. Increments the image session ID
 * 4. Updates loading states
 */
fun setImageBitmap(bitmap: Bitmap)

/**
 * Sets an image from a drawable resource.
 * 
 * @param context Android context for resource access
 * @param drawableId Resource ID of the drawable
 */
fun setImageBitmapFromDrawable(context: Context, drawableId: Int)

/**
 * Updates the currently selected color for painting.
 * 
 * @param color The new color to select
 */
fun setSelectedColor(color: Color)

/**
 * Initiates a flood fill operation at the specified coordinates.
 * 
 * @param x X coordinate on the bitmap
 * @param y Y coordinate on the bitmap
 * 
 * The operation is performed asynchronously and updates the undo stack.
 */
fun startFloodFill(x: Int, y: Int)

/**
 * Undoes the last painting operation.
 * 
 * Returns the bitmap to its previous state if undo is available.
 */
fun undo()

/**
 * Redoes the last undone operation.
 * 
 * Restores a previously undone state if redo is available.
 */
fun redo()

/**
 * Clears the current image and returns to selection screen.
 * 
 * Resets all state including undo/redo history.
 */
fun clearImage()
```

#### Usage Example

```kotlin
@Composable
fun ExampleScreen() {
    val viewModel: PaintingViewModel = viewModel()
    val imageBitmap by viewModel.imageBitmap.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    
    // Load an image
    LaunchedEffect(Unit) {
        val bitmap = loadBitmapFromAssets()
        viewModel.setImageBitmap(bitmap)
    }
    
    // Handle color selection
    ColorPicker(
        selectedColor = selectedColor,
        onColorSelected = { color ->
            viewModel.setSelectedColor(color)
        }
    )
}
```

## UI Components

### PaintingScreen

Main composable for the painting interface.

```kotlin
/**
 * The main painting screen that displays the canvas and controls.
 * 
 * @param viewModel The painting view model for state management
 */
@Composable
fun PaintingScreen(viewModel: PaintingViewModel = viewModel())
```

#### Features
- Automatic switching between image selection and painting modes
- Integrated loading states
- Gallery picker integration
- Responsive layout adaptation

### PaintingCanvas

The interactive canvas component for painting operations.

```kotlin
/**
 * Interactive canvas for displaying and coloring images.
 * 
 * @param bitmap The bitmap image to display
 * @param imageSessionId Unique session identifier for the image
 * @param viewModel View model for state management
 * @param scale Current zoom scale of the canvas
 * @param offset Current pan offset of the canvas
 * @param onScaleChange Callback for scale changes
 * @param onOffsetChange Callback for offset changes
 */
@Composable
fun PaintingCanvas(
    bitmap: Bitmap,
    imageSessionId: Int,
    viewModel: PaintingViewModel,
    scale: Float,
    offset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit
)
```

#### Gesture Handling
- **Pinch-to-zoom**: Smooth scaling with center-based transformation
- **Pan**: Drag to move the image around the canvas
- **Tap**: Flood fill at tap coordinates with coordinate transformation

### PaintingControls

Control panel for painting operations.

```kotlin
/**
 * Control buttons for painting operations.
 * 
 * @param viewModel View model for operations
 * @param onShowColorPicker Callback to show/hide color picker
 * @param onFitToScreen Callback to fit image to screen
 * @param canUndo Whether undo is available
 * @param canRedo Whether redo is available
 */
@Composable
fun PaintingControls(
    viewModel: PaintingViewModel,
    onShowColorPicker: () -> Unit,
    onFitToScreen: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean
)
```

### ImageSelectionScreen

Screen for selecting images to color.

```kotlin
/**
 * Image selection interface with grid layout.
 * 
 * @param viewModel View model for image operations
 * @param onImageSelected Callback when image is selected
 */
@Composable
fun ImageSelectionScreen(
    viewModel: PaintingViewModel = viewModel(), 
    onImageSelected: () -> Unit
)
```

### HoneycombColorPicker

Specialized color picker with honeycomb layout.

```kotlin
/**
 * Honeycomb-style color picker interface.
 * 
 * @param onColorSelected Callback when a color is selected
 */
@Composable
fun HoneycombColorPicker(onColorSelected: (Color) -> Unit)
```

#### Color Palette
- 24 predefined colors in honeycomb arrangement
- High contrast for accessibility
- Visual feedback on selection

## Image Processing

### segmentImageByColor

Main image processing function using K-means clustering.

```kotlin
/**
 * Processes an image for coloring using K-means color segmentation.
 * 
 * @param bitmap Input bitmap to process
 * @return Processed bitmap with distinct color regions
 * 
 * Algorithm:
 * 1. Checks if image is already grayscale
 * 2. Applies K-means clustering (k=16) for color segmentation
 * 3. Creates distinct color regions suitable for flood fill
 * 4. Returns bitmap in ARGB_8888 format
 * 
 * @throws IllegalArgumentException if bitmap is null or invalid
 */
fun segmentImageByColor(bitmap: Bitmap): Bitmap
```

#### Processing Steps
1. **Grayscale Detection**: Analyzes HSV saturation to detect B&W images
2. **K-means Clustering**: Groups similar colors into 16 clusters
3. **Region Creation**: Assigns each pixel to its cluster center color
4. **Format Conversion**: Ensures ARGB_8888 output format

#### Performance Characteristics
- **Time Complexity**: O(n * k * iterations) where n = pixels, k = clusters
- **Memory Usage**: ~3x input bitmap size during processing
- **Typical Processing Time**: 1-3 seconds for 1080p images

### isGrayscale

Helper function to detect grayscale images.

```kotlin
/**
 * Determines if a bitmap is grayscale by analyzing color saturation.
 * 
 * @param bitmap Bitmap to analyze
 * @return true if image is grayscale, false otherwise
 * 
 * Uses HSV color space analysis with saturation threshold of 15.0
 */
private fun isGrayscale(bitmap: Bitmap): Boolean
```

## Utilities

### FloodFill

Advanced flood fill algorithm with color tolerance.

```kotlin
/**
 * Performs flood fill operation with color tolerance.
 * 
 * @param bitmap Source bitmap to modify
 * @param x X coordinate of fill start point
 * @param y Y coordinate of fill start point
 * @param newColor Color to fill with
 * @param tolerance Color matching tolerance (0-255)
 * @return New bitmap with filled region
 * 
 * Features:
 * - Color tolerance for similar color matching
 * - Boundary checking for safe operation
 * - Queue-based implementation for stack safety
 * - ARGB color space support
 */
suspend fun floodFill(
    bitmap: Bitmap,
    x: Int, 
    y: Int, 
    newColor: Color, 
    tolerance: Int = 30
): Bitmap
```

#### Algorithm Details
- **Implementation**: Breadth-first search with queue
- **Color Matching**: ARGB component-wise tolerance checking
- **Memory Optimization**: In-place pixel array modification
- **Thread Safety**: Suspending function for coroutine usage

### areColorsSimilar

Color comparison utility with tolerance support.

```kotlin
/**
 * Compares two colors with tolerance for similarity.
 * 
 * @param color1 First color (ARGB format)
 * @param color2 Second color (ARGB format)
 * @param tolerance Maximum difference per channel (0-255)
 * @return true if colors are similar within tolerance
 */
private fun areColorsSimilar(color1: Int, color2: Int, tolerance: Int): Boolean
```

## Data Models

### UiState (Conceptual)

While not explicitly defined, the app uses reactive state patterns:

```kotlin
// Implicit state structure
data class PaintingUiState(
    val imageBitmap: Bitmap? = null,
    val imageSessionId: Int = 0,
    val isLoading: Boolean = false,
    val selectedColor: Color = Color.Red,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)
```

### Image Resource IDs

Pre-loaded coloring pages:

```kotlin
val initialImages = listOf(
    R.drawable.coloring_page_1,  // Coloring page 1
    R.drawable.coloring_page_2,  // Coloring page 2
    R.drawable.coloring_page_3,  // Coloring page 3
    R.drawable.coloring_page_4,  // Coloring page 4
    R.drawable.coloring_page_5,  // Coloring page 5
    R.drawable.coloring_page_6   // Coloring page 6
)
```

## Constants

### Image Processing

```kotlin
// K-means clustering parameters
const val K_MEANS_CLUSTERS = 16
const val K_MEANS_MAX_ITERATIONS = 10
const val K_MEANS_EPSILON = 1.0

// Grayscale detection
const val GRAYSCALE_SATURATION_THRESHOLD = 15.0

// Flood fill
const val DEFAULT_COLOR_TOLERANCE = 30
```

### UI Constants

```kotlin
// Canvas scaling
const val MIN_SCALE = 0.5f
const val MAX_SCALE = 5.0f
const val DEFAULT_SCALE = 1.0f

// Animation durations (conceptual)
const val COLOR_PICKER_ANIMATION_DURATION = 300
const val LOADING_ANIMATION_DURATION = 1000
```

## Extension Functions

### Bitmap Extensions

```kotlin
/**
 * Creates a mutable copy of the bitmap.
 * 
 * @param config Desired bitmap configuration
 * @param isMutable Whether the copy should be mutable
 * @return Mutable copy of the bitmap
 */
fun Bitmap.copy(config: Bitmap.Config, isMutable: Boolean): Bitmap
```

### Color Extensions

```kotlin
/**
 * Converts Compose Color to Android ARGB integer.
 * 
 * @return ARGB color value as integer
 */
fun Color.toArgb(): Int
```

### StateFlow Extensions

```kotlin
/**
 * Collects StateFlow values in Compose with lifecycle awareness.
 * 
 * @return Current state value
 */
@Composable
fun <T> StateFlow<T>.collectAsState(): State<T>
```

## Error Handling

### Common Exceptions

```kotlin
// Image processing errors
class ImageProcessingException(message: String) : Exception(message)

// Memory related errors
class InsufficientMemoryException(message: String) : Exception(message)

// Invalid coordinates
class InvalidCoordinateException(message: String) : Exception(message)
```

### Error Recovery

The API includes built-in error recovery mechanisms:

1. **Graceful Degradation**: Falls back to original image if processing fails
2. **Memory Management**: Automatically handles low memory conditions
3. **Bounds Checking**: Validates coordinates before operations
4. **State Consistency**: Maintains valid state even during errors

## Threading Model

### Coroutine Usage

```kotlin
// Image processing on background thread
viewModelScope.launch(Dispatchers.Default) {
    val processedBitmap = segmentImageByColor(bitmap)
    _imageBitmap.value = processedBitmap
}

// UI updates on main thread
viewModelScope.launch(Dispatchers.Main) {
    updateLoadingState(false)
}
```

### Thread Safety

- **StateFlow**: Thread-safe reactive state management
- **Bitmap Operations**: Isolated to background threads
- **UI Updates**: Dispatched to main thread
- **Memory Access**: Synchronized bitmap access

## Performance Considerations

### Memory Management

```kotlin
// Efficient bitmap handling
fun processImageSafely(bitmap: Bitmap): Bitmap? {
    return try {
        if (bitmap.isRecycled) return null
        segmentImageByColor(bitmap)
    } catch (e: OutOfMemoryError) {
        System.gc()
        null
    }
}
```

### Optimization Tips

1. **Image Size**: Limit input images to 2000x2000 pixels
2. **Memory**: Monitor heap usage during processing
3. **Threading**: Keep heavy operations off main thread
4. **Caching**: Reuse processed bitmaps when possible

---

## Migration Guide

### From 0.x to 1.0

No breaking changes - initial stable release.

### Future Compatibility

The API is designed for backward compatibility with semantic versioning:
- **Patch** (1.0.x): Bug fixes, no API changes
- **Minor** (1.x.0): New features, backward compatible
- **Major** (x.0.0): Breaking changes, migration guide provided

---

**API Version**: 1.0.0  
**Last Updated**: November 1, 2025  
**Compatibility**: Android API 24+
