package com.example.coloringapp

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/**
 * SLIC (Simple Linear Iterative Clustering) Superpixel Segmentation
 * 
 * This algorithm segments an image into compact, nearly uniform regions (superpixels)
 * that respect image boundaries. Perfect for paint-by-number because:
 * - Works on ANY image (photos, art, screenshots)
 * - Produces clean, compact regions with natural boundaries
 * - Each region has a clear center for number placement
 * - Controllable region count via the 'k' parameter
 * 
 * Based on: "SLIC Superpixels Compared to State-of-the-art Superpixel Methods"
 * by Achanta et al. (2012)
 */

/**
 * Data class representing a superpixel cluster center
 */
data class SlicCluster(
    var l: Float,      // LAB L component (lightness)
    var a: Float,      // LAB A component (green-red)
    var b: Float,      // LAB B component (blue-yellow)
    var x: Float,      // X position
    var y: Float,      // Y position
    var pixelCount: Int = 0
)

/**
 * Result of SLIC segmentation
 */
data class SlicResult(
    val labels: Array<IntArray>,        // Pixel -> superpixel label mapping
    val clusters: List<SlicCluster>,    // Final cluster centers
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SlicResult
        return labels.contentDeepEquals(other.labels) && clusters == other.clusters
    }

    override fun hashCode(): Int {
        var result = labels.contentDeepHashCode()
        result = 31 * result + clusters.hashCode()
        return result
    }
}

/**
 * Performs SLIC superpixel segmentation on the given bitmap
 * 
 * @param bitmap The input image
 * @param k Desired number of superpixels (approximate)
 * @param compactness Controls the balance between color and spatial proximity.
 *                    Higher values give more compact, regular superpixels.
 *                    Typical range: 10-40. Use 20-30 for photos, 10-20 for graphics.
 * @param maxIterations Maximum number of clustering iterations
 * @return SlicResult containing the segmentation labels and cluster centers
 */
