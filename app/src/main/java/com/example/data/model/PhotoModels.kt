package com.example.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SamplePhoto(
  val key: String,
  val title: String,
  val subtitle: String,
  @DrawableRes val drawableRes: Int,
) {
  PORTRAIT_CAFE(
    key = "sample_portrait",
    title = "Golden Hour Cafe",
    subtitle = "3:4 Portrait",
    drawableRes = R.drawable.img_sample_portrait,
  ),
  COASTAL_VILLA(
    key = "sample_landscape",
    title = "Coastal Sunset Villa",
    subtitle = "4:3 Architecture",
    drawableRes = R.drawable.img_sample_landscape,
  ),
  ROOFTOP_DUSK(
    key = "sample_hero",
    title = "Neon Dusk Skyline",
    subtitle = "16:9 Cinema",
    drawableRes = R.drawable.img_studio_hero,
  );

  companion object {
    fun fromKey(key: String?): SamplePhoto =
      entries.firstOrNull { it.key == key } ?: PORTRAIT_CAFE
  }
}

sealed interface PhotoSource {
  val title: String

  data class Sample(val sample: SamplePhoto) : PhotoSource {
    override val title: String get() = sample.title
  }

  data class Gallery(val uriString: String, override val title: String = "Gallery Photo") : PhotoSource
}

enum class AspectPreset(
  val key: String,
  val label: String,
  val ratio: Float?,
  val socialHint: String,
) {
  FREE("FREE", "Free", null, "Custom"),
  SQUARE("1:1", "1:1", 1f, "IG Post"),
  PORTRAIT_4_5("4:5", "4:5", 4f / 5f, "IG Feed"),
  STORY_9_16("9:16", "9:16", 9f / 16f, "Reels / TikTok"),
  WIDE_16_9("16:9", "16:9", 16f / 9f, "YouTube / X"),
  CLASSIC_3_4("3:4", "3:4", 3f / 4f, "Portrait"),
  CLASSIC_4_3("4:3", "4:3", 4f / 3f, "Editorial");

  companion object {
    fun fromKey(key: String?): AspectPreset =
      entries.firstOrNull { it.key == key } ?: FREE
  }
}

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
  JPEG("JPEG", "jpg", "image/jpeg"),
  PNG("PNG", "png", "image/png"),
  WEBP("WEBP", "webp", "image/webp"),
}

enum class ExportPlatform(
  val label: String,
  val recommendedAspect: AspectPreset,
  val packageCandidates: List<String>,
  val accentColor: Color,
) {
  INSTAGRAM(
    label = "Instagram",
    recommendedAspect = AspectPreset.PORTRAIT_4_5,
    packageCandidates = listOf("com.instagram.android"),
    accentColor = Color(0xFFE1306C),
  ),
  TIKTOK(
    label = "TikTok",
    recommendedAspect = AspectPreset.STORY_9_16,
    packageCandidates = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
    accentColor = Color(0xFF25F4EE),
  ),
  X_TWITTER(
    label = "X / Twitter",
    recommendedAspect = AspectPreset.WIDE_16_9,
    packageCandidates = listOf("com.twitter.android"),
    accentColor = Color(0xFFE7E9EA),
  ),
  WHATSAPP(
    label = "WhatsApp",
    recommendedAspect = AspectPreset.SQUARE,
    packageCandidates = listOf("com.whatsapp"),
    accentColor = Color(0xFF25D366),
  ),
  SNAPCHAT(
    label = "Snapchat",
    recommendedAspect = AspectPreset.STORY_9_16,
    packageCandidates = listOf("com.snapchat.android"),
    accentColor = Color(0xFFFFFC00),
  ),
  SYSTEM_SHARE(
    label = "More Apps",
    recommendedAspect = AspectPreset.FREE,
    packageCandidates = emptyList(),
    accentColor = Color(0xFF5CE1E6),
  ),
}

enum class EditorTab(val label: String) {
  FILTERS("Filters"),
  ADJUST("Adjust"),
  CROP("Crop"),
  PRESETS("Presets"),
}

enum class AdjustParam(
  val label: String,
  val min: Float,
  val max: Float,
  val defaultValue: Float,
) {
  EXPOSURE("Brightness", -1f, 1f, 0f),
  CONTRAST("Contrast", -1f, 1f, 0f),
  SATURATION("Saturation", -1f, 1f, 0f),
  WARMTH("Warmth", -1f, 1f, 0f),
  TINT("Tint", -1f, 1f, 0f),
  FADE("Fade", 0f, 1f, 0f),
  VIGNETTE("Vignette", 0f, 1f, 0f),
}

