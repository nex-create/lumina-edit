package com.example.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.ui.graphics.ColorFilter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.example.data.model.EditRecipe
import com.example.data.model.PhotoSource
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix

object PhotoEngine {

  fun buildAndroidColorMatrix(recipe: EditRecipe): ColorMatrix {
    val preset = FilterCatalog.findById(recipe.filterId)
    val intensity = if (preset.id == "original") 0f else recipe.filterIntensity.coerceIn(0f, 1f)

    val combinedExposure = (recipe.exposure + preset.exposureBias * intensity).coerceIn(-1f, 1f)
    val combinedContrast = (recipe.contrast + preset.contrastBias * intensity).coerceIn(-1f, 1f)
    val combinedSaturation = (recipe.saturation + preset.saturationBias * intensity).coerceIn(-1f, 1f)
    val combinedWarmth = (recipe.warmth + preset.warmthBias * intensity).coerceIn(-1f, 1f)
    val combinedTint = (recipe.tint + preset.tintBias * intensity).coerceIn(-1f, 1f)
    val combinedFade = (recipe.fade + preset.fadeBias * intensity).coerceIn(0f, 1f)

    val matrix = ColorMatrix()

    // 1. Saturation (special-case monochrome so Noir 35mm stays crisp B&W even with tweaks)
    val satValue = (1f + combinedSaturation).coerceIn(0f, 2f)
    val satMatrix = ColorMatrix().apply { setSaturation(satValue) }
    matrix.postConcat(satMatrix)

    // 2. Contrast & Brightness intensity via FilterManager
    matrix.postConcat(
      FilterManager.buildBrightnessContrastMatrix(
        brightness = combinedExposure,
        contrast = combinedContrast,
      )
    )

    // 3. Matte Fade lift (Hypic/film black-floor lift)
    if (combinedFade > 0.001f) {
      val fadeLift = combinedFade * 36f
      val fadeScale = 1f - combinedFade * 0.14f
      val fadeMatrix = ColorMatrix(
        floatArrayOf(
          fadeScale, 0f, 0f, 0f, fadeLift,
          0f, fadeScale, 0f, 0f, fadeLift,
          0f, 0f, fadeScale, 0f, fadeLift,
          0f, 0f, 0f, 1f, 0f,
        )
      )
      matrix.postConcat(fadeMatrix)
    }

    // 4. Warmth, Tint & Preset Channel Gains
    val rPreset = 1f + (preset.rGain - 1f) * intensity
    val gPreset = 1f + (preset.gGain - 1f) * intensity
    val bPreset = 1f + (preset.bGain - 1f) * intensity

    val rScale = (rPreset + combinedWarmth * 0.18f + combinedTint * 0.06f).coerceIn(0.6f, 1.5f)
    val gScale = (gPreset - combinedTint * 0.14f).coerceIn(0.6f, 1.5f)
    val bScale = (bPreset - combinedWarmth * 0.18f + combinedTint * 0.06f).coerceIn(0.6f, 1.5f)

    val tempMatrix = ColorMatrix(
      floatArrayOf(
        rScale, 0f, 0f, 0f, combinedWarmth * 10f,
        0f, gScale, 0f, 0f, -combinedTint * 8f,
        0f, 0f, bScale, 0f, -combinedWarmth * 10f,
        0f, 0f, 0f, 1f, 0f,
      )
    )
    matrix.postConcat(tempMatrix)

    return matrix
  }

  fun buildComposeColorFilter(recipe: EditRecipe): ColorFilter {
    val androidMatrix = buildAndroidColorMatrix(recipe)
    return ColorFilter.colorMatrix(ComposeColorMatrix(androidMatrix.array))
  }

