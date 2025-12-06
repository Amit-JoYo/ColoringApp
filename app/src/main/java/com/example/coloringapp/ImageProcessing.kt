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
 * Processes a bitmap image for coloring.
 * If the image is black & white, it's returned as is.
 * If the image is colorful, it's converted to a coloring book style:
 * - Clear black outlines/edges
 * - White background for coloring
 */
fun segmentImageByColor(bitmap: Bitmap): Bitmap {
    // First, check if the image is already black and white (grayscale)
    if (isGrayscale(bitmap)) {
        // If so, return the original bitmap without any processing
        return bitmap
    }

    // --- Convert color image to coloring book style (black lines on white) ---

    try {
        // 1. Convert the input Bitmap to an OpenCV Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // 2. Convert to grayscale first
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        
        // 3. Calculate image statistics for dynamic threshold adjustment
        val meanMat = org.opencv.core.MatOfDouble()
        val stdDevMat = org.opencv.core.MatOfDouble()
        Core.meanStdDev(grayMat, meanMat, stdDevMat)
        val stdDevValue = stdDevMat.get(0, 0)[0]
        val meanValue = meanMat.get(0, 0)[0]
        
        // Calculate median using histogram (for better robustness to outliers)
        val hist = Mat()
        Imgproc.calcHist(
            listOf(grayMat),
            org.opencv.core.MatOfInt(0),
            Mat(),
            hist,
            org.opencv.core.MatOfInt(256),
            org.opencv.core.MatOfFloat(0f, 256f)
        )
        
        var count = 0.0
        var medianValue = 128.0
        val halfPixels = (grayMat.rows() * grayMat.cols()) / 2.0
        for (i in 0 until 256) {
            count += hist.get(i, 0)[0]
            if (count >= halfPixels) {
                medianValue = i.toDouble()
                break
            }
        }
        
        // Dynamic threshold calculation based on image characteristics
        // Lower threshold = 0.5 * median (adjusted by std dev)
        // Higher threshold = 2.5 * lower threshold
        val lowerThreshold = kotlin.math.max(20.0, kotlin.math.min(50.0, 
            0.5 * medianValue * (1.0 + stdDevValue / 128.0)))
        val upperThreshold = 2.5 * lowerThreshold
        
        android.util.Log.d("ImageProcessing", 
            "Dynamic thresholds: lower=$lowerThreshold, upper=$upperThreshold (median=$medianValue, stdDev=$stdDevValue)")
        
        // 4. Apply bilateral filter to reduce noise while preserving edges
        val filtered = Mat()
        Imgproc.bilateralFilter(grayMat, filtered, 9, 100.0, 100.0)
        
        // 5. Apply slight Gaussian blur for smoother edge detection
        val blurred = Mat()
        Imgproc.GaussianBlur(filtered, blurred, org.opencv.core.Size(3.0, 3.0), 0.0)
        
        // 6. Detect edges using Canny algorithm with dynamic thresholds
        val edges = Mat()
        Imgproc.Canny(blurred, edges, lowerThreshold, upperThreshold)
        
        // Clean up temporary mats
        stdDevMat.release()
        meanMat.release()
        hist.release()
        
        // 7. Dilate edges to make them thicker and more visible
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(3.0, 3.0))
        val dilated = Mat()
        Imgproc.dilate(edges, dilated, kernel)
        
        // 8. Invert to get black lines on white background (coloring book style)
        val inverted = Mat()
        Core.bitwise_not(dilated, inverted)
        
        // 9. Apply threshold to ensure pure white background and black lines
        val thresholded = Mat()
        Imgproc.threshold(inverted, thresholded, 240.0, 255.0, Imgproc.THRESH_BINARY)
        
        // 10. Convert back to RGBA for bitmap
        val resultMat = Mat()
        Imgproc.cvtColor(thresholded, resultMat, Imgproc.COLOR_GRAY2RGBA)
        
        // 11. Convert back to Bitmap
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resultMat, resultBitmap)
        
        // Clean up all Mat objects
        mat.release()
        grayMat.release()
        filtered.release()
        blurred.release()
        edges.release()
        kernel.release()
        dilated.release()
        inverted.release()
        thresholded.release()
        resultMat.release()
        
        return resultBitmap
    } catch (e: Exception) {
        // If processing fails, return a simple threshold conversion
        android.util.Log.e("ImageProcessing", "Error processing colored image: ${e.message}")
        
        // Fallback: Simple grayscale + threshold
        try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            
            // Apply adaptive threshold for better line detection
            val thresholded = Mat()
            Imgproc.adaptiveThreshold(
                grayMat, 
                thresholded, 
                255.0, 
                Imgproc.ADAPTIVE_THRESH_MEAN_C, 
                Imgproc.THRESH_BINARY, 
                11, 
                2.0
            )
            
            val resultMat = Mat()
            Imgproc.cvtColor(thresholded, resultMat, Imgproc.COLOR_GRAY2RGBA)
            
            val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultMat, resultBitmap)
            
            mat.release()
            grayMat.release()
            thresholded.release()
            resultMat.release()
            
            return resultBitmap
        } catch (fallbackError: Exception) {
            android.util.Log.e("ImageProcessing", "Fallback also failed: ${fallbackError.message}")
            // Last resort: return original bitmap
            return bitmap
        }
    }
}
