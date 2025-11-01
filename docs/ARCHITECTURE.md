# ColoringApp Architecture Documentation

## Table of Contents
- [Overview](#overview)
- [Architecture Pattern](#architecture-pattern)
- [Layer Architecture](#layer-architecture)
- [Component Interaction](#component-interaction)
- [State Management](#state-management)
- [Data Flow](#data-flow)
- [Threading Model](#threading-model)
- [Memory Management](#memory-management)
- [Performance Architecture](#performance-architecture)
- [Security Architecture](#security-architecture)

## Overview

ColoringApp follows a modern Android architecture based on MVVM (Model-View-ViewModel) pattern with Jetpack Compose for UI, emphasizing separation of concerns, testability, and maintainability. The architecture is designed for scalability and follows Android's recommended app architecture guidelines.

## Architecture Pattern

### MVVM (Model-View-ViewModel)

```
┌─────────────────────────────────────────────────────────────┐
│                        View Layer                           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ PaintingScreen  │  │ImageSelectionScr│  │ColorPicker   │ │
│  │                 │  │                 │  │              │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    ViewModel Layer                          │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              PaintingViewModel                          │ │
│  │  • State Management                                     │ │
│  │  • Business Logic                                       │ │
│  │  • User Interaction Handling                            │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Model Layer                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ ImageProcessing │  │    FloodFill    │  │   Utilities  │ │
│  │                 │  │                 │  │              │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Benefits of MVVM

- **Separation of Concerns**: Clear responsibility boundaries
- **Testability**: Easy unit testing of business logic
- **Maintainability**: Changes isolated to appropriate layers
- **Reusability**: ViewModels can be shared across different UIs
- **Lifecycle Awareness**: Automatic cleanup and state preservation

## Layer Architecture

### 1. Presentation Layer (UI)

#### Responsibilities
- User interface rendering
- User input handling
- UI state observation
- Navigation coordination

#### Components
```kotlin
// Screen Composables
@Composable
fun PaintingScreen(viewModel: PaintingViewModel)

@Composable  
fun ImageSelectionScreen(viewModel: PaintingViewModel, onImageSelected: () -> Unit)

@Composable
fun HoneycombColorPicker(onColorSelected: (Color) -> Unit)

// UI Components
@Composable
fun PaintingCanvas(...)

@Composable
fun PaintingControls(...)
```

#### Design Principles
- **Stateless Composables**: UI components receive state as parameters
- **Unidirectional Data Flow**: Data flows down, events flow up
- **Single Source of Truth**: ViewModel holds the authoritative state
- **Reactive UI**: Automatic updates when state changes

### 2. ViewModel Layer (Presentation Logic)

#### Responsibilities
- State management and coordination
- Business logic orchestration
- User action processing
- Data transformation for UI

#### Architecture
```kotlin
class PaintingViewModel : ViewModel() {
    // State holders
    private val _imageBitmap = MutableStateFlow<Bitmap?>(null)
    val imageBitmap = _imageBitmap.asStateFlow()
    
    // Business logic
    fun setImageBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val processed = segmentImageByColor(bitmap)
            _imageBitmap.value = processed
        }
    }
    
    // Undo/Redo management
    private val undoStack = mutableListOf<Bitmap>()
    private val redoStack = mutableListOf<Bitmap>()
}
```

#### State Management Strategy
- **StateFlow**: Reactive state emission
- **Immutable State**: Prevents accidental mutations
- **Lifecycle Awareness**: Automatic cleanup on destruction
- **Coroutine Integration**: Asynchronous operations

### 3. Domain Layer (Business Logic)

#### Responsibilities
- Core business logic
- Image processing algorithms
- Data manipulation utilities
- Business rule enforcement

#### Components
```kotlin
// Image Processing
fun segmentImageByColor(bitmap: Bitmap): Bitmap
private fun isGrayscale(bitmap: Bitmap): Boolean

// Color Filling
suspend fun floodFill(bitmap: Bitmap, x: Int, y: Int, newColor: Color): Bitmap
private fun areColorsSimilar(color1: Int, color2: Int, tolerance: Int): Boolean

// Utility Functions
fun calculateFitToScreenScale(canvasSize: Size, bitmapSize: Size): Float
fun transformCoordinates(tapPoint: Offset, scale: Float, offset: Offset): Offset
```

### 4. Data Layer (Currently Minimal)

#### Current Implementation
- Resource-based image storage
- In-memory state management
- No external data persistence

#### Future Extensions
```kotlin
// Repository Pattern (Future)
interface ImageRepository {
    suspend fun loadPreloadedImages(): List<ImageData>
    suspend fun saveColoredImage(bitmap: Bitmap): String
    suspend fun loadSavedProjects(): List<ProjectData>
}

// Data Sources (Future)
interface LocalDataSource {
    suspend fun cacheProcessedImage(bitmap: Bitmap)
    suspend fun getCachedImage(id: String): Bitmap?
}
```

## Component Interaction

### Communication Patterns

#### 1. UI → ViewModel
```kotlin
// Event-based communication
viewModel.setSelectedColor(color)
viewModel.startFloodFill(x, y)
viewModel.undo()
```

#### 2. ViewModel → UI
```kotlin
// State observation
val imageBitmap by viewModel.imageBitmap.collectAsState()
val isLoading by viewModel.isLoading.collectAsState()
val canUndo by viewModel.canUndo.collectAsState()
```

#### 3. ViewModel → Domain
```kotlin
// Direct function calls
viewModelScope.launch(Dispatchers.Default) {
    val processedBitmap = segmentImageByColor(bitmap)
    val filledBitmap = floodFill(processedBitmap, x, y, color)
}
```

### Dependency Injection (Conceptual)

Future architecture may include:
```kotlin
// Hilt/Dagger modules
@Module
class ImageProcessingModule {
    @Provides
    fun provideImageProcessor(): ImageProcessor = OpenCVImageProcessor()
}

@Module  
class RepositoryModule {
    @Provides
    fun provideImageRepository(
        localDataSource: LocalDataSource,
        remoteDataSource: RemoteDataSource
    ): ImageRepository = ImageRepositoryImpl(localDataSource, remoteDataSource)
}
```

## State Management

### State Flow Architecture

```kotlin
// ViewModel State Management
class PaintingViewModel : ViewModel() {
    // Private mutable state
    private val _uiState = MutableStateFlow(PaintingUiState())
    
    // Public read-only state
    val uiState: StateFlow<PaintingUiState> = _uiState.asStateFlow()
    
    // Individual state streams
    val imageBitmap: StateFlow<Bitmap?> = _uiState
        .map { it.imageBitmap }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}
```

### State Types

#### 1. UI State
```kotlin
data class PaintingUiState(
    val imageBitmap: Bitmap? = null,
    val imageSessionId: Int = 0,
    val isLoading: Boolean = false,
    val selectedColor: Color = Color.Red,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)
```

#### 2. Local Component State
```kotlin
@Composable
fun PaintingCanvas(...) {
    // Local UI state for gestures
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
}
```

#### 3. Persistent State (Future)
```kotlin
// Saved state for process death survival
class PaintingViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    var selectedColor: Color
        get() = savedStateHandle.get<Int>("selected_color")?.let { Color(it) } ?: Color.Red
        set(value) { savedStateHandle["selected_color"] = value.toArgb() }
}
```

## Data Flow

### Unidirectional Data Flow

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   User Action   │───▶│   ViewModel      │───▶│  Domain Logic   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   UI Update     │◀───│   State Update   │◀───│  Result/Data    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### Flow Examples

#### 1. Image Selection Flow
```kotlin
// 1. User taps image in gallery
onImageClick(imageId) {
    // 2. ViewModel processes action
    viewModel.setImageBitmapFromDrawable(context, imageId)
}

// 3. ViewModel executes business logic
fun setImageBitmapFromDrawable(context: Context, drawableId: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        _isLoading.value = true
        val bitmap = BitmapFactory.decodeResource(context.resources, drawableId)
        val processed = segmentImageByColor(bitmap)
        _imageBitmap.value = processed
        _isLoading.value = false
    }
}

// 4. UI observes and updates
val imageBitmap by viewModel.imageBitmap.collectAsState()
LaunchedEffect(imageBitmap) {
    if (imageBitmap != null) {
        // Navigate to painting screen
    }
}
```

#### 2. Color Fill Flow
```kotlin
// 1. User taps on canvas
Canvas(modifier = Modifier.pointerInput(Unit) {
    detectTapGestures { tapOffset ->
        // 2. Transform coordinates
        val bitmapCoords = transformToBitmapCoordinates(tapOffset)
        // 3. Trigger fill
        viewModel.startFloodFill(bitmapCoords.x, bitmapCoords.y)
    }
})

// 4. ViewModel processes fill
fun startFloodFill(x: Int, y: Int) {
    viewModelScope.launch(Dispatchers.Default) {
        _imageBitmap.value?.let { currentBitmap ->
            // Save to undo stack
            undoStack.add(currentBitmap.copy(currentBitmap.config, true))
            // Perform fill
            val filled = floodFill(currentBitmap, x, y, selectedColor.value)
            _imageBitmap.value = filled
        }
    }
}
```

## Threading Model

### Coroutine Strategy

```kotlin
class PaintingViewModel : ViewModel() {
    
    // CPU-intensive image processing
    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val result = segmentImageByColor(bitmap)
            withContext(Dispatchers.Main) {
                _imageBitmap.value = result
            }
        }
    }
    
    // I/O operations (file loading)
    fun loadImageFromFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = loadBitmapFromUri(uri)
            processImage(bitmap)
        }
    }
    
    // Main thread (UI updates)
    fun updateSelectedColor(color: Color) {
        _selectedColor.value = color // Already on main thread
    }
}
```

### Thread Allocation

- **Main Thread**: UI rendering, state updates, user interactions
- **Default Dispatcher**: Image processing, K-means clustering, flood fill
- **IO Dispatcher**: File operations, network calls (future)
- **Unconfined**: Quick state access, testing

### Synchronization

```kotlin
// Thread-safe operations
class PaintingViewModel : ViewModel() {
    
    // StateFlow provides thread-safe state access
    private val _imageBitmap = MutableStateFlow<Bitmap?>(null)
    
    // Concurrent collections for undo/redo
    private val undoStack = Collections.synchronizedList(mutableListOf<Bitmap>())
    
    // Atomic operations for counters
    private val sessionIdCounter = AtomicInteger(0)
}
```

## Memory Management

### Bitmap Memory Strategy

```kotlin
class BitmapMemoryManager {
    
    // Efficient bitmap copying
    fun createMutableCopy(source: Bitmap): Bitmap {
        return if (source.isMutable) {
            source
        } else {
            source.copy(source.config, true)
        }
    }
    
    // Memory-conscious undo stack
    private fun addToUndoStack(bitmap: Bitmap) {
        if (undoStack.size >= MAX_UNDO_OPERATIONS) {
            undoStack.removeFirst()?.recycle()
        }
        undoStack.add(bitmap.copy(bitmap.config, true))
    }
    
    // Cleanup on destroy
    override fun onCleared() {
        super.onCleared()
        undoStack.forEach { it.recycle() }
        redoStack.forEach { it.recycle() }
        _imageBitmap.value?.recycle()
    }
}
```

### Memory Optimization Techniques

1. **Lazy Loading**: Images loaded only when needed
2. **Bitmap Recycling**: Explicit cleanup of unused bitmaps
3. **Configuration Optimization**: Use ARGB_8888 only when necessary
4. **Garbage Collection**: Strategic System.gc() calls during low memory
5. **Memory Monitoring**: Track heap usage during operations

```kotlin
// Memory monitoring
private fun monitorMemory() {
    val runtime = Runtime.getRuntime()
    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
    val maxMemory = runtime.maxMemory()
    val memoryPercentage = (usedMemory.toFloat() / maxMemory.toFloat()) * 100
    
    if (memoryPercentage > 80) {
        // Trigger cleanup
        cleanupOldBitmaps()
    }
}
```

## Performance Architecture

### Image Processing Pipeline

```kotlin
// Optimized processing pipeline
suspend fun processImagePipeline(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
    
    // 1. Input validation
    require(!bitmap.isRecycled) { "Bitmap is recycled" }
    require(bitmap.width * bitmap.height < MAX_PIXELS) { "Image too large" }
    
    // 2. Quick grayscale check
    if (isGrayscale(bitmap)) {
        return@withContext bitmap
    }
    
    // 3. Memory pre-allocation
    val resultBitmap = Bitmap.createBitmap(
        bitmap.width, 
        bitmap.height, 
        Bitmap.Config.ARGB_8888
    )
    
    // 4. Chunked processing for large images
    return@withContext if (bitmap.width * bitmap.height > CHUNK_THRESHOLD) {
        processInChunks(bitmap, resultBitmap)
    } else {
        segmentImageByColor(bitmap)
    }
}
```

### Rendering Optimization

```kotlin
@Composable
fun OptimizedCanvas(bitmap: Bitmap, scale: Float, offset: Offset) {
    
    // Hardware acceleration
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
                renderEffect = null // Avoid software rendering
            )
    ) {
        // Efficient bitmap drawing
        drawImage(
            image = bitmap.asImageBitmap(),
            filterQuality = FilterQuality.Low // Fast rendering for gestures
        )
    }
}
```

### Caching Strategy

```kotlin
// Future caching implementation
class ImageCache {
    private val memoryCache = LruCache<String, Bitmap>(CACHE_SIZE)
    
