package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiImageAspect
import com.example.ai.AiStudioRepository
import com.example.ai.VideoAspectOption
import com.example.data.firebase.CloudSyncRepository
import com.example.data.firebase.PhotoCloudSyncService
import com.example.data.firebase.PhotoSyncStatus
import com.example.data.firebase.SyncState
import com.example.data.model.AdjustParam
import com.example.data.model.AspectPreset
import com.example.data.model.CloudPhotoProject
import com.example.data.model.CloudPreset
import com.example.data.model.EditRecipe
import com.example.data.model.EditorTab
import com.example.data.model.ExportFormat
import com.example.data.model.ExportPlatform
import com.example.data.model.PhotoSource
import com.example.data.model.SamplePhoto
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.editor.EditHistoryManager
import com.example.editor.FilterCatalog
import com.example.editor.PhotoEngine
import com.example.export.ExportManager
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

enum class MainDestination {
  STUDIO_HOME,
  AI_LAB,
  CLOUD_VAULT,
  PRESETS,
}

enum class AiLabMode(val label: String) {
  CREATE_IMAGE("Create"),
  EDIT_IMAGE("Edit Photo"),
  ANIMATE_VIDEO("Animate"),
}

sealed interface AiImageResultState {
  data object Idle : AiImageResultState
  data object Loading : AiImageResultState
  data class Success(val bitmap: Bitmap) : AiImageResultState
  data class Error(val message: String) : AiImageResultState
}

sealed interface AiVideoResultState {
  data object Idle : AiVideoResultState
  data object Loading : AiVideoResultState
  data class Success(val videoUri: Uri, val aspect: VideoAspectOption) : AiVideoResultState
  data class Error(val message: String) : AiVideoResultState
}

data class AiLabUiState(
  val mode: AiLabMode = AiLabMode.CREATE_IMAGE,
  val inputBitmap: Bitmap? = null,
  val inputLabel: String = "No photo selected",
  val imageState: AiImageResultState = AiImageResultState.Idle,
  val videoState: AiVideoResultState = AiVideoResultState.Idle,
  val statusMessage: String? = null,
  val isErrorStatus: Boolean = false,
)

data class EditorUiState(
  val isEditorOpen: Boolean = false,
  val isCameraOpen: Boolean = false,
  val source: PhotoSource? = null,
  val currentProjectId: String? = null,
  val projectTitle: String = "Untitled Edit",
  val sourceBitmap: Bitmap? = null,
  val isLoadingBitmap: Boolean = false,
  val recipe: EditRecipe = EditRecipe(),
  val canUndo: Boolean = false,
  val canRedo: Boolean = false,
  val undoCount: Int = 0,
  val redoCount: Int = 0,
  val nextUndoLabel: String? = null,
  val nextRedoLabel: String? = null,
  val historyFeedbackMessage: String? = null,
  val activeTab: EditorTab = EditorTab.FILTERS,
  val selectedAdjustParam: AdjustParam = AdjustParam.EXPOSURE,
  val selectedFilterCategory: String = "All",
  val isComparingOriginal: Boolean = false,
  val isSyncing: Boolean = false,
  val isExporting: Boolean = false,
  val hasUnsyncedChanges: Boolean = false,
  val statusMessage: String? = null,
  val isErrorStatus: Boolean = false,
)

