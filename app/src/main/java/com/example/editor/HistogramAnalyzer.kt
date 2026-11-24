package com.example.editor

import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlin.math.max
import kotlin.math.roundToInt

data class HistogramData(
  val redBins: FloatArray,
  val greenBins: FloatArray,
  val blueBins: FloatArray,
  val lumaBins: FloatArray,
  val shadowClippingRatio: Float = 0f,
  val highlightClippingRatio: Float = 0f,
  val meanLuminance: Float = 0.5f,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is HistogramData) return false
    return redBins.contentEquals(other.redBins) &&
      greenBins.contentEquals(other.greenBins) &&
      blueBins.contentEquals(other.blueBins) &&
      lumaBins.contentEquals(other.lumaBins) &&
      shadowClippingRatio == other.shadowClippingRatio &&
      highlightClippingRatio == other.highlightClippingRatio &&
      meanLuminance == other.meanLuminance
  }

  override fun hashCode(): Int {
    var result = redBins.contentHashCode()
    result = 31 * result + greenBins.contentHashCode()
    result = 31 * result + blueBins.contentHashCode()
    result = 31 * result + lumaBins.contentHashCode()
    result = 31 * result + shadowClippingRatio.hashCode()
    result = 31 * result + highlightClippingRatio.hashCode()
    result = 31 * result + meanLuminance.hashCode()
    return result
  }
}

/**
 * Computes real-time RGB and Luminance histograms from a downsampled ARGB pixel buffer
 * and a 4x5 Android ColorMatrix array in pure Kotlin for smooth 60fps slider updates.
 */
object HistogramAnalyzer {
  const val DEFAULT_BIN_COUNT = 64

  fun identityMatrixValues(): FloatArray = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
  )

  fun extractSamplePixels(bitmap: Bitmap, targetEdge: Int = 64): IntArray {
    val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
      bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
      bitmap
    }
    val longest = max(safeBitmap.width, safeBitmap.height).coerceAtLeast(1)
    val scale = (targetEdge.toFloat() / longest.toFloat()).coerceAtMost(1f)
    val w = (safeBitmap.width * scale).roundToInt().coerceAtLeast(1)
    val h = (safeBitmap.height * scale).roundToInt().coerceAtLeast(1)
    val sampled = if (w == safeBitmap.width && h == safeBitmap.height) {
      safeBitmap
    } else {
      safeBitmap.scale(w, h, filter = true)
    }
    val pixels = IntArray(w * h)
    sampled.getPixels(pixels, 0, w, 0, 0, w, h)
    return pixels
  }

  fun computeHistogram(
    pixels: IntArray,
    colorMatrixValues: FloatArray,
    binCount: Int = DEFAULT_BIN_COUNT,
  ): HistogramData {
    val safeBins = binCount.coerceIn(16, 256)
    if (pixels.isEmpty() || colorMatrixValues.size < 20) {
      return HistogramData(
        redBins = FloatArray(safeBins),
        greenBins = FloatArray(safeBins),
        blueBins = FloatArray(safeBins),
        lumaBins = FloatArray(safeBins),
      )
    }

    val rRaw = IntArray(safeBins)
    val gRaw = IntArray(safeBins)
    val bRaw = IntArray(safeBins)
    val lRaw = IntArray(safeBins)

    val m0 = colorMatrixValues[0]
    val m1 = colorMatrixValues[1]
    val m2 = colorMatrixValues[2]
    val m4 = colorMatrixValues[4]

    val m5 = colorMatrixValues[5]
    val m6 = colorMatrixValues[6]
    val m7 = colorMatrixValues[7]
    val m9 = colorMatrixValues[9]

    val m10 = colorMatrixValues[10]
    val m11 = colorMatrixValues[11]
    val m12 = colorMatrixValues[12]
    val m14 = colorMatrixValues[14]

    var shadowClipped = 0
    var highlightClipped = 0
    var lumaSum = 0L

    for (pixel in pixels) {
      val r = (pixel ushr 16) and 0xFF
      val g = (pixel ushr 8) and 0xFF
      val b = pixel and 0xFF

      val nr = (m0 * r + m1 * g + m2 * b + m4).roundToInt().coerceIn(0, 255)
      val ng = (m5 * r + m6 * g + m7 * b + m9).roundToInt().coerceIn(0, 255)
      val nb = (m10 * r + m11 * g + m12 * b + m14).roundToInt().coerceIn(0, 255)
      val lum = (0.299f * nr + 0.587f * ng + 0.114f * nb).roundToInt().coerceIn(0, 255)

      rRaw[(nr * safeBins) / 256]++
      gRaw[(ng * safeBins) / 256]++
      bRaw[(nb * safeBins) / 256]++
      lRaw[(lum * safeBins) / 256]++

      if (lum <= 3) shadowClipped++
      if (lum >= 252) highlightClipped++
      lumaSum += lum
    }

    val rSmooth = smoothBins(rRaw)
    val gSmooth = smoothBins(gRaw)
    val bSmooth = smoothBins(bRaw)
    val lSmooth = smoothBins(lRaw)

    // Use a robust peak across all channels so relative channel shifts remain readable
    val peak = maxOf(
      robustPeak(rSmooth),
      robustPeak(gSmooth),
      robustPeak(bSmooth),
      robustPeak(lSmooth),
      1f,
    )

    val total = pixels.size.toFloat().coerceAtLeast(1f)
    return HistogramData(
      redBins = normalize(rSmooth, peak),
      greenBins = normalize(gSmooth, peak),
      blueBins = normalize(bSmooth, peak),
      lumaBins = normalize(lSmooth, peak),
      shadowClippingRatio = shadowClipped / total,
      highlightClippingRatio = highlightClipped / total,
      meanLuminance = (lumaSum / total) / 255f,
    )
  }

  private fun smoothBins(raw: IntArray): FloatArray {
    val n = raw.size
    val out = FloatArray(n)
    for (i in 0 until n) {
      val prev = raw[(i - 1).coerceAtLeast(0)]
      val cur = raw[i]
      val next = raw[(i + 1).coerceAtMost(n - 1)]
      out[i] = 0.2f * prev + 0.6f * cur + 0.2f * next
    }
    return out
  }

  private fun robustPeak(bins: FloatArray): Float {
    if (bins.isEmpty()) return 1f
    val sorted = bins.sorted()
    val p96 = sorted[(sorted.size * 0.96f).toInt().coerceIn(0, sorted.lastIndex)]
    val maxVal = sorted.last()
    // Blend 96th percentile with max so extreme clipping spikes don't flatten the entire waveform
    return max(p96 * 1.15f, maxVal * 0.55f).coerceAtLeast(1f)
  }

  private fun normalize(bins: FloatArray, peak: Float): FloatArray {
    return FloatArray(bins.size) { i ->
      (bins[i] / peak).coerceIn(0f, 1f)
    }
  }
}
