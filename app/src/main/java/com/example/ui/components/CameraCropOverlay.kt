package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.editor.CameraCropAspect
import com.example.editor.CameraFilter
import com.example.editor.EditHistoryManager
import com.example.editor.FilterManager
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.WarmAmber
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CameraViewfinderCropMask(
  targetRatio: Float,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@Canvas

    val containerRatio = w / h
    val cropW: Float
    val cropH: Float
    if (containerRatio > targetRatio) {
      cropH = h * 0.88f
      cropW = min(w * 0.94f, cropH * targetRatio)
    } else {
      cropW = w * 0.92f
      cropH = min(h * 0.86f, cropW / targetRatio)
    }

    val left = (w - cropW) / 2f
    val top = (h - cropH) / 2f
    val right = left + cropW
    val bottom = top + cropH

    val maskColor = ObsidianCanvas.copy(alpha = 0.62f)
    // Top, Bottom, Left, Right scrim bars around the active crop window
    drawRect(color = maskColor, topLeft = Offset.Zero, size = Size(w, top))
    drawRect(color = maskColor, topLeft = Offset(0f, bottom), size = Size(w, h - bottom))
    drawRect(color = maskColor, topLeft = Offset(0f, top), size = Size(left, cropH))
    drawRect(color = maskColor, topLeft = Offset(right, top), size = Size(w - right, cropH))

    // Subtle border & rule-of-thirds lines
    val gridColor = Color.White.copy(alpha = 0.25f)
    val thirdW = cropW / 3f
    val thirdH = cropH / 3f
    for (i in 1..2) {
      drawLine(
        color = gridColor,
        start = Offset(left + thirdW * i, top),
        end = Offset(left + thirdW * i, bottom),
        strokeWidth = 1.dp.toPx(),
      )
      drawLine(
        color = gridColor,
        start = Offset(left, top + thirdH * i),
        end = Offset(right, top + thirdH * i),
        strokeWidth = 1.dp.toPx(),
      )
    }

    drawRect(
      color = Color.White.copy(alpha = 0.55f),
      topLeft = Offset(left, top),
      size = Size(cropW, cropH),
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
    )

    // Corner brackets
    val cornerLen = min(cropW, cropH) * 0.10f
    val bracketStroke = 3.dp.toPx()
    val bracketColor = StudioCyan

    // Top-left
    drawLine(bracketColor, Offset(left, top), Offset(left + cornerLen, top), bracketStroke)
    drawLine(bracketColor, Offset(left, top), Offset(left, top + cornerLen), bracketStroke)
    // Top-right
    drawLine(bracketColor, Offset(right - cornerLen, top), Offset(right, top), bracketStroke)
    drawLine(bracketColor, Offset(right, top), Offset(right, top + cornerLen), bracketStroke)
    // Bottom-left
    drawLine(bracketColor, Offset(left, bottom - cornerLen), Offset(left, bottom), bracketStroke)
    drawLine(bracketColor, Offset(left, bottom), Offset(left + cornerLen, bottom), bracketStroke)
    // Bottom-right
    drawLine(bracketColor, Offset(right - cornerLen, bottom), Offset(right, bottom), bracketStroke)
    drawLine(bracketColor, Offset(right, bottom - cornerLen), Offset(right, bottom), bracketStroke)
  }
}

@Composable
fun CameraAspectRatioSelectorRow(
  selectedAspect: CameraCropAspect,
  enabled: Boolean,
  onSelectAspect: (CameraCropAspect) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .testTag("camera_aspect_row"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Rounded.Crop,
      contentDescription = null,
      tint = StudioCyan,
      modifier = Modifier.size(16.dp),
    )
    CameraCropAspect.entries.forEach { aspect ->
      val isSelected = selectedAspect == aspect
      Surface(
        color = if (isSelected) StudioCyan.copy(alpha = 0.2f) else StudioSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
          width = 1.dp,
          color = if (isSelected) StudioCyan else HairlineBorder,
        ),
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .clip(RoundedCornerShape(10.dp))
          .clickable(enabled = enabled) { onSelectAspect(aspect) }
          .testTag("camera_aspect_${aspect.id}"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = aspect.label,
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (isSelected) StudioCyan else Color.White,
            maxLines = 1,
          )
        }
      }
    }
  }
}