fun slicSuperpixels(
    bitmap: Bitmap,
    k: Int = 200,
    compactness: Float = 25f,
    maxIterations: Int = 10
): SlicResult {
    val width = bitmap.width
    val height = bitmap.height
    val n = width * height
    
    // Step size (approximate superpixel size)
    val s = sqrt(n.toFloat() / k).toInt().coerceAtLeast(2)
    
    // Convert image to LAB color space for perceptually uniform distance
    val labImage = convertToLab(bitmap)
    
    // Initialize cluster centers on a regular grid, offset by S/2
    val clusters = mutableListOf<SlicCluster>()
    var y = s / 2
    while (y < height) {
        var x = s / 2
        while (x < width) {
            // Move cluster center to lowest gradient position in 3x3 neighborhood
            val (cx, cy) = findLowestGradientPosition(labImage, x, y, width, height)
            val idx = cy * width + cx
            clusters.add(SlicCluster(
                l = labImage[idx * 3],
                a = labImage[idx * 3 + 1],
                b = labImage[idx * 3 + 2],
                x = cx.toFloat(),
                y = cy.toFloat()
            ))
            x += s
        }
        y += s
    }
    
    // Labels array: which cluster each pixel belongs to
    val labels = Array(width) { IntArray(height) { -1 } }
    
    // Distance array: distance of each pixel to its assigned cluster
    val distances = Array(width) { FloatArray(height) { Float.MAX_VALUE } }
    
    // Normalization factor for spatial distance
    val m = compactness
    val invS = 1f / s
    
    // Main SLIC loop
    repeat(maxIterations) {
        // Reset distances for this iteration
        for (dx in 0 until width) {
            for (dy in 0 until height) {
                distances[dx][dy] = Float.MAX_VALUE
            }
        }
        
        // For each cluster center, update pixels in 2S x 2S region
        clusters.forEachIndexed { clusterIdx, cluster ->
            val xMin = (cluster.x - s).toInt().coerceIn(0, width - 1)
            val xMax = (cluster.x + s).toInt().coerceIn(0, width - 1)
            val yMin = (cluster.y - s).toInt().coerceIn(0, height - 1)
            val yMax = (cluster.y + s).toInt().coerceIn(0, height - 1)
            
            for (py in yMin..yMax) {
                for (px in xMin..xMax) {
                    val idx = py * width + px
                    
                    // Color distance in LAB space
                    val dl = labImage[idx * 3] - cluster.l
                    val da = labImage[idx * 3 + 1] - cluster.a
                    val db = labImage[idx * 3 + 2] - cluster.b
                    val colorDist = sqrt(dl * dl + da * da + db * db)
                    
                    // Spatial distance (normalized by S)
                    val dx = px - cluster.x
                    val dy = py - cluster.y
                    val spatialDist = sqrt(dx * dx + dy * dy) * invS
                    
                    // Combined distance: D = sqrt(dc² + (ds/S)² × m²)
                    val totalDist = sqrt(colorDist * colorDist + spatialDist * spatialDist * m * m)
                    
                    if (totalDist < distances[px][py]) {
                        distances[px][py] = totalDist
                        labels[px][py] = clusterIdx
                    }
                }
            }
        }
        
        // Update cluster centers
        clusters.forEach { cluster ->
            cluster.l = 0f
            cluster.a = 0f
            cluster.b = 0f
            cluster.x = 0f
            cluster.y = 0f
            cluster.pixelCount = 0
        }
        
        for (py in 0 until height) {
            for (px in 0 until width) {
                val label = labels[px][py]
                if (label >= 0 && label < clusters.size) {
                    val idx = py * width + px
                    clusters[label].l += labImage[idx * 3]
                    clusters[label].a += labImage[idx * 3 + 1]
                    clusters[label].b += labImage[idx * 3 + 2]
                    clusters[label].x += px
                    clusters[label].y += py
                    clusters[label].pixelCount++
                }
            }
        }
        
        clusters.forEach { cluster ->
            if (cluster.pixelCount > 0) {
                cluster.l /= cluster.pixelCount
                cluster.a /= cluster.pixelCount
                cluster.b /= cluster.pixelCount
                cluster.x /= cluster.pixelCount
                cluster.y /= cluster.pixelCount
            }
        }
    }
    
    // Enforce connectivity: merge small isolated regions
    enforceConnectivity(labels, clusters.size, width, height, s * s / 4)
    
    return SlicResult(labels, clusters, width, height)
}

/**
 * Convert RGB bitmap to LAB color space for perceptually uniform distances
 */
private fun convertToLab(bitmap: Bitmap): FloatArray {
    val width = bitmap.width
    val height = bitmap.height
    val lab = FloatArray(width * height * 3)
    
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = bitmap.getPixel(x, y)
            val r = Color.red(pixel) / 255f
            val g = Color.green(pixel) / 255f
            val b = Color.blue(pixel) / 255f
            
            // RGB to XYZ
            val rLinear = if (r > 0.04045f) ((r + 0.055f) / 1.055f).pow(2.4f) else r / 12.92f
            val gLinear = if (g > 0.04045f) ((g + 0.055f) / 1.055f).pow(2.4f) else g / 12.92f
            val bLinear = if (b > 0.04045f) ((b + 0.055f) / 1.055f).pow(2.4f) else b / 12.92f
            
            val xVal = (rLinear * 0.4124564f + gLinear * 0.3575761f + bLinear * 0.1804375f) / 0.95047f
            val yVal = (rLinear * 0.2126729f + gLinear * 0.7151522f + bLinear * 0.0721750f) / 1.00000f
            val zVal = (rLinear * 0.0193339f + gLinear * 0.1191920f + bLinear * 0.9503041f) / 1.08883f
            
            // XYZ to LAB
            val fx = if (xVal > 0.008856f) xVal.pow(1f / 3f) else (7.787f * xVal + 16f / 116f)
            val fy = if (yVal > 0.008856f) yVal.pow(1f / 3f) else (7.787f * yVal + 16f / 116f)
            val fz = if (zVal > 0.008856f) zVal.pow(1f / 3f) else (7.787f * zVal + 16f / 116f)
            
            val idx = (y * width + x) * 3
            lab[idx] = 116f * fy - 16f      // L: 0-100
            lab[idx + 1] = 500f * (fx - fy) // a: -128 to 127
            lab[idx + 2] = 200f * (fy - fz) // b: -128 to 127
        }
    }
    
    return lab
}

