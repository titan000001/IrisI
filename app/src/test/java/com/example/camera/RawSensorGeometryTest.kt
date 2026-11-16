package com.example.camera

import org.junit.Assert.*
import org.junit.Test

class RawSensorGeometryTest {
    @Test fun croppedStreamIsNotDngCompatible() {
        assertEquals(4080 to 3072, RawSensorGeometry.select(
            listOf(4080 to 2296, 4080 to 3072), listOf(4080 to 3072), 3840 to 2160))
    }
    @Test fun rejectsMissingNativeStream() {
        assertNull(RawSensorGeometry.select(listOf(1920 to 1080), listOf(4080 to 3072), 1920 to 1080))
        assertNull(RawSensorGeometry.select(emptyList(), listOf(4080 to 3072), 3840 to 2160))
    }
    @Test fun acceptsEitherPermittedSensorArray() {
        assertEquals(4080 to 3072, RawSensorGeometry.select(
            listOf(4096 to 3072, 4080 to 3072), listOf(4096 to 3072, 4080 to 3072), 4080 to 3072))
    }
}