private data class CameraReviewHistoryState(
  val aspect: CameraCropAspect,
  val filter: CameraFilter,
  val brightness: Float = 0f,
  val contrast: Float = 0f,
)

@Composable
fun CameraPostCaptureReview(
  rawBitmap: Bitmap,
  selectedAspect: CameraCropAspect,
  selectedFilter: CameraFilter,
  brightness: Float,
  contrast: Float,
  filterManager: FilterManager,
  isBusy: Boolean,
  onSelectAspect: (CameraCropAspect) -> Unit,
  onSelectFilter: (CameraFilter) -> Unit,
  onBrightnessChange: (Float) -> Unit,
  onContrastChange: (Float) -> Unit,
  onRetake: () -> Unit,
  onShare: () -> Unit,
  onUsePhoto: () -> Unit,
) {
  val reviewHistory = remember(rawBitmap) {
    EditHistoryManager(CameraReviewHistoryState(selectedAspect, selectedFilter, brightness, contrast))
  }
  var historyTick by remember(rawBitmap) { mutableIntStateOf(0) }
  var reviewFeedback by remember(rawBitmap) { mutableStateOf<String?>(null) }
  var showToneControls by remember(rawBitmap) { mutableStateOf(true) }
  var isDraggingBrightness by remember(rawBitmap) { mutableStateOf(false) }
  var isDraggingContrast by remember(rawBitmap) { mutableStateOf(false) }

  LaunchedEffect(reviewFeedback) {
    if (!reviewFeedback.isNullOrBlank()) {
      delay(1400L)
      reviewFeedback = null
    }
  }

  val croppedPreview = remember(rawBitmap, selectedAspect) {
    filterManager.cropToAspectRatio(rawBitmap, selectedAspect)
  }
  val liveColorFilter = remember(selectedFilter, brightness, contrast) {
    filterManager.buildComposeColorFilter(selectedFilter, brightness, contrast)
  }

  val canUndo = remember(historyTick, reviewHistory) { reviewHistory.canUndo }
  val canRedo = remember(historyTick, reviewHistory) { reviewHistory.canRedo }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianCanvas),
  ) {
    // Top bar with Undo / Redo for post-capture crop, filter, brightness & contrast edits
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Crop & Filter Preview",
          style = MaterialTheme.typography.titleMedium,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${selectedAspect.label} • ${selectedFilter.displayName} • ${croppedPreview.width} × ${croppedPreview.height}",
          style = MaterialTheme.typography.labelSmall,
          color = StudioCyan,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        val toneModified = abs(brightness) > 0.01f || abs(contrast) > 0.01f
        Surface(
          color = if (showToneControls) CoralFlame.copy(alpha = 0.2f) else StudioSurfaceElevated,
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, if (showToneControls || toneModified) CoralFlame else HairlineBorder),
          modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = !isBusy) { showToneControls = !showToneControls }
            .testTag("review_tone_toggle"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Tune,
              contentDescription = "Toggle brightness and contrast",
              tint = if (showToneControls || toneModified) CoralFlame else Color.White,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Tone",
              style = MaterialTheme.typography.labelMedium,
              color = if (showToneControls || toneModified) CoralFlame else Color.White,
            )
          }
        }
        IconButton(
          onClick = {
            reviewHistory.undo()?.let { transition ->
              onSelectAspect(transition.newState.aspect)
              onSelectFilter(transition.newState.filter)
              onBrightnessChange(transition.newState.brightness)
              onContrastChange(transition.newState.contrast)
              reviewFeedback = "Undid ${transition.label}"
              historyTick++
            }
          },
          enabled = canUndo && !isBusy,
          modifier = Modifier
            .size(48.dp)
            .testTag("camera_review_undo_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Undo,
            contentDescription = reviewHistory.nextUndoLabel?.let { "Undo $it" } ?: "Undo",
            tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.28f),
          )
        }
        IconButton(
          onClick = {
            reviewHistory.redo()?.let { transition ->
              onSelectAspect(transition.newState.aspect)
              onSelectFilter(transition.newState.filter)
              onBrightnessChange(transition.newState.brightness)
              onContrastChange(transition.newState.contrast)
              reviewFeedback = "Redid ${transition.label}"
              historyTick++
            }
          },
          enabled = canRedo && !isBusy,
          modifier = Modifier
            .size(48.dp)
            .testTag("camera_review_redo_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Redo,
            contentDescription = reviewHistory.nextRedoLabel?.let { "Redo $it" } ?: "Redo",
            tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.28f),
          )
        }
      }
    }

    // Processed Image Preview (live FilterManager ColorFilter for instant Brightness & Contrast feedback)
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(16.dp),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        bitmap = croppedPreview.asImageBitmap(),
        contentDescription = "Captured photo crop preview",
        contentScale = ContentScale.Fit,
        colorFilter = liveColorFilter,
        modifier = Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(12.dp))
          .testTag("post_capture_preview_image"),
      )

      if (!reviewFeedback.isNullOrBlank()) {
        Surface(
          color = StudioSurfaceElevated.copy(alpha = 0.94f),
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.75f)),
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp)
            .testTag("camera_review_feedback_pill"),
        ) {
          Text(
            text = reviewFeedback.orEmpty(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = WarmAmber,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
          )
        }
      }
    }

    // Post-processing Brightness/Contrast, Aspect Ratio, Filter Strip & Actions
    Surface(
      color = StudioSurface,
      border = BorderStroke(1.dp, HairlineBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        AnimatedVisibility(visible = showToneControls) {
          Surface(
            color = StudioSurfaceElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.WbSunny,
                  contentDescription = null,
                  tint = if (abs(brightness) > 0.01f) CoralFlame else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Brightness",
                  style = MaterialTheme.typography.labelMedium,
                  color = Color.White,
                  modifier = Modifier.width(66.dp),
                )
                Slider(
                  value = brightness,
                  onValueChange = {
                    if (!isDraggingBrightness) {
                      isDraggingBrightness = true
                      reviewHistory.beginGesture(
                        CameraReviewHistoryState(selectedAspect, selectedFilter, brightness, contrast)
                      )
                    }
                    reviewHistory.updateGesturePreview(
                      CameraReviewHistoryState(selectedAspect, selectedFilter, it, contrast)
                    )
                    onBrightnessChange(it)
                  },
                  onValueChangeFinished = {
                    if (isDraggingBrightness) {
                      isDraggingBrightness = false
                      if (reviewHistory.finishGesture("Brightness")) {
                        reviewFeedback = null
                        historyTick++
                      }
                    }
                  },
                  enabled = !isBusy,
                  valueRange = -1f..1f,
                  colors = SliderDefaults.colors(
                    thumbColor = CoralFlame,
                    activeTrackColor = CoralFlame,
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("review_brightness_slider"),
                )
                Text(
                  text = "${if (brightness > 0.005f) "+" else ""}${(brightness * 100).roundToInt()}",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (abs(brightness) > 0.01f) CoralFlame else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.width(32.dp),
                )
                IconButton(
                  onClick = {
                    if (reviewHistory.commit(
                        CameraReviewHistoryState(selectedAspect, selectedFilter, 0f, contrast),
                        "Reset Brightness",
                      )
                    ) {
                      reviewFeedback = null
                      historyTick++
                      onBrightnessChange(0f)
                    }
                  },
                  enabled = !isBusy,
                  modifier = Modifier.size(48.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reset Brightness",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Contrast,
                  contentDescription = null,
                  tint = if (abs(contrast) > 0.01f) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Contrast",
                  style = MaterialTheme.typography.labelMedium,
                  color = Color.White,
                  modifier = Modifier.width(66.dp),
                )
                Slider(
                  value = contrast,
                  onValueChange = {
                    if (!isDraggingContrast) {
                      isDraggingContrast = true
                      reviewHistory.beginGesture(
                        CameraReviewHistoryState(selectedAspect, selectedFilter, brightness, contrast)
                      )
                    }
                    reviewHistory.updateGesturePreview(
                      CameraReviewHistoryState(selectedAspect, selectedFilter, brightness, it)
                    )
                    onContrastChange(it)
                  },
                  onValueChangeFinished = {
                    if (isDraggingContrast) {
                      isDraggingContrast = false
                      if (reviewHistory.finishGesture("Contrast")) {
                        reviewFeedback = null
                        historyTick++
                      }
                    }
                  },
                  enabled = !isBusy,
                  valueRange = -1f..1f,
                  colors = SliderDefaults.colors(
                    thumbColor = StudioCyan,
                    activeTrackColor = StudioCyan,
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("review_contrast_slider"),
                )
                Text(
                  text = "${if (contrast > 0.005f) "+" else ""}${(contrast * 100).roundToInt()}",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (abs(contrast) > 0.01f) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.width(32.dp),
                )
                IconButton(
                  onClick = {
                    if (reviewHistory.commit(
                        CameraReviewHistoryState(selectedAspect, selectedFilter, brightness, 0f),
                        "Reset Contrast",
                      )
                    ) {
                      reviewFeedback = null
                      historyTick++
                      onContrastChange(0f)
                    }
                  },
                  enabled = !isBusy,
                  modifier = Modifier.size(48.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reset Contrast",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }

        CameraAspectRatioSelectorRow(
          selectedAspect = selectedAspect,
          enabled = !isBusy,
          onSelectAspect = { aspect ->
            if (reviewHistory.commit(
                CameraReviewHistoryState(aspect, selectedFilter, brightness, contrast),
                "Crop ${aspect.label}",
              )
            ) {
              reviewFeedback = null
              historyTick++
              onSelectAspect(aspect)
            }
          },
        )

        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(FilterManager.availableFilters, key = { it.id }) { filter ->
            val isSelected = selectedFilter == filter
            Surface(
              color = if (isSelected) CoralFlame.copy(alpha = 0.2f) else StudioSurfaceElevated,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, if (isSelected) CoralFlame else HairlineBorder),
              modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !isBusy) {
                  if (reviewHistory.commit(
                      CameraReviewHistoryState(selectedAspect, filter, brightness, contrast),
                      "Filter: ${filter.displayName}",
                    )
                  ) {
                    reviewFeedback = null
                    historyTick++
                    onSelectFilter(filter)
                  }
                }
                .testTag("review_filter_${filter.id}"),
            ) {
              Box(
                modifier = Modifier.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = filter.displayName,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (isSelected) CoralFlame else Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onRetake,
            enabled = !isBusy,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier
              .height(50.dp)
              .testTag("camera_retake_button"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retake")
          }

          OutlinedButton(
            onClick = onShare,
            enabled = !isBusy,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.65f)),
            modifier = Modifier
              .height(50.dp)
              .testTag("camera_review_share_button"),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Share", color = StudioCyan)
          }

          Button(
            onClick = onUsePhoto,
            enabled = !isBusy,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = CoralFlame,
              contentColor = ObsidianCanvas,
            ),
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .testTag("camera_use_photo_button"),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ObsidianCanvas,
                strokeWidth = 2.dp,
              )
            } else {
              Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Edit Photo",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
              )
            }
          }
        }
      }
    }
  }
}
