package com.example.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.model.CloudPhotoProject
import com.example.data.model.EditRecipe
import com.example.data.model.ExportFormat
import com.example.data.model.PhotoSource
import com.example.editor.PhotoEngine
import com.example.export.ExportManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

enum class SyncState {
  IDLE,
  SYNCING,
  SYNCED,
  OFFLINE_PENDING,
  ERROR,
}

data class PhotoSyncStatus(
  val state: SyncState = SyncState.IDLE,
  val activeTitle: String? = null,
  val pendingCount: Int = 0,
  val lastSyncedAtMillis: Long? = null,
  val lastMessage: String? = null,
)

private data class PendingStorageUpload(
  val projectId: String,
  val title: String,
  val bitmap: Bitmap,
  val recipe: EditRecipe,
  val deviceName: String,
)

/**
 * Service that automatically saves edited photos to Firebase Firestore (edit recipe & metadata)
 * and Firebase Storage (full-resolution rendered image), and enables cross-device gallery sync.
 */
class PhotoCloudSyncService(
  private val repository: CloudSyncRepository,
  private val currentUserId: String,
  private val scope: CoroutineScope,
  private val storage: FirebaseStorage = Firebase.storage,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  private val _syncStatus = MutableStateFlow(PhotoSyncStatus())
  val syncStatus: StateFlow<PhotoSyncStatus> = _syncStatus.asStateFlow()

  private val pendingUploads = ConcurrentHashMap<String, PendingStorageUpload>()
  private var debounceJob: Job? = null
  private var connectivityManager: ConnectivityManager? = null
  private var networkCallback: ConnectivityManager.NetworkCallback? = null

  fun startNetworkObserver(context: Context) {
    if (networkCallback != null) return
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return
    connectivityManager = cm
    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        if (pendingUploads.isNotEmpty()) {
          scope.launch { retryPendingUploads() }
        }
      }
    }
    try {
      cm.registerDefaultNetworkCallback(callback)
      networkCallback = callback
    } catch (e: Exception) {
      Log.w(TAG, "Unable to register network observer", e)
    }
  }

  fun release() {
    debounceJob?.cancel()
    networkCallback?.let { cb ->
      try {
        connectivityManager?.unregisterNetworkCallback(cb)
      } catch (_: Exception) {
      }
    }
    networkCallback = null
  }

  fun scheduleAutoSync(
    source: PhotoSource,
    existingProjectId: String?,
    title: String,
    sourceBitmap: Bitmap,
    recipe: EditRecipe,
    immediate: Boolean = false,
    onSyncedProjectId: (String) -> Unit,
  ) {
    debounceJob?.cancel()
    debounceJob = scope.launch {
      if (!immediate) {
        delay(AUTO_SYNC_DEBOUNCE_MS)
      }
      val result = syncEditedPhotoNow(
        source = source,
        existingProjectId = existingProjectId,
        title = title,
        sourceBitmap = sourceBitmap,
        recipe = recipe,
      )
      result.onSuccess { synced ->
        onSyncedProjectId(synced.projectId)
      }
    }
  }

  data class SyncOutcome(
    val projectId: String,
    val storageUploaded: Boolean,
    val storageErrorMessage: String? = null,
  )

  suspend fun syncEditedPhotoNow(
    source: PhotoSource,
    existingProjectId: String?,
    title: String,
    sourceBitmap: Bitmap,
    recipe: EditRecipe,
  ): Result<SyncOutcome> {
    val activeUid = auth.currentUser?.uid
    if (activeUid.isNullOrBlank() || activeUid != currentUserId) {
      return Result.failure(IllegalStateException("Sign in with Google before syncing photos."))
    }

    val safeTitle = title.trim().ifBlank { "Untitled Edit" }.take(100)
    val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80)

    _syncStatus.update {
      it.copy(
        state = SyncState.SYNCING,
        activeTitle = safeTitle,
        lastMessage = "Saving \"$safeTitle\" to Cloud Vault...",
      )
    }

    // Step 1: Prepare thumbnail & persist recipe metadata in Firestore
    val thumbBase64 = withContext(Dispatchers.Default) {
      PhotoEngine.encodeThumbnailBase64(sourceBitmap, recipe)
    }
    val sourceType = if (source is PhotoSource.Sample) "sample" else "gallery"
    val sampleKey = (source as? PhotoSource.Sample)?.sample?.key
    val localUri = (source as? PhotoSource.Gallery)?.uriString

    val firestoreResult = repository.upsertProjectBackup(
      userId = currentUserId,
      existingId = existingProjectId,
      title = safeTitle,
      sourceType = sourceType,
      sampleKey = sampleKey,
      localUri = localUri,
      thumbnailBase64 = thumbBase64,
      recipe = recipe,
      deviceName = deviceName,
    )

    val projectId = firestoreResult.getOrElse { err ->
      _syncStatus.update {
        it.copy(
          state = SyncState.ERROR,
          activeTitle = null,
          pendingCount = pendingUploads.size,
          lastMessage = err.localizedMessage ?: "Firestore backup failed",
        )
      }
      return Result.failure(err)
    }

    // Step 2: Render full-resolution edited JPEG and upload to Firebase Storage
    val uploadOutcome = uploadRenderedPhotoToStorage(
      projectId = projectId,
      title = safeTitle,
      sourceBitmap = sourceBitmap,
      recipe = recipe,
      deviceName = deviceName,
    )

    return uploadOutcome.fold(
      onSuccess = {
        pendingUploads.remove(projectId)
        _syncStatus.update {
          it.copy(
            state = SyncState.SYNCED,
            activeTitle = null,
            pendingCount = pendingUploads.size,
            lastSyncedAtMillis = System.currentTimeMillis(),
            lastMessage = "Synced \"$safeTitle\" to Firestore & Storage",
          )
        }
        Result.success(SyncOutcome(projectId = projectId, storageUploaded = true))
      },
      onFailure = { err ->
        pendingUploads[projectId] = PendingStorageUpload(
          projectId = projectId,
          title = safeTitle,
          bitmap = sourceBitmap,
          recipe = recipe,
          deviceName = deviceName,
        )
        val isStorageSetupIssue = isBucketUnconfiguredError(err)
        val friendlyMessage = if (isStorageSetupIssue) {
          "Recipe saved to Firestore. Enable Firebase Storage in Firebase Console to upload HD photo."
        } else {
          err.localizedMessage ?: "Recipe saved; HD Storage upload queued for retry."
        }
        _syncStatus.update {
          it.copy(
            state = if (isStorageSetupIssue) SyncState.ERROR else SyncState.OFFLINE_PENDING,
            activeTitle = null,
            pendingCount = pendingUploads.size,
            lastMessage = friendlyMessage,
          )
        }
        // Firestore recipe + thumbnail already succeeded, so return success with storageUploaded = false
        Result.success(
          SyncOutcome(
            projectId = projectId,
            storageUploaded = false,
            storageErrorMessage = friendlyMessage,
          )
        )
      },
    )
  }

  suspend fun retryPendingUploads(): Int {
    if (pendingUploads.isEmpty()) return 0
    val snapshot = pendingUploads.values.toList()
    var uploadedCount = 0
    for (item in snapshot) {
      val res = uploadRenderedPhotoToStorage(
        projectId = item.projectId,
        title = item.title,
        sourceBitmap = item.bitmap,
        recipe = item.recipe,
        deviceName = item.deviceName,
      )
      if (res.isSuccess) {
        pendingUploads.remove(item.projectId)
        uploadedCount++
      }
    }
    _syncStatus.update {
      it.copy(
        state = if (pendingUploads.isEmpty()) SyncState.SYNCED else SyncState.OFFLINE_PENDING,
        pendingCount = pendingUploads.size,
        lastSyncedAtMillis = if (uploadedCount > 0) System.currentTimeMillis() else it.lastSyncedAtMillis,
        lastMessage = if (pendingUploads.isEmpty()) {
          "All edited photos synced across devices"
        } else {
          "${pendingUploads.size} HD upload(s) still pending"
        },
      )
    }
    return uploadedCount
  }

  private suspend fun uploadRenderedPhotoToStorage(
    projectId: String,
    title: String,
    sourceBitmap: Bitmap,
    recipe: EditRecipe,
    deviceName: String,
  ): Result<Unit> {
    return try {
      val uid = auth.currentUser?.uid
        ?: return Result.failure(IllegalStateException("User not authenticated."))
      check(uid == currentUserId) { "Cross-user storage upload rejected." }

      val jpegBytes = withContext(Dispatchers.Default) {
        val rendered = PhotoEngine.renderFinalEditedBitmap(
          source = sourceBitmap,
          recipe = recipe,
          maxOutputEdge = 2048,
          addWatermark = false,
        )
        val baos = ByteArrayOutputStream()
        rendered.compress(Bitmap.CompressFormat.JPEG, 92, baos)
        baos.toByteArray()
      }

      val safeDocId = projectId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
      val storagePath = "users/$uid/edited_photos/$safeDocId.jpg"
      val ref = storage.reference.child(storagePath)

      val metadata = storageMetadata {
        contentType = "image/jpeg"
        setCustomMetadata("projectId", safeDocId)
        setCustomMetadata("title", title.take(80))
        setCustomMetadata("filterId", recipe.filterId)
        setCustomMetadata("aspectRatio", recipe.aspectRatio.key)
        setCustomMetadata("deviceName", deviceName)
      }

      ref.putBytes(jpegBytes, metadata).await()
      val downloadUrl = ref.downloadUrl.await().toString()

      repository.attachStorageAsset(
        userId = uid,
        projectId = projectId,
        storagePath = storagePath,
        downloadUrl = downloadUrl,
        sizeBytes = jpegBytes.size.toLong(),
        deviceName = deviceName,
      ).getOrThrow()

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Firebase Storage upload failed for $projectId", e)
      Result.failure(e)
    }
  }

  suspend fun downloadSyncedBitmap(
    context: Context,
    project: CloudPhotoProject,
    maxEdge: Int = 1800,
  ): Bitmap? {
    val cacheFile = getOrDownloadCloudCacheFile(context, project) ?: return null
    return withContext(Dispatchers.IO) {
      decodeSampledFile(cacheFile, maxEdge)
    }
  }

  suspend fun saveCloudProjectToDeviceGallery(
    context: Context,
    project: CloudPhotoProject,
  ): Result<Uri> {
    val appCtx = context.applicationContext
    val bmp = downloadSyncedBitmap(appCtx, project, maxEdge = 2400)
      ?: PhotoEngine.decodeThumbnailBitmap(project.thumbnailBase64)
      ?: return Result.failure(IllegalStateException("Cloud photo not available offline."))

    val res = withContext(Dispatchers.IO) {
      ExportManager.saveToGallery(
        context = appCtx,
        bitmap = bmp,
        format = ExportFormat.JPEG,
        quality = 94,
        titlePrefix = project.title.ifBlank { "LuminaCloud" },
      )
    }
    res.onSuccess {
      markProjectSavedToLocalGallery(appCtx, project)
    }
    return res
  }

  suspend fun syncCloudGalleryToDevice(
    context: Context,
    projects: List<CloudPhotoProject>,
  ): Result<Int> {
    val appCtx = context.applicationContext
    val prefs = appCtx.getSharedPreferences(PREFS_SYNCED_GALLERY, Context.MODE_PRIVATE)
    var savedCount = 0

    for (project in projects) {
      if (project.storagePath.isNullOrBlank() && project.storageDownloadUrl.isNullOrBlank()) continue
      val versionKey = syncedGalleryKey(project)
      if (prefs.getBoolean(versionKey, false)) continue

      val bmp = downloadSyncedBitmap(appCtx, project, maxEdge = 2400) ?: continue
      val saved = withContext(Dispatchers.IO) {
        ExportManager.saveToGallery(
          context = appCtx,
          bitmap = bmp,
          format = ExportFormat.JPEG,
          quality = 94,
          titlePrefix = project.title.ifBlank { "LuminaSync" },
        )
      }
      if (saved.isSuccess) {
        prefs.edit().putBoolean(versionKey, true).apply()
        savedCount++
      }
    }

    _syncStatus.update {
      it.copy(
        lastMessage = if (savedCount > 0) {
          "Saved $savedCount synced photo(s) to Pictures/LuminaEdit"
        } else {
          "Device gallery is already up to date"
        },
      )
    }
    return Result.success(savedCount)
  }

  suspend fun deleteSyncedProject(
    userId: String,
    project: CloudPhotoProject,
  ): Result<Unit> {
    pendingUploads.remove(project.id)
    val path = project.storagePath
    if (!path.isNullOrBlank()) {
      try {
        storage.reference.child(path).delete().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Ignore missing objects in Storage so Firestore cleanup still completes
        Log.w(TAG, "Storage delete skipped for $path", e)
      }
    }
    return repository.deleteProject(userId, project.id)
  }

  private suspend fun getOrDownloadCloudCacheFile(
    context: Context,
    project: CloudPhotoProject,
  ): File? {
    val cacheDir = File(context.applicationContext.cacheDir, "cloud_gallery").apply { mkdirs() }
    val versionStamp = project.updatedAt?.seconds ?: project.syncedPhotoSizeBytes
    val targetFile = File(cacheDir, "${project.id}_$versionStamp.jpg")
    if (targetFile.exists() && targetFile.length() > 0L) {
      return targetFile
    }

    // Clean older versions for this project
    cacheDir.listFiles()?.filter { it.name.startsWith("${project.id}_") }?.forEach { it.delete() }

    val storagePath = project.storagePath
    if (!storagePath.isNullOrBlank()) {
      val downloaded = try {
        storage.reference.child(storagePath).getFile(targetFile).await()
        targetFile.exists() && targetFile.length() > 0L
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Storage SDK download failed for $storagePath", e)
        false
      }
      if (downloaded) return targetFile
    }

    val urlString = project.storageDownloadUrl
    if (!urlString.isNullOrBlank()) {
      val downloadedFromUrl = withContext(Dispatchers.IO) {
        try {
          URL(urlString).openStream().use { input ->
            FileOutputStream(targetFile).use { output ->
              input.copyTo(output)
            }
          }
          targetFile.exists() && targetFile.length() > 0L
        } catch (e: Exception) {
          Log.w(TAG, "HTTP download failed for $urlString", e)
          targetFile.delete()
          false
        }
      }
      if (downloadedFromUrl) return targetFile
    }

    return null
  }

  private fun markProjectSavedToLocalGallery(context: Context, project: CloudPhotoProject) {
    val prefs = context.getSharedPreferences(PREFS_SYNCED_GALLERY, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(syncedGalleryKey(project), true).apply()
  }

  private fun syncedGalleryKey(project: CloudPhotoProject): String =
    "${project.id}_${project.updatedAt?.seconds ?: project.syncedPhotoSizeBytes}"

  private fun decodeSampledFile(file: File, maxEdge: Int): Bitmap? {
    return try {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, bounds)
      val longest = max(bounds.outWidth, bounds.outHeight)
      var sample = 1
      while (longest / sample > maxEdge * 2) {
        sample *= 2
      }
      val opts = BitmapFactory.Options().apply {
        inSampleSize = sample.coerceAtLeast(1)
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
      val bmpLongest = max(bmp.width, bmp.height)
      if (bmpLongest > maxEdge && bmpLongest > 0) {
        val ratio = maxEdge.toFloat() / bmpLongest.toFloat()
        Bitmap.createScaledBitmap(
          bmp,
          (bmp.width * ratio).roundToInt().coerceAtLeast(1),
          (bmp.height * ratio).roundToInt().coerceAtLeast(1),
          true,
        )
      } else {
        bmp
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun isBucketUnconfiguredError(error: Throwable): Boolean {
    if (error is StorageException) {
      return error.errorCode == StorageException.ERROR_BUCKET_NOT_FOUND ||
        error.errorCode == StorageException.ERROR_PROJECT_NOT_FOUND ||
        error.httpResultCode == 404
    }
    val msg = error.localizedMessage.orEmpty()
    return msg.contains("bucket", ignoreCase = true) || msg.contains("404")
  }

  private companion object {
    const val TAG = "PhotoCloudSyncService"
    const val AUTO_SYNC_DEBOUNCE_MS = 1600L
    const val PREFS_SYNCED_GALLERY = "lumina_cloud_gallery_sync"
  }
}
