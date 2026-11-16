package com.example.camera

import org.junit.Assert.*
import org.junit.Test

class AnalysisGenerationTest {
    @Test fun retiredSessionCannotPublishAfterClear() {
        val gate = AnalysisGeneration()
        val oldSession = gate.snapshot()
        var value = "old"
        gate.invalidate { value = "empty" }
        assertFalse(gate.publish(oldSession) { value = "stale" })
        assertEquals("empty", value)
    }

    @Test fun newSessionCanPublishButQueuedOldFramesCannot() {
        val gate = AnalysisGeneration()
        val oldSession = gate.snapshot()
        gate.invalidate {}
        var publications = 0
        assertTrue(gate.publish(gate.snapshot()) { publications++ })
        assertFalse(gate.publish(oldSession) { publications++ })
        assertEquals(1, publications)
    }
}
