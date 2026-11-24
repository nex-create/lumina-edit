package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.firebase.PhotoSyncStatus
import com.example.data.firebase.SyncState
import com.example.data.model.CloudPhotoProject
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.editor.FilterCatalog
import com.example.editor.PhotoEngine
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.WarmAmber
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CloudVaultScreen(
  profileState: UiState<UserProfile?>,
  projectsState: UiState<List<CloudPhotoProject>>,
  syncStatus: PhotoSyncStatus,
  vaultStatusMessage: String?,
  onClearVaultStatus: () -> Unit,
  onToggleAutoSync: (Boolean) -> Unit,
  onSyncNow: () -> Unit,
  onSaveToDeviceGallery: (CloudPhotoProject) -> Unit,
  onRestoreProject: (CloudPhotoProject) -> Unit,
  onDeleteProject: (CloudPhotoProject) -> Unit,
  onBackToStudio: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler { onBackToStudio() }

  val profile = (profileState as? UiState.Success)?.data
  val autoSyncEnabled = profile?.autoSyncEnabled ?: true
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(vaultStatusMessage) {
    if (!vaultStatusMessage.isNullOrBlank()) {
      snackbarHostState.showSnackbar(vaultStatusMessage)
      onClearVaultStatus()
    }
  }

  Scaffold(
    containerColor = Color.Transparent,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 680.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cloud Backup Vault", style = MaterialTheme.typography.headlineMedium)
            Text(
              text = "Edited photos auto-sync to Firestore & Firebase Storage so your gallery stays in sync across devices.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Auto-sync service status & cross-device controls card
        item {
          val projects = (projectsState as? UiState.Success)?.data.orEmpty()
          val projectCount = projects.size
          val cloudHdCount = projects.count {
            !it.storageDownloadUrl.isNullOrBlank() || !it.storagePath.isNullOrBlank()
          }
          Surface(
            color = StudioSurface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, HairlineBorder),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.CloudSync,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(24.dp),
                  )
                  Column {
                    Text("Auto-Save Cloud Service", style = MaterialTheme.typography.titleMedium)
                    Text(
                      text = if (autoSyncEnabled) {
                        "Auto-saving edits to Firestore & Storage"
                      } else {
                        "Manual cloud backup only"
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Switch(
                  checked = autoSyncEnabled,
                  onCheckedChange = onToggleAutoSync,
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianCanvas,
                    checkedTrackColor = StudioCyan,
                  ),
                  modifier = Modifier.testTag("auto_sync_switch"),
                )
              }

              // Live Sync Service Pill
              val (pillColor, statusLabel) = when (syncStatus.state) {
                SyncState.SYNCING -> StudioCyan to (syncStatus.lastMessage ?: "Uploading edited photo...")
                SyncState.SYNCED -> StatusSuccess to (syncStatus.lastMessage ?: "All edits synced")
                SyncState.OFFLINE_PENDING -> WarmAmber to (syncStatus.lastMessage ?: "${syncStatus.pendingCount} photo(s) queued for upload")
                SyncState.ERROR -> StatusError to (syncStatus.lastMessage ?: "Storage sync attention needed")
                SyncState.IDLE -> StudioCyan to "Sync service standing by"
              }

              Surface(
                color = pillColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, pillColor.copy(alpha = 0.4f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("sync_service_status_banner"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  if (syncStatus.state == SyncState.SYNCING) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(16.dp),
                      strokeWidth = 2.dp,
                      color = StudioCyan,
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Rounded.CloudDone,
                      contentDescription = null,
                      tint = pillColor,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                  Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = pillColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                StatBox(
                  label = "Synced Edits",
                  value = projectCount.toString(),
                  accent = CoralFlame,
                  modifier = Modifier.weight(1f),
                )
                StatBox(
                  label = "Storage HD",
                  value = cloudHdCount.toString(),
                  accent = StudioCyan,
                  modifier = Modifier.weight(1f),
                )
                StatBox(
                  label = "Total Backups",
                  value = (profile?.totalBackups ?: projectCount.toLong()).toString(),
                  accent = WarmAmber,
                  modifier = Modifier.weight(1f),
                )
              }

              OutlinedButton(
                onClick = onSyncNow,
                enabled = syncStatus.state != SyncState.SYNCING,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.6f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("sync_gallery_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Sync,
                  contentDescription = null,
                  tint = StudioCyan,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Sync Gallery Across Devices",
                  color = StudioCyan,
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
              }
            }
          }
        }

        when (projectsState) {
          is UiState.Loading -> {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 40.dp),
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
                border = BorderStroke(1.dp, StatusError.copy(alpha = 0.45f)),
              ) {
                Text(
                  text = projectsState.message,
                  style = MaterialTheme.typography.bodyMedium,
                  color = StatusError,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          }

          is UiState.Success -> {
            val projects = projectsState.data
            if (projects.isEmpty()) {
              item {
                Surface(
                  color = StudioSurfaceElevated,
                  shape = MaterialTheme.shapes.medium,
                  border = BorderStroke(1.dp, HairlineBorder),
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.CloudDone,
                      contentDescription = null,
                      tint = StudioCyan,
                      modifier = Modifier.size(36.dp),
                    )
                    Text("No Cloud Backups Yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                      text = "Edit any photo in Studio and Lumina Edit will automatically save both its edit recipe to Firestore and full-res image to Firebase Storage.",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                      onClick = onBackToStudio,
                      colors = ButtonDefaults.buttonColors(
                        containerColor = CoralFlame,
                        contentColor = ObsidianCanvas,
                      ),
                      modifier = Modifier.height(48.dp),
                    ) {
                      Text("Go to Studio")
                    }
                  }
                }
              }
            } else {
              items(projects, key = { it.id }) { project ->
                CloudProjectCard(
                  project = project,
                  onRestore = { onRestoreProject(project) },
                  onSaveToGallery = { onSaveToDeviceGallery(project) },
                  onDelete = { onDeleteProject(project) },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatBox(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = StudioSurfaceElevated,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun CloudProjectCard(
  project: CloudPhotoProject,
  onRestore: () -> Unit,
  onSaveToGallery: () -> Unit,
  onDelete: () -> Unit,
) {
  val thumb = remember(project.thumbnailBase64) {
    PhotoEngine.decodeThumbnailBitmap(project.thumbnailBase64)
  }
  val filterName = remember(project.filterId) {
    FilterCatalog.findById(project.filterId).displayName
  }
  val dateText = remember(project.updatedAt, project.createdAt) {
    val ts = project.updatedAt ?: project.createdAt ?: Timestamp.now()
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(ts.toDate())
  }
  val hasStorageAsset = !project.storageDownloadUrl.isNullOrBlank() || !project.storagePath.isNullOrBlank()

  Surface(
    color = StudioSurfaceElevated,
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("cloud_project_card_${project.id}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(StudioSurface),
          contentAlignment = Alignment.Center,
        ) {
          if (thumb != null) {
            Image(
              bitmap = thumb.asImageBitmap(),
              contentDescription = project.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Icon(Icons.Rounded.Edit, contentDescription = null, tint = StudioCyan)
          }
          if (!project.storageDownloadUrl.isNullOrBlank()) {
            AsyncImage(
              model = project.storageDownloadUrl,
              contentDescription = project.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = project.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "$filterName  •  ${project.aspectRatio}  •  $dateText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = if (hasStorageAsset) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                contentDescription = null,
                tint = if (hasStorageAsset) StudioCyan else WarmAmber,
                modifier = Modifier.size(13.dp),
              )
              val storageLabel = if (hasStorageAsset) {
                val kb = (project.syncedPhotoSizeBytes / 1024L).coerceAtLeast(1L)
                "Storage HD (${kb} KB)"
              } else {
                "Recipe Synced"
              }
              Text(
                text = storageLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (hasStorageAsset) StudioCyan else WarmAmber,
                maxLines = 1,
              )
            }

            if (project.exportedCount > 0L) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.IosShare,
                  contentDescription = null,
                  tint = StatusSuccess,
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text = "${project.exportedCount}x",
                  style = MaterialTheme.typography.labelSmall,
                  color = StatusSuccess,
                )
              }
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = onRestore,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = CoralFlame.copy(alpha = 0.18f),
            contentColor = CoralFlame,
          ),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("restore_project_${project.id}"),
        ) {
          Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Edit", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }

        OutlinedButton(
          onClick = onSaveToGallery,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.55f)),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("save_cloud_gallery_${project.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.CloudDownload,
            contentDescription = null,
            tint = StudioCyan,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Save", style = MaterialTheme.typography.labelLarge, color = StudioCyan, maxLines = 1)
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .size(48.dp)
            .testTag("delete_project_${project.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Delete backup",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}
