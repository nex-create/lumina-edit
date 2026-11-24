package com.example

import com.example.data.model.AspectPreset
import com.example.data.model.EditRecipe
import com.example.editor.CameraCropAspect
import com.example.editor.CameraFilter
import com.example.editor.EditHistoryManager
import com.example.editor.FilterManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun filterManager_exposesCoreCameraFilters() {
    val ids = FilterManager.availableFilters.map { it.id }
    assertTrue(ids.contains(CameraFilter.ORIGINAL.id))
    assertTrue(ids.contains(CameraFilter.GRAYSCALE.id))
    assertTrue(ids.contains(CameraFilter.SEPIA.id))
    assertTrue(ids.contains(CameraFilter.HIGH_CONTRAST.id))
    assertEquals(6, FilterManager.availableFilters.size)
  }

  @Test
  fun cameraCropAspect_mapsPresetsAndWeights() {
    assertNull(CameraCropAspect.FULL.ratio)
    assertEquals(AspectPreset.FREE, CameraCropAspect.FULL.editorPreset)

    assertEquals(1f, CameraCropAspect.SQUARE_1_1.ratio!!, 0.001f)
    assertEquals(AspectPreset.SQUARE, CameraCropAspect.SQUARE_1_1.editorPreset)

    assertEquals(4f / 3f, CameraCropAspect.CLASSIC_4_3.ratio!!, 0.001f)
    assertEquals(AspectPreset.CLASSIC_4_3, CameraCropAspect.CLASSIC_4_3.editorPreset)

    assertEquals(16f / 9f, CameraCropAspect.WIDE_16_9.ratio!!, 0.001f)
    assertEquals(AspectPreset.WIDE_16_9, CameraCropAspect.WIDE_16_9.editorPreset)
  }

  @Test
  fun computeCenteredCropRect_cropsSquareFromLandscape() {
    val rect = FilterManager.computeCenteredCropRect(width = 1600, height = 1200, targetRatio = 1f)
    assertEquals(200, rect[0]) // left
    assertEquals(0, rect[1])   // top
    assertEquals(1200, rect[2]) // width
    assertEquals(1200, rect[3]) // height
  }

  @Test
  fun computeCenteredCropRect_crops16by9From4by3() {
    val rect = FilterManager.computeCenteredCropRect(
      width = 1600,
      height = 1200,
      targetRatio = 16f / 9f,
    )
    assertEquals(0, rect[0])
    assertEquals(150, rect[1])
    assertEquals(1600, rect[2])
    assertEquals(900, rect[3])
  }

  @Test
  fun computeCenteredCropRect_crops4by3FromTallPortrait() {
    val rect = FilterManager.computeCenteredCropRect(
      width = 1200,
      height = 1600,
      targetRatio = 4f / 3f,
    )
    assertEquals(0, rect[0])
    assertEquals(350, rect[1])
    assertEquals(1200, rect[2])
    assertEquals(900, rect[3])
  }

  @Test
  fun cloudPhotoProject_retainsStorageFieldsAndMapsRecipe() {
    val project = com.example.data.model.CloudPhotoProject(
      id = "proj_sync_1",
      userId = "user_1",
      title = "Studio Sunset",
      sourceType = "gallery",
      storagePath = "users/user_1/edited_photos/proj_sync_1.jpg",
      storageDownloadUrl = "https://firebasestorage.googleapis.com/example.jpg",
      syncedPhotoSizeBytes = 245_000L,
      filterId = "creamy",
      aspectRatio = "1:1",
    )
    val recipe = project.toEditRecipe()
    assertEquals("creamy", recipe.filterId)
    assertEquals(AspectPreset.SQUARE, recipe.aspectRatio)
    assertEquals("users/user_1/edited_photos/proj_sync_1.jpg", project.storagePath)
    assertEquals(245_000L, project.syncedPhotoSizeBytes)
  }

  @Test
  fun editHistoryManager_undoAndRedoFilterAndCropPresets() {
    val initial = EditRecipe()
    val history = EditHistoryManager(initial)

    assertFalse(history.canUndo)
    assertFalse(history.canRedo)

    val filtered = initial.copy(filterId = "kodak_gold")
    assertTrue(history.commit(filtered, "Filter: Kodak Gold"))
    val cropped = filtered.copy(aspectRatio = AspectPreset.SQUARE, cropLeft = 0.1f, cropRight = 0.9f)
    assertTrue(history.commit(cropped, "Crop 1:1"))

    assertEquals(2, history.undoCount)
    assertEquals("Crop 1:1", history.nextUndoLabel)

    val undoCrop = history.undo()!!
    assertEquals("Crop 1:1", undoCrop.label)
    assertEquals(filtered, undoCrop.newState)
    assertTrue(history.canRedo)
    assertEquals("Crop 1:1", history.nextRedoLabel)

    val undoFilter = history.undo()!!
    assertEquals("Filter: Kodak Gold", undoFilter.label)
    assertEquals(initial, undoFilter.newState)
    assertFalse(history.canUndo)
    assertEquals(2, history.redoCount)

    val redoFilter = history.redo()!!
    assertEquals(filtered, redoFilter.newState)
    val redoCrop = history.redo()!!
    assertEquals(cropped, redoCrop.newState)
    assertFalse(history.canRedo)
  }

  @Test
  fun editHistoryManager_continuousCropGestureCommitsSingleAtomicStep() {
    val initial = EditRecipe()
    val history = EditHistoryManager(initial)

    // Simulate multi-frame crop handle drag
    history.beginGesture()
    history.updateGesturePreview(initial.copy(cropLeft = 0.05f, cropTop = 0.05f))
    history.updateGesturePreview(initial.copy(cropLeft = 0.12f, cropTop = 0.10f))
    val finalCrop = initial.copy(cropLeft = 0.20f, cropTop = 0.15f, cropRight = 0.85f, cropBottom = 0.88f)
    history.updateGesturePreview(finalCrop)

    assertTrue(history.finishGesture("Crop Frame"))
    assertEquals(1, history.undoCount)
    assertEquals(finalCrop, history.current)

    val undone = history.undo()!!
    assertEquals("Crop Frame", undone.label)
    assertEquals(initial, undone.newState)

    val redone = history.redo()!!
    assertEquals(finalCrop, redone.newState)
  }

  @Test
  fun editHistoryManager_clearsRedoOnBranchEdit_ignoresNoOps_andCapsMaxSteps() {
    val history = EditHistoryManager(EditRecipe(), maxSteps = 3)

    // No-op commit is ignored
    assertFalse(history.commit(EditRecipe(), "No-op"))

    // 4 commits with maxSteps = 3 drops oldest
    history.commit(EditRecipe(filterId = "creamy"), "Step 1")
    history.commit(EditRecipe(filterId = "peach_glow"), "Step 2")
    history.commit(EditRecipe(filterId = "fuji_400h"), "Step 3")
    history.commit(EditRecipe(filterId = "kodak_gold"), "Step 4")

    assertEquals(3, history.undoCount)
    history.undo()
    assertTrue(history.canRedo)

    // Branching with a new edit clears redo stack
    assertTrue(history.commit(EditRecipe(filterId = "noir_35"), "Filter: Noir 35mm"))
    assertFalse(history.canRedo)
    assertEquals(0, history.redoCount)
  }

  @Test
  fun filterManager_computeBrightnessContrastValues_appliesExpectedToneAndIdentity() {
    val identity = FilterManager.computeBrightnessContrastValues(brightness = 0f, contrast = 0f)
    assertEquals(1f, identity[0], 0.001f)
    assertEquals(0f, identity[4], 0.001f)
    assertEquals(1f, identity[6], 0.001f)

    val brighter = FilterManager.computeBrightnessContrastValues(brightness = 0.5f, contrast = 0f)
    assertTrue(brighter[4] > 20f)
    assertTrue(brighter[0] > 1.0f)

    val darker = FilterManager.computeBrightnessContrastValues(brightness = -0.5f, contrast = 0f)
    assertTrue(darker[4] < -20f)
    assertTrue(darker[0] < 1.0f)

    val higherContrast = FilterManager.computeBrightnessContrastValues(brightness = 0f, contrast = 0.5f)
    assertTrue(higherContrast[0] > 1.25f)
    assertTrue(higherContrast[4] < -20f)

    val recipe = EditRecipe()
      .withAdjustValue(com.example.data.model.AdjustParam.EXPOSURE, 0.4f)
      .withAdjustValue(com.example.data.model.AdjustParam.CONTRAST, -0.3f)
    assertEquals(0.4f, recipe.exposure, 0.001f)
    assertEquals(-0.3f, recipe.contrast, 0.001f)
    assertEquals("Brightness", com.example.data.model.AdjustParam.EXPOSURE.label)
  }

  @Test
  fun histogramAnalyzer_updatesDistributionWhenBrightnessAndContrastChange() {
    // Full-range pixel gradient (RGB values spanning 20..236)
    val pixels = IntArray(64) { i ->
      val v = (20 + (i * 216) / 63).coerceIn(0, 255)
      (0xFF shl 24) or (v shl 16) or ((v - 8).coerceAtLeast(0) shl 8) or (v + 8).coerceAtMost(255)
    }

    val baseline = com.example.editor.HistogramAnalyzer.computeHistogram(
      pixels = pixels,
      colorMatrixValues = com.example.editor.HistogramAnalyzer.identityMatrixValues(),
    )
    val brightened = com.example.editor.HistogramAnalyzer.computeHistogram(
      pixels = pixels,
      colorMatrixValues = FilterManager.computeBrightnessContrastValues(brightness = 0.7f, contrast = 0f),
    )
    val darkened = com.example.editor.HistogramAnalyzer.computeHistogram(
      pixels = pixels,
      colorMatrixValues = FilterManager.computeBrightnessContrastValues(brightness = -0.7f, contrast = 0f),
    )
    val highContrast = com.example.editor.HistogramAnalyzer.computeHistogram(
      pixels = pixels,
      colorMatrixValues = FilterManager.computeBrightnessContrastValues(brightness = 0f, contrast = 0.9f),
    )

    assertTrue(brightened.meanLuminance > baseline.meanLuminance + 0.15f)
    assertTrue(darkened.meanLuminance < baseline.meanLuminance - 0.15f)
    assertTrue(brightened.highlightClippingRatio > baseline.highlightClippingRatio)
    assertTrue(
      highContrast.shadowClippingRatio + highContrast.highlightClippingRatio >
        baseline.shadowClippingRatio + baseline.highlightClippingRatio
    )
    assertEquals(com.example.editor.HistogramAnalyzer.DEFAULT_BIN_COUNT, baseline.lumaBins.size)
  }
}
