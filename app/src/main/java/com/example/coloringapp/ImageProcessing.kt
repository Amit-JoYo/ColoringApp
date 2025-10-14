package com.example.coloringapp

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc

/**
 * Checks if a bitmap is grayscale by analyzing its color saturation.
 * A very low average saturation value indicates a lack of color.
 */
private fun isGrayscale(bitmap: Bitmap): Boolean {
    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)

    // Convert the image from BGR to HSV color space
    val hsvMat = Mat()
    Imgproc.cvtColor(mat, hsvMat, Imgproc.COLOR_BGR2HSV)

    // Split the HSV image into its 3 channels (Hue, Saturation, Value)
    val hsvChannels = mutableListOf<Mat>()
    Core.split(hsvMat, hsvChannels)

    // Get the saturation channel
    val saturationChannel = hsvChannels[1]

    // Calculate the average saturation
    val meanSaturation = Core.mean(saturationChannel)

    // Define a threshold for what is considered "grayscale"
    // This value might need tuning, but values close to 0 are colorless.
    val grayscaleThreshold = 15.0

    return meanSaturation.`val`[0] < grayscaleThreshold
}

/**
 * Processes a bitmap image.
 * If the image is black & white, it's returned as is.
 * If the image is colorful, it's segmented into distinct color regions using k-means clustering.
 */
fun segmentImageByColor(bitmap: Bitmap): Bitmap {
    // First, check if the image is already black and white (grayscale)
    if (isGrayscale(bitmap)) {
        // If so, return the original bitmap without any processing
        return bitmap
    }

    // --- K-Means Color Segmentation for color images ---

    // 1. Convert the input Bitmap to an OpenCV Mat
    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)
    // Convert from RGBA (from bitmap) to BGR for processing
    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)


    // 2. Reshape the image Mat to be a list of pixels (samples)
    // This creates a Mat with 3 columns (for B, G, R) and one row for each pixel
    val samples = mat.reshape(3, mat.cols() * mat.rows())
    val samples32f = Mat()
    // Convert the data type to 32-bit float for k-means
    samples.convertTo(samples32f, CvType.CV_32F, 1.0 / 255.0)

    // 3. Define the termination criteria for the k-means algorithm
    val term = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 10, 1.0)

    // These will hold the output of the k-means algorithm
    val labels = Mat() // Will store the cluster index for each pixel
    val centers = Mat() // Will store the center color of each cluster

    // Define the number of color clusters to find
    val k = 16 // Increased K for more detail

    // 4. Apply the k-means algorithm
    Core.kmeans(samples32f, k, labels, term, 3, Core.KMEANS_PP_CENTERS, centers)

    // 5. Reconstruct the segmented image from the k-means results
    // Convert the center colors back to the 8-bit BGR color space
    centers.convertTo(centers, CvType.CV_8UC1, 255.0)
    // Reshape centers to be a k x 1 matrix with 3 channels
    centers.reshape(3, k)

    val labelsInt = IntArray(labels.rows() * labels.cols())
    labels.get(0, 0, labelsInt)

    val newMatData = ByteArray(mat.rows() * mat.cols() * 3)

    // For each pixel, find its cluster's center color and assign it
    for (i in labelsInt.indices) {
        val clusterId = labelsInt[i]
        val center = centers.get(clusterId, 0)
        newMatData[i * 3] = center[0].toByte()     // Blue
        newMatData[i * 3 + 1] = center[1].toByte() // Green
        newMatData[i * 3 + 2] = center[2].toByte() // Red
    }

    // Create a new Mat for the result and populate it with the segmented color data
    val newMat = Mat(mat.size(), CvType.CV_8UC3)
    newMat.put(0, 0, newMatData)

    // 6. Convert the processed Mat back to a Bitmap to be displayed
    val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    // Convert from BGR back to RGBA for the bitmap
    Imgproc.cvtColor(newMat, newMat, Imgproc.COLOR_BGR2RGBA)
    Utils.matToBitmap(newMat, resultBitmap)

    return resultBitmap
}
