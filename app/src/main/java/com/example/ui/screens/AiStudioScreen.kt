package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ai.AiImageAspect
import com.example.ai.VideoAspectOption
import com.example.data.model.SamplePhoto
import com.example.export.ExportManager
import com.example.ui.AiImageResultState
import com.example.ui.AiLabMode
import com.example.ui.AiLabUiState
import com.example.ui.AiVideoResultState
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StatusError
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.WarmAmber

@Composable
fun AiStudioScreen(
  aiState: AiLabUiState,
  onSelectMode: (AiLabMode) -> Unit,
  onPickGalleryPhoto: (Uri) -> Unit,
  onSelectSample: (SamplePhoto) -> Unit,
  onGenerateImage: (String, AiImageAspect) -> Unit,
  onEditImage: (String, AiImageAspect) -> Unit,
  onAnimateVideo: (String, VideoAspectOption) -> Unit,
  onOpenBitmapInEditor: (Bitmap, String) -> Unit,
  onSaveBitmapToGallery: (Bitmap, String) -> Unit,
  onSaveVideoToGallery: (Uri, String) -> Unit,
  onUseBitmapAsInput: (Bitmap, String, AiLabMode) -> Unit,
  onClearStatus: () -> Unit,
  onBackToStudio: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler { onBackToStudio() }

  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(aiState.statusMessage) {
    val msg = aiState.statusMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      onClearStatus()
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = { uri ->
      if (uri != null) onPickGalleryPhoto(uri)
    },
  )

  var createPrompt by rememberSaveable { mutableStateOf("") }
  var editPrompt by rememberSaveable { mutableStateOf("") }
  var videoPrompt by rememberSaveable { mutableStateOf("Gentle cinematic camera push-in with subtle ambient motion") }
  var selectedImageAspect by rememberSaveable { mutableStateOf(AiImageAspect.PORTRAIT_4_5) }
  var selectedVideoAspect by rememberSaveable { mutableStateOf(VideoAspectOption.PORTRAIT_9_16) }
  var showOriginalCompare by remember { mutableStateOf(false) }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 680.dp),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Header
      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = CoralFlame,
              modifier = Modifier.size(22.dp),
            )
            Text(
              text = "AI Studio Lab",
              style = MaterialTheme.typography.headlineMedium,
            )
          }
          Text(
            text = "Create artwork, restyle photos with prompts, or animate stills into 16:9 and 9:16 video clips.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Mode Selector Bar
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AiLabMode.entries.forEach { mode ->
            val selected = aiState.mode == mode
            val tag = when (mode) {
              AiLabMode.CREATE_IMAGE -> "ai_mode_create"
              AiLabMode.EDIT_IMAGE -> "ai_mode_edit"
              AiLabMode.ANIMATE_VIDEO -> "ai_mode_animate"
            }
            val icon = when (mode) {
              AiLabMode.CREATE_IMAGE -> Icons.Rounded.AutoAwesome
              AiLabMode.EDIT_IMAGE -> Icons.Rounded.AutoFixHigh
              AiLabMode.ANIMATE_VIDEO -> Icons.Rounded.MovieCreation
            }
            Surface(
              color = if (selected) CoralFlame else StudioSurfaceElevated,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, if (selected) CoralFlame else HairlineBorder),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onSelectMode(mode) }
                .testTag(tag),
            ) {
              Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = null,
                  tint = if (selected) ObsidianCanvas else MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = mode.label,
                  style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  ),
                  color = if (selected) ObsidianCanvas else MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }

      // Source Photo Card for Edit & Animate Modes
      if (aiState.mode != AiLabMode.CREATE_IMAGE) {
        item {
          Surface(
            color = StudioSurface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Source Photo", style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = aiState.inputLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                OutlinedButton(
                  onClick = {
                    photoPickerLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.65f)),
                  modifier = Modifier
                    .height(48.dp)
                    .testTag("ai_pick_photo_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Pick Photo", color = StudioCyan)
                }
              }

              if (aiState.inputBitmap != null) {
                Image(
                  bitmap = aiState.inputBitmap.asImageBitmap(),
                  contentDescription = aiState.inputLabel,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp)),
                )
              } else {
                Text(
                  text = "Choose a studio sample below or pick any photo from your gallery:",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SamplePhoto.entries, key = { it.key }) { sample ->
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                      .clip(RoundedCornerShape(10.dp))
                      .clickable { onSelectSample(sample) },
                  ) {
                    Row(
                      modifier = Modifier.padding(6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Image(
                        painter = painterResource(sample.drawableRes),
                        contentDescription = sample.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .size(36.dp)
                          .clip(RoundedCornerShape(6.dp)),
                      )
                      Text(
                        text = sample.title,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 6.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Mode-specific controls and results
      when (aiState.mode) {
        AiLabMode.CREATE_IMAGE -> {
          item {
            ImagePromptComposer(
              title = "Describe the image to create",
              placeholder = "e.g., Editorial street portrait in Tokyo rain, warm neon reflections, 35mm film grain",
              prompt = createPrompt,
              onPromptChange = { createPrompt = it },
              suggestions = listOf(
                "Golden hour rooftop portrait, 35mm Kodak film look",
                "Neon cyberpunk rain street with moody reflections",
                "Minimalist coastal architecture at pastel dusk",
                "Editorial studio still life with warm sunlight shadows",
              ),
              selectedAspect = selectedImageAspect,
              onSelectAspect = { selectedImageAspect = it },
              isLoading = aiState.imageState is AiImageResultState.Loading,
              ctaLabel = "Generate Image",
              ctaTestTag = "ai_generate_image_button",
              inputTestTag = "ai_create_prompt_input",
              onSubmit = { onGenerateImage(createPrompt, selectedImageAspect) },
            )
          }

          item {
            AiImageResultSection(
              state = aiState.imageState,
              originalBitmap = null,
              showOriginalCompare = false,
              onToggleCompare = {},
              onOpenInEditor = { bmp -> onOpenBitmapInEditor(bmp, "AI Creation") },
              onSaveToGallery = { bmp -> onSaveBitmapToGallery(bmp, "LuminaAI") },
              onSendToAnimate = { bmp ->
                onUseBitmapAsInput(bmp, "AI Creation", AiLabMode.ANIMATE_VIDEO)
              },
            )
          }
        }

        AiLabMode.EDIT_IMAGE -> {
          item {
            ImagePromptComposer(
              title = "Describe how to edit this photo",
              placeholder = "e.g., Transform lighting to warm golden sunset and add soft dreamy haze",
              prompt = editPrompt,
              onPromptChange = { editPrompt = it },
              suggestions = listOf(
                "Transform to warm golden hour sunset lighting",
                "Add dreamy editorial film glow and soft pastel sky",
                "Restyle as high-contrast monochrome street photography",
                "Replace background with soft studio bokeh",
              ),
              selectedAspect = selectedImageAspect,
              onSelectAspect = { selectedImageAspect = it },
              isLoading = aiState.imageState is AiImageResultState.Loading,
              ctaLabel = "Apply AI Edit",
              ctaTestTag = "ai_apply_edit_button",
              inputTestTag = "ai_edit_prompt_input",
              onSubmit = { onEditImage(editPrompt, selectedImageAspect) },
            )
          }

          item {
            AiImageResultSection(
              state = aiState.imageState,
              originalBitmap = aiState.inputBitmap,
              showOriginalCompare = showOriginalCompare,
              onToggleCompare = { showOriginalCompare = !showOriginalCompare },
              onOpenInEditor = { bmp -> onOpenBitmapInEditor(bmp, "${aiState.inputLabel} AI") },
              onSaveToGallery = { bmp -> onSaveBitmapToGallery(bmp, "${aiState.inputLabel}_AI") },
              onSendToAnimate = { bmp ->
                onUseBitmapAsInput(bmp, "${aiState.inputLabel} AI", AiLabMode.ANIMATE_VIDEO)
              },
            )
          }
        }

        AiLabMode.ANIMATE_VIDEO -> {
          item {
            Surface(
              color = StudioSurface,
              shape = MaterialTheme.shapes.medium,
              border = BorderStroke(1.dp, HairlineBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text(
                  text = "Video Aspect Ratio",
                  style = MaterialTheme.typography.titleSmall,
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  VideoAspectOption.entries.forEach { option ->
                    val selected = selectedVideoAspect == option
                    val tag = if (option == VideoAspectOption.LANDSCAPE_16_9) {
                      "video_aspect_16_9"
                    } else {
                      "video_aspect_9_16"
                    }
                    Surface(
                      color = if (selected) StudioCyan.copy(alpha = 0.16f) else StudioSurfaceElevated,
                      shape = RoundedCornerShape(12.dp),
                      border = BorderStroke(1.dp, if (selected) StudioCyan else HairlineBorder),
                      modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedVideoAspect = option }
                        .testTag(tag),
                    ) {
                      Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                      ) {
                        Text(
                          text = "${option.ratioString} • ${option.orientationLabel}",
                          style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                          ),
                          color = if (selected) StudioCyan else MaterialTheme.colorScheme.onSurface,
                        )
                      }
                    }
                  }
                }

                Text(
                  text = "Motion Direction",
                  style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                  value = videoPrompt,
                  onValueChange = { videoPrompt = it },
                  placeholder = { Text("Describe camera motion & scene animation...") },
                  minLines = 2,
                  maxLines = 4,
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioCyan,
                    unfocusedBorderColor = HairlineBorder,
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_video_prompt_input"),
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  val motions = listOf(
                    "Slow cinematic camera push-in with subtle light shimmer",
                    "Gentle breeze moving hair and foliage, warm glow",
                    "Smooth parallax orbit around subject",
                  )
                  items(motions) { suggestion ->
                    FilterChip(
                      selected = videoPrompt == suggestion,
                      onClick = { videoPrompt = suggestion },
                      label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                      colors = FilterChipDefaults.filterChipColors(
                        containerColor = StudioSurfaceElevated,
                        selectedContainerColor = StudioCyan.copy(alpha = 0.18f),
                      ),
                    )
                  }
                }

                Button(
                  onClick = { onAnimateVideo(videoPrompt, selectedVideoAspect) },
                  enabled = aiState.videoState !is AiVideoResultState.Loading,
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = StudioCyan,
                    contentColor = ObsidianCanvas,
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("ai_animate_video_button"),
                ) {
                  Icon(Icons.Rounded.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (aiState.videoState is AiVideoResultState.Loading) {
                      "Animating Video..."
                    } else {
                      "Animate Photo (${selectedVideoAspect.ratioString})"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }
            }
          }

          item {
            AiVideoResultSection(
              state = aiState.videoState,
              onSaveVideo = { uri -> onSaveVideoToGallery(uri, "${aiState.inputLabel}_motion") },
              onShareVideo = { uri -> ExportManager.shareVideo(context, uri) },
            )
          }
        }
      }
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(16.dp),
    )
  }
}

@Composable
private fun ImagePromptComposer(
  title: String,
  placeholder: String,
  prompt: String,
  onPromptChange: (String) -> Unit,
  suggestions: List<String>,
  selectedAspect: AiImageAspect,
  onSelectAspect: (AiImageAspect) -> Unit,
  isLoading: Boolean,
  ctaLabel: String,
  ctaTestTag: String,
  inputTestTag: String,
  onSubmit: () -> Unit,
) {
  Surface(
    color = StudioSurface,
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(text = title, style = MaterialTheme.typography.titleSmall)

      OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChange,
        placeholder = { Text(placeholder) },
        minLines = 2,
        maxLines = 4,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = CoralFlame,
          unfocusedBorderColor = HairlineBorder,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag(inputTestTag),
      )

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(suggestions) { suggestion ->
          FilterChip(
            selected = prompt == suggestion,
            onClick = { onPromptChange(suggestion) },
            label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = FilterChipDefaults.filterChipColors(
              containerColor = StudioSurfaceElevated,
              selectedContainerColor = CoralFlame.copy(alpha = 0.2f),
            ),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Ratio",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AiImageAspect.entries.forEach { aspect ->
          val active = selectedAspect == aspect
          Surface(
            color = if (active) CoralFlame.copy(alpha = 0.2f) else StudioSurfaceElevated,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (active) CoralFlame else HairlineBorder),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .clip(RoundedCornerShape(10.dp))
              .clickable { onSelectAspect(aspect) },
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = aspect.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) CoralFlame else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      Button(
        onClick = onSubmit,
        enabled = !isLoading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = CoralFlame,
          contentColor = ObsidianCanvas,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag(ctaTestTag),
      ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (isLoading) "Generating..." else ctaLabel,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
      }
    }
  }
}

@Composable
private fun AiImageResultSection(
  state: AiImageResultState,
  originalBitmap: Bitmap?,
  showOriginalCompare: Boolean,
  onToggleCompare: () -> Unit,
  onOpenInEditor: (Bitmap) -> Unit,
  onSaveToGallery: (Bitmap) -> Unit,
  onSendToAnimate: (Bitmap) -> Unit,
) {
  when (state) {
    AiImageResultState.Idle -> Unit

    AiImageResultState.Loading -> {
      Surface(
        color = StudioSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, CoralFlame.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          CircularProgressIndicator(color = CoralFlame)
          Text(
            text = "Synthesizing high-res image (may take 30–60s)...",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    is AiImageResultState.Error -> {
      Surface(
        color = StatusError.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.55f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("ai_image_error_banner"),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = StatusError)
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    is AiImageResultState.Success -> {
      val displayBmp = if (showOriginalCompare && originalBitmap != null) originalBitmap else state.bitmap
      Surface(
        color = StudioSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (showOriginalCompare) "Original Photo" else "AI Output",
              style = MaterialTheme.typography.titleSmall,
              color = if (showOriginalCompare) WarmAmber else StudioCyan,
            )
            if (originalBitmap != null) {
              OutlinedButton(
                onClick = onToggleCompare,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(40.dp),
              ) {
                Icon(Icons.Rounded.Compare, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (showOriginalCompare) "Show AI" else "Compare")
              }
            }
          }

          Image(
            bitmap = displayBmp.asImageBitmap(),
            contentDescription = "AI generated image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(ObsidianCanvas),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = { onOpenInEditor(state.bitmap) },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = CoralFlame,
                contentColor = ObsidianCanvas,
              ),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("ai_open_in_editor_button"),
            ) {
              Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Open in Editor", maxLines = 1)
            }
            OutlinedButton(
              onClick = { onSaveToGallery(state.bitmap) },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.height(48.dp),
            ) {
              Icon(Icons.Rounded.Download, contentDescription = "Save to gallery", modifier = Modifier.size(18.dp))
            }
            OutlinedButton(
              onClick = { onSendToAnimate(state.bitmap) },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.height(48.dp),
            ) {
              Icon(Icons.Rounded.MovieCreation, contentDescription = "Animate", tint = StudioCyan, modifier = Modifier.size(18.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AiVideoResultSection(
  state: AiVideoResultState,
  onSaveVideo: (Uri) -> Unit,
  onShareVideo: (Uri) -> Unit,
) {
  when (state) {
    AiVideoResultState.Idle -> Unit

    AiVideoResultState.Loading -> {
      Surface(
        color = StudioSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          CircularProgressIndicator(color = StudioCyan)
          Text(
            text = "Animating photo into video (may take 30–60s)...",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    is AiVideoResultState.Error -> {
      Surface(
        color = StatusError.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.55f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("ai_video_error_banner"),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = StatusError)
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    is AiVideoResultState.Success -> {
      Surface(
        color = StudioSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "Generated Video (${state.aspect.ratioString})",
            style = MaterialTheme.typography.titleSmall,
            color = StudioCyan,
          )

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(state.aspect.aspectValue)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black),
            contentAlignment = Alignment.Center,
          ) {
            AndroidView(
              factory = { ctx ->
                VideoView(ctx).apply {
                  val controller = MediaController(ctx)
                  controller.setAnchorView(this)
                  setMediaController(controller)
                  setVideoURI(state.videoUri)
                  setOnPreparedListener { mp ->
                    mp.isLooping = true
                    start()
                  }
                }
              },
              update = { view ->
                view.setVideoURI(state.videoUri)
              },
              modifier = Modifier.fillMaxSize(),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = { onSaveVideo(state.videoUri) },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = StudioCyan,
                contentColor = ObsidianCanvas,
              ),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("ai_save_video_button"),
            ) {
              Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Save Video")
            }
            OutlinedButton(
              onClick = { onShareVideo(state.videoUri) },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("ai_share_video_button"),
            ) {
              Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Share")
            }
          }
        }
      }
    }
  }
}
