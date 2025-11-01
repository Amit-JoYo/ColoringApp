# Coloring Book App

A feature-rich coloring book app for Android that allows users to select an image, convert it into a line drawing, and color it using both flood-fill and free-hand brush tools. Save and share your creations!

## Features

### Core Features
- **Image Selection:** Select an image from the device's gallery or from a predefined list of images.
- **Image Processing:** Convert the selected image into a black-and-white line drawing using OpenCV.
- **Painting Canvas:** A zoomable and pannable canvas to display the image.
- **Color Palette:** A honeycomb color picker with color history (last 10 colors).
- **Two Drawing Modes:**
  - **Flood Fill:** Tap to fill entire regions with color (traditional coloring)
  - **Brush Tool:** Free-hand drawing for detailed work (NEW in v1.2.0)
- **Adjustable Brush Size:** 5-100 pixels with real-time slider (NEW in v1.2.0)
- **Save to Gallery:** Save your colored artwork to your device (NEW in v1.1.0)
- **Share:** Share your creations with friends and family (NEW in v1.1.0)
- **Undo/Redo:** Advanced undo/redo with action tracking (Enhanced in v1.2.0)
- **Back Navigation:** Navigate back to the image selection screen.

### What's New in v1.2.0
- 🎨 **Brush Tool:** Draw freely with adjustable brush size
- 🌈 **Color History:** Quick access to recently used colors
- 🔄 **Enhanced Undo/Redo:** Action labels and timestamps for better context
- 🎯 **Mode Toggle:** Easily switch between Fill and Brush modes
- 📏 **Brush Size Slider:** Adjust brush size from 5px to 100px

### What's New in v1.1.0
- 💾 **Save to Gallery:** Export your artwork as PNG
- 📤 **Share Functionality:** Share via any app
- 🔐 **Permission Handling:** Proper Android 10+ support

## Screenshots

| Image Selection | Painting Screen |
| --- | --- |
| ![Image Selection](screenshots/image_selection.png) | ![Painting Screen](screenshots/painting_screen.png) |

## Technology Stack

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose
- **Image Loading:** Coil
- **Image Processing:** OpenCV 4.5.3
- **Architecture:** MVVM (Model-View-ViewModel)
- **State Management:** Kotlin StateFlow
- **Storage:** MediaStore API / Legacy Storage

## Getting Started

To get a local copy up and running follow these simple steps.

### Prerequisites

- Android Studio
- Git

### Installation

1. Clone the repo
   ```sh
   git clone https://github.com/Amit-JoYo/ColoringApp.git
   ```
2. Open the project in Android Studio
3. Build and run the app

## How to Use

1.  **Select an Image:**
    -   Select an image from the predefined list of images.
    -   Or, tap the "Select Image from Gallery" button to open the device's gallery and select an image.
2.  **Image Processing:** The app will automatically convert the selected image into a line drawing.
3.  **Zoom and Pan:** You can zoom in and out of the image using pinch gestures, and pan by dragging the image.
4.  **Select a Drawing Mode:**
    -   Tap the mode button to toggle between Fill mode (default) and Brush mode.
    -   In Brush mode, adjust the brush size using the slider that appears.
5.  **Select a Color:**
    -   Tap the brush icon to open the honeycomb color picker.
    -   Select a color from the picker or tap a recent color from the history row.
6.  **Color the Image:**
    -   **Fill Mode:** Tap on a region to fill it completely with the selected color.
    -   **Brush Mode:** Drag your finger/stylus to draw freehand strokes.
7.  **Undo/Redo:** Use the undo and redo buttons to reverse or reapply actions.
8.  **Save or Share:**
    -   Tap the save icon to export your artwork to the gallery.
    -   Tap the share icon to share your creation via any app.
9.  **Go Back:** Tap the "Back" button to select a different image.

## Documentation

- [Brush Tool & Enhanced Features Guide](docs/BRUSH_AND_ENHANCED_FEATURES.md)
- [Save & Export Feature Documentation](docs/SAVE_EXPORT_FEATURE.md)
- [Release Notes](docs/RELEASE_NOTES.md)

## Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

Distributed under the MIT License. See `LICENSE` for more information.