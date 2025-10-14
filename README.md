# Coloring Book App

This is an Android application that allows users to select an image from their device's gallery, convert it into a line drawing, and then color it using a flood fill tool.

## Features

- **Image Selection:** Select an image from the device's gallery.
- **Image Processing:** Convert the selected image into a black-and-white line drawing.
- **Painting Canvas:** A zoomable and pannable canvas to display the image.
- **Color Palette:** A color palette to select colors.
- **Flood Fill:** Tap to fill a region with the selected color.

## Technology Stack

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose
- **Image Loading:** Coil
- **Architecture:** MVVM (Model-View-ViewModel)

## How to Operate the App

1.  **Select an Image:** Tap the "Select Image" button to open the device's gallery and select an image.
2.  **Image Processing:** The app will automatically convert the selected image into a line drawing.
3.  **Zoom and Pan:** You can zoom in and out of the image using pinch gestures, and pan by dragging the image.
4.  **Select a Color:** Tap on a color from the color palette at the bottom of the screen to select it.
5.  **Color the Image:** Tap on a region of the image to fill it with the selected color.

## Project History

This project was created as a demonstration of how to build a coloring book application for Android using modern technologies like Jetpack Compose and Kotlin.

The project was developed in the following steps:

1.  **Project Setup:** The project was set up with the necessary dependencies for Jetpack Compose, ViewModel, and Coil.
2.  **Image Selection:** The image selection functionality was implemented using the `rememberLauncherForActivityResult` with `PickVisualMedia`.
3.  **Image Processing:** The image processing logic was implemented to convert the selected image into a line drawing.
4.  **PaintingViewModel:** The `PaintingViewModel` was created to manage the state of the application.
5.  **Flood Fill Algorithm:** The flood fill algorithm was implemented to allow users to color the image.
6.  **UI - The Painting Canvas:** The painting canvas was created to display the image and handle user input.
7.  **UI - Putting it all Together:** The main screen of the application was created, combining the painting canvas, color palette, and image selection button.
