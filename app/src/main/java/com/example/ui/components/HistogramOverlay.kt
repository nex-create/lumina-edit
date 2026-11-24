package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.editor.HistogramData
import com.example.ui.theme.CoralFlame
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.WarmAmber
import kotlin.math.roundToInt

private val HistogramRed = Color(0xFFFF5E6C)
private val HistogramGreen = Color(0xFF4ADE80)
private val HistogramBlue = Color(0xFF5CE1E6)

@Composable
fun HistogramOverlay(
  histogram: HistogramData,
  isComparingOriginal: Boolean,
  modifier: Modifier = Modifier,
) {
  val shadowClipped = histogram.shadowClippingRatio > 0.03f
  val highlightClipped = histogram.highlightClippingRatio > 0.03f
  val avgPercent = (histogram.meanLuminance * 100f).roundToInt()

  Surface(
    color = ObsidianCanvas.copy(alpha = 0.80f),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(
      width = 1.dp,
      color = if (isComparingOriginal) WarmAmber.copy(alpha = 0.65f) else HairlineBorder,
    ),
    tonalElevation = 6.dp,
    modifier = modifier
      .width(196.dp)
      .testTag("histogram_overlay"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          // Shadow clipping indicator dot
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(if (shadowClipped) StudioCyan else Color.White.copy(alpha = 0.25f)),
          )
          Text(
            text = if (isComparingOriginal) "ORIG RGB" else "RGB + LUMA",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isComparingOriginal) WarmAmber else Color.White.copy(alpha = 0.85f),
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "Y $avgPercent%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.68f),
          )
          // Highlight clipping indicator dot
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(if (highlightClipped) CoralFlame else Color.White.copy(alpha = 0.25f)),
          )
        }
      }

      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(62.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF0A0B10).copy(alpha = 0.72f))
          .testTag("histogram_canvas"),
      ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Quarter-tone reference grid
        val gridColor = Color.White.copy(alpha = 0.09f)
        for (i in 1..3) {
          val x = w * (i / 4f)
          drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
        }
        drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1.dp.toPx())

        drawChannelWaveform(bins = histogram.redBins, color = HistogramRed, fillAlpha = 0.24f)
        drawChannelWaveform(bins = histogram.greenBins, color = HistogramGreen, fillAlpha = 0.22f)
        drawChannelWaveform(bins = histogram.blueBins, color = HistogramBlue, fillAlpha = 0.24f)
        drawChannelWaveform(
          bins = histogram.lumaBins,
          color = Color.White.copy(alpha = 0.92f),
          fillAlpha = 0.14f,
          strokeWidthDp = 1.5f,
        )
      }
    }
  }
}

private fun DrawScope.drawChannelWaveform(
  bins: FloatArray,
  color: Color,
  fillAlpha: Float,
  strokeWidthDp: Float = 1.15f,
) {
  if (bins.size < 2) return
  val w = size.width
  val h = size.height
  val padTop = 3.dp.toPx()
  val usableH = (h - padTop).coerceAtLeast(1f)
  val stepX = w / (bins.size - 1).toFloat()

  val linePath = Path()
  val fillPath = Path()

  bins.forEachIndexed { index, value ->
    val x = index * stepX
    val y = h - (value.coerceIn(0f, 1f) * usableH)
    if (index == 0) {
      linePath.moveTo(x, y)
      fillPath.moveTo(x, h)
      fillPath.lineTo(x, y)
    } else {
      val prevX = (index - 1) * stepX
      val prevY = h - (bins[index - 1].coerceIn(0f, 1f) * usableH)
      val midX = (prevX + x) * 0.5f
      linePath.cubicTo(midX, prevY, midX, y, x, y)
      fillPath.cubicTo(midX, prevY, midX, y, x, y)
    }
  }

  fillPath.lineTo(w, h)
  fillPath.close()

  drawPath(
    path = fillPath,
    brush = Brush.verticalGradient(
      colors = listOf(
        color.copy(alpha = fillAlpha),
        color.copy(alpha = fillAlpha * 0.15f),
      ),
      startY = padTop,
      endY = h,
    ),
  )

  drawPath(
    path = linePath,
    color = color.copy(alpha = 0.90f),
    style = Stroke(
      width = strokeWidthDp.dp.toPx(),
      cap = StrokeCap.Round,
      join = StrokeJoin.Round,
    ),
  )
}
