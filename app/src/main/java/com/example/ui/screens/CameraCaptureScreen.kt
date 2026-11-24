package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.ExportFormat
import com.example.data.model.ExportPlatform
import com.example.data.model.PhotoSource
import com.example.editor.CameraCropAspect
import com.example.editor.CameraFilter
import com.example.editor.FilterManager
import com.example.editor.PhotoEngine
import com.example.export.ExportManager
import com.example.ui.components.CameraAspectRatioSelectorRow
import com.example.ui.components.CameraPostCaptureReview
import com.example.ui.components.CameraViewfinderCropMask
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StatusError
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "CameraCapture"

@Composable
fun CameraCaptureScreen(
  hasEditedPhoto: Boolean = false,
  onShareEditedPhoto: ((Boolean, String?) -> Unit) -> Unit = { cb -> cb(false, null) },
  onPhotoCaptured: (Uri, CameraCropAspect) -> Unit,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val filterManager = remember { FilterManager() }
  val snackbarHostState = remember { SnackbarHostState() }

  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted -> hasPermission = granted },
  )

  LaunchedEffect(Unit) {
    if (!hasPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
  var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
  var selectedFilter by remember { mutableStateOf(CameraFilter.ORIGINAL) }
  var selectedAspect by remember { mutableStateOf(CameraCropAspect.FULL) }
  var reviewBrightness by remember { mutableFloatStateOf(0f) }
  var reviewContrast by remember { mutableFloatStateOf(0f) }
  var isCapturing by remember { mutableStateOf(false) }
  var isSharing by remember { mutableStateOf(false) }
  var isProcessingReview by remember { mutableStateOf(false) }
  var capturedRawBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var cameraError by remember { mutableStateOf<String?>(null) }

  BackHandler {
    if (capturedRawBitmap != null) {
      capturedRawBitmap = null
      reviewBrightness = 0f
      reviewContrast = 0f
    } else {
      onClose()
    }
  }

  val previewChipThumb = remember(context) {
    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
    BitmapFactory.decodeResource(context.resources, R.drawable.img_sample_portrait, opts)
  }

  val previewView = remember(context) {
    PreviewView(context).apply {
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }

  val imageCapture = remember(selectedAspect) {
    ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
      .build()
  }

  LaunchedEffect(flashMode, imageCapture) {
    imageCapture.flashMode = flashMode
  }

  DisposableEffect(hasPermission, lensFacing, selectedAspect, lifecycleOwner, capturedRawBitmap) {
    if (!hasPermission || capturedRawBitmap != null) {
      return@DisposableEffect onDispose {}
    }

    var cameraProvider: ProcessCameraProvider? = null
    val providerFuture = ProcessCameraProvider.getInstance(context)
    val executor = ContextCompat.getMainExecutor(context)

    providerFuture.addListener(
      {
        try {
          val provider = providerFuture.get()
          cameraProvider = provider
          val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

          val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
          }

          val cropRational = selectedAspect.toRational()
          val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
          imageCapture.targetRotation = rotation
          if (cropRational != null) {
            imageCapture.setCropAspectRatio(cropRational)
          }

          val useCaseGroupBuilder = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageCapture)

          if (cropRational != null) {
            val viewPort = ViewPort.Builder(cropRational, rotation)
              .setScaleType(ViewPort.FILL_CENTER)
              .build()
            useCaseGroupBuilder.setViewPort(viewPort)
          } else {
            previewView.viewPort?.let { useCaseGroupBuilder.setViewPort(it) }
          }

          provider.unbindAll()
          provider.bindToLifecycle(lifecycleOwner, selector, useCaseGroupBuilder.build())
          cameraError = null
        } catch (e: Exception) {
          Log.e(TAG, "CameraX binding failed", e)
          cameraError = e.localizedMessage ?: "Camera unavailable on this device."
        }
      },
      executor,
    )

    onDispose {
      try {
        cameraProvider?.unbindAll()
      } catch (_: Exception) {
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianCanvas),
  ) {
    if (!hasPermission) {
      CameraPermissionGate(
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onClose = onClose,
      )
    } else if (capturedRawBitmap != null) {
      val rawBmp = capturedRawBitmap!!
      CameraPostCaptureReview(
        rawBitmap = rawBmp,
        selectedAspect = selectedAspect,
        selectedFilter = selectedFilter,
        brightness = reviewBrightness,
        contrast = reviewContrast,
        filterManager = filterManager,
        isBusy = isProcessingReview,
        onSelectAspect = { selectedAspect = it },
        onSelectFilter = { selectedFilter = it },
        onBrightnessChange = { reviewBrightness = it },
        onContrastChange = { reviewContrast = it },
        onRetake = {
          capturedRawBitmap = null
          reviewBrightness = 0f
          reviewContrast = 0f
        },
        onShare = {
          isProcessingReview = true
          scope.launch {
            val res = withContext(Dispatchers.Default) {
              val cropped = filterManager.cropToAspectRatio(rawBmp, selectedAspect)
              val filtered = filterManager.applyFilter(
                bitmap = cropped,
                filter = selectedFilter,
                brightness = reviewBrightness,
                contrast = reviewContrast,
              )
              ExportManager.prepareShareUri(
                context = context.applicationContext,
                bitmap = filtered,
                format = ExportFormat.JPEG,
                quality = 94,
                titlePrefix = "Lumina_${selectedAspect.id}_${selectedFilter.id}",
              )
            }
            isProcessingReview = false
            res.fold(
              onSuccess = { shareUri ->
                ExportManager.shareToPlatform(
                  context = context,
                  uri = shareUri,
                  platform = ExportPlatform.SYSTEM_SHARE,
                  mimeType = ExportFormat.JPEG.mimeType,
                )
              },
              onFailure = { err ->
                snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to share photo.")
              },
            )
          }
        },
        onUsePhoto = {
          isProcessingReview = true
          scope.launch {
            val finalUri = withContext(Dispatchers.Default) {
              saveProcessedBitmapToCache(
                context = context.applicationContext,
                rawBitmap = rawBmp,
                filterManager = filterManager,
                aspect = selectedAspect,
                filter = selectedFilter,
                brightness = reviewBrightness,
                contrast = reviewContrast,
              )
            }
            isProcessingReview = false
            if (finalUri != null) {
              capturedRawBitmap = null
              reviewBrightness = 0f
              reviewContrast = 0f
              onPhotoCaptured(finalUri, selectedAspect)
            } else {
              snackbarHostState.showSnackbar("Could not save processed photo.")
            }
          }
        },
      )
    } else {
      Column(modifier = Modifier.fillMaxSize()) {
        // Live CameraX Preview Area
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(Color.Black),
        ) {
          AndroidView(
            factory = { previewView },
            modifier = Modifier
              .fillMaxSize()
              .testTag("camerax_preview"),
          )

          val tintColor = when (selectedFilter) {
            CameraFilter.ORIGINAL -> Color.Transparent
            CameraFilter.GRAYSCALE -> Color(0xFF888E99).copy(alpha = 0.16f)
            CameraFilter.SEPIA -> Color(0xFFB5783A).copy(alpha = 0.22f)
            CameraFilter.HIGH_CONTRAST -> Color(0xFF101218).copy(alpha = 0.12f)
            CameraFilter.WARM -> Color(0xFFF5A623).copy(alpha = 0.16f)
            CameraFilter.COOL -> Color(0xFF3CC8E6).copy(alpha = 0.16f)
          }
          if (tintColor != Color.Transparent) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(tintColor),
            )
          }

          selectedAspect.ratio?.let { targetRatio ->
            CameraViewfinderCropMask(targetRatio = targetRatio)
          }

          // Top Controls Bar
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .align(Alignment.TopCenter)
              .windowInsetsPadding(WindowInsets.statusBars)
              .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = CircleShape,
              color = ObsidianCanvas.copy(alpha = 0.65f),
              border = BorderStroke(1.dp, HairlineBorder),
            ) {
              IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("camera_close_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = stringResource(R.string.cd_back),
                  tint = Color.White,
                )
              }
            }

            if (selectedFilter != CameraFilter.ORIGINAL || selectedAspect != CameraCropAspect.FULL) {
              Surface(
                shape = RoundedCornerShape(50),
                color = ObsidianCanvas.copy(alpha = 0.72f),
                border = BorderStroke(
                  1.dp,
                  if (selectedAspect != CameraCropAspect.FULL) StudioCyan.copy(alpha = 0.7f)
                  else selectedFilter.badgeColor.copy(alpha = 0.7f),
                ),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = if (selectedAspect != CameraCropAspect.FULL) Icons.Rounded.Crop else Icons.Rounded.AutoFixHigh,
                    contentDescription = null,
                    tint = if (selectedAspect != CameraCropAspect.FULL) StudioCyan else selectedFilter.badgeColor,
                    modifier = Modifier.size(14.dp),
                  )
                  val badgeLabel = buildList {
                    if (selectedAspect != CameraCropAspect.FULL) add(selectedAspect.label)
                    if (selectedFilter != CameraFilter.ORIGINAL) add(selectedFilter.displayName)
                  }.joinToString(" • ")
                  Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                  )
                }
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Surface(
                shape = CircleShape,
                color = ObsidianCanvas.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, HairlineBorder),
              ) {
                IconButton(
                  onClick = {
                    flashMode = when (flashMode) {
                      ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                      ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                      else -> ImageCapture.FLASH_MODE_AUTO
                    }
                  },
                  modifier = Modifier.testTag("camera_flash_button"),
                ) {
                  val flashIcon = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                    ImageCapture.FLASH_MODE_OFF -> Icons.Rounded.FlashOff
                    else -> Icons.Rounded.FlashAuto
                  }
                  Icon(
                    imageVector = flashIcon,
                    contentDescription = stringResource(R.string.cd_flash_mode),
                    tint = if (flashMode == ImageCapture.FLASH_MODE_ON) CoralFlame else Color.White,
                  )
                }
              }

              Surface(
                shape = CircleShape,
                color = ObsidianCanvas.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, HairlineBorder),
              ) {
                IconButton(
                  onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                      CameraSelector.LENS_FACING_FRONT
                    } else {
                      CameraSelector.LENS_FACING_BACK
                    }
                  },
                  modifier = Modifier.testTag("camera_switch_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Cameraswitch,
                    contentDescription = stringResource(R.string.cd_flip_camera),
                    tint = Color.White,
                  )
                }
              }
            }
          }

          if (cameraError != null) {
            Surface(
              color = ObsidianCanvas.copy(alpha = 0.88f),
              shape = MaterialTheme.shapes.medium,
              border = BorderStroke(1.dp, StatusError.copy(alpha = 0.6f)),
              modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .widthIn(max = 360.dp),
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = StatusError)
                Text(
                  text = cameraError.orEmpty(),
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color.White,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }

          // Floating Action Button to export & share edited photo to social media platforms
          FloatingActionButton(
            onClick = {
              if (isCapturing || isSharing) return@FloatingActionButton
              if (cameraError != null) {
                if (hasEditedPhoto) {
                  isSharing = true
                  onShareEditedPhoto { ok, err ->
                    isSharing = false
                    if (!ok && !err.isNullOrBlank()) {
                      scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                  }
                } else {
                  scope.launch {
                    snackbarHostState.showSnackbar("Camera unavailable and no edited photo loaded.")
                  }
                }
                return@FloatingActionButton
              }

              isSharing = true
              val captureDir = File(context.cacheDir, "captures").apply { mkdirs() }
              val photoFile = File(captureDir, "share_capture_${System.currentTimeMillis()}.jpg")
              val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
              val chosenFilter = selectedFilter
              val chosenAspect = selectedAspect

              imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                  override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val rawUri = FileProvider.getUriForFile(
                      context,
                      "${context.packageName}.fileprovider",
                      photoFile,
                    )
                    scope.launch {
                      val shareUriResult = withContext(Dispatchers.Default) {
                        prepareCroppedFilteredShareUri(
                          context = context.applicationContext,
                          rawUri = rawUri,
                          filterManager = filterManager,
                          aspect = chosenAspect,
                          filter = chosenFilter,
                        )
                      }
                      isSharing = false
                      shareUriResult.fold(
                        onSuccess = { shareUri ->
                          ExportManager.shareToPlatform(
                            context = context,
                            uri = shareUri,
                            platform = ExportPlatform.SYSTEM_SHARE,
                            mimeType = ExportFormat.JPEG.mimeType,
                          )
                        },
                        onFailure = { err ->
                          snackbarHostState.showSnackbar(
                            err.localizedMessage ?: "Failed to prepare photo for sharing."
                          )
                        },
                      )
                    }
                  }

                  override fun onError(exception: ImageCaptureException) {
                    if (hasEditedPhoto) {
                      onShareEditedPhoto { ok, err ->
                        isSharing = false
                        if (!ok && !err.isNullOrBlank()) {
                          scope.launch { snackbarHostState.showSnackbar(err) }
                        }
                      }
                    } else {
                      isSharing = false
                      Log.e(TAG, "Share capture failed", exception)
                      scope.launch {
                        snackbarHostState.showSnackbar(
                          exception.localizedMessage ?: "Could not capture photo to share."
                        )
                      }
                    }
                  }
                },
              )
            },
            shape = CircleShape,
            containerColor = CoralFlame,
            contentColor = ObsidianCanvas,
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(16.dp)
              .testTag("camera_share_fab"),
          ) {
            if (isSharing) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = ObsidianCanvas,
                strokeWidth = 2.5.dp,
              )
            } else {
              Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = stringResource(R.string.cd_share_social),
              )
            }
          }

          SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(start = 16.dp, end = 88.dp, bottom = 16.dp),
          )
        }

        // Bottom Aspect Ratio + Filter Strip + Shutter Bar directly below camera preview
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            CameraAspectRatioSelectorRow(
              selectedAspect = selectedAspect,
              enabled = !isCapturing && !isSharing,
              onSelectAspect = { selectedAspect = it },
            )

            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("camera_filter_row"),
            ) {
              items(FilterManager.availableFilters, key = { it.id }) { filter ->
                val isSelected = selectedFilter == filter
                val filterColorFilter = remember(filter) {
                  filterManager.buildComposeColorFilter(filter)
                }
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                  modifier = Modifier
                    .width(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isCapturing) { selectedFilter = filter }
                    .padding(vertical = 4.dp)
                    .testTag("camera_filter_${filter.id}"),
                ) {
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(
                      width = if (isSelected) 2.dp else 1.dp,
                      color = if (isSelected) CoralFlame else HairlineBorder,
                    ),
                    modifier = Modifier.size(52.dp),
                  ) {
                    if (previewChipThumb != null) {
                      Image(
                        bitmap = previewChipThumb.asImageBitmap(),
                        contentDescription = filter.displayName,
                        contentScale = ContentScale.Crop,
                        colorFilter = filterColorFilter,
                        modifier = Modifier.fillMaxSize(),
                      )
                    } else {
                      Box(
                        modifier = Modifier
                          .fillMaxSize()
                          .background(filter.badgeColor.copy(alpha = 0.25f)),
                      )
                    }
                  }
                  Text(
                    text = filter.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) CoralFlame else Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }

            // Shutter Button
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .border(3.5.dp, Color.White, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(if (isCapturing) CoralFlame else Color.White)
                .clickable(enabled = !isCapturing && !isSharing && cameraError == null) {
                  isCapturing = true
                  val captureDir = File(context.cacheDir, "captures").apply { mkdirs() }
                  val photoFile = File(captureDir, "capture_${System.currentTimeMillis()}.jpg")
                  val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                  imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                      override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val rawUri = FileProvider.getUriForFile(
                          context,
                          "${context.packageName}.fileprovider",
                          photoFile,
                        )
                        scope.launch {
                          val decoded = withContext(Dispatchers.IO) {
                            PhotoEngine.loadBitmapForSource(
                              context = context.applicationContext,
                              source = PhotoSource.Gallery(rawUri.toString(), "Camera"),
                              maxEdge = 2400,
                            )
                          }
                          isCapturing = false
                          if (decoded != null) {
                            capturedRawBitmap = decoded
                          } else {
                            onPhotoCaptured(rawUri, selectedAspect)
                          }
                        }
                      }

                      override fun onError(exception: ImageCaptureException) {
                        isCapturing = false
                        Log.e(TAG, "Photo capture failed", exception)
                        cameraError = exception.localizedMessage ?: "Capture failed"
                      }
                    },
                  )
                }
                .testTag("camera_shutter_button"),
            ) {
              if (isCapturing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(28.dp),
                  color = ObsidianCanvas,
                  strokeWidth = 2.5.dp,
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun saveProcessedBitmapToCache(
  context: Context,
  rawBitmap: Bitmap,
  filterManager: FilterManager,
  aspect: CameraCropAspect,
  filter: CameraFilter,
  brightness: Float = 0f,
  contrast: Float = 0f,
): Uri? {
  return try {
    val cropped = filterManager.cropToAspectRatio(rawBitmap, aspect)
    val transformed = filterManager.applyFilter(cropped, filter, brightness, contrast)
    val captureDir = File(context.cacheDir, "captures").apply { mkdirs() }
    val outFile = File(captureDir, "capture_${aspect.id}_${filter.id}_${System.currentTimeMillis()}.jpg")
    FileOutputStream(outFile).use { out ->
      transformed.compress(Bitmap.CompressFormat.JPEG, 94, out)
    }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
  } catch (e: Exception) {
    Log.e(TAG, "Failed to save processed photo", e)
    null
  }
}

private fun prepareCroppedFilteredShareUri(
  context: Context,
  rawUri: Uri,
  filterManager: FilterManager,
  aspect: CameraCropAspect,
  filter: CameraFilter,
): Result<Uri> {
  return try {
    val decoded = PhotoEngine.loadBitmapForSource(
      context = context,
      source = PhotoSource.Gallery(rawUri.toString(), "Camera"),
      maxEdge = 2400,
    ) ?: return Result.failure(IllegalStateException("Unable to decode captured photo."))
    val cropped = filterManager.cropToAspectRatio(decoded, aspect)
    val transformed = filterManager.applyFilter(cropped, filter)
    ExportManager.prepareShareUri(
      context = context,
      bitmap = transformed,
      format = ExportFormat.JPEG,
      quality = 92,
      titlePrefix = "Lumina_${aspect.id}_${filter.id}",
    )
  } catch (e: Exception) {
    Result.failure(e)
  }
}

@Composable
private fun CameraPermissionGate(
  onRequestPermission: () -> Unit,
  onClose: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.statusBars)
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(24.dp),
  ) {
    IconButton(
      onClick = onClose,
      modifier = Modifier
        .align(Alignment.TopStart)
        .testTag("camera_permission_close_button"),
    ) {
      Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
    }

    Surface(
      color = StudioSurfaceElevated,
      shape = MaterialTheme.shapes.large,
      border = BorderStroke(1.dp, HairlineBorder),
      modifier = Modifier
        .align(Alignment.Center)
        .widthIn(max = 420.dp),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = CoralFlame.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, CoralFlame.copy(alpha = 0.45f)),
        ) {
          Icon(
            imageVector = Icons.Rounded.CameraAlt,
            contentDescription = null,
            tint = CoralFlame,
            modifier = Modifier
              .padding(16.dp)
              .size(32.dp),
          )
        }
        Text(
          text = stringResource(R.string.camera_permission_title),
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
        )
        Text(
          text = stringResource(R.string.camera_permission_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
          onClick = onRequestPermission,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = CoralFlame,
            contentColor = ObsidianCanvas,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("grant_camera_permission_button"),
        ) {
          Text(
            text = stringResource(R.string.action_grant_camera),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }
}
