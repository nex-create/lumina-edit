package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.FileDataPart
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class AiImageAspect(
  val label: String,
  val apiAspect: AspectRatio?,
) {
  SQUARE_1_1("1:1", null),
  PORTRAIT_4_5("4:5", AspectRatio.PORTRAIT_3x4),
  STORY_9_16("9:16", AspectRatio.PORTRAIT_9x16),
  WIDE_16_9("16:9", AspectRatio.LANDSCAPE_16x9),
}

enum class VideoAspectOption(
  val ratioString: String,
  val orientationLabel: String,
  val aspectValue: Float,
) {
  LANDSCAPE_16_9("16:9", "Landscape", 16f / 9f),
  PORTRAIT_9_16("9:16", "Portrait", 9f / 16f),
}

class AiStudioRepository {

  private val defaultImageModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = IMAGE_MODEL_NAME,
      generationConfig = generationConfig {
        responseModalities = listOf(ResponseModality.IMAGE)
      },
    )
  }

  private val videoModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(modelName = VIDEO_MODEL_NAME)
  }

  private fun buildImageModel(aspect: AiImageAspect): GenerativeModel {
    val targetAspect = aspect.apiAspect ?: return defaultImageModel
    return Firebase.ai.generativeModel(
      modelName = IMAGE_MODEL_NAME,
      generationConfig = generationConfig {
        responseModalities = listOf(ResponseModality.IMAGE)
        imageConfig = ImageConfig.builder()
          .setAspectRatio(targetAspect)
          .setImageSize(ImageSize.SIZE_1K)
          .build()
      },
    )
  }

  suspend fun generateImage(
    prompt: String,
    aspect: AiImageAspect,
  ): Result<Bitmap> {
    return try {
      val model = buildImageModel(aspect)
      val response = withTimeout(IMAGE_TIMEOUT_MS) {
        model.generateContent(prompt.trim())
      }
      val imagePart = response.candidates.firstOrNull()
        ?.content?.parts?.filterIsInstance<ImagePart>()
        ?.firstOrNull()

      val bitmap = imagePart?.image
      if (bitmap != null) {
        Result.success(bitmap)
      } else {
        val modelNote = response.text?.take(200)
        Result.failure(
          IllegalStateException(
            modelNote?.takeIf { it.isNotBlank() }
              ?: "No image was returned by $IMAGE_MODEL_NAME."
          )
        )
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Image generation timed out", e)
      Result.failure(IllegalStateException("Image generation timed out after 60 seconds."))
    } catch (e: QuotaExceededException) {
      Log.e(TAG, "Quota exceeded for image generation", e)
      Result.failure(IllegalStateException("Rate limit or quota exceeded. Please try again later."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Image generation failed", e)
      Result.failure(e)
    }
  }

  suspend fun editImage(
    inputBitmap: Bitmap,
    instruction: String,
    aspect: AiImageAspect,
  ): Result<Bitmap> {
    return try {
      val safeInput = scaleBitmapForPayload(inputBitmap, maxEdge = 1280)
      val model = buildImageModel(aspect)
      val response = withTimeout(IMAGE_TIMEOUT_MS) {
        model.generateContent(
          content {
            image(safeInput)
            text(instruction.trim())
          }
        )
      }
      val imagePart = response.candidates.firstOrNull()
        ?.content?.parts?.filterIsInstance<ImagePart>()
        ?.firstOrNull()

      val bitmap = imagePart?.image
      if (bitmap != null) {
        Result.success(bitmap)
      } else {
        val modelNote = response.text?.take(200)
        Result.failure(
          IllegalStateException(
            modelNote?.takeIf { it.isNotBlank() }
              ?: "No edited image was returned by $IMAGE_MODEL_NAME."
          )
        )
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Image editing timed out", e)
      Result.failure(IllegalStateException("Image edit timed out after 60 seconds."))
    } catch (e: QuotaExceededException) {
      Log.e(TAG, "Quota exceeded for image edit", e)
      Result.failure(IllegalStateException("Rate limit or quota exceeded. Please try again later."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Image editing failed", e)
      Result.failure(e)
    }
  }

  suspend fun animateImageToVideo(
    context: Context,
    inputBitmap: Bitmap,
    motionPrompt: String,
    aspectRatio: VideoAspectOption,
  ): Result<Uri> {
    return try {
      val safeInput = scaleBitmapForPayload(inputBitmap, maxEdge = 1024)
      val promptText = buildString {
        append("Animate this photo into a smooth video in ${aspectRatio.ratioString} (${aspectRatio.orientationLabel}) aspect ratio.")
        if (motionPrompt.isNotBlank()) {
          append(" Motion prompt: ${motionPrompt.trim()}")
        }
      }
      val response = withTimeout(VIDEO_TIMEOUT_MS) {
        videoModel.generateContent(
          content {
            image(safeInput)
            text(promptText)
          }
        )
      }

      val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
      val inlineVideo = parts.filterIsInstance<InlineDataPart>()
        .firstOrNull { it.mimeType.startsWith("video/", ignoreCase = true) }

      if (inlineVideo != null) {
        val savedUri = withContext(Dispatchers.IO) {
          val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
          val outFile = File(exportDir, "lumina_video_${System.currentTimeMillis()}.mp4")
          FileOutputStream(outFile).use { out ->
            out.write(inlineVideo.inlineData)
          }
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        }
        return Result.success(savedUri)
      }

      val fileVideo = parts.filterIsInstance<FileDataPart>()
        .firstOrNull {
          it.mimeType.startsWith("video/", ignoreCase = true) ||
            it.uri.endsWith(".mp4", ignoreCase = true)
        }

      if (fileVideo != null) {
        return Result.success(Uri.parse(fileVideo.uri))
      }

      val fallbackNote = response.text?.trim().orEmpty()
      Result.failure(
        IllegalStateException(
          if (fallbackNote.isNotBlank()) {
            "Video stream unavailable from $VIDEO_MODEL_NAME: ${fallbackNote.take(220)}"
          } else {
            "No video output was returned by $VIDEO_MODEL_NAME."
          }
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Video animation timed out", e)
      Result.failure(IllegalStateException("Video generation timed out after 60 seconds."))
    } catch (e: QuotaExceededException) {
      Log.e(TAG, "Quota exceeded for video generation", e)
      Result.failure(IllegalStateException("Rate limit or quota exceeded. Please try again later."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Video animation failed", e)
      Result.failure(e)
    }
  }

  private fun scaleBitmapForPayload(bitmap: Bitmap, maxEdge: Int): Bitmap {
    val safe = if (bitmap.config == Bitmap.Config.HARDWARE) {
      bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
      bitmap
    }
    val longest = max(safe.width, safe.height)
    if (longest <= maxEdge || longest <= 0) return safe
    val scale = maxEdge.toFloat() / longest.toFloat()
    return safe.scale(
      (safe.width * scale).roundToInt().coerceAtLeast(1),
      (safe.height * scale).roundToInt().coerceAtLeast(1),
      filter = true,
    )
  }

  private companion object {
    const val TAG = "AiStudioRepository"
    const val IMAGE_MODEL_NAME = "gemini-nano-banana-2.1"
    const val VIDEO_MODEL_NAME = "gemini-omni-1.2-flash"
    const val IMAGE_TIMEOUT_MS = 60_000L
    const val VIDEO_TIMEOUT_MS = 60_000L
  }
}