    fun get(key: String): Bitmap? = memoryCache.get(key)
    
    fun put(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }
    
    // Disk cache for processed images
    private val diskCache = DiskLruCache.open(...)
}
```

## Security Architecture

### Current Security Measures

1. **Local Processing**: All image processing happens on-device
2. **No Network**: No external communication or data transmission
3. **Permission Minimal**: Only storage access for gallery selection
4. **Memory Protection**: No sensitive data in memory dumps

### Privacy by Design

```kotlin
// No telemetry or analytics
class PaintingViewModel : ViewModel() {
    
    // No user tracking
    fun onUserAction(action: String) {
        // Process action locally only
        when (action) {
            "color_selected" -> updateColor()
            "image_processed" -> updateImage()
            // No external reporting
        }
    }
    
    // No cloud storage
    fun saveProject(bitmap: Bitmap) {
        // Save locally only
        saveToLocalStorage(bitmap)
    }
}
```

### Future Security Considerations

```kotlin
// When adding cloud features
class SecureCloudSync {
    
    // End-to-end encryption
    fun uploadProject(project: Project) {
        val encrypted = encryptProject(project, userKey)
        cloudStorage.upload(encrypted)
    }
    
    // Biometric authentication
    fun authenticateUser(): Boolean {
        return biometricManager.authenticate()
    }
    
