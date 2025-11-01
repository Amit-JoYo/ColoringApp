# ColoringApp Documentation

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [Technical Specifications](#technical-specifications)
- [Installation](#installation)
- [Usage Guide](#usage-guide)
- [Architecture](#architecture)
- [Development](#development)
- [API Reference](#api-reference)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## Overview

ColoringApp is a modern Android application that transforms any image into a digital coloring book. Built with Jetpack Compose and powered by OpenCV image processing, it provides an intuitive and engaging coloring experience for users of all ages.

## Features

### Core Features
- **Image Selection**
  - Choose from 6 pre-loaded coloring pages
  - Import images from device gallery
  - Automatic image processing for optimal coloring

- **Advanced Canvas**
  - Smooth zoom and pan gestures
  - Precise flood-fill coloring
  - Fit-to-screen functionality
  - Real-time rendering

- **Color System**
  - Honeycomb color picker design
  - Wide color palette
  - Visual color preview

- **User Experience**
  - Unlimited undo/redo operations
  - Intuitive touch controls
  - Modern Material Design 3 UI
  - Responsive loading states

### Image Processing
- **Smart Detection**: Automatically identifies grayscale vs. color images
- **K-means Clustering**: Segments colored images into 16 distinct regions
- **Color Tolerance**: Intelligent color matching with 30-pixel tolerance
- **OpenCV Integration**: Professional-grade image processing algorithms

## Technical Specifications

### Requirements
- **Minimum Android Version**: 7.0 (API Level 24)
- **Target Android Version**: 14 (API Level 34)
- **RAM**: 2GB minimum, 4GB recommended
- **Storage**: 100MB free space

### Technology Stack
- **Language**: Kotlin 1.9.0
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Image Processing**: OpenCV 4.5.3
- **Image Loading**: Coil 2.5.0
- **Build System**: Gradle with Kotlin DSL

### Dependencies
```kotlin
// Core Android
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
implementation("androidx.activity:activity-compose:1.8.2")

// Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2023.08.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")

// Image Processing & Loading
implementation("com.quickbirdstudios:opencv:4.5.3.0")
implementation("io.coil-kt:coil-compose:2.5.0")

// ViewModel
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
```

## Installation

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 8 or higher
- Android SDK with API Level 34

### Setup Steps
1. **Clone the Repository**
   ```bash
   git clone https://github.com/Amit-JoYo/ColoringApp.git
   cd ColoringApp
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build the Project**
   ```bash
   ./gradlew build
   ```

4. **Run on Device/Emulator**
   - Connect Android device or start emulator
   - Click "Run" in Android Studio or use:
   ```bash
   ./gradlew installDebug
   ```

## Usage Guide

### Getting Started
1. **Launch the App**: Tap the ColoringApp icon
2. **Select an Image**: 
   - Choose from pre-loaded coloring pages, or
   - Tap "Select Image from Gallery" to import from device
3. **Wait for Processing**: The app will automatically convert your image
4. **Start Coloring**: Tap anywhere on the image to begin

### Canvas Controls
- **Zoom**: Pinch to zoom in/out
- **Pan**: Drag to move around the image
- **Color**: Tap the brush icon to select colors
- **Fill**: Tap any region to fill with selected color
- **Undo/Redo**: Use arrow buttons to undo/redo actions
- **Fit Screen**: Tap the fit icon to auto-resize image
- **Back**: Return to image selection screen

### Pro Tips
- **Zoom In**: For detailed work, zoom in before coloring small areas
- **Color Tolerance**: The app automatically handles similar colors in the same region
- **Undo History**: Your entire coloring session is saved - undo/redo as needed
- **Image Quality**: Higher resolution images provide better coloring detail

## Architecture

### MVVM Pattern
The app follows the Model-View-ViewModel architecture pattern:

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│      View       │────│    ViewModel     │────│     Model       │
│  (Composables)  │    │ (State Manager)  │    │ (Data/Logic)    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### Key Components

#### Views (UI Layer)
- `MainActivity`: Entry point and navigation host
- `ImageSelectionScreen`: Gallery and pre-loaded image selection
- `PaintingScreen`: Main coloring canvas and controls
- `HoneycombColorPicker`: Color selection interface

#### ViewModel (Presentation Layer)
- `PaintingViewModel`: Manages app state, image processing, and user interactions

#### Model (Data Layer)
- `ImageProcessing`: OpenCV-based image segmentation
- `FloodFill`: Pixel-perfect color filling algorithm

### State Management
- **StateFlow**: Reactive state management for UI updates
- **Coroutines**: Asynchronous image processing and file operations
- **Compose State**: Local UI state management

## Development

### Project Structure
```
app/src/main/
├── java/com/example/coloringapp/
│   ├── MainActivity.kt              # App entry point
│   ├── PaintingViewModel.kt         # State management
│   ├── PaintingScreen.kt           # Main canvas UI
│   ├── ImageSelectionScreen.kt     # Image picker UI
│   ├── HoneycombColorPicker.kt     # Color picker UI
│   ├── ImageProcessing.kt          # OpenCV algorithms
│   ├── FloodFill.kt               # Color fill logic
│   └── ui/theme/                  # Material Design theme
├── res/
│   ├── drawable/                  # Icons and pre-loaded images
│   ├── values/                    # Colors, strings, themes
│   └── xml/                       # Backup rules, etc.
└── AndroidManifest.xml            # App configuration
```

### Building from Source
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Generate APK
./gradlew build
```

### Code Style
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use [ktlint](https://ktlint.github.io/) for code formatting
- Document public APIs with KDoc comments
- Use meaningful variable and function names

## API Reference

### PaintingViewModel
Main state management class for the coloring functionality.

#### Properties
```kotlin
val imageBitmap: StateFlow<Bitmap?>          // Current image
val selectedColor: StateFlow<Color>          // Selected color
val canUndo: StateFlow<Boolean>             // Undo availability
val canRedo: StateFlow<Boolean>             // Redo availability
val isLoading: StateFlow<Boolean>           // Loading state
```

#### Methods
```kotlin
fun setImageBitmap(bitmap: Bitmap)          // Load new image
fun setSelectedColor(color: Color)          // Change brush color
fun startFloodFill(x: Int, y: Int)          // Fill region at coordinates
fun undo()                                  // Undo last action
fun redo()                                  // Redo last undone action
fun clearImage()                            // Return to image selection
```

### ImageProcessing
OpenCV-based image processing utilities.

```kotlin
fun segmentImageByColor(bitmap: Bitmap): Bitmap    // K-means segmentation
private fun isGrayscale(bitmap: Bitmap): Boolean   // Detect B&W images
```

### FloodFill
Color filling algorithm with tolerance support.

```kotlin
suspend fun floodFill(
    bitmap: Bitmap,
    x: Int, 
    y: Int, 
    newColor: Color, 
    tolerance: Int = 30
): Bitmap
```

## Troubleshooting

### Common Issues

#### OpenCV Initialization Failed
**Problem**: App crashes on startup with OpenCV error
**Solution**: 
- Ensure device has sufficient RAM (2GB+)
- Restart the app
- Clear app cache in device settings

#### Image Processing Too Slow
**Problem**: Long loading times when processing images
**Solution**:
- Use smaller image sizes (< 2MP recommended)
- Close other memory-intensive apps
- Ensure device has adequate free storage

#### Color Fill Not Working
**Problem**: Tapping doesn't fill the intended region
**Solution**:
- Zoom in for better precision
- Try tapping slightly inside the region boundary
- Ensure the region has clear boundaries after processing

#### Out of Memory Errors
**Problem**: App crashes when loading large images
**Solution**:
- Reduce image resolution before importing
- Restart the app to clear memory
- Use images smaller than 5MB

### Performance Tips
- **Image Size**: Keep imported images under 2000x2000 pixels
- **Memory**: Close other apps before processing large images
- **Zoom Level**: Use moderate zoom levels for better performance
- **Undo History**: Clear history (by loading new image) if experiencing slowdowns

### Getting Help
- Check the [Issues](https://github.com/Amit-JoYo/ColoringApp/issues) page for known problems
- Search existing issues before creating new ones
- Provide device information and steps to reproduce when reporting bugs

## Contributing

### Development Workflow
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes and test thoroughly
4. Commit with descriptive messages: `git commit -m 'Add amazing feature'`
5. Push to your branch: `git push origin feature/amazing-feature`
6. Create a Pull Request

### Code Guidelines
- Write unit tests for new functionality
- Follow existing code style and conventions
- Update documentation for public API changes
- Test on multiple device types and Android versions

### Areas for Contribution
- Additional color picker designs
- New image processing algorithms
- Performance optimizations
- Accessibility improvements
- Localization (i18n)
- UI/UX enhancements

### License
This project is licensed under the MIT License - see the [LICENSE](../LICENSE) file for details.

---

**Last Updated**: November 1, 2025  
**Version**: 1.0.0  
**Maintainer**: Amit-JoYo
