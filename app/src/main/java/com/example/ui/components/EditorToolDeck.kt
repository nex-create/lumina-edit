package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdjustParam
import com.example.data.model.AspectPreset
import com.example.data.model.CloudPreset
import com.example.data.model.EditRecipe
import com.example.data.model.EditorTab
import com.example.editor.FilterCatalog
import com.example.editor.PhotoEngine
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.WarmAmber
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EditorToolDeck(
  activeTab: EditorTab,
  recipe: EditRecipe,
  thumbBitmap: Bitmap?,
  selectedCategory: String,
  selectedAdjustParam: AdjustParam,
  cloudPresets: List<CloudPreset>,
  onSelectTab: (EditorTab) -> Unit,
  onSelectCategory: (String) -> Unit,
  onSelectFilter: (String) -> Unit,
  onFilterIntensityChange: (Float) -> Unit,
  onGestureStarted: () -> Unit,
  onGestureFinished: (String) -> Unit,
  onSelectAdjustParam: (AdjustParam) -> Unit,
  onAdjustValueChange: (AdjustParam, Float) -> Unit,
  onResetAdjustParam: (AdjustParam) -> Unit,
  onSelectAspect: (AspectPreset) -> Unit,
  onRotate90: (Boolean) -> Unit,
  onFlipHorizontal: () -> Unit,
  onFlipVertical: () -> Unit,
  onStraightenChange: (Float) -> Unit,
  onResetGeometry: () -> Unit,
  onResetAll: () -> Unit,
  onApplyPreset: (CloudPreset) -> Unit,
  onOpenSavePresetDialog: () -> Unit,
) {
  Surface(
    color = StudioSurface,
    tonalElevation = 4.dp,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(top = 10.dp, bottom = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      when (activeTab) {
        EditorTab.FILTERS -> FiltersPanel(
          recipe = recipe,
          thumbBitmap = thumbBitmap,
          selectedCategory = selectedCategory,
          onSelectCategory = onSelectCategory,
          onSelectFilter = onSelectFilter,
          onFilterIntensityChange = onFilterIntensityChange,
          onAdjustValueChange = onAdjustValueChange,
          onResetAdjustParam = onResetAdjustParam,
          onGestureStarted = onGestureStarted,
          onGestureFinished = onGestureFinished,
        )

        EditorTab.ADJUST -> AdjustPanel(
          recipe = recipe,
          selectedParam = selectedAdjustParam,
          onSelectParam = onSelectAdjustParam,
          onValueChange = onAdjustValueChange,
          onResetParam = onResetAdjustParam,
          onGestureStarted = onGestureStarted,
          onGestureFinished = onGestureFinished,
          onResetAll = onResetAll,
        )

        EditorTab.CROP -> CropPanel(
          recipe = recipe,
          onSelectAspect = onSelectAspect,
          onRotate90 = onRotate90,
          onFlipHorizontal = onFlipHorizontal,
          onFlipVertical = onFlipVertical,
          onStraightenChange = onStraightenChange,
          onGestureStarted = onGestureStarted,
          onGestureFinished = onGestureFinished,
          onResetGeometry = onResetGeometry,
        )

        EditorTab.PRESETS -> PresetsPanel(
          presets = cloudPresets,
          onApplyPreset = onApplyPreset,
          onOpenSavePresetDialog = onOpenSavePresetDialog,
        )
      }

      // Bottom Segmented Studio Tab Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        EditorTab.entries.forEach { tab ->
          val selected = tab == activeTab
          val icon = when (tab) {
            EditorTab.FILTERS -> Icons.Rounded.FilterVintage
            EditorTab.ADJUST -> Icons.Rounded.Tune
            EditorTab.CROP -> Icons.Rounded.Crop
            EditorTab.PRESETS -> Icons.Rounded.AutoAwesome
          }
          Surface(
            color = if (selected) CoralFlame.copy(alpha = 0.16f) else Color.Transparent,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .clip(RoundedCornerShape(14.dp))
              .minimumInteractiveComponentSize()
              .clickable { onSelectTab(tab) }
              .testTag("editor_tab_${tab.name.lowercase()}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = icon,
                contentDescription = tab.label,
                tint = if (selected) CoralFlame else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = tab.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) CoralFlame else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FiltersPanel(
  recipe: EditRecipe,
  thumbBitmap: Bitmap?,
  selectedCategory: String,
  onSelectCategory: (String) -> Unit,
  onSelectFilter: (String) -> Unit,
  onFilterIntensityChange: (Float) -> Unit,
  onAdjustValueChange: (AdjustParam, Float) -> Unit,
  onResetAdjustParam: (AdjustParam) -> Unit,
  onGestureStarted: () -> Unit,
  onGestureFinished: (String) -> Unit,
) {
  var isDragging by remember { mutableStateOf(false) }
  var showToneSliders by remember { mutableStateOf(false) }
  val hasToneEdits = abs(recipe.exposure) > 0.01f || abs(recipe.contrast) > 0.01f
  val filteredPresets = remember(selectedCategory) {
    if (selectedCategory == "All") FilterCatalog.presets
    else FilterCatalog.presets.filter { it.id == "original" || it.category == selectedCategory }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Intensity Slider when a non-original filter is selected
    if (recipe.filterId != "original") {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Intensity",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
          value = recipe.filterIntensity,
          onValueChange = {
            if (!isDragging) {
              isDragging = true
              onGestureStarted()
            }
            onFilterIntensityChange(it)
          },
          onValueChangeFinished = {
            if (isDragging) {
              isDragging = false
              onGestureFinished("Filter Intensity")
            }
          },
          valueRange = 0f..1f,
          colors = SliderDefaults.colors(
            thumbColor = CoralFlame,
            activeTrackColor = CoralFlame,
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("filter_intensity_slider"),
        )
        Text(
          text = "${(recipe.filterIntensity * 100).roundToInt()}%",
          style = MaterialTheme.typography.labelMedium,
          color = CoralFlame,
          modifier = Modifier.width(38.dp),
        )
      }
    }

    AnimatedVisibility(visible = showToneSliders) {
      BrightnessContrastSliderCard(
        brightness = recipe.exposure,
        contrast = recipe.contrast,
        onBrightnessChange = { onAdjustValueChange(AdjustParam.EXPOSURE, it) },
        onContrastChange = { onAdjustValueChange(AdjustParam.CONTRAST, it) },
        onResetBrightness = { onResetAdjustParam(AdjustParam.EXPOSURE) },
        onResetContrast = { onResetAdjustParam(AdjustParam.CONTRAST) },
        onGestureStarted = onGestureStarted,
        onGestureFinished = onGestureFinished,
        brightnessTestTag = "filters_brightness_slider",
        contrastTestTag = "filters_contrast_slider",
      )
    }

    // Category Pills + Quick Brightness/Contrast Tone Toggle
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      item {
        val toneActive = showToneSliders || hasToneEdits
        Surface(
          color = if (showToneSliders) CoralFlame.copy(alpha = 0.2f) else StudioSurfaceElevated,
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, if (toneActive) CoralFlame else HairlineBorder),
          modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { showToneSliders = !showToneSliders }
            .testTag("filters_tone_toggle"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Brightness6,
              contentDescription = "Toggle brightness and contrast sliders",
              tint = if (toneActive) CoralFlame else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Brightness & Contrast",
              style = MaterialTheme.typography.labelMedium,
              color = if (toneActive) CoralFlame else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      items(FilterCatalog.categories) { cat ->
        val selected = cat == selectedCategory
        Surface(
          color = if (selected) StudioSurfaceHighest else Color.Transparent,
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, if (selected) StudioCyan else HairlineBorder),
          modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onSelectCategory(cat) },
        ) {
          Text(
            text = cat,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }
    }

    // Live-Tinted Photo Filter Strip
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(filteredPresets, key = { it.id }) { preset ->
        val selected = recipe.filterId == preset.id
        val previewColorFilter = remember(preset.id) {
          PhotoEngine.buildComposeColorFilter(EditRecipe(filterId = preset.id, filterIntensity = 0.9f))
        }
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(5.dp),
          modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelectFilter(preset.id) }
            .testTag("filter_item_${preset.id}"),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) CoralFlame else HairlineBorder),
            color = StudioSurfaceElevated,
            modifier = Modifier.size(64.dp),
          ) {
            if (thumbBitmap != null) {
              Image(
                bitmap = thumbBitmap.asImageBitmap(),
                contentDescription = preset.displayName,
                contentScale = ContentScale.Crop,
                colorFilter = previewColorFilter,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(preset.badgeColor.copy(alpha = 0.35f)),
              )
            }
          }
          Text(
            text = preset.displayName,
            style = MaterialTheme.typography.bodySmall.copy(
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (selected) CoralFlame else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun BrightnessContrastSliderCard(
  brightness: Float,
  contrast: Float,
  onBrightnessChange: (Float) -> Unit,
  onContrastChange: (Float) -> Unit,
  onResetBrightness: () -> Unit,
  onResetContrast: () -> Unit,
  onGestureStarted: () -> Unit,
  onGestureFinished: (String) -> Unit,
  brightnessTestTag: String,
  contrastTestTag: String,
) {
  var isDraggingBrightness by remember { mutableStateOf(false) }
  var isDraggingContrast by remember { mutableStateOf(false) }

  Surface(
    color = StudioSurfaceElevated,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      // Brightness Slider Row
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
          modifier = Modifier.width(68.dp),
        )
        Slider(
          value = brightness,
          onValueChange = {
            if (!isDraggingBrightness) {
              isDraggingBrightness = true
              onGestureStarted()
            }
            onBrightnessChange(it)
          },
          onValueChangeFinished = {
            if (isDraggingBrightness) {
              isDraggingBrightness = false
              onGestureFinished("Brightness")
            }
          },
          valueRange = -1f..1f,
          colors = SliderDefaults.colors(
            thumbColor = CoralFlame,
            activeTrackColor = CoralFlame,
          ),
          modifier = Modifier
            .weight(1f)
            .testTag(brightnessTestTag),
        )
        Text(
          text = "${if (brightness > 0.005f) "+" else ""}${(brightness * 100).roundToInt()}",
          style = MaterialTheme.typography.labelMedium,
          color = if (abs(brightness) > 0.01f) CoralFlame else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(34.dp),
        )
        IconButton(
          onClick = onResetBrightness,
          modifier = Modifier
            .size(48.dp)
            .testTag("reset_$brightnessTestTag"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Reset Brightness",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
        }
      }

      // Contrast Slider Row
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
          modifier = Modifier.width(68.dp),
        )
        Slider(
          value = contrast,
          onValueChange = {
            if (!isDraggingContrast) {
              isDraggingContrast = true
              onGestureStarted()
            }
            onContrastChange(it)
          },
          onValueChangeFinished = {
            if (isDraggingContrast) {
              isDraggingContrast = false
              onGestureFinished("Contrast")
            }
          },
          valueRange = -1f..1f,
          colors = SliderDefaults.colors(
            thumbColor = StudioCyan,
            activeTrackColor = StudioCyan,
          ),
          modifier = Modifier
            .weight(1f)
            .testTag(contrastTestTag),
        )
        Text(
          text = "${if (contrast > 0.005f) "+" else ""}${(contrast * 100).roundToInt()}",
          style = MaterialTheme.typography.labelMedium,
          color = if (abs(contrast) > 0.01f) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(34.dp),
        )
        IconButton(
          onClick = onResetContrast,
          modifier = Modifier
            .size(48.dp)
            .testTag("reset_$contrastTestTag"),
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

@Composable
private fun AdjustPanel(
  recipe: EditRecipe,
  selectedParam: AdjustParam,
  onSelectParam: (AdjustParam) -> Unit,
  onValueChange: (AdjustParam, Float) -> Unit,
  onResetParam: (AdjustParam) -> Unit,
  onGestureStarted: () -> Unit,
  onGestureFinished: (String) -> Unit,
  onResetAll: () -> Unit,
) {
  var isDragging by remember(selectedParam) { mutableStateOf(false) }
  val currentVal = recipe.adjustValue(selectedParam)

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (selectedParam == AdjustParam.EXPOSURE || selectedParam == AdjustParam.CONTRAST) {
      BrightnessContrastSliderCard(
        brightness = recipe.exposure,
        contrast = recipe.contrast,
        onBrightnessChange = { onValueChange(AdjustParam.EXPOSURE, it) },
        onContrastChange = { onValueChange(AdjustParam.CONTRAST, it) },
        onResetBrightness = { onResetParam(AdjustParam.EXPOSURE) },
        onResetContrast = { onResetParam(AdjustParam.CONTRAST) },
        onGestureStarted = onGestureStarted,
        onGestureFinished = onGestureFinished,
        brightnessTestTag = "adjust_brightness_slider",
        contrastTestTag = "adjust_contrast_slider",
      )
    } else {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = selectedParam.label,
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.width(74.dp),
        )
        Slider(
          value = currentVal,
          onValueChange = {
            if (!isDragging) {
              isDragging = true
              onGestureStarted()
            }
            onValueChange(selectedParam, it)
          },
          onValueChangeFinished = {
            if (isDragging) {
              isDragging = false
              onGestureFinished(selectedParam.label)
            }
          },
          valueRange = selectedParam.min..selectedParam.max,
          colors = SliderDefaults.colors(
            thumbColor = CoralFlame,
            activeTrackColor = CoralFlame,
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("adjust_slider"),
        )
        Text(
          text = "${if (currentVal > 0.005f) "+" else ""}${(currentVal * 100).roundToInt()}",
          style = MaterialTheme.typography.labelMedium,
          color = if (abs(currentVal) > 0.01f) CoralFlame else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(38.dp),
        )
        IconButton(
          onClick = { onResetParam(selectedParam) },
          modifier = Modifier.size(48.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Reset ${selectedParam.label}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }

    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      items(AdjustParam.entries) { param ->
        val selected = param == selectedParam
        val isModified = abs(recipe.adjustValue(param)) > 0.01f
        val icon: ImageVector = when (param) {
          AdjustParam.EXPOSURE -> Icons.Rounded.WbSunny
          AdjustParam.CONTRAST -> Icons.Rounded.Contrast
          AdjustParam.SATURATION -> Icons.Rounded.Palette
          AdjustParam.WARMTH -> Icons.Rounded.Thermostat
          AdjustParam.TINT -> Icons.Rounded.Gradient
          AdjustParam.FADE -> Icons.Rounded.Brightness6
          AdjustParam.VIGNETTE -> Icons.Rounded.BlurOn
        }
        Surface(
          color = if (selected) CoralFlame.copy(alpha = 0.18f) else StudioSurfaceElevated,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, if (selected) CoralFlame else HairlineBorder),
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelectParam(param) }
            .testTag("adjust_param_${param.name.lowercase()}"),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Box(contentAlignment = Alignment.TopEnd) {
              Icon(
                imageVector = icon,
                contentDescription = param.label,
                tint = if (selected) CoralFlame else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
              )
              if (isModified) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(StudioCyan),
                )
              }
            }
            Text(
              text = param.label,
              style = MaterialTheme.typography.bodySmall,
              color = if (selected) CoralFlame else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item {
        TextButton(onClick = onResetAll) {
          Text("Reset All", color = StudioCyan, style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@Composable
private fun CropPanel(
  recipe: EditRecipe,
  onSelectAspect: (AspectPreset) -> Unit,
  onRotate90: (Boolean) -> Unit,
  onFlipHorizontal: () -> Unit,
  onFlipVertical: () -> Unit,
  onStraightenChange: (Float) -> Unit,
  onGestureStarted: () -> Unit,
  onGestureFinished: (String) -> Unit,
  onResetGeometry: () -> Unit,
) {
  var isDragging by remember { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Straighten + Quick Rotate/Flip Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = "Tilt ${recipe.straightenDegrees.roundToInt()}°",
        style = MaterialTheme.typography.labelMedium,
        color = if (abs(recipe.straightenDegrees) > 0.5f) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(56.dp),
      )
      Slider(
        value = recipe.straightenDegrees,
        onValueChange = {
          if (!isDragging) {
            isDragging = true
            onGestureStarted()
          }
          onStraightenChange(it)
        },
        onValueChangeFinished = {
          if (isDragging) {
            isDragging = false
            onGestureFinished("Straighten")
          }
        },
        valueRange = -45f..45f,
        colors = SliderDefaults.colors(
          thumbColor = StudioCyan,
          activeTrackColor = StudioCyan,
        ),
        modifier = Modifier.weight(1f),
      )
      IconButton(
        onClick = { onRotate90(false) },
        modifier = Modifier.testTag("rotate_left_button"),
      ) {
        Icon(Icons.Rounded.Rotate90DegreesCcw, contentDescription = "Rotate Left 90°")
      }
      IconButton(
        onClick = { onRotate90(true) },
        modifier = Modifier.testTag("rotate_right_button"),
      ) {
        Icon(Icons.Rounded.Rotate90DegreesCw, contentDescription = "Rotate Right 90°")
      }
      IconButton(
        onClick = onFlipHorizontal,
        modifier = Modifier.testTag("flip_horizontal_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Flip,
          contentDescription = "Flip Horizontal",
          tint = if (recipe.flipHorizontal) CoralFlame else MaterialTheme.colorScheme.onSurface,
        )
      }
      IconButton(onClick = onResetGeometry) {
        Icon(Icons.Rounded.Refresh, contentDescription = "Reset Crop", tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    // Aspect Ratio Presets
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(AspectPreset.entries) { preset ->
        val selected = recipe.aspectRatio == preset
        Surface(
          color = if (selected) CoralFlame.copy(alpha = 0.18f) else StudioSurfaceElevated,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, if (selected) CoralFlame else HairlineBorder),
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelectAspect(preset) }
            .testTag("aspect_chip_${preset.key}"),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = preset.label,
              style = MaterialTheme.typography.labelLarge,
              color = if (selected) CoralFlame else MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = preset.socialHint,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PresetsPanel(
  presets: List<CloudPreset>,
  onApplyPreset: (CloudPreset) -> Unit,
  onOpenSavePresetDialog: () -> Unit,
) {
  LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    item {
      Surface(
        color = CoralFlame.copy(alpha = 0.16f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CoralFlame.copy(alpha = 0.55f)),
        modifier = Modifier
          .clip(RoundedCornerShape(14.dp))
          .clickable(onClick = onOpenSavePresetDialog)
          .testTag("save_preset_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, tint = CoralFlame, modifier = Modifier.size(18.dp))
          Text("Save Look", style = MaterialTheme.typography.labelLarge, color = CoralFlame)
        }
      }
    }

    if (presets.isEmpty()) {
      item {
        Text(
          text = "Save your favorite filter + adjustment stack to sync across devices.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 8.dp),
        )
      }
    } else {
      items(presets, key = { it.id }) { preset ->
        val baseFilter = remember(preset.filterId) { FilterCatalog.findById(preset.filterId) }
        Surface(
          color = StudioSurfaceElevated,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onApplyPreset(preset) },
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(WarmAmber),
              )
              Text(preset.name, style = MaterialTheme.typography.labelLarge)
            }
            Text(
              text = "Base: ${baseFilter.displayName}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