  fun loadBitmapForSource(
    context: Context,
    source: PhotoSource,
    maxEdge: Int = 1600,
  ): Bitmap? {
    return try {
      when (source) {
        is PhotoSource.Sample -> {
          val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeResource(context.resources, source.sample.drawableRes, opts)
          val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
          }
          val decoded = BitmapFactory.decodeResource(context.resources, source.sample.drawableRes, decodeOpts)
          decoded?.let { scaleDownIfNeeded(it, maxEdge) }
        }
        is PhotoSource.Gallery -> {
          val uri = Uri.parse(source.uriString)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val imageSource = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
              decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
              val w = info.size.width
              val h = info.size.height
              val longest = max(w, h)
              if (longest > maxEdge && longest > 0) {
                val scale = maxEdge.toFloat() / longest.toFloat()
                decoder.setTargetSize(
                  (w * scale).roundToInt().coerceAtLeast(1),
                  (h * scale).roundToInt().coerceAtLeast(1)
                )
              }
            }
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
          } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
              BitmapFactory.decodeStream(stream, null, bounds)
            }
            val decodeOpts = BitmapFactory.Options().apply {
              inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
              inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
              BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
            raw?.let { scaleDownIfNeeded(it, maxEdge) }
          }
        }
      }
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Produces the transformed bitmap (rotation + flip + straighten + crop) BEFORE color grading,
   * or full final bitmap when [applyColorEffects] is true.
   */
  fun renderGeometryBitmap(
    source: Bitmap,
    recipe: EditRecipe,
    applyCrop: Boolean = true,
    maxOutputEdge: Int = 2048,
  ): Bitmap {
    // 1. Apply quarter-turn rotation + flips
    val matrix = Matrix()
    if (recipe.flipHorizontal || recipe.flipVertical) {
      matrix.postScale(
        if (recipe.flipHorizontal) -1f else 1f,
        if (recipe.flipVertical) -1f else 1f,
        source.width / 2f,
        source.height / 2f
      )
    }
    if (recipe.rotationDegrees != 0) {
      matrix.postRotate(recipe.rotationDegrees.toFloat())
    }
    if (recipe.straightenDegrees != 0f) {
      matrix.postRotate(recipe.straightenDegrees)
    }

    val transformed = if (!matrix.isIdentity) {
      Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } else {
      source
    }

    val cropped = if (applyCrop) {
      val left = (recipe.cropLeft.coerceIn(0f, 0.94f) * transformed.width).roundToInt().coerceIn(0, transformed.width - 2)
      val top = (recipe.cropTop.coerceIn(0f, 0.94f) * transformed.height).roundToInt().coerceIn(0, transformed.height - 2)
      val right = (recipe.cropRight.coerceIn(recipe.cropLeft + 0.05f, 1f) * transformed.width).roundToInt().coerceIn(left + 1, transformed.width)
      val bottom = (recipe.cropBottom.coerceIn(recipe.cropTop + 0.05f, 1f) * transformed.height).roundToInt().coerceIn(top + 1, transformed.height)
      val w = (right - left).coerceAtLeast(1)
      val h = (bottom - top).coerceAtLeast(1)
      Bitmap.createBitmap(transformed, left, top, w, h)
    } else {
      transformed
    }

    return scaleDownIfNeeded(cropped, maxOutputEdge)
  }

  fun renderFinalEditedBitmap(
    source: Bitmap,
    recipe: EditRecipe,
    maxOutputEdge: Int = 2048,
    addWatermark: Boolean = false,
  ): Bitmap {
    val geoBitmap = renderGeometryBitmap(source, recipe, applyCrop = true, maxOutputEdge = maxOutputEdge)
    val output = createBitmap(geoBitmap.width, geoBitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      colorFilter = ColorMatrixColorFilter(buildAndroidColorMatrix(recipe))
    }
    canvas.drawBitmap(geoBitmap, 0f, 0f, paint)

    // Radial Vignette overlay
    if (recipe.vignette > 0.01f) {
      val cx = output.width / 2f
      val cy = output.height / 2f
      val radius = max(output.width, output.height) * 0.72f
      val edgeAlpha = (recipe.vignette.coerceIn(0f, 1f) * 190f).roundToInt().coerceIn(0, 210)
      val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
          cx,
          cy,
          radius,
          intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(edgeAlpha, 8, 8, 12)),
          floatArrayOf(0f, 0.45f, 1f),
          Shader.TileMode.CLAMP,
        )
      }
      canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), vignettePaint)
    }

    // Subtle Editorial Watermark
    if (addWatermark) {
      val minDim = min(output.width, output.height).toFloat()
      val textSize = (minDim * 0.032f).coerceIn(18f, 52f)
      val pad = textSize * 0.85f
      val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 14, 16, 22)
      }
      val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 245, 247, 250)
        this.textSize = textSize
        letterSpacing = 0.16f
        isFakeBoldText = true
      }
      val label = "LUMINA EDIT"
      val textWidth = textPaint.measureText(label)
      val rect = RectF(
        output.width - textWidth - pad * 2.2f,
        output.height - textSize - pad * 1.9f,
        output.width - pad * 0.7f,
        output.height - pad * 0.7f,
      )
      canvas.drawRoundRect(rect, textSize * 0.45f, textSize * 0.45f, pillPaint)
      canvas.drawText(label, rect.left + pad * 0.75f, rect.bottom - pad * 0.42f, textPaint)
    }

    return output
  }

  fun computeCroppedDimensions(source: Bitmap, recipe: EditRecipe): Pair<Int, Int> {
    val swap = recipe.rotationDegrees == 90 || recipe.rotationDegrees == 270
    val baseW = if (swap) source.height else source.width
    val baseH = if (swap) source.width else source.height
    val w = ((recipe.cropRight - recipe.cropLeft).coerceAtLeast(0.05f) * baseW).roundToInt().coerceAtLeast(1)
    val h = ((recipe.cropBottom - recipe.cropTop).coerceAtLeast(0.05f) * baseH).roundToInt().coerceAtLeast(1)
    return w to h
  }

  fun encodeThumbnailBase64(source: Bitmap, recipe: EditRecipe): String? {
    return try {
      val thumb = renderFinalEditedBitmap(source, recipe, maxOutputEdge = 240, addWatermark = false)
      var quality = 62
      var bytes: ByteArray
      do {
        val baos = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        bytes = baos.toByteArray()
        quality -= 10
      } while (bytes.size > 65_000 && quality >= 25)
      Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Exception) {
      null
    }
  }

  fun decodeThumbnailBitmap(base64: String?): Bitmap? {
    if (base64.isNullOrBlank()) return null
    return try {
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
      null
    }
  }

  private fun calculateSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    val longest = max(width, height)
    while (longest / sample > maxEdge * 2) {
      sample *= 2
    }
    return sample.coerceAtLeast(1)
  }

  private fun scaleDownIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= maxEdge || longest <= 0) return bitmap
    val ratio = maxEdge.toFloat() / longest.toFloat()
    val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
    val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
    return bitmap.scale(targetW, targetH, filter = true)
  }
}
