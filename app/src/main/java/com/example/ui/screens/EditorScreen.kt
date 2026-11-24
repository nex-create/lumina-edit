package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.firebase.PhotoSyncStatus
import com.example.data.firebase.SyncState
import com.example.data.model.CloudPreset
import com.example.data.model.EditorTab
import com.example.editor.HistogramAnalyzer
import com.example.editor.PhotoEngine
import com.example.ui.AiLabMode
import com.example.ui.EditorUiState
import com.example.ui.StudioViewModel
import com.example.ui.components.CropOverlayCanvas
import com.example.ui.components.EditorToolDeck
import com.example.ui.components.HistogramOverlay
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.WarmAmber
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun EditorScreen(
  editorState: EditorUiState,
  cloudPresets: List<CloudPreset>,
  syncStatus: PhotoSyncStatus,
  viewModel: StudioViewModel,
  onClose: () -> Unit,
) {
  BackHandler { onClose() }

  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  var showExportSheet by remember { mutableStateOf(false) }
  var showRenameDialog by remember { mutableStateOf(false) }
  var showSavePresetDialog by remember { mutableStateOf(false) }
  var showHistogram by remember { mutableStateOf(true) }

  LaunchedEffect(editorState.statusMessage) {
    val msg = editorState.statusMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  LaunchedEffect(editorState.historyFeedbackMessage) {
    if (!editorState.historyFeedbackMessage.isNullOrBlank()) {
      delay(1600L)
      viewModel.clearHistoryFeedback()
    }
  }

  // Generate small thumbnail once per loaded bitmap for instant live filter strips
  val filterThumbBitmap = remember(editorState.sourceBitmap) {
    editorState.sourceBitmap?.let { bmp ->
      val scale = 160f / max(bmp.width, bmp.height).coerceAtLeast(1)
      val w = (bmp.width * scale).toInt().coerceAtLeast(1)
      val h = (bmp.height * scale).toInt().coerceAtLeast(1)
      Bitmap.createScaledBitmap(bmp, w, h, true)
    }
  }

  val histogramSamplePixels = remember(editorState.sourceBitmap) {
    editorState.sourceBitmap?.let { HistogramAnalyzer.extractSamplePixels(it, targetEdge = 64) }
  }

  Scaffold(
    containerColor = ObsidianCanvas,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      val activelySyncing = editorState.isSyncing || syncStatus.state == SyncState.SYNCING
      EditorTopBar(
        title = editorState.projectTitle,
        canUndo = editorState.canUndo,
        canRedo = editorState.canRedo,
        undoCount = editorState.undoCount,
        redoCount = editorState.redoCount,
        nextUndoLabel = editorState.nextUndoLabel,
        nextRedoLabel = editorState.nextRedoLabel,
        isComparing = editorState.isComparingOriginal,
        isSyncing = activelySyncing,
        isSynced = editorState.currentProjectId != null && !editorState.hasUnsyncedChanges && !activelySyncing,
        onBack = onClose,
        onRenameClick = { showRenameDialog = true },
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onToggleCompare = { viewModel.setComparingOriginal(!editorState.isComparingOriginal) },
        onCloudSync = { viewModel.syncCurrentProjectToCloud(silent = false) },
        onExportClick = { showExportSheet = true },
      )
    },
    bottomBar = {
      EditorToolDeck(
        activeTab = editorState.activeTab,
        recipe = editorState.recipe,
        thumbBitmap = filterThumbBitmap,
        selectedCategory = editorState.selectedFilterCategory,
        selectedAdjustParam = editorState.selectedAdjustParam,
        cloudPresets = cloudPresets,
        onSelectTab = viewModel::selectEditorTab,
        onSelectCategory = viewModel::selectFilterCategory,
        onSelectFilter = viewModel::selectFilter,
        onFilterIntensityChange = { viewModel.updateFilterIntensity(it, recordHistory = false) },
        onGestureStarted = viewModel::beginRecipeGesture,
        onGestureFinished = viewModel::commitRecipeGesture,
        onSelectAdjustParam = viewModel::selectAdjustParam,
        onAdjustValueChange = { param, value -> viewModel.updateAdjustment(param, value, recordHistory = false) },
        onResetAdjustParam = viewModel::resetAdjustment,
        onSelectAspect = viewModel::selectAspectRatio,
        onRotate90 = viewModel::rotate90,
        onFlipHorizontal = viewModel::toggleFlipHorizontal,
        onFlipVertical = viewModel::toggleFlipVertical,
        onStraightenChange = { deg -> viewModel.updateStraighten(deg, recordHistory = false) },
        onResetGeometry = viewModel::resetGeometry,
        onResetAll = viewModel::resetAllEdits,
        onApplyPreset = viewModel::applyCloudPreset,
        onOpenSavePresetDialog = { showSavePresetDialog = true },
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      contentAlignment = Alignment.Center,
    ) {
      val sourceBmp = editorState.sourceBitmap
      when {
        editorState.isLoadingBitmap -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator(color = CoralFlame)
            Text("Preparing Studio Canvas...", style = MaterialTheme.typography.bodyMedium)
          }
        }

        sourceBmp == null -> {
          Text(
            text = "Unable to load photo.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        else -> {
          val recipe = editorState.recipe
          val isCropMode = editorState.activeTab == EditorTab.CROP && !editorState.isComparingOriginal

          // Render rotated/flipped (+ cropped when not in Crop mode) geometry bitmap
          val displayBitmap = remember(
            sourceBmp,
            isCropMode,
            recipe.rotationDegrees,
            recipe.straightenDegrees,
            recipe.flipHorizontal,
            recipe.flipVertical,
            if (isCropMode) 0f else recipe.cropLeft,
            if (isCropMode) 0f else recipe.cropTop,
            if (isCropMode) 1f else recipe.cropRight,
            if (isCropMode) 1f else recipe.cropBottom,
          ) {
            if (editorState.isComparingOriginal) {
              sourceBmp
            } else {
              PhotoEngine.renderGeometryBitmap(
                source = sourceBmp,
                recipe = recipe,
                applyCrop = !isCropMode,
                maxOutputEdge = 1400,
              )
            }
          }

          val activeBmp = if (editorState.isComparingOriginal) sourceBmp else displayBitmap
          val activeColorFilter = remember(recipe, editorState.isComparingOriginal) {
            if (editorState.isComparingOriginal) null else PhotoEngine.buildComposeColorFilter(recipe)
          }
          val liveHistogram = remember(histogramSamplePixels, recipe, editorState.isComparingOriginal) {
            histogramSamplePixels?.let { pixels ->
              val matrixValues = if (editorState.isComparingOriginal) {
                HistogramAnalyzer.identityMatrixValues()
              } else {
                PhotoEngine.buildAndroidColorMatrix(recipe).array
              }
              HistogramAnalyzer.computeHistogram(pixels, matrixValues)
            }
          }
          val imageAspect = activeBmp.width.toFloat().coerceAtLeast(1f) /
            activeBmp.height.toFloat().coerceAtLeast(1f)

          BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            val containerRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
            val boxModifier = if (imageAspect > containerRatio) {
              Modifier
                .fillMaxWidth()
                .aspectRatio(imageAspect)
            } else {
              Modifier
                .fillMaxSize()
                .aspectRatio(imageAspect, matchHeightConstraintsFirst = true)
            }

            Box(
              modifier = boxModifier.clip(RoundedCornerShape(6.dp)),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                bitmap = activeBmp.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_photo_preview),
                contentScale = ContentScale.FillBounds,
                colorFilter = activeColorFilter,
                modifier = Modifier.fillMaxSize(),
              )

              // Live Radial Vignette Preview
              if (!editorState.isComparingOriginal && recipe.vignette > 0.01f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                  val radius = max(size.width, size.height) * 0.72f
                  drawRect(
                    brush = Brush.radialGradient(
                      colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color(0xFF08080C).copy(alpha = (recipe.vignette * 0.75f).coerceIn(0f, 0.85f)),
                      ),
                      radius = radius,
                    )
                  )
                }
              }

              // Interactive Crop Box Overlay in Crop Tab
              if (isCropMode) {
                CropOverlayCanvas(
                  cropLeft = recipe.cropLeft,
                  cropTop = recipe.cropTop,
                  cropRight = recipe.cropRight,
                  cropBottom = recipe.cropBottom,
                  aspectPreset = recipe.aspectRatio,
                  imageAspectRatio = imageAspect,
                  onDragStarted = viewModel::beginRecipeGesture,
                  onCropChange = { l, t, r, b ->
                    viewModel.updateCropRect(l, t, r, b, recordHistory = false)
                  },
                  onDragFinished = {
                    viewModel.commitRecipeGesture("Crop Frame")
                  },
                )
              }
            }

            // Status badges & transient Undo/Redo feedback pill overlaid cleanly at top-center of canvas
            Column(
              modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (editorState.isComparingOriginal) {
                  Surface(
                    color = ObsidianCanvas.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, WarmAmber),
                  ) {
                    Text(
                      text = "ORIGINAL",
                      style = MaterialTheme.typography.labelSmall,
                      color = WarmAmber,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                } else {
                  Surface(
                    color = ObsidianCanvas.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, HairlineBorder),
                  ) {
                    Text(
                      text = "${recipe.aspectRatio.label}  •  ${viewModel.formatPixelDimensions()}",
                      style = MaterialTheme.typography.labelSmall,
                      color = StudioCyan,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                  Surface(
                    color = ObsidianCanvas.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, CoralFlame.copy(alpha = 0.65f)),
                    modifier = Modifier
                      .clip(RoundedCornerShape(50))
                      .clickable { viewModel.sendCurrentEditorPhotoToAi(AiLabMode.EDIT_IMAGE) }
                      .testTag("editor_ai_restyle_pill"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = CoralFlame,
                        modifier = Modifier.size(12.dp),
                      )
                      Text(
                        text = "AI Lab",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralFlame,
                      )
                    }
                  }
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                      .size(width = 96.dp, height = 48.dp)
                      .clip(RoundedCornerShape(50))
                      .clickable { showHistogram = !showHistogram }
                      .testTag("histogram_toggle_button"),
                  ) {
                    Surface(
                      color = if (showHistogram) StudioCyan.copy(alpha = 0.18f) else ObsidianCanvas.copy(alpha = 0.74f),
                      shape = RoundedCornerShape(50),
                      border = BorderStroke(
                        width = 1.dp,
                        color = if (showHistogram) StudioCyan.copy(alpha = 0.75f) else HairlineBorder,
                      ),
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Rounded.BarChart,
                          contentDescription = if (showHistogram) "Hide histogram overlay" else "Show histogram overlay",
                          tint = if (showHistogram) StudioCyan else Color.White.copy(alpha = 0.8f),
                          modifier = Modifier.size(13.dp),
                        )
                        Text(
                          text = "Histogram",
                          style = MaterialTheme.typography.labelSmall,
                          color = if (showHistogram) StudioCyan else Color.White.copy(alpha = 0.8f),
                        )
                      }
                    }
                  }
                }
              }

              val feedbackText = editorState.historyFeedbackMessage
              AnimatedVisibility(
                visible = !feedbackText.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
              ) {
                Surface(
                  color = StudioSurfaceElevated.copy(alpha = 0.94f),
                  shape = RoundedCornerShape(50),
                  border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.75f)),
                  modifier = Modifier.testTag("undo_redo_feedback_pill"),
                ) {
                  Text(
                    text = feedbackText.orEmpty(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = WarmAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                  )
                }
              }
            }

            AnimatedVisibility(
              visible = showHistogram && liveHistogram != null,
              enter = fadeIn(),
              exit = fadeOut(),
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 50.dp, end = 4.dp),
            ) {
              liveHistogram?.let { data ->
                HistogramOverlay(
                  histogram = data,
                  isComparingOriginal = editorState.isComparingOriginal,
                )
              }
            }
          }
        }
      }
    }
  }

  if (showExportSheet) {
    ExportSheet(
      pixelDimensions = viewModel.formatPixelDimensions(),
      currentAspect = editorState.recipe.aspectRatio,
      isExporting = editorState.isExporting,
      onDismiss = { showExportSheet = false },
      onApplyQuickAspect = viewModel::selectAspectRatio,
      onSaveToGallery = { fmt, quality, watermark ->
        viewModel.exportToGallery(context, fmt, quality, watermark) { ok, _ ->
          if (ok) showExportSheet = false
        }
      },
      onSharePlatform = { platform, fmt, quality, watermark ->
        viewModel.exportAndShareToPlatform(context, platform, fmt, quality, watermark) { _, _, _ ->
          showExportSheet = false
        }
      },
    )
  }

  if (showRenameDialog) {
    var draftTitle by remember(editorState.projectTitle) { mutableStateOf(editorState.projectTitle) }
    AlertDialog(
      onDismissRequest = { showRenameDialog = false },
      containerColor = StudioSurfaceElevated,
      title = { Text("Rename Project") },
      text = {
        OutlinedTextField(
          value = draftTitle,
          onValueChange = { if (it.length <= 100) draftTitle = it },
          label = { Text("Project title") },
          singleLine = true,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (draftTitle.isNotBlank()) viewModel.updateProjectTitle(draftTitle.trim())
            showRenameDialog = false
          }
        ) {
          Text("Save", color = CoralFlame)
        }
      },
      dismissButton = {
        TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showSavePresetDialog) {
    var presetName by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showSavePresetDialog = false },
      containerColor = StudioSurfaceElevated,
      title = { Text("Save Cloud Preset") },
      text = {
        OutlinedTextField(
          value = presetName,
          onValueChange = { if (it.length <= 40) presetName = it },
          label = { Text("Preset name (e.g. Golden Street)") },
          singleLine = true,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.saveCurrentAsPreset(presetName)
            showSavePresetDialog = false
          }
        ) {
          Text("Save to Cloud", color = CoralFlame)
        }
      },
      dismissButton = {
        TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun EditorTopBar(
  title: String,
  canUndo: Boolean,
  canRedo: Boolean,
  undoCount: Int,
  redoCount: Int,
  nextUndoLabel: String?,
  nextRedoLabel: String?,
  isComparing: Boolean,
  isSyncing: Boolean,
  isSynced: Boolean,
  onBack: () -> Unit,
  onRenameClick: () -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onToggleCompare: () -> Unit,
  onCloudSync: () -> Unit,
  onExportClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.statusBars)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f),
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.testTag("editor_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = stringResource(R.string.cd_back),
        )
      }
      Surface(
        color = StudioSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .clickable(onClick = onRenameClick),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(86.dp),
          )
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Rename",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
          )
        }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      val undoDescription = nextUndoLabel?.let { "Undo $it ($undoCount)" } ?: stringResource(R.string.action_undo)
      val redoDescription = nextRedoLabel?.let { "Redo $it ($redoCount)" } ?: stringResource(R.string.action_redo)
      IconButton(
        onClick = onUndo,
        enabled = canUndo,
        modifier = Modifier
          .size(48.dp)
          .testTag("undo_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Undo,
          contentDescription = undoDescription,
          tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
        )
      }
      IconButton(
        onClick = onRedo,
        enabled = canRedo,
        modifier = Modifier
          .size(48.dp)
          .testTag("redo_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Redo,
          contentDescription = redoDescription,
          tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
        )
      }
      IconButton(onClick = onToggleCompare, modifier = Modifier.testTag("compare_button")) {
        Icon(
          imageVector = Icons.Rounded.Compare,
          contentDescription = stringResource(R.string.action_compare),
          tint = if (isComparing) WarmAmber else MaterialTheme.colorScheme.onSurface,
        )
      }
      IconButton(
        onClick = onCloudSync,
        enabled = !isSyncing,
        modifier = Modifier.testTag("cloud_sync_button"),
      ) {
        when {
          isSyncing -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = StudioCyan)
          isSynced -> Icon(Icons.Rounded.CloudDone, contentDescription = "Synced", tint = StatusSuccess)
          else -> Icon(Icons.Rounded.CloudUpload, contentDescription = stringResource(R.string.action_cloud_sync), tint = StudioCyan)
        }
      }
      Spacer(modifier = Modifier.width(2.dp))
      Button(
        onClick = onExportClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = CoralFlame,
          contentColor = ObsidianCanvas,
        ),
        modifier = Modifier.testTag("editor_export_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.IosShare,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
          text = stringResource(R.string.action_export),
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
      }
    }
  }
}
