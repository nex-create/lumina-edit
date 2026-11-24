package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AspectPreset
import com.example.ui.theme.CoralFlame
import kotlin.math.abs
import kotlin.math.hypot

private enum class DragMode {
  NONE,
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  MOVE,
}

@Composable
fun CropOverlayCanvas(
  cropLeft: Float,
  cropTop: Float,
  cropRight: Float,
  cropBottom: Float,
  aspectPreset: AspectPreset,
  imageAspectRatio: Float,
  onCropChange: (Float, Float, Float, Float) -> Unit,
  onDragStarted: () -> Unit = {},
  onDragFinished: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val latestLeft by rememberUpdatedState(cropLeft)
  val latestTop by rememberUpdatedState(cropTop)
  val latestRight by rememberUpdatedState(cropRight)
  val latestBottom by rememberUpdatedState(cropBottom)
  val latestPreset by rememberUpdatedState(aspectPreset)
  val latestImgRatio by rememberUpdatedState(imageAspectRatio)
  val latestOnCrop by rememberUpdatedState(onCropChange)
  val latestOnStart by rememberUpdatedState(onDragStarted)
  val latestOnFinish by rememberUpdatedState(onDragFinished)

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .testTag("crop_overlay_canvas")
      .pointerInput(Unit) {
        var mode = DragMode.NONE
        val minSize = 0.14f

        detectDragGestures(
          onDragStart = { startOffset ->
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            val lPx = latestLeft * w
            val tPx = latestTop * h
            val rPx = latestRight * w
            val bPx = latestBottom * h
            val hitRadius = 48.dp.toPx()

            mode = when {
              hypot(startOffset.x - lPx, startOffset.y - tPx) <= hitRadius -> DragMode.TOP_LEFT
              hypot(startOffset.x - rPx, startOffset.y - tPx) <= hitRadius -> DragMode.TOP_RIGHT
              hypot(startOffset.x - lPx, startOffset.y - bPx) <= hitRadius -> DragMode.BOTTOM_LEFT
              hypot(startOffset.x - rPx, startOffset.y - bPx) <= hitRadius -> DragMode.BOTTOM_RIGHT
              startOffset.x in lPx..rPx && startOffset.y in tPx..bPx -> DragMode.MOVE
              else -> DragMode.NONE
            }
            if (mode != DragMode.NONE) {
              latestOnStart()
            }
          },
          onDragEnd = {
            if (mode != DragMode.NONE) {
              latestOnFinish()
            }
            mode = DragMode.NONE
          },
          onDragCancel = {
            mode = DragMode.NONE
          },
          onDrag = { change, dragAmount ->
            if (mode == DragMode.NONE) return@detectDragGestures
            change.consume()
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            val dx = dragAmount.x / w
            val dy = dragAmount.y / h

            var l = latestLeft
            var t = latestTop
            var r = latestRight
            var b = latestBottom
            val lockedRatio = latestPreset.ratio

            when (mode) {
              DragMode.MOVE -> {
                val cw = r - l
                val ch = b - t
                val nl = (l + dx).coerceIn(0f, 1f - cw)
                val nt = (t + dy).coerceIn(0f, 1f - ch)
                l = nl
                t = nt
                r = nl + cw
                b = nt + ch
              }

              DragMode.TOP_LEFT -> {
                l = (l + dx).coerceIn(0f, r - minSize)
                t = (t + dy).coerceIn(0f, b - minSize)
                if (lockedRatio != null) {
                  val normRatio = lockedRatio / latestImgRatio.coerceAtLeast(0.1f)
                  val candidateW = r - l
                  val candidateH = candidateW / normRatio
                  val newT = b - candidateH
                  if (newT >= 0f && b - newT >= minSize) {
                    t = newT
                  } else {
                    val clampedH = (b - t).coerceAtLeast(minSize)
                    val clampedW = clampedH * normRatio
                    if (r - clampedW >= 0f) l = r - clampedW
                  }
                }
              }

              DragMode.TOP_RIGHT -> {
                r = (r + dx).coerceIn(l + minSize, 1f)
                t = (t + dy).coerceIn(0f, b - minSize)
                if (lockedRatio != null) {
                  val normRatio = lockedRatio / latestImgRatio.coerceAtLeast(0.1f)
                  val candidateW = r - l
                  val candidateH = candidateW / normRatio
                  val newT = b - candidateH
                  if (newT >= 0f && b - newT >= minSize) {
                    t = newT
                  } else {
                    val clampedH = (b - t).coerceAtLeast(minSize)
                    val clampedW = clampedH * normRatio
                    if (l + clampedW <= 1f) r = l + clampedW
                  }
                }
              }

              DragMode.BOTTOM_LEFT -> {
                l = (l + dx).coerceIn(0f, r - minSize)
                b = (b + dy).coerceIn(t + minSize, 1f)
                if (lockedRatio != null) {
                  val normRatio = lockedRatio / latestImgRatio.coerceAtLeast(0.1f)
                  val candidateW = r - l
                  val candidateH = candidateW / normRatio
                  val newB = t + candidateH
                  if (newB <= 1f && newB - t >= minSize) {
                    b = newB
                  } else {
                    val clampedH = (b - t).coerceAtLeast(minSize)
                    val clampedW = clampedH * normRatio
                    if (r - clampedW >= 0f) l = r - clampedW
                  }
                }
              }

              DragMode.BOTTOM_RIGHT -> {
                r = (r + dx).coerceIn(l + minSize, 1f)
                b = (b + dy).coerceIn(t + minSize, 1f)
                if (lockedRatio != null) {
                  val normRatio = lockedRatio / latestImgRatio.coerceAtLeast(0.1f)
                  val candidateW = r - l
                  val candidateH = candidateW / normRatio
                  val newB = t + candidateH
                  if (newB <= 1f && newB - t >= minSize) {
                    b = newB
                  } else {
                    val clampedH = (b - t).coerceAtLeast(minSize)
                    val clampedW = clampedH * normRatio
                    if (l + clampedW <= 1f) r = l + clampedW
                  }
                }
              }

              DragMode.NONE -> Unit
            }

            if (abs(r - l) >= minSize && abs(b - t) >= minSize) {
              latestOnCrop(l, t, r, b)
            }
          },
        )
      }
  ) {
    val w = size.width
    val h = size.height
    val left = cropLeft * w
    val top = cropTop * h
    val right = cropRight * w
    val bottom = cropBottom * h
    val cropW = (right - left).coerceAtLeast(1f)
    val cropH = (bottom - top).coerceAtLeast(1f)

    // Dimmed outer crop mask (4 bands around active crop box)
    val maskColor = Color.Black.copy(alpha = 0.62f)
    drawRect(color = maskColor, topLeft = Offset.Zero, size = Size(w, top))
    drawRect(color = maskColor, topLeft = Offset(0f, bottom), size = Size(w, (h - bottom).coerceAtLeast(0f)))
    drawRect(color = maskColor, topLeft = Offset(0f, top), size = Size(left, cropH))
    drawRect(color = maskColor, topLeft = Offset(right, top), size = Size((w - right).coerceAtLeast(0f), cropH))

    // Outer frame border
    drawRect(
      color = Color.White.copy(alpha = 0.9f),
      topLeft = Offset(left, top),
      size = Size(cropW, cropH),
      style = Stroke(width = 1.5.dp.toPx()),
    )

    // Rule of thirds grid
    val gridColor = Color.White.copy(alpha = 0.35f)
    val stroke1 = 1.dp.toPx()
    for (i in 1..2) {
      val gx = left + cropW * (i / 3f)
      val gy = top + cropH * (i / 3f)
      drawLine(gridColor, Offset(gx, top), Offset(gx, bottom), strokeWidth = stroke1)
      drawLine(gridColor, Offset(left, gy), Offset(right, gy), strokeWidth = stroke1)
    }

    // Precision L-bracket corner handles
    val arm = 18.dp.toPx().coerceAtMost(cropW * 0.35f).coerceAtMost(cropH * 0.35f)
    val bracketStroke = Stroke(width = 3.5.dp.toPx())
    val bracketPath = Path().apply {
      // Top-left
      moveTo(left, top + arm)
      lineTo(left, top)
      lineTo(left + arm, top)
      // Top-right
      moveTo(right - arm, top)
      lineTo(right, top)
      lineTo(right, top + arm)
      // Bottom-left
      moveTo(left, bottom - arm)
      lineTo(left, bottom)
      lineTo(left + arm, bottom)
      // Bottom-right
      moveTo(right - arm, bottom)
      lineTo(right, bottom)
      lineTo(right, bottom - arm)
    }
    drawPath(bracketPath, color = CoralFlame, style = bracketStroke)
  }
}
