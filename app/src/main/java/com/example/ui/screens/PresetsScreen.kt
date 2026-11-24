package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudPreset
import com.example.data.model.EditRecipe
import com.example.data.model.SamplePhoto
import com.example.data.model.UiState
import com.example.editor.FilterCatalog
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.StatusError
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.WarmAmber
import kotlin.math.roundToInt

@Composable
fun PresetsScreen(
  presetsState: UiState<List<CloudPreset>>,
  onApplyPresetToSample: (SamplePhoto, EditRecipe) -> Unit,
  onDeletePreset: (String) -> Unit,
  onBackToStudio: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler { onBackToStudio() }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 680.dp),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Presets & Color Looks", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "Custom cloud-synced recipes & built-in editorial film grades. Tap any look to open it in Studio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      item {
        Text("Your Cloud Presets", style = MaterialTheme.typography.titleLarge)
      }

      when (presetsState) {
        is UiState.Loading -> {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(color = CoralFlame)
            }
          }
        }

        is UiState.Error -> {
          item {
            Surface(
              color = StatusError.copy(alpha = 0.14f),
              shape = MaterialTheme.shapes.medium,
            ) {
              Text(
                text = presetsState.message,
                color = StatusError,
                modifier = Modifier.padding(14.dp),
              )
            }
          }
        }

        is UiState.Success -> {
          val presets = presetsState.data
          if (presets.isEmpty()) {
            item {
              Surface(
                color = StudioSurface,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, HairlineBorder),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = WarmAmber)
                  Text(
                    text = "No custom presets yet. Inside the Editor, open the Presets tab and tap \"Save Look\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            items(presets, key = { it.id }) { preset ->
              val base = remember(preset.filterId) { FilterCatalog.findById(preset.filterId) }
              Surface(
                color = StudioSurfaceElevated,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(MaterialTheme.shapes.medium)
                  .clickable {
                    onApplyPresetToSample(SamplePhoto.PORTRAIT_CAFE, preset.applyTo(EditRecipe()))
                  },
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Box(
                      modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(base.badgeColor.copy(alpha = 0.25f)),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = base.badgeColor,
                        modifier = Modifier.size(18.dp),
                      )
                    }
                    Column {
                      Text(preset.name, style = MaterialTheme.typography.titleMedium)
                      Text(
                        text = "${base.displayName} (${(preset.filterIntensity * 100).roundToInt()}%) • Exp ${
                          if (preset.exposure >= 0) "+" else ""
                        }${(preset.exposure * 100).roundToInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  IconButton(onClick = { onDeletePreset(preset.id) }) {
                    Icon(
                      imageVector = Icons.Rounded.DeleteOutline,
                      contentDescription = "Delete preset",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }
      }

      item {
        Text(
          text = "Curated Studio Filters",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(top = 8.dp),
        )
      }

      items(FilterCatalog.presets.drop(1), key = { it.id }) { filter ->
        Surface(
          color = StudioSurface,
          shape = MaterialTheme.shapes.medium,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable {
              val sample = if (filter.category == "Mood") SamplePhoto.ROOFTOP_DUSK else SamplePhoto.PORTRAIT_CAFE
              onApplyPresetToSample(sample, EditRecipe(filterId = filter.id, filterIntensity = 0.88f))
            },
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(filter.badgeColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.FilterVintage,
                  contentDescription = null,
                  tint = filter.badgeColor,
                  modifier = Modifier.size(18.dp),
                )
              }
              Column {
                Text(filter.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                  text = filter.category,
                  style = MaterialTheme.typography.bodySmall,
                  color = StudioCyan,
                )
              }
            }
            Text(
              text = "Try Look",
              style = MaterialTheme.typography.labelLarge,
              color = CoralFlame,
            )
          }
        }
      }
    }
  }
}
