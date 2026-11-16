package com.example.camera

import org.junit.Assert.*
import org.junit.Test

class VideoOutputGeometryTest {
    @Test fun acceptsExactAndRotatedUhd() {
        assertTrue(VideoOutputGeometry.matches(3840, 2160, 3840, 2160))
        assertTrue(VideoOutputGeometry.matches(3840, 2160, 2160, 3840))
    }

    @Test fun rejectsSilentFhdAndHdFallback() {
        assertFalse(VideoOutputGeometry.matches(3840, 2160, 1920, 1080))
        assertFalse(VideoOutputGeometry.matches(3840, 2160, 720, 1280))
        assertFalse(VideoOutputGeometry.matches(1920, 1080, 3840, 2160))
    }

    @Test fun rejectsMissingGeometry() {
        assertFalse(VideoOutputGeometry.matches(0, 0, 0, 0))
        assertFalse(VideoOutputGeometry.matches(1920, 1080, 0, 0))
    }
}
