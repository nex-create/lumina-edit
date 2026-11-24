package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.configureAppCheck
import com.example.data.auth.signOutUser
import com.example.data.firebase.CloudSyncRepository
import com.example.data.model.UiState
import com.example.ui.AiLabMode
import com.example.ui.MainDestination
import com.example.ui.StudioViewModel
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CameraCaptureScreen
import com.example.ui.screens.CloudVaultScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.PresetsScreen
import com.example.ui.screens.StudioHomeScreen
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.LuminaEditTheme
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StudioSurface
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    setContent {
      LuminaEditTheme {
        AppContent()
      }
    }
  }
}

@Composable
fun AppContent() {
  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth ->
      currentUser = auth.currentUser
    }
    Firebase.auth.addAuthStateListener(listener)
    onDispose {
      Firebase.auth.removeAuthStateListener(listener)
    }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(
      onAuthSuccess = {
        currentUser = Firebase.auth.currentUser
      }
    )
  } else {
    key(user.uid) {
      AuthenticatedStudioApp(currentUser = user)
    }
  }
}

@Composable
private fun AuthenticatedStudioApp(currentUser: FirebaseUser) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: StudioViewModel = viewModel(
    key = currentUser.uid,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        StudioViewModel(
          repository = CloudSyncRepository(db),
          currentUserId = currentUser.uid,
        )
      }
    },
  )

    LaunchedEffect(currentUser.uid) {
    viewModel.ensureProfileSynced(context, currentUser)
  }

  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val editorState by viewModel.editorState.collectAsStateWithLifecycle()
  val aiLabState by viewModel.aiLabState.collectAsStateWithLifecycle()
  val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
  val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
  val presetsState by viewModel.presetsState.collectAsStateWithLifecycle()
  val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
  val vaultStatusMessage by viewModel.vaultStatusMessage.collectAsStateWithLifecycle()

  val cloudPresets = (presetsState as? UiState.Success)?.data.orEmpty()

  if (editorState.isCameraOpen) {
    CameraCaptureScreen(
      hasEditedPhoto = editorState.sourceBitmap != null,
      onShareEditedPhoto = { onResult -> viewModel.shareCurrentEditedPhoto(context, onResult) },
      onPhotoCaptured = { uri, aspect ->
        viewModel.onCameraPhotoCaptured(context, uri, aspect.editorPreset)
      },
      onClose = viewModel::closeCamera,
    )
  } else if (editorState.isEditorOpen) {
    EditorScreen(
      editorState = editorState,
      cloudPresets = cloudPresets,
      syncStatus = syncStatus,
      viewModel = viewModel,
      onClose = viewModel::closeEditor,
    )
  } else {
    Scaffold(
      containerColor = ObsidianCanvas,
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        NavigationBar(
          containerColor = StudioSurface,
        ) {
          NavigationBarItem(
            selected = currentTab == MainDestination.STUDIO_HOME,
            onClick = { viewModel.selectDestination(MainDestination.STUDIO_HOME) },
            icon = {
              Icon(
                imageVector = if (currentTab == MainDestination.STUDIO_HOME) Icons.Rounded.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                contentDescription = stringResource(R.string.nav_studio),
              )
            },
            label = { Text(stringResource(R.string.nav_studio)) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = CoralFlame,
              selectedTextColor = CoralFlame,
              indicatorColor = CoralFlame.copy(alpha = 0.16f),
            ),
            modifier = Modifier.testTag("nav_studio"),
          )
          NavigationBarItem(
            selected = currentTab == MainDestination.AI_LAB,
            onClick = { viewModel.selectDestination(MainDestination.AI_LAB) },
            icon = {
              Icon(
                imageVector = if (currentTab == MainDestination.AI_LAB) Icons.Rounded.AutoAwesome else Icons.Outlined.AutoAwesome,
                contentDescription = stringResource(R.string.nav_ai_lab),
              )
            },
            label = { Text(stringResource(R.string.nav_ai_lab)) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = CoralFlame,
              selectedTextColor = CoralFlame,
              indicatorColor = CoralFlame.copy(alpha = 0.16f),
            ),
            modifier = Modifier.testTag("nav_ai_lab"),
          )
          NavigationBarItem(
            selected = currentTab == MainDestination.CLOUD_VAULT,
            onClick = { viewModel.selectDestination(MainDestination.CLOUD_VAULT) },
            icon = {
              Icon(
                imageVector = if (currentTab == MainDestination.CLOUD_VAULT) Icons.Rounded.CloudDone else Icons.Outlined.CloudQueue,
                contentDescription = stringResource(R.string.nav_vault),
              )
            },
            label = { Text(stringResource(R.string.nav_vault)) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = CoralFlame,
              selectedTextColor = CoralFlame,
              indicatorColor = CoralFlame.copy(alpha = 0.16f),
            ),
            modifier = Modifier.testTag("nav_vault"),
          )
          NavigationBarItem(
            selected = currentTab == MainDestination.PRESETS,
            onClick = { viewModel.selectDestination(MainDestination.PRESETS) },
            icon = {
              Icon(
                imageVector = if (currentTab == MainDestination.PRESETS) Icons.Rounded.Tune else Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.nav_presets),
              )
            },
            label = { Text(stringResource(R.string.nav_presets)) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = CoralFlame,
              selectedTextColor = CoralFlame,
              indicatorColor = CoralFlame.copy(alpha = 0.16f),
            ),
            modifier = Modifier.testTag("nav_presets"),
          )
        }
      },
    ) { innerPadding ->
      when (currentTab) {
        MainDestination.STUDIO_HOME -> StudioHomeScreen(
          currentUser = currentUser,
          projectsState = projectsState,
          onPickGalleryPhoto = { uri -> viewModel.openGalleryUriInEditor(context, uri) },
          onOpenCamera = viewModel::openCamera,
          onOpenAiLab = { mode -> viewModel.openAiLab(mode) },
          onOpenSample = { sample, recipe ->
            viewModel.openSampleInEditor(context, sample, initialRecipe = recipe)
          },
          onOpenCloudProject = { proj -> viewModel.openCloudProjectInEditor(context, proj) },
          onNavigateToVault = { viewModel.selectDestination(MainDestination.CLOUD_VAULT) },
          onSignOut = {
            signOutUser(credentialManager = credentialManager, scope = scope)
          },
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        )

        MainDestination.AI_LAB -> AiStudioScreen(
          aiState = aiLabState,
          onSelectMode = viewModel::selectAiLabMode,
          onPickGalleryPhoto = { uri -> viewModel.setAiInputFromGallery(context, uri) },
          onSelectSample = { sample -> viewModel.setAiInputFromSample(context, sample) },
          onGenerateImage = viewModel::generateAiImage,
          onEditImage = viewModel::editAiImage,
          onAnimateVideo = { prompt, aspect -> viewModel.animatePhotoToVideo(context, prompt, aspect) },
          onOpenBitmapInEditor = { bmp, title -> viewModel.openBitmapInEditor(context, bmp, title) },
          onSaveBitmapToGallery = { bmp, title -> viewModel.saveAiBitmapToGallery(context, bmp, title) },
          onSaveVideoToGallery = { uri, title -> viewModel.saveAiVideoToGallery(context, uri, title) },
          onUseBitmapAsInput = viewModel::useBitmapAsAiInput,
          onClearStatus = viewModel::clearAiStatusMessage,
          onBackToStudio = { viewModel.selectDestination(MainDestination.STUDIO_HOME) },
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        )

        MainDestination.CLOUD_VAULT -> CloudVaultScreen(
          profileState = profileState,
          projectsState = projectsState,
          syncStatus = syncStatus,
          vaultStatusMessage = vaultStatusMessage,
          onClearVaultStatus = viewModel::clearVaultStatusMessage,
          onToggleAutoSync = viewModel::toggleAutoSync,
          onSyncNow = { viewModel.syncAllPendingAndGallery(context) },
          onSaveToDeviceGallery = { proj -> viewModel.saveCloudProjectToDeviceGallery(context, proj) },
          onRestoreProject = { proj -> viewModel.openCloudProjectInEditor(context, proj) },
          onDeleteProject = viewModel::deleteProject,
          onBackToStudio = { viewModel.selectDestination(MainDestination.STUDIO_HOME) },
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        )

        MainDestination.PRESETS -> PresetsScreen(
          presetsState = presetsState,
          onApplyPresetToSample = { sample, recipe ->
            viewModel.openSampleInEditor(context, sample, initialRecipe = recipe)
          },
          onDeletePreset = viewModel::deletePreset,
          onBackToStudio = { viewModel.selectDestination(MainDestination.STUDIO_HOME) },
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        )
      }
    }
  }
}