/**
 * Find the lowest gradient position in a 3x3 neighborhood
 * This avoids placing cluster centers on edges
 */
private fun findLowestGradientPosition(
    lab: FloatArray,
    x: Int,
    y: Int,
    width: Int,
    height: Int
): Pair<Int, Int> {
    var minGrad = Float.MAX_VALUE
    var minX = x
    var minY = y
    
    for (dy in -1..1) {
        for (dx in -1..1) {
            val nx = (x + dx).coerceIn(1, width - 2)
            val ny = (y + dy).coerceIn(1, height - 2)
            
            // Compute gradient using L channel differences
            val idx = ny * width + nx
            val idxRight = ny * width + (nx + 1)
            val idxDown = (ny + 1) * width + nx
            
            val gradX = lab[idxRight * 3] - lab[(ny * width + nx - 1) * 3]
            val gradY = lab[idxDown * 3] - lab[((ny - 1) * width + nx) * 3]
            val grad = gradX * gradX + gradY * gradY
            
            if (grad < minGrad) {
                minGrad = grad
                minX = nx
                minY = ny
            }
        }
    }
    
    return Pair(minX, minY)
}

/**
 * Enforce connectivity by merging small isolated regions into adjacent superpixels
 */
private fun enforceConnectivity(
    labels: Array<IntArray>,
    numSuperpixels: Int,
    width: Int,
    height: Int,
    minSize: Int
) {
    val newLabels = Array(width) { IntArray(height) { -1 } }
    var currentLabel = 0
    
    val dx = intArrayOf(1, -1, 0, 0)
    val dy = intArrayOf(0, 0, 1, -1)
    
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (newLabels[x][y] < 0) {
                // BFS to find connected component
                val component = mutableListOf<Pair<Int, Int>>()
                val queue = ArrayDeque<Pair<Int, Int>>()
                queue.add(Pair(x, y))
                val originalLabel = labels[x][y]
                
                while (queue.isNotEmpty()) {
                    val (cx, cy) = queue.removeFirst()
                    if (cx < 0 || cx >= width || cy < 0 || cy >= height) continue
                    if (newLabels[cx][cy] >= 0) continue
                    if (labels[cx][cy] != originalLabel) continue
                    
                    newLabels[cx][cy] = currentLabel
                    component.add(Pair(cx, cy))
                    
                    for (i in 0..3) {
                        queue.add(Pair(cx + dx[i], cy + dy[i]))
                    }
                }
                
                // If component is too small, merge with adjacent label
                if (component.size < minSize && component.isNotEmpty()) {
                    // Find adjacent label
                    var adjacentLabel = -1
                    for ((px, py) in component) {
                        for (i in 0..3) {
                            val nx = px + dx[i]
                            val ny = py + dy[i]
                            if (nx in 0 until width && ny in 0 until height) {
                                val adjLabel = newLabels[nx][ny]
                                if (adjLabel >= 0 && adjLabel != currentLabel) {
                                    adjacentLabel = adjLabel
                                    break
                                }
                            }
                        }
                        if (adjacentLabel >= 0) break
                    }
                    
                    // Merge with adjacent if found
                    if (adjacentLabel >= 0) {
                        for ((px, py) in component) {
                            newLabels[px][py] = adjacentLabel
                        }
                    }
                }
                
                currentLabel++
            }
        }
    }
    
    // Copy back to original labels
    for (y in 0 until height) {
        for (x in 0 until width) {
            labels[x][y] = newLabels[x][y]
        }
    }
}

/**
 * Convert SLIC result to ColorRegions for the paint-by-number game
 * 
 * @param bitmap Original image for color sampling
 * @param slicResult Result from slicSuperpixels()
 * @param numColors Number of colors in the palette
 * @return Pair of (color palette, list of ColorRegions)
 */
