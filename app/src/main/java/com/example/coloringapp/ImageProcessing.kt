package com.example.coloringapp

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc


fun convertToGrayscaleWithEdges(bitmap: Bitmap): Bitmap {
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
