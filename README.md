# Coloring Book App

A simple coloring book app for Android that allows users to select an image from their device's gallery or from a predefined list of images, convert it into a line drawing, and then color it using a flood fill tool.

## Features

- **Image Selection:** Select an image from the device's gallery or from a predefined list of images.
- **Image Processing:** Convert the selected image into a black-and-white line drawing.
- **Painting Canvas:** A zoomable and pannable canvas to display the image.
- **Color Palette:** A honeycomb color picker to select a wide range of colors.
- **Flood Fill:** Tap to fill a region with the selected color.
- **Back Navigation:** Navigate back to the image selection screen from the painting screen.

## Screenshots

| Image Selection | Painting Screen |
| --- | --- |
| ![Image Selection](screenshots/image_selection.png) | ![Painting Screen](screenshots/painting_screen.png) |

## Technology Stack

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose
- **Image Loading:** Coil
- **Architecture:** MVVM (Model-View-ViewModel)

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
4.  **Select a Color:**
    -   Tap the brush icon to open the honeycomb color picker.
    -   Select a color from the color picker.
5.  **Color the Image:** Tap on a region of the image to fill it with the selected color.
6.  **Go Back:** Tap the "Back" button to go back to the image selection screen.

## Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

Distributed under the MIT License. See `LICENSE` for more information.