package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HistogramData
import com.example.model.MonitorScopeMode
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.HistogramBlue
import com.example.ui.theme.HistogramGreen
import com.example.ui.theme.RecordRed
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MonitorScopeWidget(
    scopeMode: MonitorScopeMode,
    data: HistogramData,
    onCycleScope: () -> Unit,
    modifier: Modifier = Modifier
) {
    val widgetSize = if (scopeMode == MonitorScopeMode.VECTORSCOPE) 96.dp else 138.dp
    val widgetHeight = if (scopeMode == MonitorScopeMode.VECTORSCOPE) 96.dp else 72.dp

    Surface(
        modifier = modifier
            .width(widgetSize)
            .height(widgetHeight)
            .testTag("hud_scope_widget")
            .clickable(onClick = onCycleScope),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = scopeMode.shortCode,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (data.shadowCrushPct > 4f) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(HistogramBlue)
                        )
                    }
                    if (data.highlightClipPct > 2f) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(RecordRed)
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                when (scopeMode) {
                    MonitorScopeMode.OFF,
                    MonitorScopeMode.HISTOGRAM -> {
                        val bins = data.lumaBins.size
                        if (bins < 2) return@Canvas
                        val dx = w / (bins - 1)
                        fun drawChan(vals: FloatArray, c: Color, fillA: Float) {
                            val fillPath = Path().apply {
                                moveTo(0f, h)
                                for (i in vals.indices) lineTo(i * dx, h - vals[i].coerceIn(0f, 1f) * h)
                                lineTo(w, h)
                                close()
                            }
                            drawPath(fillPath, color = c.copy(alpha = fillA))
                            val strokePath = Path().apply {
                                for (i in vals.indices) {
                                    val ptX = i * dx
                                    val ptY = h - vals[i].coerceIn(0f, 1f) * h
                                    if (i == 0) moveTo(ptX, ptY) else lineTo(ptX, ptY)
                                }
                            }
                            drawPath(strokePath, color = c, style = Stroke(width = 1.2f))
                        }
                        drawChan(data.redBins, RecordRed.copy(alpha = 0.8f), 0.12f)
                        drawChan(data.greenBins, HistogramGreen.copy(alpha = 0.8f), 0.12f)
                        drawChan(data.blueBins, HistogramBlue.copy(alpha = 0.8f), 0.12f)
                        drawChan(data.lumaBins, Color.White.copy(alpha = 0.9f), 0.18f)
                    }

                    MonitorScopeMode.WAVEFORM -> {
                        // IRE 25 / 50 / 75 graticule lines
                        listOf(0.25f, 0.5f, 0.75f).forEach { ire ->
                            val y = h - ire * h
                            drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                        }
                        val cols = data.waveformColumns
                        if (cols.size >= 2) {
                            val dx = w / (cols.size - 1)
                            val path = Path()
                            for (i in cols.indices) {
                                val px = i * dx
                                val py = h - cols[i].coerceIn(0f, 1f) * h
                                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                            }
                            drawPath(path, color = CinemaAmber, style = Stroke(width = 1.8f, cap = StrokeCap.Round))
                        }
                    }

                    MonitorScopeMode.RGB_PARADE -> {
                        val thirdW = w / 3f
                        listOf(
                            Triple(data.redBins, RecordRed, 0f),
                            Triple(data.greenBins, HistogramGreen, thirdW),
                            Triple(data.blueBins, HistogramBlue, thirdW * 2f)
                        ).forEach { (bins, col, startX) ->
                            val step = (thirdW - 2f) / (bins.size.coerceAtLeast(2) - 1)
                            val path = Path()
                            for (i in bins.indices) {
                                val px = startX + 1f + i * step
                                val py = h - bins[i].coerceIn(0f, 1f) * h
                                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                            }
                            drawPath(path, color = col, style = Stroke(width = 1.2f))
                        }
                    }

                    MonitorScopeMode.VECTORSCOPE -> {
                        val r = size.minDimension / 2f - 2f
                        drawCircle(Color.White.copy(alpha = 0.18f), radius = r, center = center, style = Stroke(1f))
                        drawLine(Color.White.copy(alpha = 0.14f), Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1f)
                        drawLine(Color.White.copy(alpha = 0.14f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1f)
                        // Skin-tone I-line (~123 degrees)
                        val skinRad = Math.toRadians(-57.0)
                        drawLine(
                            CinemaAmber.copy(alpha = 0.38f),
                            center,
                            Offset(center.x + cos(skinRad).toFloat() * r, center.y + sin(skinRad).toFloat() * r),
                            strokeWidth = 1f
                        )
                        val xs = data.vectorPointsX
                        val ys = data.vectorPointsY
                        for (i in 0 until minOf(xs.size, ys.size)) {
                            val px = center.x + xs[i] * r * 1.4f
                            val py = center.y - ys[i] * r * 1.4f
                            drawCircle(Color.White.copy(alpha = 0.72f), radius = 1.6f, center = Offset(px, py))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HorizonLevelOverlay(
    rollDegrees: Float,
    modifier: Modifier = Modifier
) {
    val isLevel = abs(rollDegrees) < 0.8f
    val lineColor = if (isLevel) HistogramGreen.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.65f)
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val halfSpan = 56.dp.toPx()
        val gap = 14.dp.toPx()

        // Fixed outer reference ticks
        drawLine(Color.White.copy(alpha = 0.35f), Offset(cx - halfSpan - 10.dp.toPx(), cy), Offset(cx - halfSpan - 2.dp.toPx(), cy), strokeWidth = 1.5.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.35f), Offset(cx + halfSpan + 2.dp.toPx(), cy), Offset(cx + halfSpan + 10.dp.toPx(), cy), strokeWidth = 1.5.dp.toPx())

        val rad = Math.toRadians(rollDegrees.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()

        // Split horizon arms around center focal zone
        drawLine(
            color = lineColor,
            start = Offset(cx - halfSpan * dx, cy - halfSpan * dy),
            end = Offset(cx - gap * dx, cy - gap * dy),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = lineColor,
            start = Offset(cx + gap * dx, cy + gap * dy),
            end = Offset(cx + halfSpan * dx, cy + halfSpan * dy),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun FalseColorLegendBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Color(0xFF7828DC) to "Crush",
                Color(0xFF2D6EE1) to "Shdw",
                Color(0xFF4BC373) to "18%",
                Color(0xFFEB9BB9) to "Skin",
                Color(0xFFEBC83C) to "90%",
                Color(0xFFEB4141) to "Clip"
            ).forEach { (col, lbl) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(col))
                    Text(lbl, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                }
            }
        }
    }
}