data class EditRecipe(
  val filterId: String = "original",
  val filterIntensity: Float = 0.85f,
  val exposure: Float = 0f,
  val contrast: Float = 0f,
  val saturation: Float = 0f,
  val warmth: Float = 0f,
  val tint: Float = 0f,
  val fade: Float = 0f,
  val vignette: Float = 0f,
  val aspectRatio: AspectPreset = AspectPreset.FREE,
  val cropLeft: Float = 0f,
  val cropTop: Float = 0f,
  val cropRight: Float = 1f,
  val cropBottom: Float = 1f,
  val rotationDegrees: Int = 0,
  val straightenDegrees: Float = 0f,
  val flipHorizontal: Boolean = false,
  val flipVertical: Boolean = false,
) {
  fun adjustValue(param: AdjustParam): Float = when (param) {
    AdjustParam.EXPOSURE -> exposure
    AdjustParam.CONTRAST -> contrast
    AdjustParam.SATURATION -> saturation
    AdjustParam.WARMTH -> warmth
    AdjustParam.TINT -> tint
    AdjustParam.FADE -> fade
    AdjustParam.VIGNETTE -> vignette
  }

  fun withAdjustValue(param: AdjustParam, value: Float): EditRecipe = when (param) {
    AdjustParam.EXPOSURE -> copy(exposure = value.coerceIn(-1f, 1f))
    AdjustParam.CONTRAST -> copy(contrast = value.coerceIn(-1f, 1f))
    AdjustParam.SATURATION -> copy(saturation = value.coerceIn(-1f, 1f))
    AdjustParam.WARMTH -> copy(warmth = value.coerceIn(-1f, 1f))
    AdjustParam.TINT -> copy(tint = value.coerceIn(-1f, 1f))
    AdjustParam.FADE -> copy(fade = value.coerceIn(0f, 1f))
    AdjustParam.VIGNETTE -> copy(vignette = value.coerceIn(0f, 1f))
  }

  val hasGeometryChanges: Boolean
    get() = cropLeft > 0.005f || cropTop > 0.005f || cropRight < 0.995f || cropBottom < 0.995f ||
      rotationDegrees != 0 || straightenDegrees != 0f || flipHorizontal || flipVertical
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val email: String = "",
  val photoUrl: String? = null,
  val autoSyncEnabled: Boolean = true,
  val totalBackups: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CloudPhotoProject(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val sourceType: String = "sample",
  val sampleKey: String? = null,
  val localUri: String? = null,
  val thumbnailBase64: String? = null,
  val storagePath: String? = null,
  val storageDownloadUrl: String? = null,
  val syncedPhotoSizeBytes: Long = 0L,
  val filterId: String = "original",
  val filterIntensity: Double = 0.85,
  val exposure: Double = 0.0,
  val contrast: Double = 0.0,
  val saturation: Double = 0.0,
  val warmth: Double = 0.0,
  val tint: Double = 0.0,
  val fade: Double = 0.0,
  val vignette: Double = 0.0,
  val aspectRatio: String = "FREE",
  val cropLeft: Double = 0.0,
  val cropTop: Double = 0.0,
  val cropRight: Double = 1.0,
  val cropBottom: Double = 1.0,
  val rotationDegrees: Long = 0L,
  val straightenDegrees: Double = 0.0,
  val flipHorizontal: Boolean = false,
  val flipVertical: Boolean = false,
  val lastExportPlatform: String? = null,
  val exportedCount: Long = 0L,
  val deviceName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toEditRecipe(): EditRecipe = EditRecipe(
    filterId = filterId.ifBlank { "original" },
    filterIntensity = filterIntensity.toFloat().coerceIn(0f, 1f),
    exposure = exposure.toFloat().coerceIn(-1f, 1f),
    contrast = contrast.toFloat().coerceIn(-1f, 1f),
    saturation = saturation.toFloat().coerceIn(-1f, 1f),
    warmth = warmth.toFloat().coerceIn(-1f, 1f),
    tint = tint.toFloat().coerceIn(-1f, 1f),
    fade = fade.toFloat().coerceIn(0f, 1f),
    vignette = vignette.toFloat().coerceIn(0f, 1f),
    aspectRatio = AspectPreset.fromKey(aspectRatio),
    cropLeft = cropLeft.toFloat().coerceIn(0f, 0.95f),
    cropTop = cropTop.toFloat().coerceIn(0f, 0.95f),
    cropRight = cropRight.toFloat().coerceIn(0.05f, 1f),
    cropBottom = cropBottom.toFloat().coerceIn(0.05f, 1f),
    rotationDegrees = rotationDegrees.toInt().let { if (it in listOf(0, 90, 180, 270)) it else 0 },
    straightenDegrees = straightenDegrees.toFloat().coerceIn(-45f, 45f),
    flipHorizontal = flipHorizontal,
    flipVertical = flipVertical,
  )
}

@Keep
@IgnoreExtraProperties
data class CloudPreset(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val filterId: String = "original",
  val filterIntensity: Double = 0.85,
  val exposure: Double = 0.0,
  val contrast: Double = 0.0,
  val saturation: Double = 0.0,
  val warmth: Double = 0.0,
  val tint: Double = 0.0,
  val fade: Double = 0.0,
  val vignette: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun applyTo(existing: EditRecipe): EditRecipe = existing.copy(
    filterId = filterId.ifBlank { "original" },
    filterIntensity = filterIntensity.toFloat().coerceIn(0f, 1f),
    exposure = exposure.toFloat().coerceIn(-1f, 1f),
    contrast = contrast.toFloat().coerceIn(-1f, 1f),
    saturation = saturation.toFloat().coerceIn(-1f, 1f),
    warmth = warmth.toFloat().coerceIn(-1f, 1f),
    tint = tint.toFloat().coerceIn(-1f, 1f),
    fade = fade.toFloat().coerceIn(0f, 1f),
    vignette = vignette.toFloat().coerceIn(0f, 1f),
  )
}

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}
