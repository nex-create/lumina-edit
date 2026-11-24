package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AspectPreset
import com.example.data.model.ExportFormat
import com.example.data.model.ExportPlatform
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
  pixelDimensions: String,
  currentAspect: AspectPreset,
  isExporting: Boolean,
  onDismiss: () -> Unit,
  onApplyQuickAspect: (AspectPreset) -> Unit,
  onSaveToGallery: (ExportFormat, Int, Boolean) -> Unit,
  onSharePlatform: (ExportPlatform, ExportFormat, Int, Boolean) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedFormat by remember { mutableStateOf(ExportFormat.JPEG) }
  var qualityFloat by remember { mutableFloatStateOf(92f) }
  var addWatermark by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = StudioSurface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Header + resolution pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Export Studio",
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text = "Ready for Gallery & Social Feeds",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Surface(
          color = StudioSurfaceElevated,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, HairlineBorder),
        ) {
          Text(
            text = pixelDimensions,
            style = MaterialTheme.typography.labelMedium,
            color = StudioCyan,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }

      // Quick Social Aspect Fit Strip
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "QUICK ASPECT FIT",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val quickAspects = listOf(
            AspectPreset.PORTRAIT_4_5,
            AspectPreset.STORY_9_16,
            AspectPreset.SQUARE,
            AspectPreset.WIDE_16_9,
          )
          items(quickAspects) { aspect ->
            val selected = currentAspect == aspect
            FilterChip(
              selected = selected,
              onClick = { onApplyQuickAspect(aspect) },
              label = { Text("${aspect.label} ${aspect.socialHint}") },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = CoralFlame.copy(alpha = 0.2f),
                selectedLabelColor = CoralFlame,
              ),
              modifier = Modifier.testTag("export_aspect_${aspect.key}"),
            )
          }
        }
      }

      // Format & Quality Card
      Surface(
        color = StudioSurfaceElevated,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, HairlineBorder),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Format", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ExportFormat.entries.forEach { fmt ->
                FilterChip(
                  selected = selectedFormat == fmt,
                  onClick = { selectedFormat = fmt },
                  label = { Text(fmt.label, style = MaterialTheme.typography.labelMedium) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StudioCyan.copy(alpha = 0.2f),
                    selectedLabelColor = StudioCyan,
                  ),
                )
              }
            }
          }

          if (selectedFormat != ExportFormat.PNG) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Compression Quality", style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "${qualityFloat.roundToInt()}%",
                  style = MaterialTheme.typography.labelMedium,
                  color = CoralFlame,
                )
              }
              Slider(
                value = qualityFloat,
                onValueChange = { qualityFloat = it },
                valueRange = 65f..100f,
                colors = SliderDefaults.colors(
                  thumbColor = CoralFlame,
                  activeTrackColor = CoralFlame,
                ),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = CoralFlame,
                modifier = Modifier.size(18.dp),
              )
              Text("Lumina Studio Watermark", style = MaterialTheme.typography.titleSmall)
            }
            Switch(
              checked = addWatermark,
              onCheckedChange = { addWatermark = it },
              colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianCanvas,
                checkedTrackColor = CoralFlame,
              ),
              modifier = Modifier.testTag("watermark_switch"),
            )
          }
        }
      }

      // Primary Save to Gallery CTA
      Button(
        onClick = { onSaveToGallery(selectedFormat, qualityFloat.roundToInt(), addWatermark) },
        enabled = !isExporting,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = CoralFlame,
          contentColor = ObsidianCanvas,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("save_to_gallery_button"),
      ) {
        if (isExporting) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = ObsidianCanvas,
            strokeWidth = 2.dp,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text("Rendering Photo...", style = MaterialTheme.typography.labelLarge)
        } else {
          Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = stringResource(R.string.action_save_gallery),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
      }

      HorizontalDivider(color = HairlineBorder)

      // Social Media Platforms Grid
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "SHARE TO SOCIAL PLATFORMS",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ExportPlatform.entries.take(3).forEach { platform ->
            SocialPlatformCard(
              platform = platform,
              isRecommended = currentAspect == platform.recommendedAspect,
              enabled = !isExporting,
              onClick = { onSharePlatform(platform, selectedFormat, qualityFloat.roundToInt(), addWatermark) },
              modifier = Modifier.weight(1f),
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ExportPlatform.entries.drop(3).forEach { platform ->
            SocialPlatformCard(
              platform = platform,
              isRecommended = currentAspect == platform.recommendedAspect,
              enabled = !isExporting,
              onClick = { onSharePlatform(platform, selectedFormat, qualityFloat.roundToInt(), addWatermark) },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SocialPlatformCard(
  platform: ExportPlatform,
  isRecommended: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val icon: ImageVector = when (platform) {
    ExportPlatform.INSTAGRAM -> Icons.Rounded.CameraAlt
    ExportPlatform.TIKTOK -> Icons.Rounded.VideoLibrary
    ExportPlatform.X_TWITTER -> Icons.Rounded.Tag
    ExportPlatform.WHATSAPP -> Icons.Rounded.ChatBubble
    ExportPlatform.SNAPCHAT -> Icons.Rounded.Public
    ExportPlatform.SYSTEM_SHARE -> Icons.Rounded.Share
  }

  Surface(
    color = StudioSurfaceElevated,
    shape = MaterialTheme.shapes.small,
    border = BorderStroke(
      1.dp,
      if (isRecommended) StatusSuccess.copy(alpha = 0.6f) else HairlineBorder,
    ),
    modifier = modifier
      .clip(MaterialTheme.shapes.small)
      .clickable(enabled = enabled, onClick = onClick)
      .testTag("share_platform_${platform.name.lowercase()}"),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = platform.accentColor.copy(alpha = 0.16f),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = platform.label,
          tint = platform.accentColor,
          modifier = Modifier
            .padding(8.dp)
            .size(18.dp),
        )
      }
      Text(
        text = platform.label,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        if (isRecommended) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = StatusSuccess,
            modifier = Modifier.size(11.dp),
          )
        }
        Text(
          text = platform.recommendedAspect.label,
          style = MaterialTheme.typography.labelSmall,
          color = if (isRecommended) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
