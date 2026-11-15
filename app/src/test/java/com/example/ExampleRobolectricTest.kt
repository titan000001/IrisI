package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.camera.ColorEngine
import com.example.model.ColorGradingSettings
import com.example.model.DevelopSettings
import com.example.model.ExposureProgram
import com.example.model.FilmPreset
import com.example.model.LogProfile
import com.example.model.VideoResolution
import com.example.viewmodel.CineRigViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("IrisI", appName)
    }

    @Test
    fun `color engine builds distinct flat log and film matrices`() {
        val flatMatrix = ColorEngine.buildColorMatrix(
            LogProfile.SLOG3,
            FilmPreset.PORTRA_400,
            ColorGradingSettings(monitoringLutEnabled = false)
        )
        val gradedMatrix = ColorEngine.buildColorMatrix(
            LogProfile.SLOG3,
            FilmPreset.PORTRA_400,
            ColorGradingSettings(monitoringLutEnabled = true)
        )
        assertTrue(!flatMatrix.array.contentEquals(gradedMatrix.array))
    }

    @Test
    fun `develop engine applies exposure and film matrix changes`() {
        val neutral = ColorEngine.buildDevelopColorMatrix(DevelopSettings())
        val pushed = ColorEngine.buildDevelopColorMatrix(
            DevelopSettings(
                exposureEv = 1.2f,
                highlights = -0.4f,
                shadows = 0.5f,
                filmPreset = FilmPreset.KODACHROME_64
            )
        )
        assertFalse(neutral.array.contentEquals(pushed.array))
    }

    @Test
    fun `multi frame merge averages noise and supports light trails max blend`() {
        val darkFrame = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(20, 20, 20))
        }
        val brightPixelFrame = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(20, 20, 20))
            setPixel(1, 1, Color.rgb(240, 220, 180))
        }
        val avgMerged = ColorEngine.mergeMultiFrameBitmaps(listOf(darkFrame, brightPixelFrame), lightTrailsMode = false)
        val trailsMerged = ColorEngine.mergeMultiFrameBitmaps(listOf(darkFrame, brightPixelFrame), lightTrailsMode = true)
        assertNotNull(avgMerged)
        assertNotNull(trailsMerged)
        val trailsRed = Color.red(trailsMerged!!.getPixel(1, 1))
        val avgRed = Color.red(avgMerged!!.getPixel(1, 1))
        assertTrue(trailsRed >= 235)
        assertTrue(avgRed < trailsRed)
        darkFrame.recycle()
        brightPixelFrame.recycle()
        avgMerged.recycle()
        trailsMerged.recycle()
    }

    @Test
    fun `kelvin conversion produces positive gains`() {
        val warm = ColorEngine.kelvinToRggbGains(3200)
        val cool = ColorEngine.kelvinToRggbGains(7500)
        assertEquals(4, warm.size)
        assertTrue(warm.all { it > 0f })
        assertTrue(cool.all { it > 0f })
    }

    @Test
    fun `shutter duration and byte formatters produce expected telemetry`() {
        assertEquals("1/48", CineRigViewModel.formatShutterSpeed(20_833_333L))
        assertEquals("1.0\"", CineRigViewModel.formatShutterSpeed(1_000_000_000L))
        assertEquals("01:05", CineRigViewModel.formatDuration(65_000L))
        assertEquals("512 KB", CineRigViewModel.formatBytes(512 * 1024L))
        assertEquals("1.5 GB", CineRigViewModel.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `open gate half resolution preserves 4_3 aspect and binned flag`() {
        val full = VideoResolution.OPEN_GATE_4_3
        val half = VideoResolution.OPEN_GATE_HALF
        assertTrue(full.isOpenGate)
        assertFalse(full.isBinned)
        assertTrue(half.isOpenGate)
        assertTrue(half.isBinned)
        assertEquals(full.width / 2, half.width)
        assertEquals(full.height / 2, half.height)
    }

    @Test
    fun `exposure programs cover auto p s iso and manual modes`() {
        assertEquals(listOf("AUTO", "P", "S", "ISO", "M"), ExposureProgram.entries.map { it.shortCode })
    }

    @Test
    fun `focus stack merge selects sharpest plane per pixel`() {
        val softFrame = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(100, 100, 100))
        }
        val sharpFrame = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(100, 100, 100))
            setPixel(2, 2, Color.rgb(240, 240, 240))
        }
        val fused = ColorEngine.mergeFocusStackBitmaps(listOf(softFrame, sharpFrame))
        assertNotNull(fused)
        assertTrue(Color.red(fused!!.getPixel(2, 2)) >= 230)
        val sharpened = ColorEngine.applyDevelopToBitmap(fused, DevelopSettings(sharpening = 0.8f))
        assertNotNull(sharpened)
        softFrame.recycle()
        sharpFrame.recycle()
        fused.recycle()
        sharpened.recycle()
    }
}
