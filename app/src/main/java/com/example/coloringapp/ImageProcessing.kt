package com.example.coloringapp

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

fun processImageToLineArt(bitmap: Bitmap): Bitmap {
    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)

    val grayMat = Mat()
    Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

    val blurredMat = Mat()
    Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)

    val edgesMat = Mat()
    Imgproc.Canny(blurredMat, edgesMat, 50.0, 150.0)

    val invertedMat = Mat()
    org.opencv.core.Core.bitwise_not(edgesMat, invertedMat)

    val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(invertedMat, resultBitmap)

    return resultBitmap
}