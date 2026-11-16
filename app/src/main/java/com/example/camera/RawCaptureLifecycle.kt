package com.example.camera

/** Handler-confined ownership state, independent of Camera2 and the file writer. */
internal class RawCaptureLifecycle {
    var capturing = false
        private set
    var writing = false
        private set
    var stopping = false
        private set
    var completed = false
        private set

    fun beginCapture(): Boolean {
        if (stopping || completed || capturing || writing) return false
        capturing = true
        return true
    }

    fun beginWrite(imageTimestamp: Long?, resultTimestamp: Long?): Boolean {
        if (!capturing || stopping || imageTimestamp == null || imageTimestamp != resultTimestamp) return false
        capturing = false
        writing = true
        return true
    }

    fun finishWrite() { writing = false }

    fun stop() {
        stopping = true
        capturing = false
    }

    fun complete(): Boolean {
        if (!stopping || writing || completed) return false
        completed = true
        return true
    }
}
