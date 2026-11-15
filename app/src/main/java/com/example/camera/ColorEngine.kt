package com.example.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.example.model.ColorGradingSettings
import com.example.model.DevelopSettings
import com.example.model.FilmPreset
import com.example.model.LogProfile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object ColorEngine {

    fun buildColorMatrix(
        logProfile: LogProfile,
        filmPreset: FilmPreset,
        grading: ColorGradingSettings
    ): ColorMatrix {
        val result = ColorMatrix()

        val applyRawLogLook = logProfile != LogProfile.REC_709 && !grading.monitoringLutEnabled
        if (applyRawLogLook) {
            val s = logProfile.contrastScale
            val lift = logProfile.blackLift
            val logSat = ColorMatrix().apply { setSaturation(logProfile.saturationScale) }
            val logTone = ColorMatrix(
                floatArrayOf(
                    s, 0f, 0f, 0f, lift,
                    0f, s, 0f, 0f, lift,
                    0f, 0f, s, 0f, lift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(logSat)
            result.postConcat(logTone)
            return result
        }

        val totalContrast = (filmPreset.contrast * grading.contrast).coerceIn(0.4f, 2.0f)
        val totalSaturation = (filmPreset.saturation * grading.saturation).coerceIn(0f, 2.0f)

        val satMatrix = ColorMatrix().apply { setSaturation(totalSaturation) }
        result.postConcat(satMatrix)

        val temp = (filmPreset.warmth + grading.temperatureOffset).coerceIn(-1f, 1f)
        val tint = (filmPreset.tint + grading.tintOffset).coerceIn(-1f, 1f)

        val rGain = (filmPreset.redGain * grading.gain * (1f + temp * 0.16f + tint * 0.06f)).coerceIn(0.5f, 1.6f)
        val gGain = (filmPreset.greenGain * grading.gain * (1f - tint * 0.12f)).coerceIn(0.5f, 1.6f)
        val bGain = (filmPreset.blueGain * grading.gain * (1f - temp * 0.16f + tint * 0.06f)).coerceIn(0.5f, 1.6f)

        val contrastTranslate = 128f * (1f - totalContrast)
        val liftOffset = grading.lift * 90f
        val gammaOffset = grading.gamma * 45f
        val totalOffset = contrastTranslate + liftOffset + gammaOffset

        val gradeMatrix = ColorMatrix(
            floatArrayOf(
                rGain * totalContrast, 0f, 0f, 0f, totalOffset,
                0f, gGain * totalContrast, 0f, 0f, totalOffset,
                0f, 0f, bGain * totalContrast, 0f, totalOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        result.postConcat(gradeMatrix)

        if (filmPreset == FilmPreset.TEAL_ORANGE) {
            val tealOrange = ColorMatrix(
                floatArrayOf(
                    1.08f, -0.04f, -0.04f, 0f, 6f,
                    -0.02f, 1.01f, 0.03f, 0f, -2f,
                    -0.06f, 0.04f, 1.04f, 0f, 4f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(tealOrange)
        } else if (filmPreset == FilmPreset.CINESTILL_800T) {
            val tungsten = ColorMatrix(
                floatArrayOf(
                    0.98f, 0f, 0.03f, 0f, 2f,
                    0f, 1.02f, 0.02f, 0f, 0f,
                    0.02f, 0.04f, 1.08f, 0f, -4f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(tungsten)
        }

        return result
    }

    /**
     * Converts non-destructive RAW/photo DevelopSettings into a ColorMatrix for instant preview
     * and offline export.
     */
    fun buildDevelopColorMatrix(dev: DevelopSettings): ColorMatrix {
        val evScale = 2.0.pow(dev.exposureEv.toDouble()).toFloat().coerceIn(0.25f, 4.0f)
        val totalContrast = (dev.filmPreset.contrast * dev.contrast * (1f + (dev.whites - dev.blacks) * 0.25f)).coerceIn(0.4f, 2.2f)
        val totalSat = (dev.filmPreset.saturation * dev.saturation * (1f + dev.vibrance * 0.45f)).coerceIn(0f, 2.2f)

        val result = ColorMatrix()
        result.postConcat(ColorMatrix().apply { setSaturation(totalSat) })

        val temp = (dev.filmPreset.warmth + dev.temperature).coerceIn(-1f, 1f)
        val tint = (dev.filmPreset.tint + dev.tint).coerceIn(-1f, 1f)
        val rGain = (dev.filmPreset.redGain * evScale * (1f + temp * 0.18f + tint * 0.06f)).coerceIn(0.2f, 3.0f)
        val gGain = (dev.filmPreset.greenGain * evScale * (1f - tint * 0.14f)).coerceIn(0.2f, 3.0f)
        val bGain = (dev.filmPreset.blueGain * evScale * (1f - temp * 0.18f + tint * 0.06f)).coerceIn(0.2f, 3.0f)

        val contrastTranslate = 128f * (1f - totalContrast)
        val shadowLift = (dev.shadows * 28f) + (dev.blacks * 40f)
        val highPull = dev.highlights * 18f + dev.whites * 22f
        val offset = contrastTranslate + shadowLift + highPull

        result.postConcat(
            ColorMatrix(
                floatArrayOf(
                    rGain * totalContrast, 0f, 0f, 0f, offset,
                    0f, gGain * totalContrast, 0f, 0f, offset,
                    0f, 0f, bGain * totalContrast, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return result
    }

    fun applyDevelopToBitmap(source: Bitmap, dev: DevelopSettings): Bitmap {
        val working = if (dev.rotationDeg % 360 != 0) {
            val m = Matrix().apply { postRotate((dev.rotationDeg % 360).toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        } else {
            source
        }

        val out = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildDevelopColorMatrix(dev))
        }
        canvas.drawBitmap(working, 0f, 0f, paint)
        if (working !== source) working.recycle()

        // Pixel-level Highlights / Shadows tonal roll-off when non-zero
        if (abs(dev.highlights) > 0.03f || abs(dev.shadows) > 0.03f) {
            val w = out.width
            val h = out.height
            val pixels = IntArray(w * h)
            out.getPixels(pixels, 0, w, 0, 0, w, h)
            val hiAdj = dev.highlights * 42f
            val shAdj = dev.shadows * 42f
            for (i in pixels.indices) {
                val c = pixels[i]
                val a = c ushr 24 and 0xFF
                var r = c ushr 16 and 0xFF
                var g = c ushr 8 and 0xFF
                var b = c and 0xFF
                val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                val delta = if (luma > 0.5f) {
                    hiAdj * ((luma - 0.5f) * 2f)
                } else {
                    shAdj * ((0.5f - luma) * 2f)
                }
                r = (r + delta).toInt().coerceIn(0, 255)
                g = (g + delta).toInt().coerceIn(0, 255)
                b = (b + delta).toInt().coerceIn(0, 255)
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            out.setPixels(pixels, 0, w, 0, 0, w, h)
        }

        if (dev.vignette > 0.02f) {
            val cx = out.width / 2f
            val cy = out.height / 2f
            val radius = maxOf(out.width, out.height) * 0.75f
            val edgeAlpha = (dev.vignette * 190f).toInt().coerceIn(0, 210)
            val vigPaint = Paint().apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(0x00000000, (edgeAlpha shl 24)),
                    floatArrayOf(0.45f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), vigPaint)
        }
        return out
    }

    /**
     * Multi-frame computational photography stacking:
     * - Normal/HDR/Night mode: averages aligned luminance & chroma across frames to cut sensor read noise
     *   while applying Mertens-style midtone weight so highlights aren't blown out.
     * - Light Trails mode: retains maximum brightness per channel for star/traffic light trails.
     */
    fun mergeMultiFrameBitmaps(frames: List<Bitmap>, lightTrailsMode: Boolean): Bitmap? {
        if (frames.isEmpty()) return null
        if (frames.size == 1) return frames.first().copy(Bitmap.Config.ARGB_8888, false)
        val base = frames.first()
        val w = base.width
        val h = base.height
        val count = w * h
        val accR = FloatArray(count)
        val accG = FloatArray(count)
        val accB = FloatArray(count)
        val weightSum = FloatArray(count)

        val rowBuf = IntArray(count)
        frames.forEach { bmp ->
            val scaled = if (bmp.width == w && bmp.height == h) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
            scaled.getPixels(rowBuf, 0, w, 0, 0, w, h)
            if (scaled !== bmp) scaled.recycle()

            for (i in 0 until count) {
                val c = rowBuf[i]
                val r = (c ushr 16 and 0xFF).toFloat()
                val g = (c ushr 8 and 0xFF).toFloat()
                val b = (c and 0xFF).toFloat()
                if (lightTrailsMode) {
                    accR[i] = max(accR[i], r)
                    accG[i] = max(accG[i], g)
                    accB[i] = max(accB[i], b)
                    weightSum[i] = 1f
                } else {
                    // Well-exposed pixels (~0.5 normalized) get higher weight than clipped or crushed pixels
                    val norm = ((0.299f * r + 0.587f * g + 0.114f * b) / 255f).coerceIn(0f, 1f)
                    val diff = norm - 0.5f
                    val exposureWeight = kotlin.math.exp((-diff * diff / (2f * 0.24f * 0.24f)).toDouble()).toFloat().coerceAtLeast(0.12f)
                    accR[i] += r * exposureWeight
                    accG[i] += g * exposureWeight
                    accB[i] += b * exposureWeight
                    weightSum[i] += exposureWeight
                }
            }
        }

        val outPixels = IntArray(count)
        for (i in 0 until count) {
            val wt = weightSum[i].coerceAtLeast(1e-4f)
            val r = (accR[i] / wt).toInt().coerceIn(0, 255)
            val g = (accG[i] / wt).toInt().coerceIn(0, 255)
            val b = (accB[i] / wt).toInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    fun applyGradingToBitmap(
        source: Bitmap,
        logProfile: LogProfile,
        filmPreset: FilmPreset,
        grading: ColorGradingSettings
    ): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildColorMatrix(logProfile, filmPreset, grading))
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        if (grading.vignette > 0.02f) {
            val cx = source.width / 2f
            val cy = source.height / 2f
            val radius = maxOf(source.width, source.height) * 0.75f
            val edgeAlpha = (grading.vignette * 190f).toInt().coerceIn(0, 210)
            val vigPaint = Paint().apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(0x00000000, (edgeAlpha shl 24)),
                    floatArrayOf(0.45f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), vigPaint)
        }

        val grainStrength = if (logProfile != LogProfile.REC_709 && !grading.monitoringLutEnabled) 0f else grading.grainAmount
        if (grainStrength > 0.03f) {
            val grainTileSize = 256
            val tile = Bitmap.createBitmap(grainTileSize, grainTileSize, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(grainTileSize * grainTileSize)
            val rng = Random(System.nanoTime())
            val maxAlpha = (grainStrength * 52f).toInt().coerceIn(4, 64)
            for (i in pixels.indices) {
                val n = rng.nextInt(-128, 128)
                val lum = (128 + n).coerceIn(0, 255)
                val alpha = (abs(n) * maxAlpha / 128).coerceIn(0, maxAlpha)
                pixels[i] = (alpha shl 24) or (lum shl 16) or (lum shl 8) or lum
            }
            tile.setPixels(pixels, 0, grainTileSize, 0, 0, grainTileSize, grainTileSize)
            val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
            val step = (grainTileSize * grading.grainSize).toInt().coerceAtLeast(128)
            var y = 0
            while (y < source.height) {
                var x = 0
                while (x < source.width) {
                    val dst = android.graphics.Rect(x, y, x + step, y + step)
                    canvas.drawBitmap(tile, null, dst, tilePaint)
                    x += step
                }
                y += step
            }
            tile.recycle()
        }

        return output
    }

    fun kelvinToRggbGains(kelvin: Int): FloatArray {
        val temp = (kelvin.coerceIn(2000, 10000)) / 100f
        val red = if (temp <= 66f) 255f
        else (329.698727446 * Math.pow((temp - 60f).toDouble(), -0.1332047592)).toFloat().coerceIn(0f, 255f)
        val green = if (temp <= 66f) (99.4708025861 * Math.log(temp.toDouble()) - 161.1195681661).toFloat().coerceIn(0f, 255f)
        else (288.1221695283 * Math.pow((temp - 60f).toDouble(), -0.0755148492)).toFloat().coerceIn(0f, 255f)
        val blue = if (temp >= 66f) 255f else if (temp <= 19f) 0f
        else (138.5177312231 * Math.log((temp - 10f).toDouble()) - 305.0447927307).toFloat().coerceIn(0f, 255f)
        val rGain = (255f / red.coerceAtLeast(40f)).coerceIn(0.7f, 3.2f)
        val gGain = (255f / green.coerceAtLeast(40f)).coerceIn(0.7f, 2.0f)
        val bGain = (255f / blue.coerceAtLeast(40f)).coerceIn(0.7f, 3.2f)
        val minGain = minOf(rGain, gGain, bGain)
        return floatArrayOf(rGain / minGain, gGain / minGain, gGain / minGain, bGain / minGain)
    }
}
