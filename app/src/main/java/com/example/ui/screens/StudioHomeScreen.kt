package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AspectPreset
import com.example.data.model.CloudPhotoProject
import com.example.data.model.EditRecipe
import com.example.data.model.SamplePhoto
import com.example.data.model.UiState
import com.example.editor.FilterCatalog
import com.example.editor.PhotoEngine
import com.example.ui.AiLabMode
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.WarmAmber
import com.google.firebase.auth.FirebaseUser

private data class QuickRecipeCard(
  val title: String,
  val subtitle: String,
  val sample: SamplePhoto,
  val recipe: EditRecipe,
  val accent: Color,
)

@Composable
fun StudioHomeScreen(
  currentUser: FirebaseUser,
  projectsState: UiState<List<CloudPhotoProject>>,
  onPickGalleryPhoto: (Uri) -> Unit,
  onOpenCamera: () -> Unit,
  onOpenAiLab: (AiLabMode) -> Unit,
  onOpenSample: (SamplePhoto, EditRecipe?) -> Unit,
  onOpenCloudProject: (CloudPhotoProject) -> Unit,
  onNavigateToVault: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = { uri ->
      if (uri != null) {
        onPickGalleryPhoto(uri)
      }
    },
  )

  val quickTemplates = remember {
    listOf(
      QuickRecipeCard(
        title = "IG Feed 4:5",
        subtitle = "Creamy Portrait",
        sample = SamplePhoto.PORTRAIT_CAFE,
        recipe = EditRecipe(
          filterId = "creamy",
          filterIntensity = 0.85f,
          aspectRatio = AspectPreset.PORTRAIT_4_5,
          warmth = 0.12f,
        ),
        accent = CoralFlame,
      ),
      QuickRecipeCard(
        title = "Reels 9:16",
        subtitle = "Cyber Neon",
        sample = SamplePhoto.ROOFTOP_DUSK,
        recipe = EditRecipe(
          filterId = "cyber_neon",
          filterIntensity = 0.9f,
          aspectRatio = AspectPreset.STORY_9_16,
          contrast = 0.15f,
        ),
        accent = StudioCyan,
      ),
      QuickRecipeCard(
        title = "Travel 4:3",
        subtitle = "Kodak Gold",
        sample = SamplePhoto.COASTAL_VILLA,
        recipe = EditRecipe(
          filterId = "kodak_gold",
          filterIntensity = 0.8f,
          aspectRatio = AspectPreset.CLASSIC_4_3,
          warmth = 0.2f,
        ),
        accent = WarmAmber,
      ),
    )
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 680.dp),
      contentPadding = PaddingValues(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      // Top Studio Bar
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (currentUser.photoUrl != null) {
              AsyncImage(
                model = currentUser.photoUrl,
                contentDescription = currentUser.displayName ?: "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape),
              )
            } else {
              Surface(
                shape = CircleShape,
                color = CoralFlame.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, CoralFlame),
                modifier = Modifier.size(40.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = (currentUser.displayName?.firstOrNull() ?: 'L').uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = CoralFlame,
                  )
                }
              }
            }
            Column {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.CloudDone,
                  contentDescription = null,
                  tint = StatusSuccess,
                  modifier = Modifier.size(13.dp),
                )
                Text(
                  text = currentUser.displayName ?: "Cloud Synced",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          IconButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("sign_out_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Logout,
              contentDescription = stringResource(R.string.cd_sign_out),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Hero Studio Import Card
      item {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = StudioSurface,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                Brush.linearGradient(
                  colors = listOf(
                    CoralFlame.copy(alpha = 0.16f),
                    StudioSurface,
                    StudioCyan.copy(alpha = 0.12f),
                  )
                )
              )
              .padding(20.dp),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Text(
                  text = "PHOTO WORKSPACE",
                  style = MaterialTheme.typography.labelMedium,
                  color = CoralFlame,
                )
              }
              Text(
                text = "Grade, Crop & Export in Seconds",
                style = MaterialTheme.typography.headlineMedium,
              )
              Text(
                text = "Import any photo from your device gallery to apply Hypic-style film presets, social aspect ratios, and sync backups to your Cloud Vault.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = {
                    photoPickerLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = CoralFlame,
                    contentColor = ObsidianCanvas,
                  ),
                  modifier = Modifier
                    .weight(1.45f)
                    .height(52.dp)
                    .testTag("import_photo_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Gallery",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                  )
                }

                OutlinedButton(
                  onClick = onOpenCamera,
                  shape = RoundedCornerShape(14.dp),
                  border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.65f)),
                  colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = StudioCyan,
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("take_photo_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.action_take_photo),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                  )
                }
              }

              // AI Lab Quick Actions
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Surface(
                  color = StudioSurfaceElevated,
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, CoralFlame.copy(alpha = 0.45f)),
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenAiLab(AiLabMode.CREATE_IMAGE) }
                    .testTag("home_ai_create_button"),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                  ) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = CoralFlame, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Create", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                  }
                }
                Surface(
                  color = StudioSurfaceElevated,
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.45f)),
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenAiLab(AiLabMode.ANIMATE_VIDEO) }
                    .testTag("home_ai_animate_button"),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                  ) {
                    Icon(Icons.Rounded.MovieCreation, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Animate Video", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                  }
                }
              }
            }
          }
        }
      }

      // Studio Sample Photos
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Try Studio Samples",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp),
          )
          LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(SamplePhoto.entries, key = { it.key }) { sample ->
              Surface(
                shape = MaterialTheme.shapes.medium,
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                  .width(170.dp)
                  .clip(MaterialTheme.shapes.medium)
                  .clickable { onOpenSample(sample, null) }
                  .testTag("sample_card_${sample.key}"),
              ) {
                Column {
                  Image(
                    painter = painterResource(id = sample.drawableRes),
                    contentDescription = sample.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(140.dp),
                  )
                  Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                  ) {
                    Text(
                      text = sample.title,
                      style = MaterialTheme.typography.titleSmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = sample.subtitle,
                      style = MaterialTheme.typography.bodySmall,
                      color = StudioCyan,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Social Aspect + Filter Starters
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "One-Tap Social Starters",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp),
          )
          LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(quickTemplates) { template ->
              Surface(
                shape = MaterialTheme.shapes.medium,
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, template.accent.copy(alpha = 0.45f)),
                modifier = Modifier
                  .width(156.dp)
                  .clip(MaterialTheme.shapes.medium)
                  .clickable { onOpenSample(template.sample, template.recipe) },
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Crop,
                      contentDescription = null,
                      tint = template.accent,
                      modifier = Modifier.size(16.dp),
                    )
                    Text(
                      text = template.title,
                      style = MaterialTheme.typography.labelLarge,
                      color = template.accent,
                    )
                  }
                  Text(
                    text = template.subtitle,
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.FilterVintage,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(12.dp),
                    )
                    Text(
                      text = "Tap to edit",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Recent Cloud Backups strip
      item {
        val recentProjects = (projectsState as? UiState.Success)?.data.orEmpty().take(5)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Recent Cloud Backups",
              style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onNavigateToVault) {
              Text("Open Vault", color = StudioCyan)
            }
          }

          if (recentProjects.isEmpty()) {
            Surface(
              color = StudioSurface,
              shape = MaterialTheme.shapes.medium,
              border = BorderStroke(1.dp, HairlineBorder),
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            ) {
              Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.CloudDone,
                  contentDescription = null,
                  tint = StudioCyan,
                  modifier = Modifier.size(28.dp),
                )
                Column {
                  Text("Your Cloud Vault is ready", style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = "Open any sample or gallery photo and tap Sync in the editor to back up its edit recipe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 20.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(recentProjects, key = { it.id }) { proj ->
                val thumb = remember(proj.thumbnailBase64) {
                  PhotoEngine.decodeThumbnailBitmap(proj.thumbnailBase64)
                }
                val filterName = remember(proj.filterId) {
                  FilterCatalog.findById(proj.filterId).displayName
                }
                Surface(
                  color = StudioSurfaceElevated,
                  shape = MaterialTheme.shapes.medium,
                  border = BorderStroke(1.dp, HairlineBorder),
                  modifier = Modifier
                    .width(150.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onOpenCloudProject(proj) },
                ) {
                  Column {
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(StudioSurface),
                      contentAlignment = Alignment.Center,
                    ) {
                      if (thumb != null) {
                        Image(
                          bitmap = thumb.asImageBitmap(),
                          contentDescription = proj.title,
                          contentScale = ContentScale.Crop,
                          modifier = Modifier.fillMaxSize(),
                        )
                      } else {
                        Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = StudioCyan)
                      }
                      if (!proj.storageDownloadUrl.isNullOrBlank()) {
                        AsyncImage(
                          model = proj.storageDownloadUrl,
                          contentDescription = proj.title,
                          contentScale = ContentScale.Crop,
                          modifier = Modifier.fillMaxSize(),
                        )
                      }
                    }
                    Column(modifier = Modifier.padding(10.dp)) {
                      Text(
                        text = proj.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "$filterName • ${proj.aspectRatio}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoralFlame,
                        maxLines = 1,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
