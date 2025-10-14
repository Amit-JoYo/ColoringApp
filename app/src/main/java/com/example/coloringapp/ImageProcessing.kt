package com.example.coloringapp

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc

private fun isColoringPage(bitmap: Bitmap): Boolean {
    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)

    // --- 1. Check Saturation ---
    // Convert the image from BGR to HSV color space
    val hsvMat = Mat()
    Imgproc.cvtColor(mat, hsvMat, Imgproc.COLOR_BGR2HSV)
    val hsvChannels = mutableListOf<Mat>()
    Core.split(hsvMat, hsvChannels)
    val saturationChannel = hsvChannels[1]
    val meanSaturation = Core.mean(saturationChannel)

    // If the image has significant color, it's not a B&W page.
    val saturationThreshold = 25.0 // Increased threshold to be more lenient
    if (meanSaturation.`val`[0] >= saturationThreshold) {
        return false
    }

    // --- 2. Check Brightness Distribution ---
    // If the image is grayscale, check if it's mostly white.
    val valueChannel = hsvChannels[2] // Value channel represents brightness
    val totalPixels = valueChannel.total().toInt()
    var whitePixels = 0
    val whiteThreshold = 240 // Pixels with brightness > 240 are considered "white"

    // This is more efficient than a histogram for a simple threshold count
    val buffer = ByteArray(totalPixels)
    valueChannel.get(0, 0, buffer)
    for (byte in buffer) {
        // Convert signed byte to unsigned int
        if (byte.toInt() and 0xFF > whiteThreshold) {
            whitePixels++
        }
    }

    val whitePixelPercentage = whitePixels.toDouble() / totalPixels

    // If more than 70% of the image is white, it's very likely a coloring page.
    return whitePixelPercentage > 0.70
}

fun convertToGrayscaleWithEdges(bitmap: Bitmap): Bitmap {
    // Check if the image is already a coloring page
    if (isColoringPage(bitmap)) {
        return bitmap
    }

    // 1. Resize the image if it's too large to prevent memory issues
    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)
    val maxImageSize = 1024.0
    val ratio = maxImageSize / maxOf(mat.width(), mat.height())
    if (ratio < 1) {
        val newSize = Size(mat.width() * ratio, mat.height() * ratio)
        Imgproc.resize(mat, mat, newSize, 0.0, 0.0, Imgproc.INTER_AREA)
    }

    // 2. Convert to grayscale
    val grayMat = Mat()
    Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

    // 3. Apply a bilateral filter to reduce noise while keeping edges sharp
    val bilateralFilteredMat = Mat()
    Imgproc.bilateralFilter(grayMat, bilateralFilteredMat, 9, 75.0, 75.0)


    // 4. Detect edges using the Canny edge detector
    val edgesMat = Mat()
    Imgproc.Canny(bilateralFilteredMat, edgesMat, 50.0, 150.0)

    // 5. Invert the image to get black edges on a white background
    Core.bitwise_not(edgesMat, edgesMat)

    // 6. Convert the black and white Mat back to a Bitmap
    val resultBitmap = Bitmap.createBitmap(edgesMat.cols(), edgesMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(edgesMat, resultBitmap)
    return resultBitmap
}