fun slicToColorRegions(
    bitmap: Bitmap,
    slicResult: SlicResult,
    numColors: Int
): Pair<List<Int>, List<ColorRegion>> {
    val width = slicResult.width
    val height = slicResult.height
    val labels = slicResult.labels
    
    // Group pixels by superpixel label
    val superpixelPixels = mutableMapOf<Int, MutableSet<Pair<Int, Int>>>()
    val superpixelColors = mutableMapOf<Int, MutableList<Int>>()
    
    for (y in 0 until height) {
        for (x in 0 until width) {
            val label = labels[x][y]
            if (label >= 0) {
                superpixelPixels.getOrPut(label) { mutableSetOf() }.add(Pair(x, y))
                superpixelColors.getOrPut(label) { mutableListOf() }.add(bitmap.getPixel(x, y))
            }
        }
    }
    
    // Calculate average color for each superpixel
    val avgColors = superpixelColors.mapValues { (_, colors) ->
        if (colors.isEmpty()) Color.GRAY
        else {
            val avgR = colors.map { Color.red(it) }.average().toInt()
            val avgG = colors.map { Color.green(it) }.average().toInt()
            val avgB = colors.map { Color.blue(it) }.average().toInt()
            Color.rgb(avgR, avgG, avgB)
        }
    }
    
    // Extract palette using K-means on superpixel average colors
    val palette = kMeansOnSuperpixelColors(avgColors.values.toList(), numColors)
    
    // Create ColorRegions
    val regions = superpixelPixels.mapNotNull { (label, pixels) ->
        if (pixels.isEmpty()) return@mapNotNull null
        
        val avgColor = avgColors[label] ?: Color.GRAY
        val colorIndex = findClosestPaletteIndex(avgColor, palette)
        
        // Calculate center (median for stability)
        val sortedX = pixels.map { it.first }.sorted()
        val sortedY = pixels.map { it.second }.sorted()
        val centerX = sortedX[sortedX.size / 2]
        val centerY = sortedY[sortedY.size / 2]
        
        ColorRegion(
            colorIndex = colorIndex,
            originalColor = palette[colorIndex],
            pixels = pixels,
            centerX = centerX,
            centerY = centerY
        )
    }
    
    return Pair(palette, regions)
}

/**
 * K-means clustering specifically for superpixel average colors
 */
private fun kMeansOnSuperpixelColors(colors: List<Int>, numColors: Int, maxIterations: Int = 15): List<Int> {
    if (colors.size <= numColors) {
        return colors.distinctBy { 
            Color.red(it) / 20 * 10000 + Color.green(it) / 20 * 100 + Color.blue(it) / 20
        }.take(numColors)
    }
    
    // Convert to float arrays
    val samples = colors.map { 
        floatArrayOf(Color.red(it).toFloat(), Color.green(it).toFloat(), Color.blue(it).toFloat())
    }
    
    // K-means++ initialization
    val centroids = mutableListOf<FloatArray>()
    val random = java.util.Random(42)
    centroids.add(samples[random.nextInt(samples.size)].copyOf())
    
    while (centroids.size < numColors) {
        val distances = samples.map { sample ->
            centroids.minOf { centroid ->
                val dr = sample[0] - centroid[0]
                val dg = sample[1] - centroid[1]
                val db = sample[2] - centroid[2]
                dr * dr + dg * dg + db * db
            }
        }
        
        val totalDist = distances.sum()
        if (totalDist <= 0) break
        
        var threshold = random.nextFloat() * totalDist
        var chosenIdx = 0
        for (i in distances.indices) {
            threshold -= distances[i]
            if (threshold <= 0) {
                chosenIdx = i
                break
            }
        }
        centroids.add(samples[chosenIdx].copyOf())
    }
    
    // K-means iterations
    val assignments = IntArray(samples.size)
    
    repeat(maxIterations) {
        // Assign each sample to nearest centroid
        samples.forEachIndexed { idx, sample ->
            assignments[idx] = centroids.indices.minByOrNull { c ->
                val dr = sample[0] - centroids[c][0]
                val dg = sample[1] - centroids[c][1]
                val db = sample[2] - centroids[c][2]
                dr * dr + dg * dg + db * db
            } ?: 0
        }
        
        // Update centroids
        val newCentroids = Array(numColors) { floatArrayOf(0f, 0f, 0f) }
        val counts = IntArray(numColors)
        
        samples.forEachIndexed { idx, sample ->
            val cluster = assignments[idx]
            newCentroids[cluster][0] += sample[0]
            newCentroids[cluster][1] += sample[1]
            newCentroids[cluster][2] += sample[2]
            counts[cluster]++
        }
        
        for (c in 0 until numColors) {
            if (counts[c] > 0) {
                centroids[c][0] = newCentroids[c][0] / counts[c]
                centroids[c][1] = newCentroids[c][1] / counts[c]
                centroids[c][2] = newCentroids[c][2] / counts[c]
            }
        }
    }
    
    return centroids
        .map { Color.rgb(it[0].toInt().coerceIn(0, 255), it[1].toInt().coerceIn(0, 255), it[2].toInt().coerceIn(0, 255)) }
        .distinctBy { Color.red(it) / 15 * 10000 + Color.green(it) / 15 * 100 + Color.blue(it) / 15 }
        .sortedBy { 0.299 * Color.red(it) + 0.587 * Color.green(it) + 0.114 * Color.blue(it) }
}

