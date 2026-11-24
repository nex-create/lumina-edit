package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.auth.attemptAutoSignIn
import com.example.data.auth.onGoogleSignInClicked
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StatusError
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.WarmAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var isCheckingAutoSignIn by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {
        isCheckingAutoSignIn = false
        onAuthSuccess()
      },
      onUnauthenticated = {
        isCheckingAutoSignIn = false
      },
      scope = scope,
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianCanvas),
  ) {
    // Top editorial hero photography
    Image(
      painter = painterResource(id = R.drawable.img_studio_hero),
      contentDescription = stringResource(R.string.cd_hero_banner),
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxSize(0.58f),
    )

    // Smooth cinematic gradient scrim into ObsidianCanvas
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              0.0f to ObsidianCanvas.copy(alpha = 0.28f),
              0.38f to ObsidianCanvas.copy(alpha = 0.65f),
              0.56f to ObsidianCanvas,
              1.0f to ObsidianCanvas,
            )
          )
        ),
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(horizontal = 24.dp, vertical = 20.dp)
        .widthIn(max = 540.dp)
        .align(Alignment.Center),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // Top studio badge
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = CoralFlame.copy(alpha = 0.18f),
          border = BorderStroke(1.dp, CoralFlame.copy(alpha = 0.45f)),
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = CoralFlame,
            modifier = Modifier
              .padding(8.dp)
              .size(18.dp),
          )
        }
        Column {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = stringResource(R.string.studio_tagline),
            style = MaterialTheme.typography.labelSmall,
            color = StudioCyan,
          )
        }
      }

      // Bottom editorial onboarding + Google Sign-In CTA
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Text(
          text = stringResource(R.string.sign_in_headline),
          style = MaterialTheme.typography.displaySmall,
          color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
          text = stringResource(R.string.sign_in_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FeaturePill(Icons.Rounded.FilterVintage, "9+ Film Filters", CoralFlame)
          FeaturePill(Icons.Rounded.Crop, "Social Aspect Crop", StudioCyan)
          FeaturePill(Icons.Rounded.CloudSync, "Cloud Vault Sync", WarmAmber)
        }

        if (errorMessage != null) {
          Surface(
            color = StatusError.copy(alpha = 0.14f),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.45f)),
          ) {
            Text(
              text = errorMessage.orEmpty(),
              style = MaterialTheme.typography.bodyMedium,
              color = StatusError,
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            )
          }
        }

        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = {
                isLoading = false
              },
            )
          },
          enabled = !isLoading && !isCheckingAutoSignIn,
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = CoralFlame,
            contentColor = ObsidianCanvas,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("google_sign_in_button"),
        ) {
          if (isLoading || isCheckingAutoSignIn) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = ObsidianCanvas,
              strokeWidth = 2.5.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = if (isCheckingAutoSignIn) "Checking session..." else "Signing in...",
              style = MaterialTheme.typography.labelLarge,
            )
          } else {
            Text(
              text = stringResource(R.string.action_sign_in_google),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FeaturePill(
  icon: ImageVector,
  label: String,
  accent: Color,
) {
  Surface(
    color = StudioSurfaceElevated.copy(alpha = 0.9f),
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, HairlineBorder),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .clip(RoundedCornerShape(50))
        .padding(horizontal = 12.dp, vertical = 7.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(15.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