    // Secure key storage
    private val keyStore = AndroidKeyStore()
}
```

---

## Architecture Evolution

### Current Architecture (v1.0)
- Simple MVVM with Compose
- Local state management
- Single-activity architecture
- Minimal data layer

### Planned Evolution (v1.1+)
- Repository pattern introduction
- Dependency injection with Hilt
- Local database for saved projects
- Improved error handling

### Future Architecture (v2.0+)
- Clean Architecture layers
- Domain-driven design
- Cloud synchronization
- Modular architecture

### Migration Strategy

```kotlin
// Gradual migration approach
// 1. Extract repositories
interface ImageRepository {
    suspend fun getPreloadedImages(): List<ImageData>
    suspend fun processImage(bitmap: Bitmap): Bitmap
}

// 2. Add use cases
class ProcessImageUseCase(
    private val repository: ImageRepository
) {
    suspend operator fun invoke(bitmap: Bitmap): Result<Bitmap> {
        return try {
            Result.success(repository.processImage(bitmap))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// 3. Update ViewModel
class PaintingViewModel(
    private val processImageUseCase: ProcessImageUseCase
) : ViewModel() {
    // Implementation with use cases
}
```

---

**Architecture Version**: 1.0  
**Last Updated**: November 1, 2025  
**Compliance**: Android Architecture Guidelines
