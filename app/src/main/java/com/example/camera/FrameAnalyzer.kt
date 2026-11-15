package com.example.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.toArgb
import com.example.model.HistogramData
import com.example.model.MonitorScopeMode
import com.example.model.PeakingColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class FrameAnalysisResult(
    val histogram: HistogramData,
    val peakingBitmap: Bitmap? = null,
    val loupeBitmap: Bitmap? = null,
    val zebraBitmap: Bitmap? = null,
    val falseColorBitmap: Bitmap? = null
)

class FrameAnalyzer(
    private val scopeMode: () -> MonitorScopeMode,
    private val isHistogramEnabled: () -> Boolean,
    private val isPeakingEnabled: () -> Boolean,
    private val peakingColor: () -> PeakingColor,
    private val peakingSensitivity: () -> Float,
    private val isLoupeEnabled: () -> Boolean,
    private val loupeZoomFactor: () -> Float,
    private val focusPointNormalized: () -> Pair<Float, Float>,
    private val isZebraEnabled: () -> Boolean,
    private val zebraThresholdPct: () -> Float,
    private val isFalseColorEnabled: () -> Boolean,
    private val isPriorityMeteringNeeded: () -> Boolean,
    private val onResult: (FrameAnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastAnalyzedMs < 65L) return
            lastAnalyzedMs = now

            val activeScope = scopeMode()
            val needScope = activeScope != MonitorScopeMode.OFF || isHistogramEnabled() || isPriorityMeteringNeeded()
            val needPeaking = isPeakingEnabled()
            val needLoupe = isLoupeEnabled()
            val needZebra = isZebraEnabled()
            val needFalseColor = isFalseColorEnabled()
            if (!needScope && !needPeaking && !needLoupe && !needZebra && !needFalseColor) return

            val width = image.width
            val height = image.height
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            val lumaCounts = IntArray(48)
            val rCounts = IntArray(48)
            val gCounts = IntArray(48)
            val bCounts = IntArray(48)
            val waveformAcc = FloatArray(48)
            val waveformCount = IntArray(48)
            val vectX = FloatArray(64)
            val vectY = FloatArray(64)
            var vectIdx = 0

            var totalSamples = 0
            var highlightClip = 0
            var shadowCrush = 0
            var lumaSum = 0L

            if (needScope) {
                val step = 8
                var row = 0
                while (row < height) {
                    val yRowOffset = row * yRowStride
                    val uvRowOffset = (row / 2) * uvRowStride
                    var col = 0
                    while (col < width) {
                        val yIdx = yRowOffset + col * yPixelStride
                        val uvIdx = uvRowOffset + (col / 2) * uvPixelStride
                        if (yIdx < yBuffer.limit() && uvIdx < uBuffer.limit() && uvIdx < vBuffer.limit()) {
                            val yVal = yBuffer.get(yIdx).toInt() and 0xFF
                            val uRaw = uBuffer.get(uvIdx).toInt() and 0xFF
                            val vRaw = vBuffer.get(uvIdx).toInt() and 0xFF
                            val uVal = uRaw - 128
                            val vVal = vRaw - 128

                            val r = (yVal + (1.370705f * vVal)).toInt().coerceIn(0, 255)
                            val g = (yVal - (0.337633f * uVal) - (0.698001f * vVal)).toInt().coerceIn(0, 255)
                            val b = (yVal + (1.732446f * uVal)).toInt().coerceIn(0, 255)

                            lumaCounts[(yVal * 47) / 255]++
                            rCounts[(r * 47) / 255]++
                            gCounts[(g * 47) / 255]++
                            bCounts[(b * 47) / 255]++

                            // Column average luminance for real Waveform trace across horizontal frame width
                            val wfCol = ((col * 47) / width.coerceAtLeast(1)).coerceIn(0, 47)
                            waveformAcc[wfCol] += yVal / 255f
                            waveformCount[wfCol]++

                            if (activeScope == MonitorScopeMode.VECTORSCOPE && vectIdx < vectX.size && (row % 24 == 0 && col % 24 == 0)) {
                                vectX[vectIdx] = (uVal / 128f).coerceIn(-1f, 1f)
                                vectY[vectIdx] = (vVal / 128f).coerceIn(-1f, 1f)
                                vectIdx++
                            }

                            lumaSum += yVal
                            if (yVal >= 248) highlightClip++
                            if (yVal <= 8) shadowCrush++
                            totalSamples++
                        }
                        col += step
                    }
                    row += step
                }
            }

            val maxBin = maxOf(
                1,
                lumaCounts.maxOrNull() ?: 1,
                rCounts.maxOrNull() ?: 1,
                gCounts.maxOrNull() ?: 1,
                bCounts.maxOrNull() ?: 1
            ).toFloat()

            val hist = HistogramData(
                lumaBins = FloatArray(48) { lumaCounts[it] / maxBin },
                redBins = FloatArray(48) { rCounts[it] / maxBin },
                greenBins = FloatArray(48) { gCounts[it] / maxBin },
                blueBins = FloatArray(48) { bCounts[it] / maxBin },
                waveformColumns = FloatArray(48) { if (waveformCount[it] > 0) (waveformAcc[it] / waveformCount[it]).coerceIn(0f, 1f) else 0f },
                vectorPointsX = vectX.copyOf(vectIdx),
                vectorPointsY = vectY.copyOf(vectIdx),
                highlightClipPct = if (totalSamples > 0) (highlightClip * 100f / totalSamples) else 0f,
                shadowCrushPct = if (totalSamples > 0) (shadowCrush * 100f / totalSamples) else 0f,
                avgLumaEv = if (totalSamples > 0) (lumaSum / (totalSamples * 255f)) else 0.5f
            )

            val rotationDeg = image.imageInfo.rotationDegrees

            var peakingBmp: Bitmap? = null
            if (needPeaking) {
                val dsW = 160
                val dsH = (160 * height / width.coerceAtLeast(1)).coerceIn(80, 200)
                val stepX = max(1, width / dsW)
                val stepY = max(1, height / dsH)
                val outPixels = IntArray(dsW * dsH)
                val peakArgb = peakingColor().color.toArgb()
                val threshold = (18 + (1f - peakingSensitivity()) * 72f).toInt()

                for (py in 1 until dsH - 1) {
                    val sy = min(height - 2, py * stepY)
                    val rowPrev = (sy - stepY).coerceAtLeast(0) * yRowStride
                    val rowCurr = sy * yRowStride
                    val rowNext = min(height - 1, sy + stepY) * yRowStride
                    for (px in 1 until dsW - 1) {
                        val sx = min(width - 2, px * stepX)
                        val sxPrev = (sx - stepX).coerceAtLeast(0) * yPixelStride
                        val sxCurr = sx * yPixelStride
                        val sxNext = min(width - 1, sx + stepX) * yPixelStride

                        val tl = yBuffer.get(rowPrev + sxPrev).toInt() and 0xFF
                        val tc = yBuffer.get(rowPrev + sxCurr).toInt() and 0xFF
                        val tr = yBuffer.get(rowPrev + sxNext).toInt() and 0xFF
                        val ml = yBuffer.get(rowCurr + sxPrev).toInt() and 0xFF
                        val mr = yBuffer.get(rowCurr + sxNext).toInt() and 0xFF
                        val bl = yBuffer.get(rowNext + sxPrev).toInt() and 0xFF
                        val bc = yBuffer.get(rowNext + sxCurr).toInt() and 0xFF
                        val br = yBuffer.get(rowNext + sxNext).toInt() and 0xFF

                        val gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                        val gy = (bl + 2 * bc + br) - (tl + 2 * tc + tr)
                        val mag = (abs(gx) + abs(gy)) shr 2
                        if (mag > threshold) {
                            outPixels[py * dsW + px] = peakArgb
                        }
                    }
                }
                peakingBmp = rotateBitmapIfNeeded(Bitmap.createBitmap(outPixels, dsW, dsH, Bitmap.Config.ARGB_8888), rotationDeg)
            }

            var zebraBmp: Bitmap? = null
            if (needZebra) {
                val dsW = 144
                val dsH = (144 * height / width.coerceAtLeast(1)).coerceIn(72, 180)
                val stepX = max(1, width / dsW)
                val stepY = max(1, height / dsH)
                val zPixels = IntArray(dsW * dsH)
                val threshY = ((zebraThresholdPct().coerceIn(0.65f, 0.99f)) * 255f).toInt()
                val stripeColor = Color.argb(185, 230, 190, 92)
                for (py in 0 until dsH) {
                    val sy = min(height - 1, py * stepY) * yRowStride
                    for (px in 0 until dsW) {
                        if ((px + py) % 4 < 2) {
                            val sx = min(width - 1, px * stepX) * yPixelStride
                            val yVal = yBuffer.get(sy + sx).toInt() and 0xFF
                            if (yVal >= threshY) {
                                zPixels[py * dsW + px] = stripeColor
                            }
                        }
                    }
                }
                zebraBmp = rotateBitmapIfNeeded(Bitmap.createBitmap(zPixels, dsW, dsH, Bitmap.Config.ARGB_8888), rotationDeg)
            }

            var falseColorBmp: Bitmap? = null
            if (needFalseColor) {
                val dsW = 144
                val dsH = (144 * height / width.coerceAtLeast(1)).coerceIn(72, 180)
                val stepX = max(1, width / dsW)
                val stepY = max(1, height / dsH)
                val fcPixels = IntArray(dsW * dsH)
                for (py in 0 until dsH) {
                    val sy = min(height - 1, py * stepY) * yRowStride
                    for (px in 0 until dsW) {
                        val sx = min(width - 1, px * stepX) * yPixelStride
                        val yVal = yBuffer.get(sy + sx).toInt() and 0xFF
                        val ire = (yVal * 100) / 255
                        fcPixels[py * dsW + px] = when {
                            ire <= 4 -> Color.argb(170, 120, 40, 220)   // Crushed Black (Purple)
                            ire <= 18 -> Color.argb(145, 45, 110, 225)  // Deep Shadow (Blue)
                            ire in 42..52 -> Color.argb(150, 75, 195, 115) // Middle Grey 18% (Green)
                            ire in 56..68 -> Color.argb(150, 235, 155, 185) // Skin Tone (Pink)
                            ire in 86..94 -> Color.argb(155, 235, 200, 60) // Near Clip (Yellow)
                            ire >= 95 -> Color.argb(180, 235, 65, 65)   // Clipped Highlight (Red)
                            else -> Color.argb(70, yVal, yVal, yVal)    // Monochrome Luma Reference
                        }
                    }
                }
                falseColorBmp = rotateBitmapIfNeeded(Bitmap.createBitmap(fcPixels, dsW, dsH, Bitmap.Config.ARGB_8888), rotationDeg)
            }

            var loupeBmp: Bitmap? = null
            if (needLoupe) {
                val zoom = loupeZoomFactor().coerceIn(2f, 6f)
                val (nx, ny) = focusPointNormalized()
                val cropW = (width / zoom).toInt().coerceIn(64, width)
                val cropH = (height / zoom).toInt().coerceIn(64, height)

                val sensorX = when (rotationDeg) {
                    90 -> ny
                    270 -> 1f - ny
                    180 -> 1f - nx
                    else -> nx
                }.coerceIn(0.1f, 0.9f)
                val sensorY = when (rotationDeg) {
                    90 -> 1f - nx
                    270 -> nx
                    180 -> 1f - ny
                    else -> ny
                }.coerceIn(0.1f, 0.9f)

                val startX = ((sensorX * width) - cropW / 2f).toInt().coerceIn(0, width - cropW)
                val startY = ((sensorY * height) - cropH / 2f).toInt().coerceIn(0, height - cropH)

                val outSize = 144
                val stepX = max(1, cropW / outSize)
                val stepY = max(1, cropH / outSize)
                val outW = max(1, cropW / stepX)
                val outH = max(1, cropH / stepY)
                val rgbPixels = IntArray(outW * outH)

                for (oy in 0 until outH) {
                    val sy = min(height - 1, startY + oy * stepY)
                    val yRowOff = sy * yRowStride
                    val uvRowOff = (sy / 2) * uvRowStride
                    for (ox in 0 until outW) {
                        val sx = min(width - 1, startX + ox * stepX)
                        val yIdx = yRowOff + sx * yPixelStride
                        val uvIdx = uvRowOff + (sx / 2) * uvPixelStride
                        val yVal = yBuffer.get(yIdx).toInt() and 0xFF
                        val uVal = (uBuffer.get(uvIdx).toInt() and 0xFF) - 128
                        val vVal = (vBuffer.get(uvIdx).toInt() and 0xFF) - 128

                        val r = (yVal + (1.370705f * vVal)).toInt().coerceIn(0, 255)
                        val g = (yVal - (0.337633f * uVal) - (0.698001f * vVal)).toInt().coerceIn(0, 255)
                        val b = (yVal + (1.732446f * uVal)).toInt().coerceIn(0, 255)
                        rgbPixels[oy * outW + ox] = Color.rgb(r, g, b)
                    }
                }
                loupeBmp = rotateBitmapIfNeeded(Bitmap.createBitmap(rgbPixels, outW, outH, Bitmap.Config.ARGB_8888), rotationDeg)
            }

            onResult(FrameAnalysisResult(hist, peakingBmp, loupeBmp, zebraBmp, falseColorBmp))
        } catch (_: Throwable) {
            // Gracefully ignore transient buffer sizing errors during lens switches
        } finally {
            image.close()
        }
    }

    private fun rotateBitmapIfNeeded(bmp: Bitmap, rotationDeg: Int): Bitmap {
        if (rotationDeg == 0) return bmp
        val m = Matrix().apply { postRotate(rotationDeg.toFloat()) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, false)
        if (rotated != bmp) bmp.recycle()
        return rotated
    }
}
