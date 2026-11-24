package com.example.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Rational
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.core.graphics.createBitmap
import com.example.data.model.AspectPreset
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix

enum class CameraFilter(
  val id: String,
  val displayName: String,
  val badgeColor: Color,
) {
  ORIGINAL("original", "Original", Color(0xFF9EA7B8)),
  GRAYSCALE("grayscale", "Grayscale", Color(0xFFDDE2EB)),
  SEPIA("sepia", "Sepia", Color(0xFFD9A066)),
  HIGH_CONTRAST("high_contrast", "High Contrast", Color(0xFFFF6B4A)),
  WARM("warm", "Warm", Color(0xFFF7C948)),
  COOL("cool", "Cool", Color(0xFF5CE1E6)),
}

enum class CameraCropAspect(
  val id: String,
  val label: String,
  val widthWeight: Int?,
  val heightWeight: Int?,
  val editorPreset: AspectPreset,
) {
  FULL("full", "Full", null, null, AspectPreset.FREE),
  SQUARE_1_1("1_1", "1:1", 1, 1, AspectPreset.SQUARE),
  CLASSIC_4_3("4_3", "4:3", 4, 3, AspectPreset.CLASSIC_4_3),
  WIDE_16_9("16_9", "16:9", 16, 9, AspectPreset.WIDE_16_9);

  val ratio: Float?
    get() = if (widthWeight != null && heightWeight != null) {
      widthWeight.toFloat() / heightWeight.toFloat()
    } else {
      null
    }

  fun toRational(): Rational? =
    if (widthWeight != null && heightWeight != null) Rational(widthWeight, heightWeight) else null
}

/**
 * Utility class that builds color transformation matrices, applies filters
 * (such as Grayscale, Sepia, and High Contrast), and crops captured Bitmaps
 * to user-selected aspect ratios.
 */
class FilterManager {

  fun buildBrightnessContrastMatrix(
    brightness: Float = 0f,
    contrast: Float = 0f,
  ): ColorMatrix = Companion.buildBrightnessContrastMatrix(brightness, contrast)

  fun buildColorMatrix(
    filter: CameraFilter,
    brightness: Float = 0f,
    contrast: Float = 0f,
  ): ColorMatrix = Companion.buildColorMatrix(filter, brightness, contrast)

  fun buildComposeColorFilter(
    filter: CameraFilter,
    brightness: Float = 0f,
    contrast: Float = 0f,
  ): ColorFilter? = Companion.buildComposeColorFilter(filter, brightness, contrast)

  fun applyFilter(
    bitmap: Bitmap,
    filter: CameraFilter,
    brightness: Float = 0f,
    contrast: Float = 0f,
  ): Bitmap = Companion.applyFilter(bitmap, filter, brightness, contrast)

  fun cropToAspectRatio(bitmap: Bitmap, aspect: CameraCropAspect): Bitmap =
    Companion.cropToAspectRatio(bitmap, aspect)

  companion object {
    val availableFilters: List<CameraFilter> = CameraFilter.entries
    val availableCropAspects: List<CameraCropAspect> = CameraCropAspect.entries

    fun computeCenteredCropRect(
      width: Int,
      height: Int,
      targetRatio: Float,
    ): IntArray {
      val safeW = width.coerceAtLeast(1)
      val safeH = height.coerceAtLeast(1)
      val currentRatio = safeW.toFloat() / safeH.toFloat()
      if (abs(currentRatio - targetRatio) < 0.005f) {
        return intArrayOf(0, 0, safeW, safeH)
      }
      val (cropW, cropH) = if (currentRatio > targetRatio) {
        (safeH * targetRatio).roundToInt().coerceIn(1, safeW) to safeH
      } else {
        safeW to (safeW / targetRatio).roundToInt().coerceIn(1, safeH)
      }
      val left = ((safeW - cropW) / 2).coerceAtLeast(0)
      val top = ((safeH - cropH) / 2).coerceAtLeast(0)
      return intArrayOf(left, top, cropW, cropH)
    }

    fun cropToAspectRatio(bitmap: Bitmap, aspect: CameraCropAspect): Bitmap {
      val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
      } else {
        bitmap
      }
      val targetRatio = aspect.ratio ?: return safeBitmap
      val rect = computeCenteredCropRect(safeBitmap.width, safeBitmap.height, targetRatio)
      val left = rect[0]
      val top = rect[1]
      val cropW = rect[2]
      val cropH = rect[3]
      if (left == 0 && top == 0 && cropW == safeBitmap.width && cropH == safeBitmap.height) {
        return safeBitmap
      }
      return Bitmap.createBitmap(safeBitmap, left, top, cropW, cropH)
    }