class StudioViewModel(
  private val repository: CloudSyncRepository,
  private val currentUserId: String,
  private val aiRepository: AiStudioRepository = AiStudioRepository(),
) : ViewModel() {

  private val syncService = PhotoCloudSyncService(
    repository = repository,
    currentUserId = currentUserId,
    scope = viewModelScope,
  )

  val syncStatus: StateFlow<PhotoSyncStatus> = syncService.syncStatus

  private val _vaultStatusMessage = MutableStateFlow<String?>(null)
  val vaultStatusMessage: StateFlow<String?> = _vaultStatusMessage.asStateFlow()

  private var isAutoSyncEnabled: Boolean = true

  val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
    .map<UserProfile?, UiState<UserProfile?>> { profile ->
      if (profile != null) {
        isAutoSyncEnabled = profile.autoSyncEnabled
      }
      UiState.Success(profile)
    }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing profile", rootError)
      emit(UiState.Error(rootError.message ?: "Failed to load profile"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading,
    )

  val projectsState: StateFlow<UiState<List<CloudPhotoProject>>> = repository.observeProjects(currentUserId)
    .map<List<CloudPhotoProject>, UiState<List<CloudPhotoProject>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing cloud projects", rootError)
      emit(UiState.Error(rootError.message ?: "Failed to load cloud backups"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading,
    )

  val presetsState: StateFlow<UiState<List<CloudPreset>>> = repository.observePresets(currentUserId)
    .map<List<CloudPreset>, UiState<List<CloudPreset>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing cloud presets", rootError)
      emit(UiState.Error(rootError.message ?: "Failed to load cloud presets"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading,
    )

  private val _currentTab = MutableStateFlow(MainDestination.STUDIO_HOME)
  val currentTab: StateFlow<MainDestination> = _currentTab.asStateFlow()

  private val editHistory = EditHistoryManager(EditRecipe())

  private val _editorState = MutableStateFlow(EditorUiState())
  val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

  private val _aiLabState = MutableStateFlow(AiLabUiState())
  val aiLabState: StateFlow<AiLabUiState> = _aiLabState.asStateFlow()

  fun ensureProfileSynced(context: Context, user: FirebaseUser) {
    syncService.startNetworkObserver(context)
    viewModelScope.launch {
      repository.ensureUserProfile(user)
    }
  }

  override fun onCleared() {
    super.onCleared()
    syncService.release()
  }

  fun clearVaultStatusMessage() {
    _vaultStatusMessage.value = null
  }

  fun selectDestination(dest: MainDestination) {
    _currentTab.value = dest
  }

  fun openSampleInEditor(
    context: Context,
    sample: SamplePhoto,
    initialFilterId: String = "original",
    initialRecipe: EditRecipe? = null,
  ) {
    val source = PhotoSource.Sample(sample)
    val startRecipe = initialRecipe ?: EditRecipe(filterId = initialFilterId)
    openPhotoSource(
      context = context,
      source = source,
      projectId = null,
      title = sample.title,
      recipe = startRecipe,
      fallbackBase64 = null,
    )
  }

  fun openCamera() {
    _editorState.update { it.copy(isCameraOpen = true, isEditorOpen = false) }
  }

  fun closeCamera() {
    _editorState.update { it.copy(isCameraOpen = false) }
  }

  fun onCameraPhotoCaptured(
    context: Context,
    uri: Uri,
    aspectPreset: AspectPreset = AspectPreset.FREE,
  ) {
    val label = "Camera_${System.currentTimeMillis().toString().takeLast(4)}"
    openPhotoSource(
      context = context,
      source = PhotoSource.Gallery(uri.toString(), label),
      projectId = null,
      title = label,
      recipe = EditRecipe(aspectRatio = aspectPreset),
      fallbackBase64 = null,
    )
  }

  fun openGalleryUriInEditor(context: Context, uri: Uri) {
    val label = "Studio_${System.currentTimeMillis().toString().takeLast(4)}"
    openPhotoSource(
      context = context,
      source = PhotoSource.Gallery(uri.toString(), label),
      projectId = null,
      title = label,
      recipe = EditRecipe(),
      fallbackBase64 = null,
    )
  }

  fun openCloudProjectInEditor(context: Context, project: CloudPhotoProject) {
    val source = if (project.sourceType == "sample") {
      PhotoSource.Sample(SamplePhoto.fromKey(project.sampleKey))
    } else {
      PhotoSource.Gallery(project.localUri.orEmpty(), project.title)
    }
    openPhotoSource(
      context = context,
      source = source,
      projectId = project.id,
      title = project.title.ifBlank { "Cloud Backup" },
      recipe = project.toEditRecipe(),
      fallbackBase64 = project.thumbnailBase64,
      cloudProject = project,
    )
  }

  private fun openPhotoSource(
    context: Context,
    source: PhotoSource,
    projectId: String?,
    title: String,
    recipe: EditRecipe,
    fallbackBase64: String?,
    cloudProject: CloudPhotoProject? = null,
  ) {
    editHistory.reset(recipe)
    _editorState.value = EditorUiState(
      isEditorOpen = true,
      source = source,
      currentProjectId = projectId,
      projectTitle = title,
      isLoadingBitmap = true,
      recipe = recipe,
      hasUnsyncedChanges = projectId == null,
    )

    viewModelScope.launch {
      val appCtx = context.applicationContext
      val loaded = withContext(Dispatchers.IO) {
        PhotoEngine.loadBitmapForSource(appCtx, source, maxEdge = 1600)
          ?: cloudProject?.let { syncService.downloadSyncedBitmap(appCtx, it, maxEdge = 1600) }
          ?: PhotoEngine.decodeThumbnailBitmap(fallbackBase64)
      }
      if (loaded == null) {
        _editorState.update {
          it.copy(
            isLoadingBitmap = false,
            statusMessage = "Could not load image file.",
            isErrorStatus = true,
          )
        }
      } else {
        _editorState.update {
          it.copy(
            isLoadingBitmap = false,
            sourceBitmap = loaded,
          )
        }
      }
    }
  }

  fun closeEditor() {
    _editorState.update { it.copy(isEditorOpen = false, statusMessage = null) }
  }

  fun clearStatusMessage() {
    _editorState.update { it.copy(statusMessage = null, isErrorStatus = false) }
  }

  fun selectEditorTab(tab: EditorTab) {
    _editorState.update { it.copy(activeTab = tab) }
  }

  fun selectFilterCategory(category: String) {
    _editorState.update { it.copy(selectedFilterCategory = category) }
  }

  fun selectAdjustParam(param: AdjustParam) {
    _editorState.update { it.copy(selectedAdjustParam = param) }
  }

  fun setComparingOriginal(comparing: Boolean) {
    _editorState.update { it.copy(isComparingOriginal = comparing) }
  }

  fun updateProjectTitle(title: String) {
    val clean = title.take(100)
    _editorState.update { it.copy(projectTitle = clean, hasUnsyncedChanges = true) }
    triggerAutoSyncIfEnabled(immediate = false)
  }

  fun clearHistoryFeedback() {
    _editorState.update { it.copy(historyFeedbackMessage = null) }
  }

  private fun syncHistoryMetadata(
    state: EditorUiState,
    feedbackMessage: String? = null,
  ): EditorUiState = state.copy(
    canUndo = editHistory.canUndo,
    canRedo = editHistory.canRedo,
    undoCount = editHistory.undoCount,
    redoCount = editHistory.redoCount,
    nextUndoLabel = editHistory.nextUndoLabel,
    nextRedoLabel = editHistory.nextRedoLabel,
    historyFeedbackMessage = feedbackMessage,
  )

  private fun applyRecipeChange(
    pushHistory: Boolean = true,
    actionLabel: String = "Edit",
    transform: (EditRecipe) -> EditRecipe,
  ) {
    var changed = false
    _editorState.update { state ->
      val updated = transform(state.recipe)
      if (updated == state.recipe) return@update state
      changed = true
      if (pushHistory) {
        editHistory.commit(updated, actionLabel)
        syncHistoryMetadata(
          state = state.copy(
            recipe = updated,
            hasUnsyncedChanges = true,
          ),
          feedbackMessage = null,
        )
      } else {
        editHistory.updateGesturePreview(updated)
        state.copy(
          recipe = updated,
          hasUnsyncedChanges = true,
          historyFeedbackMessage = null,
        )
      }
    }
    if (changed) {
      triggerAutoSyncIfEnabled(immediate = false)
    }
  }

  fun beginRecipeGesture() {
    editHistory.beginGesture(_editorState.value.recipe)
  }

  fun commitRecipeGesture(actionLabel: String) {
    val committed = editHistory.finishGesture(actionLabel)
    if (committed) {
      _editorState.update { state ->
        syncHistoryMetadata(
          state = state.copy(hasUnsyncedChanges = true),
          feedbackMessage = null,
        )
      }
      triggerAutoSyncIfEnabled(immediate = false)
    }
  }

  fun undo() {
    val transition = editHistory.undo() ?: return
    _editorState.update { state ->
      syncHistoryMetadata(
        state = state.copy(
          recipe = transition.newState,
          hasUnsyncedChanges = true,
        ),
        feedbackMessage = "Undid ${transition.label}",
      )
    }
    triggerAutoSyncIfEnabled(immediate = false)
  }

  fun redo() {
    val transition = editHistory.redo() ?: return
    _editorState.update { state ->
      syncHistoryMetadata(
        state = state.copy(
          recipe = transition.newState,
          hasUnsyncedChanges = true,
        ),
        feedbackMessage = "Redid ${transition.label}",
      )
    }
    triggerAutoSyncIfEnabled(immediate = false)
  }

  fun resetAllEdits() {
    applyRecipeChange(pushHistory = true, actionLabel = "Reset All") { EditRecipe() }
  }

  fun selectFilter(filterId: String) {
    val filterName = FilterCatalog.findById(filterId).displayName
    applyRecipeChange(pushHistory = true, actionLabel = "Filter: $filterName") {
      it.copy(filterId = filterId)
    }
  }

  fun updateFilterIntensity(intensity: Float, recordHistory: Boolean = false) {
    applyRecipeChange(pushHistory = recordHistory, actionLabel = "Filter Intensity") {
      it.copy(filterIntensity = intensity.coerceIn(0f, 1f))
    }
  }

  fun updateAdjustment(param: AdjustParam, value: Float, recordHistory: Boolean = false) {
    applyRecipeChange(pushHistory = recordHistory, actionLabel = param.label) {
      it.withAdjustValue(param, value)
    }
  }

  fun resetAdjustment(param: AdjustParam) {
    applyRecipeChange(pushHistory = true, actionLabel = "Reset ${param.label}") {
      it.withAdjustValue(param, param.defaultValue)
    }
  }

  fun applyCloudPreset(preset: CloudPreset) {
    applyRecipeChange(pushHistory = true, actionLabel = "Preset: ${preset.name}") {
      preset.applyTo(it)
    }
    _editorState.update {
      it.copy(statusMessage = "Applied preset \"${preset.name}\"", isErrorStatus = false)
    }
  }

  fun selectAspectRatio(preset: AspectPreset) {
    val bmp = _editorState.value.sourceBitmap
    applyRecipeChange(pushHistory = true, actionLabel = "Crop ${preset.label}") { current ->
      if (preset == AspectPreset.FREE || preset.ratio == null || bmp == null) {
        current.copy(aspectRatio = preset)
      } else {
        val swap = current.rotationDegrees == 90 || current.rotationDegrees == 270
        val imgW = (if (swap) bmp.height else bmp.width).toFloat().coerceAtLeast(1f)
        val imgH = (if (swap) bmp.width else bmp.height).toFloat().coerceAtLeast(1f)
        val imgAspect = imgW / imgH
        val targetAspect = preset.ratio

        val (cropW, cropH) = if (targetAspect > imgAspect) {
          1f to (imgAspect / targetAspect).coerceIn(0.1f, 1f)
        } else {
          (targetAspect / imgAspect).coerceIn(0.1f, 1f) to 1f
        }
        val left = ((1f - cropW) / 2f).coerceIn(0f, 0.9f)
        val top = ((1f - cropH) / 2f).coerceIn(0f, 0.9f)
        current.copy(
          aspectRatio = preset,
          cropLeft = left,
          cropTop = top,
          cropRight = (left + cropW).coerceIn(left + 0.05f, 1f),
          cropBottom = (top + cropH).coerceIn(top + 0.05f, 1f),
        )
      }
    }
  }

  fun updateCropRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    recordHistory: Boolean = false,
  ) {
    applyRecipeChange(pushHistory = recordHistory, actionLabel = "Crop Frame") { current ->
      val safeLeft = left.coerceIn(0f, 0.92f)
      val safeTop = top.coerceIn(0f, 0.92f)
      val safeRight = right.coerceIn(safeLeft + 0.08f, 1f)
      val safeBottom = bottom.coerceIn(safeTop + 0.08f, 1f)
      current.copy(
        cropLeft = safeLeft,
        cropTop = safeTop,
        cropRight = safeRight,
        cropBottom = safeBottom,
      )
    }
  }

  fun rotate90(clockwise: Boolean = true) {
    applyRecipeChange(
      pushHistory = true,
      actionLabel = if (clockwise) "Rotate 90° CW" else "Rotate 90° CCW",
    ) { current ->
      val delta = if (clockwise) 90 else 270
      current.copy(rotationDegrees = (current.rotationDegrees + delta) % 360)
    }
  }

  fun toggleFlipHorizontal() {
    applyRecipeChange(pushHistory = true, actionLabel = "Flip Horizontal") {
      it.copy(flipHorizontal = !it.flipHorizontal)
    }
  }

  fun toggleFlipVertical() {
    applyRecipeChange(pushHistory = true, actionLabel = "Flip Vertical") {
      it.copy(flipVertical = !it.flipVertical)
    }
  }

  fun updateStraighten(degrees: Float, recordHistory: Boolean = false) {
    applyRecipeChange(pushHistory = recordHistory, actionLabel = "Straighten") {
      it.copy(straightenDegrees = degrees.coerceIn(-45f, 45f))
    }
  }

  fun resetGeometry() {
    applyRecipeChange(pushHistory = true, actionLabel = "Reset Crop") {
      it.copy(
        aspectRatio = AspectPreset.FREE,
        cropLeft = 0f,
        cropTop = 0f,
        cropRight = 1f,
        cropBottom = 1f,
        rotationDegrees = 0,
        straightenDegrees = 0f,
        flipHorizontal = false,
        flipVertical = false,
      )
    }
  }

  fun toggleAutoSync(enabled: Boolean) {
    isAutoSyncEnabled = enabled
    viewModelScope.launch {
      val result = repository.setAutoSync(currentUserId, enabled)
      result.onSuccess {
        if (enabled && _editorState.value.hasUnsyncedChanges) {
          triggerAutoSyncIfEnabled(immediate = true)
        }
      }
      result.onFailure { err ->
        _editorState.update {
          it.copy(statusMessage = err.localizedMessage ?: "Failed to update sync setting", isErrorStatus = true)
        }
      }
    }
  }

  private fun triggerAutoSyncIfEnabled(immediate: Boolean = false) {
    if (!isAutoSyncEnabled) return
    val state = _editorState.value
    val bitmap = state.sourceBitmap ?: return
    val source = state.source ?: return
    syncService.scheduleAutoSync(
      source = source,
      existingProjectId = state.currentProjectId,
      title = state.projectTitle,
      sourceBitmap = bitmap,
      recipe = state.recipe,
      immediate = immediate,
      onSyncedProjectId = { docId ->
        _editorState.update { cur ->
          if (cur.source == source) {
            cur.copy(currentProjectId = docId, hasUnsyncedChanges = false)
          } else cur
        }
      },
    )
  }

  fun syncCurrentProjectToCloud(silent: Boolean = false) {
    val state = _editorState.value
    val bitmap = state.sourceBitmap ?: return
    val source = state.source ?: return
    if (state.isSyncing) return

    _editorState.update { it.copy(isSyncing = true) }
    viewModelScope.launch {
      val result = syncService.syncEditedPhotoNow(
        source = source,
        existingProjectId = state.currentProjectId,
        title = state.projectTitle,
        sourceBitmap = bitmap,
        recipe = state.recipe,
      )

      result.fold(
        onSuccess = { outcome ->
          _editorState.update {
            val msg = when {
              silent -> it.statusMessage
              outcome.storageUploaded -> "Saved edited photo to Firestore & Firebase Storage"
              else -> outcome.storageErrorMessage ?: "Saved recipe to Firestore"
            }
            it.copy(
              isSyncing = false,
              currentProjectId = outcome.projectId,
              hasUnsyncedChanges = false,
              statusMessage = msg,
              isErrorStatus = false,
            )
          }
        },
        onFailure = { err ->
          _editorState.update {
            it.copy(
              isSyncing = false,
              statusMessage = err.localizedMessage ?: "Cloud sync failed",
              isErrorStatus = true,
            )
          }
        },
      )
    }
  }

  fun syncAllPendingAndGallery(context: Context) {
    viewModelScope.launch {
      val uploaded = syncService.retryPendingUploads()
      val projects = (projectsState.value as? UiState.Success)?.data.orEmpty()
      val downloadedRes = syncService.syncCloudGalleryToDevice(context, projects)
      val downloaded = downloadedRes.getOrDefault(0)
      _vaultStatusMessage.value = when {
        uploaded > 0 && downloaded > 0 -> "Uploaded $uploaded & saved $downloaded photo(s) to Gallery"
        uploaded > 0 -> "Uploaded $uploaded pending photo(s) to Firebase Storage"
        downloaded > 0 -> "Synced $downloaded cloud photo(s) to Pictures/LuminaEdit"
        syncStatus.value.state == SyncState.ERROR -> syncStatus.value.lastMessage
        else -> "Cloud Gallery is up to date across devices"
      }
    }
  }

  fun saveCloudProjectToDeviceGallery(context: Context, project: CloudPhotoProject) {
    viewModelScope.launch {
      val res = syncService.saveCloudProjectToDeviceGallery(context, project)
      res.fold(
        onSuccess = {
          _vaultStatusMessage.value = "Saved \"${project.title}\" to Pictures/LuminaEdit"
        },
        onFailure = { err ->
          _vaultStatusMessage.value = err.localizedMessage ?: "Unable to save cloud photo"
        },
      )
    }
  }

  fun saveCurrentAsPreset(name: String) {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
      _editorState.update { it.copy(statusMessage = "Please enter a preset name.", isErrorStatus = true) }
      return
    }
    viewModelScope.launch {
      val result = repository.saveCustomPreset(currentUserId, cleanName, _editorState.value.recipe)
      result.fold(
        onSuccess = {
          _editorState.update {
            it.copy(statusMessage = "Saved \"$cleanName\" to Cloud Presets", isErrorStatus = false)
          }
        },
        onFailure = { err ->
          _editorState.update {
            it.copy(statusMessage = err.localizedMessage ?: "Failed to save preset", isErrorStatus = true)
          }
        },
      )
    }
  }

  fun deleteProject(project: CloudPhotoProject) {
    viewModelScope.launch {
      syncService.deleteSyncedProject(currentUserId, project).onFailure { err ->
        _vaultStatusMessage.value = err.localizedMessage ?: "Delete failed"
      }
    }
  }

  fun deletePreset(presetId: String) {
    viewModelScope.launch {
      repository.deletePreset(currentUserId, presetId).onFailure { err ->
        _editorState.update {
          it.copy(statusMessage = err.localizedMessage ?: "Delete failed", isErrorStatus = true)
        }
      }
    }
  }

  fun exportToGallery(
    context: Context,
    format: ExportFormat,
    quality: Int,
    addWatermark: Boolean,
    onComplete: (Boolean, String) -> Unit,
  ) {
    val state = _editorState.value
    val bitmap = state.sourceBitmap ?: run {
      onComplete(false, "No image loaded.")
      return
    }
    _editorState.update { it.copy(isExporting = true) }
    viewModelScope.launch {
      val appCtx = context.applicationContext
      val result = withContext(Dispatchers.Default) {
        val rendered = PhotoEngine.renderFinalEditedBitmap(
          source = bitmap,
          recipe = state.recipe,
          maxOutputEdge = 2400,
          addWatermark = addWatermark,
        )
        ExportManager.saveToGallery(appCtx, rendered, format, quality, state.projectTitle)
      }

      result.fold(
        onSuccess = {
          state.currentProjectId?.let { projId ->
            repository.recordExportTelemetry(currentUserId, projId, "Gallery")
          }
          if (state.hasUnsyncedChanges || state.currentProjectId == null) {
            triggerAutoSyncIfEnabled(immediate = true)
          }
          _editorState.update {
            it.copy(
              isExporting = false,
              statusMessage = "Saved to Pictures/LuminaEdit",
              isErrorStatus = false,
            )
          }
          onComplete(true, "Saved to Pictures/LuminaEdit")
        },
        onFailure = { err ->
          _editorState.update {
            it.copy(
              isExporting = false,
              statusMessage = err.localizedMessage ?: "Gallery export failed",
              isErrorStatus = true,
            )
          }
          onComplete(false, err.localizedMessage ?: "Gallery export failed")
        },
      )
    }
  }

  fun exportAndShareToPlatform(
    context: Context,
    platform: ExportPlatform,
    format: ExportFormat,
    quality: Int,
    addWatermark: Boolean,
    onShareReady: (Uri, String, Boolean) -> Unit,
  ) {
    val state = _editorState.value
    val bitmap = state.sourceBitmap ?: return
    _editorState.update { it.copy(isExporting = true) }
    viewModelScope.launch {
      val appCtx = context.applicationContext
      val result = withContext(Dispatchers.Default) {
        val rendered = PhotoEngine.renderFinalEditedBitmap(
          source = bitmap,
          recipe = state.recipe,
          maxOutputEdge = 2400,
          addWatermark = addWatermark,
        )
        ExportManager.prepareShareUri(appCtx, rendered, format, quality, state.projectTitle)
      }

      result.fold(
        onSuccess = { uri ->
          state.currentProjectId?.let { projId ->
            repository.recordExportTelemetry(currentUserId, projId, platform.label)
          }
          if (state.hasUnsyncedChanges || state.currentProjectId == null) {
            triggerAutoSyncIfEnabled(immediate = true)
          }
          _editorState.update { it.copy(isExporting = false) }
          val openedDirect = ExportManager.shareToPlatform(context, uri, platform, format.mimeType)
          onShareReady(uri, format.mimeType, openedDirect)
        },
        onFailure = { err ->
          _editorState.update {
            it.copy(
              isExporting = false,
              statusMessage = err.localizedMessage ?: "Share export failed",
              isErrorStatus = true,
            )
          }
        },
      )
    }
  }

  fun formatPixelDimensions(): String {
    val bmp = _editorState.value.sourceBitmap ?: return "—"
    val (w, h) = PhotoEngine.computeCroppedDimensions(bmp, _editorState.value.recipe)
    val scale = min(1f, 2400f / maxOf(w, h).coerceAtLeast(1))
    val outW = (w * scale).toInt().coerceAtLeast(1)
    val outH = (h * scale).toInt().coerceAtLeast(1)
    return "$outW × $outH px"
  }

  fun shareCurrentEditedPhoto(
    context: Context,
    onComplete: (Boolean, String?) -> Unit,
  ) {
    val state = _editorState.value
    val bitmap = state.sourceBitmap ?: run {
      onComplete(false, "No edited photo available to share.")
      return
    }
    viewModelScope.launch {
      val appCtx = context.applicationContext
      val result = withContext(Dispatchers.Default) {
        val rendered = PhotoEngine.renderFinalEditedBitmap(
          source = bitmap,
          recipe = state.recipe,
          maxOutputEdge = 2400,
          addWatermark = false,
        )
        ExportManager.prepareShareUri(appCtx, rendered, ExportFormat.JPEG, 92, state.projectTitle)
      }
      result.fold(
        onSuccess = { uri ->
          ExportManager.shareToPlatform(
            context = context,
            uri = uri,
            platform = ExportPlatform.SYSTEM_SHARE,
            mimeType = ExportFormat.JPEG.mimeType,
          )
          onComplete(true, null)
        },
        onFailure = { err ->
          onComplete(false, err.localizedMessage ?: "Failed to prepare photo for sharing.")
        },
      )
    }
  }

  // --- AI Lab Actions (Create/Edit Images & Animate Video) ---

  fun selectAiLabMode(mode: AiLabMode) {
    _aiLabState.update { it.copy(mode = mode) }
  }

  fun clearAiStatusMessage() {
    _aiLabState.update { it.copy(statusMessage = null, isErrorStatus = false) }
  }

  fun openAiLab(mode: AiLabMode) {
    _currentTab.value = MainDestination.AI_LAB
    _aiLabState.update { it.copy(mode = mode) }
  }

  fun setAiInputFromGallery(context: Context, uri: Uri) {
    viewModelScope.launch {
      val appCtx = context.applicationContext
      val bmp = withContext(Dispatchers.IO) {
        PhotoEngine.loadBitmapForSource(appCtx, PhotoSource.Gallery(uri.toString(), "Gallery Photo"), maxEdge = 1400)
      }
      if (bmp != null) {
        _aiLabState.update {
          it.copy(
            inputBitmap = bmp,
            inputLabel = "Selected Photo",
            imageState = AiImageResultState.Idle,
            videoState = AiVideoResultState.Idle,
          )
        }
      } else {
        _aiLabState.update {
          it.copy(statusMessage = "Could not load selected photo.", isErrorStatus = true)
        }
      }
    }
  }

  fun setAiInputFromSample(context: Context, sample: SamplePhoto) {
    viewModelScope.launch {
      val appCtx = context.applicationContext
      val bmp = withContext(Dispatchers.IO) {
        PhotoEngine.loadBitmapForSource(appCtx, PhotoSource.Sample(sample), maxEdge = 1400)
      }
      if (bmp != null) {
        _aiLabState.update {
          it.copy(
            inputBitmap = bmp,
            inputLabel = sample.title,
            imageState = AiImageResultState.Idle,
            videoState = AiVideoResultState.Idle,
          )
        }
      }
    }
  }

  fun sendCurrentEditorPhotoToAi(mode: AiLabMode) {
    val state = _editorState.value
    val src = state.sourceBitmap ?: return
    viewModelScope.launch {
      val rendered = withContext(Dispatchers.Default) {
        PhotoEngine.renderFinalEditedBitmap(src, state.recipe, maxOutputEdge = 1400, addWatermark = false)
      }
      _aiLabState.update {
        it.copy(
          mode = mode,
          inputBitmap = rendered,
          inputLabel = state.projectTitle,
          imageState = AiImageResultState.Idle,
          videoState = AiVideoResultState.Idle,
        )
      }
      _editorState.update { it.copy(isEditorOpen = false) }
      _currentTab.value = MainDestination.AI_LAB
    }
  }

  fun generateAiImage(prompt: String, aspect: AiImageAspect) {
    if (prompt.isBlank()) {
      _aiLabState.update {
        it.copy(imageState = AiImageResultState.Error("Please enter a prompt to generate an image."))
      }
      return
    }
    viewModelScope.launch {
      _aiLabState.update { it.copy(imageState = AiImageResultState.Loading) }
      val result = aiRepository.generateImage(prompt, aspect)
      result.fold(
        onSuccess = { bmp ->
          _aiLabState.update { it.copy(imageState = AiImageResultState.Success(bmp)) }
        },
        onFailure = { err ->
          _aiLabState.update {
            it.copy(imageState = AiImageResultState.Error(err.localizedMessage ?: "Image generation failed."))
          }
        },
      )
    }
  }

  fun editAiImage(instruction: String, aspect: AiImageAspect) {
    val input = _aiLabState.value.inputBitmap
    if (input == null) {
      _aiLabState.update {
        it.copy(imageState = AiImageResultState.Error("Select or upload a photo to edit first."))
      }
      return
    }
    if (instruction.isBlank()) {
      _aiLabState.update {
        it.copy(imageState = AiImageResultState.Error("Describe how you want to edit the photo."))
      }
      return
    }
    viewModelScope.launch {
      _aiLabState.update { it.copy(imageState = AiImageResultState.Loading) }
      val result = aiRepository.editImage(input, instruction, aspect)
      result.fold(
        onSuccess = { bmp ->
          _aiLabState.update { it.copy(imageState = AiImageResultState.Success(bmp)) }
        },
        onFailure = { err ->
          _aiLabState.update {
            it.copy(imageState = AiImageResultState.Error(err.localizedMessage ?: "Image edit failed."))
          }
        },
      )
    }
  }

  fun animatePhotoToVideo(
    context: Context,
    motionPrompt: String,
    aspect: VideoAspectOption,
  ) {
    val input = _aiLabState.value.inputBitmap
    if (input == null) {
      _aiLabState.update {
        it.copy(videoState = AiVideoResultState.Error("Select or upload a photo to animate first."))
      }
      return
    }
    viewModelScope.launch {
      _aiLabState.update { it.copy(videoState = AiVideoResultState.Loading) }
      val result = aiRepository.animateImageToVideo(
        context = context.applicationContext,
        inputBitmap = input,
        motionPrompt = motionPrompt,
        aspectRatio = aspect,
      )
      result.fold(
        onSuccess = { uri ->
          _aiLabState.update { it.copy(videoState = AiVideoResultState.Success(uri, aspect)) }
        },
        onFailure = { err ->
          _aiLabState.update {
            it.copy(videoState = AiVideoResultState.Error(err.localizedMessage ?: "Video animation failed."))
          }
        },
      )
    }
  }

  fun useBitmapAsAiInput(bitmap: Bitmap, label: String, nextMode: AiLabMode) {
    _aiLabState.update {
      it.copy(
        mode = nextMode,
        inputBitmap = bitmap,
        inputLabel = label,
      )
    }
  }

  fun openBitmapInEditor(context: Context, bitmap: Bitmap, title: String) {
    viewModelScope.launch {
      val appCtx = context.applicationContext
      val uri = withContext(Dispatchers.IO) {
        val dir = File(appCtx.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "ai_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        androidx.core.content.FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.fileprovider", file)
      }
      openPhotoSource(
        context = appCtx,
        source = PhotoSource.Gallery(uri.toString(), title),
        projectId = null,
        title = title,
        recipe = EditRecipe(),
        fallbackBase64 = null,
      )
    }
  }

  fun saveAiBitmapToGallery(context: Context, bitmap: Bitmap, title: String) {
    viewModelScope.launch {
      val appCtx = context.applicationContext
      val result = withContext(Dispatchers.IO) {
        ExportManager.saveToGallery(appCtx, bitmap, ExportFormat.PNG, 95, title)
      }
      result.fold(
        onSuccess = {
          _aiLabState.update {
            it.copy(statusMessage = "Saved image to Pictures/LuminaEdit", isErrorStatus = false)
          }
        },
        onFailure = { err ->
          _aiLabState.update {
            it.copy(statusMessage = err.localizedMessage ?: "Save failed", isErrorStatus = true)
          }
        },
      )
    }
  }

  fun saveAiVideoToGallery(context: Context, videoUri: Uri, title: String) {
    viewModelScope.launch {
      val appCtx = context.applicationContext
      val result = withContext(Dispatchers.IO) {
        ExportManager.saveVideoToGallery(appCtx, videoUri, title)
      }
      result.fold(
        onSuccess = {
          _aiLabState.update {
            it.copy(statusMessage = "Saved video to Movies/LuminaEdit", isErrorStatus = false)
          }
        },
        onFailure = { err ->
          _aiLabState.update {
            it.copy(statusMessage = err.localizedMessage ?: "Video save failed", isErrorStatus = true)
          }
        },
      )
    }
  }

  private companion object {
    const val TAG = "StudioVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