/**
 * Find closest palette color index
 */
private fun findClosestPaletteIndex(color: Int, palette: List<Int>): Int {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    
    return palette.indices.minByOrNull { idx ->
        val pr = Color.red(palette[idx])
        val pg = Color.green(palette[idx])
        val pb = Color.blue(palette[idx])
        (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
    } ?: 0
}

/**
 * Generate line art (outlines) from SLIC segmentation boundaries
 * This creates clean boundaries between superpixels
 */
fun generateOutlinesFromSlic(slicResult: SlicResult, lineWidth: Int = 2): Bitmap {
    val width = slicResult.width
    val height = slicResult.height
    val labels = slicResult.labels
    
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    // Fill with white
    for (y in 0 until height) {
        for (x in 0 until width) {
            result.setPixel(x, y, Color.WHITE)
        }
    }
    
    // Draw boundaries (where neighboring pixels have different labels)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val currentLabel = labels[x][y]
            var isBoundary = false
            
            // Check 4-neighbors
            if (x > 0 && labels[x - 1][y] != currentLabel) isBoundary = true
            if (x < width - 1 && labels[x + 1][y] != currentLabel) isBoundary = true
            if (y > 0 && labels[x][y - 1] != currentLabel) isBoundary = true
            if (y < height - 1 && labels[x][y + 1] != currentLabel) isBoundary = true
            
            if (isBoundary) {
                // Draw boundary with specified line width
                for (dy in -lineWidth / 2..lineWidth / 2) {
                    for (dx in -lineWidth / 2..lineWidth / 2) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until width && ny in 0 until height) {
                            result.setPixel(nx, ny, Color.BLACK)
                        }
                    }
                }
            }
        }
    }
    
    return result
}

/**
 * Get recommended number of superpixels based on image size and difficulty
 */
fun getRecommendedSuperpixelCount(width: Int, height: Int, difficulty: Difficulty): Int {
    val area = width * height
    return when (difficulty) {
        Difficulty.EASY -> (area / 2000).coerceIn(50, 150)    // Fewer, larger regions
        Difficulty.MEDIUM -> (area / 1000).coerceIn(100, 300) // Balanced
        Difficulty.HARD -> (area / 500).coerceIn(200, 500)    // More, smaller regions
    }
}

/**
 * Get recommended compactness based on difficulty
 * Lower compactness = more irregular shapes following colors
 * Higher compactness = more regular, grid-like shapes
 */
fun getRecommendedCompactness(difficulty: Difficulty): Float {
    return when (difficulty) {
        Difficulty.EASY -> 30f    // More regular shapes
        Difficulty.MEDIUM -> 20f  // Balanced
        Difficulty.HARD -> 15f    // More irregular, follows edges better
    }
}
