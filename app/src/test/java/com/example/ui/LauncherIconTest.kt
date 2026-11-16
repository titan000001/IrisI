package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test @Config(sdk = [24])
    fun legacyIconsLoadInBothShapes() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            // Native Canvas rendering is supported from API 26, not API 24.
            // Inspect the actual decoded legacy PNG rather than a no-op shadow draw.
            val bitmap = (ContextCompat.getDrawable(context, id) as BitmapDrawable).bitmap
            assertTrue(Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 0)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        }
    }

    @Test @Config(sdk = [33])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun adaptiveIconsHaveARealMonochromeLayer() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = ContextCompat.getDrawable(context, id) as AdaptiveIconDrawable
            assertNotNull(icon.monochrome)
        }
        val bitmap = render(R.drawable.ic_launcher_monochrome)
        assertEquals("Aperture must remain transparent", 0, Color.alpha(bitmap.getPixel(54, 54)))
        assertEquals("Outer layer must remain transparent", 0, Color.alpha(bitmap.getPixel(5, 5)))
        assertTrue("Petal must be opaque", Color.alpha(bitmap.getPixel(54, 32)) > 240)
    }

    private fun render(id: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        requireNotNull(ContextCompat.getDrawable(context, id)).apply {
            setBounds(0, 0, 108, 108)
            draw(Canvas(bitmap))
        }
        return bitmap
    }
}
