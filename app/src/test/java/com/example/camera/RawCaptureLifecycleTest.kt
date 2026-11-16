package com.example.camera

import org.junit.Assert.*
import org.junit.Test

class RawCaptureLifecycleTest {
    @Test fun captureIsBoundedAndRequiresMatchingTimestamps() {
        val state = RawCaptureLifecycle()
        assertTrue(state.beginCapture())
        assertFalse(state.beginCapture())
        assertFalse(state.beginWrite(null, null))
        assertFalse(state.beginWrite(12L, 13L))
        assertTrue(state.beginWrite(12L, 12L))
        assertFalse(state.beginWrite(12L, 12L))
        assertFalse(state.beginCapture())
        state.finishWrite()
        assertTrue(state.beginCapture())
    }

    @Test fun stopAbandonsMissingPairAndCompletesExactlyOnce() {
        val state = RawCaptureLifecycle()
        state.beginCapture()
        state.stop()
        assertFalse(state.capturing)
        assertTrue(state.complete())
        assertFalse(state.complete())
        assertFalse(state.beginCapture())
        assertFalse(state.beginWrite(1L, 1L))
    }

    @Test fun stopDrainsWriterBeforeCompletion() {
        val state = RawCaptureLifecycle()
        state.beginCapture()
        state.beginWrite(3L, 3L)
        state.stop()
        state.stop()
        assertFalse(state.complete())
        assertTrue(state.writing)
        state.finishWrite()
        assertTrue(state.complete())
        assertFalse(state.complete())
    }

    @Test fun setupFailureCanCompleteWithoutAFrame() {
        val state = RawCaptureLifecycle()
        assertFalse(state.complete())
        state.stop()
        assertTrue(state.complete())
    }
}
