package com.example.camera

import android.media.MediaCodec
import android.media.MediaExtractor
import org.junit.Assert.*
import org.junit.Test

class RemuxSampleFlagsTest {
    @Test fun completeSamplesPreserveOnlyTheKeyframeMeaning() {
        assertEquals(0, RemuxSampleFlags.toCodecFlags(0))
        assertEquals(MediaCodec.BUFFER_FLAG_KEY_FRAME, RemuxSampleFlags.toCodecFlags(MediaExtractor.SAMPLE_FLAG_SYNC))
    }

    @Test fun encryptedPartialAndUnknownSamplesAreRejectedBeforeMuxing() {
        for (flags in listOf(MediaExtractor.SAMPLE_FLAG_ENCRYPTED, MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME,
            MediaExtractor.SAMPLE_FLAG_SYNC or MediaExtractor.SAMPLE_FLAG_ENCRYPTED,
            MediaExtractor.SAMPLE_FLAG_SYNC or MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME, 8, -1)) {
            assertThrows(IllegalArgumentException::class.java) { RemuxSampleFlags.toCodecFlags(flags) }
        }
    }
}
