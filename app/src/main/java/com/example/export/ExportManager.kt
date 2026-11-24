package com.example.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.ExportFormat
import com.example.data.model.ExportPlatform
import java.io.File
import java.io.FileOutputStream

object ExportManager {

  fun saveToGallery(
    context: Context,
    bitmap: Bitmap,
    format: ExportFormat,
    quality: Int,
    titlePrefix: String,
  ): Result<Uri> {
    return try {
      val sanitized = titlePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "LuminaEdit" }
      val fileName = "${sanitized}_${System.currentTimeMillis()}.${format.extension}"

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
          put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
          put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LuminaEdit")
          put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values)
          ?: return Result.failure(IllegalStateException("Unable to create MediaStore entry."))

        resolver.openOutputStream(itemUri)?.use { out ->
          val compressFormat = toCompressFormat(format)
          val ok = bitmap.compress(compressFormat, quality.coerceIn(50, 100), out)
          if (!ok) {
            resolver.delete(itemUri, null, null)
            return Result.failure(IllegalStateException("Bitmap compression failed."))
          }
        } ?: run {
          resolver.delete(itemUri, null, null)
          return Result.failure(IllegalStateException("Failed to open gallery output stream."))
        }

        val finalizeValues = ContentValues().apply {
          put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        resolver.update(itemUri, finalizeValues, null, null)
        Result.success(itemUri)
      } else {
        // Zero-permission fallback on API < 29: export via FileProvider cache & share sheet
        prepareShareUri(context, bitmap, format, quality, titlePrefix)
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun prepareShareUri(
    context: Context,
    bitmap: Bitmap,
    format: ExportFormat,
    quality: Int,
    titlePrefix: String,
  ): Result<Uri> {
    return try {
      val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
      // Prune stale exports older than 1 hour
      val cutoff = System.currentTimeMillis() - 3_600_000L
      exportDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }

      val sanitized = titlePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "LuminaEdit" }
      val file = File(exportDir, "${sanitized}_${System.currentTimeMillis()}.${format.extension}")
      FileOutputStream(file).use { out ->
        bitmap.compress(toCompressFormat(format), quality.coerceIn(50, 100), out)
      }
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      Result.success(uri)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Launches targeted social app share if installed; otherwise gracefully opens the Android System Share Sheet.
   * Returns true if the target platform app was found & opened directly, false if system chooser was opened.
   */
  fun shareToPlatform(
    context: Context,
    uri: Uri,
    platform: ExportPlatform,
    mimeType: String,
  ): Boolean {
    val baseIntent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_TEXT, "Edited with Lumina Edit")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val matchedPackage = platform.packageCandidates.firstOrNull { pkg ->
      isPackageInstalled(context, pkg)
    }

    val chooserTitle = if (platform == ExportPlatform.SYSTEM_SHARE) {
      "Share to Social Media"
    } else {
      "Share to ${platform.label}"
    }

    if (matchedPackage != null) {
      val targeted = Intent(baseIntent).apply {
        setPackage(matchedPackage)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      return try {
        context.startActivity(targeted)
        true
      } catch (_: Exception) {
        val chooser = Intent.createChooser(baseIntent, chooserTitle).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        false
      }
    }

    val chooser = Intent.createChooser(baseIntent, chooserTitle).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
    return false
  }

  private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  fun saveVideoToGallery(
    context: Context,
    videoUri: Uri,
    titlePrefix: String,
  ): Result<Uri> {
    return try {
      val sanitized = titlePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "LuminaMotion" }
      val fileName = "${sanitized}_${System.currentTimeMillis()}.mp4"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
          put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
          put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/LuminaEdit")
          put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values)
          ?: return Result.failure(IllegalStateException("Unable to create MediaStore video entry."))

        resolver.openOutputStream(itemUri)?.use { out ->
          resolver.openInputStream(videoUri)?.use { input ->
            input.copyTo(out)
          } ?: return Result.failure(IllegalStateException("Unable to read generated video stream."))
        } ?: return Result.failure(IllegalStateException("Failed to open video gallery stream."))

        val finalize = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        resolver.update(itemUri, finalize, null, null)
        Result.success(itemUri)
      } else {
        Result.success(videoUri)
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun shareVideo(context: Context, videoUri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "video/mp4"
      putExtra(Intent.EXTRA_STREAM, videoUri)
      putExtra(Intent.EXTRA_TEXT, "Animated with Lumina Edit")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Share Video").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }

  private fun toCompressFormat(format: ExportFormat): Bitmap.CompressFormat = when (format) {
    ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
    ExportFormat.PNG -> Bitmap.CompressFormat.PNG
    ExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Bitmap.CompressFormat.WEBP_LOSSY
    } else {
      @Suppress("DEPRECATION")
      Bitmap.CompressFormat.WEBP
    }
  }
}
