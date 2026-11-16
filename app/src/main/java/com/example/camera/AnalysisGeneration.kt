package com.example.camera

/** Serializes reset/publication so a retired session cannot restore stale monitoring data. */
internal class AnalysisGeneration {
    private var generation = 0L

    @Synchronized fun snapshot(): Long = generation

    @Synchronized fun invalidate(onReset: () -> Unit) {
        generation++
        onReset()
    }

    @Synchronized fun publish(expected: Long, onPublish: () -> Unit): Boolean {
        if (expected != generation) return false
        onPublish()
        return true
    }
}