    fun computeBrightnessContrastValues(
      brightness: Float = 0f,
      contrast: Float = 0f,
    ): FloatArray {
      val safeBrightness = brightness.coerceIn(-1f, 1f)
      val safeContrast = contrast.coerceIn(-1f, 1f)

      val contrastScale = (1f + safeContrast * 0.68f).coerceIn(0.32f, 1.85f)
      val contrastTranslate = (1f - contrastScale) * 128f

      val brightnessGain = if (abs(safeBrightness) > 0.001f) {
        (1f + safeBrightness * 0.18f).coerceIn(0.65f, 1.35f)
      } else {
        1f
      }
      val brightnessOffset = if (abs(safeBrightness) > 0.001f) {
        safeBrightness * 64f
      } else {
        0f
      }

      // Combined affine transform: B * (C * x + Ct) + Bt = (B*C) * x + (B*Ct + Bt)
      val combinedScale = contrastScale * brightnessGain
      val combinedTranslate = contrastTranslate * brightnessGain + brightnessOffset

      return floatArrayOf(
        combinedScale, 0f, 0f, 0f, combinedTranslate,
        0f, combinedScale, 0f, 0f, combinedTranslate,
        0f, 0f, combinedScale, 0f, combinedTranslate,
        0f, 0f, 0f, 1f, 0f,
      )
    }

    fun buildBrightnessContrastMatrix(
      brightness: Float = 0f,
      contrast: Float = 0f,
    ): ColorMatrix = ColorMatrix(computeBrightnessContrastValues(brightness, contrast))

    fun buildColorMatrix(
      filter: CameraFilter,
      brightness: Float = 0f,
      contrast: Float = 0f,
    ): ColorMatrix {
      val baseMatrix = when (filter) {
        CameraFilter.ORIGINAL -> ColorMatrix()

        CameraFilter.GRAYSCALE -> ColorMatrix().apply {
          setSaturation(0f)
        }

        CameraFilter.SEPIA -> ColorMatrix(
          floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
          )
        )

        CameraFilter.HIGH_CONTRAST -> {
          val presetContrast = 1.55f
          val translate = (1f - presetContrast) * 128f
          val matrix = ColorMatrix().apply { setSaturation(1.18f) }
          val contrastMatrix = ColorMatrix(
            floatArrayOf(
              presetContrast, 0f, 0f, 0f, translate,
              0f, presetContrast, 0f, 0f, translate,
              0f, 0f, presetContrast, 0f, translate,
              0f, 0f, 0f, 1f, 0f,
            )
          )
          matrix.postConcat(contrastMatrix)
          matrix
        }

        CameraFilter.WARM -> ColorMatrix(
          floatArrayOf(
            1.12f, 0f, 0f, 0f, 10f,
            0f, 1.02f, 0f, 0f, 4f,
            0f, 0f, 0.86f, 0f, -8f,
            0f, 0f, 0f, 1f, 0f,
          )
        )

        CameraFilter.COOL -> {
          val matrix = ColorMatrix().apply { setSaturation(0.92f) }
          val coolTint = ColorMatrix(
            floatArrayOf(
              0.90f, 0f, 0f, 0f, -6f,
              0f, 1.02f, 0f, 0f, 2f,
              0f, 0f, 1.15f, 0f, 10f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
          matrix.postConcat(coolTint)
          matrix
        }
      }

      if (abs(brightness) > 0.001f || abs(contrast) > 0.001f) {
        baseMatrix.postConcat(buildBrightnessContrastMatrix(brightness, contrast))
      }
      return baseMatrix
    }

    fun buildComposeColorFilter(
      filter: CameraFilter,
      brightness: Float = 0f,
      contrast: Float = 0f,
    ): ColorFilter? {
      if (filter == CameraFilter.ORIGINAL && abs(brightness) < 0.005f && abs(contrast) < 0.005f) {
        return null
      }
      return ColorFilter.colorMatrix(
        ComposeColorMatrix(buildColorMatrix(filter, brightness, contrast).array)
      )
    }

    fun applyFilter(
      bitmap: Bitmap,
      filter: CameraFilter,
      brightness: Float = 0f,
      contrast: Float = 0f,
    ): Bitmap {
      val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
      } else {
        bitmap
      }
      if (filter == CameraFilter.ORIGINAL && abs(brightness) < 0.005f && abs(contrast) < 0.005f) {
        return safeBitmap
      }
      val output = createBitmap(safeBitmap.width, safeBitmap.height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(output)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(buildColorMatrix(filter, brightness, contrast))
      }
      canvas.drawBitmap(safeBitmap, 0f, 0f, paint)
      return output
    }
  }
}
