package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.CloudPhotoProject
import com.example.data.model.CloudPreset
import com.example.data.model.EditRecipe
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val uid = requireUserId()
    if (uid != userId || userId.isBlank()) {
      emit(null)
      return@flow
    }
    val path = "users/$userId"
    emitAll(
      db.collection("users").document(userId)
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  fun observeProjects(userId: String): Flow<List<CloudPhotoProject>> = flow {
    val uid = requireUserId()
    if (uid != userId || userId.isBlank()) {
      emit(emptyList())
      return@flow
    }
    val path = "users/$userId/projects"
    emitAll(
      db.collection("users").document(userId).collection("projects")
        .orderBy("updatedAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(CloudPhotoProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observePresets(userId: String): Flow<List<CloudPreset>> = flow {
    val uid = requireUserId()
    if (uid != userId || userId.isBlank()) {
      emit(emptyList())
      return@flow
    }
    val path = "users/$userId/presets"
    emitAll(
      db.collection("users").document(userId).collection("presets")
        .orderBy("updatedAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(CloudPreset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
    val path = "users/${user.uid}"
    return try {
      val uid = requireUserId()
      check(uid == user.uid) { "Signed-in user changed." }
      val docRef = db.collection("users").document(uid)
      val existing = docRef.get().await()
      val safeName = (user.displayName?.trim().takeIf { !it.isNullOrBlank() } ?: "Studio Creator").take(80)
      val safeEmail = (user.email?.trim() ?: "").take(120)
      val safePhoto = user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.take(500)

      if (!existing.exists()) {
        val payload = mapOf(
          "userId" to uid,
          "displayName" to safeName,
          "email" to safeEmail,
          "photoUrl" to safePhoto,
          "autoSyncEnabled" to true,
          "totalBackups" to 0L,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null }
        docRef.set(payload).await()
      } else {
        val updatePayload = mapOf(
          "displayName" to safeName,
          "email" to safeEmail,
          "photoUrl" to safePhoto,
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null }
        docRef.update(updatePayload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun setAutoSync(userId: String, enabled: Boolean): Result<Unit> {
    val path = "users/$userId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Cross-user write rejected." }
      db.collection("users").document(uid).update(
        mapOf(
          "autoSyncEnabled" to enabled,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun upsertProjectBackup(
    userId: String,
    existingId: String?,
    title: String,
    sourceType: String,
    sampleKey: String?,
    localUri: String?,
    thumbnailBase64: String?,
    recipe: EditRecipe,
    deviceName: String,
    storagePath: String? = null,
    storageDownloadUrl: String? = null,
    syncedPhotoSizeBytes: Long? = null,
  ): Result<String> {
    var path = "users/$userId/projects"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Cross-user write rejected." }
      val collection = db.collection("users").document(uid).collection("projects")
      val isCreate = existingId.isNullOrBlank()
      val docRef = if (isCreate) collection.document() else collection.document(existingId!!)
      path = docRef.path

      val safeTitle = title.trim().ifBlank { "Untitled Edit" }.take(100)
      val safeSource = if (sourceType == "gallery") "gallery" else "sample"
      val safeSampleKey = sampleKey?.takeIf { it.isNotBlank() }?.take(60)
      val safeLocalUri = localUri?.takeIf { it.isNotBlank() }?.take(600)
      val safeThumb = thumbnailBase64?.takeIf { it.isNotBlank() && it.length <= 120_000 }
      val safeStoragePath = storagePath?.takeIf { it.isNotBlank() }?.take(300)
      val safeDownloadUrl = storageDownloadUrl?.takeIf { it.isNotBlank() }?.take(1200)
      val safeSizeBytes = syncedPhotoSizeBytes?.coerceIn(0L, 104_857_600L)
      val safeDevice = deviceName.trim().ifBlank { "Android Studio" }.take(80)
      val safeCropLeft = recipe.cropLeft.toDouble().coerceIn(0.0, 0.94)
      val safeCropTop = recipe.cropTop.toDouble().coerceIn(0.0, 0.94)
      val safeCropRight = recipe.cropRight.toDouble().coerceIn(safeCropLeft + 0.05, 1.0)
      val safeCropBottom = recipe.cropBottom.toDouble().coerceIn(safeCropTop + 0.05, 1.0)
      val safeRotation = recipe.rotationDegrees.let { if (it in listOf(0, 90, 180, 270)) it.toLong() else 0L }

      if (isCreate) {
        val createPayload = mapOf(
          "userId" to uid,
          "title" to safeTitle,
          "sourceType" to safeSource,
          "sampleKey" to safeSampleKey,
          "localUri" to safeLocalUri,
          "thumbnailBase64" to safeThumb,
          "storagePath" to safeStoragePath,
          "storageDownloadUrl" to safeDownloadUrl,
          "syncedPhotoSizeBytes" to safeSizeBytes,
          "filterId" to recipe.filterId.ifBlank { "original" }.take(40),
          "filterIntensity" to recipe.filterIntensity.toDouble().coerceIn(0.0, 1.0),
          "exposure" to recipe.exposure.toDouble().coerceIn(-1.0, 1.0),
          "contrast" to recipe.contrast.toDouble().coerceIn(-1.0, 1.0),
          "saturation" to recipe.saturation.toDouble().coerceIn(-1.0, 1.0),
          "warmth" to recipe.warmth.toDouble().coerceIn(-1.0, 1.0),
          "tint" to recipe.tint.toDouble().coerceIn(-1.0, 1.0),
          "fade" to recipe.fade.toDouble().coerceIn(0.0, 1.0),
          "vignette" to recipe.vignette.toDouble().coerceIn(0.0, 1.0),
          "aspectRatio" to recipe.aspectRatio.key.take(20),
          "cropLeft" to safeCropLeft,
          "cropTop" to safeCropTop,
          "cropRight" to safeCropRight,
          "cropBottom" to safeCropBottom,
          "rotationDegrees" to safeRotation,
          "straightenDegrees" to recipe.straightenDegrees.toDouble().coerceIn(-45.0, 45.0),
          "flipHorizontal" to recipe.flipHorizontal,
          "flipVertical" to recipe.flipVertical,
          "exportedCount" to 0L,
          "deviceName" to safeDevice,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null }
        docRef.set(createPayload).await()
        // Increment totalBackups counter cleanly
        db.collection("users").document(uid).update(
          mapOf(
            "totalBackups" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        val updatePayload = mapOf(
          "title" to safeTitle,
          "localUri" to safeLocalUri,
          "thumbnailBase64" to safeThumb,
          "storagePath" to safeStoragePath,
          "storageDownloadUrl" to safeDownloadUrl,
          "syncedPhotoSizeBytes" to safeSizeBytes,
          "filterId" to recipe.filterId.ifBlank { "original" }.take(40),
          "filterIntensity" to recipe.filterIntensity.toDouble().coerceIn(0.0, 1.0),
          "exposure" to recipe.exposure.toDouble().coerceIn(-1.0, 1.0),
          "contrast" to recipe.contrast.toDouble().coerceIn(-1.0, 1.0),
          "saturation" to recipe.saturation.toDouble().coerceIn(-1.0, 1.0),
          "warmth" to recipe.warmth.toDouble().coerceIn(-1.0, 1.0),
          "tint" to recipe.tint.toDouble().coerceIn(-1.0, 1.0),
          "fade" to recipe.fade.toDouble().coerceIn(0.0, 1.0),
          "vignette" to recipe.vignette.toDouble().coerceIn(0.0, 1.0),
          "aspectRatio" to recipe.aspectRatio.key.take(20),
          "cropLeft" to safeCropLeft,
          "cropTop" to safeCropTop,
          "cropRight" to safeCropRight,
          "cropBottom" to safeCropBottom,
          "rotationDegrees" to safeRotation,
          "straightenDegrees" to recipe.straightenDegrees.toDouble().coerceIn(-45.0, 45.0),
          "flipHorizontal" to recipe.flipHorizontal,
          "flipVertical" to recipe.flipVertical,
          "deviceName" to safeDevice,
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null }
        docRef.update(updatePayload).await()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (existingId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun attachStorageAsset(
    userId: String,
    projectId: String,
    storagePath: String,
    downloadUrl: String,
    sizeBytes: Long,
    deviceName: String,
  ): Result<Unit> {
    val path = "users/$userId/projects/$projectId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Cross-user write rejected." }
      require(projectId.isNotBlank()) { "Project ID cannot be blank" }
      val payload = mapOf(
        "storagePath" to storagePath.trim().take(300),
        "storageDownloadUrl" to downloadUrl.trim().take(1200),
        "syncedPhotoSizeBytes" to sizeBytes.coerceIn(0L, 104_857_600L),
        "deviceName" to deviceName.trim().ifBlank { "Android Studio" }.take(80),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      db.collection("users").document(uid).collection("projects").document(projectId)
        .update(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun recordExportTelemetry(
    userId: String,
    projectId: String,
    platformLabel: String,
  ): Result<Unit> {
    val path = "users/$userId/projects/$projectId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Cross-user write rejected." }
      require(projectId.isNotBlank()) { "Project ID cannot be blank" }
      db.collection("users").document(uid).collection("projects").document(projectId)
        .update(
          mapOf(
            "lastExportPlatform" to platformLabel.trim().ifBlank { "Gallery" }.take(40),
            "exportedCount" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteProject(userId: String, projectId: String): Result<Unit> {
    val path = "users/$userId/projects/$projectId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Cross-user delete rejected." }
      require(projectId.isNotBlank()) { "Project ID cannot be blank" }
      db.collection("users").document(uid).collection("projects").document(projectId)
        .delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun saveCustomPreset(
    userId: String,
    name: String,
    recipe: EditRecipe,
  ): Result<String> {
    var path = "users/$userId/presets"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Cross-user write rejected." }
      val safeName = name.trim().ifBlank { "My Look" }.take(40)
      val docRef = db.collection("users").document(uid).collection("presets").document()
      path = docRef.path
      val payload = mapOf(
        "userId" to uid,
        "name" to safeName,
        "filterId" to recipe.filterId.ifBlank { "original" }.take(40),
        "filterIntensity" to recipe.filterIntensity.toDouble().coerceIn(0.0, 1.0),
        "exposure" to recipe.exposure.toDouble().coerceIn(-1.0, 1.0),
        "contrast" to recipe.contrast.toDouble().coerceIn(-1.0, 1.0),
        "saturation" to recipe.saturation.toDouble().coerceIn(-1.0, 1.0),
        "warmth" to recipe.warmth.toDouble().coerceIn(-1.0, 1.0),
        "tint" to recipe.tint.toDouble().coerceIn(-1.0, 1.0),
        "fade" to recipe.fade.toDouble().coerceIn(0.0, 1.0),
        "vignette" to recipe.vignette.toDouble().coerceIn(0.0, 1.0),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun deletePreset(userId: String, presetId: String): Result<Unit> {
    val path = "users/$userId/presets/$presetId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Cross-user delete rejected." }
      require(presetId.isNotBlank()) { "Preset ID cannot be blank" }
      db.collection("users").document(uid).collection("presets").document(presetId)
        .delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }
}
