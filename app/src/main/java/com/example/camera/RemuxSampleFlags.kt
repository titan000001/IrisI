package com.example.camera

import android.media.MediaCodec
import android.media.MediaExtractor

/** Extractor encryption/partial bits must never be mistaken for codec config/EOS bits. */
internal object RemuxSampleFlags {
    fun toCodecFlags(sampleFlags: Int): Int {
        require(sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC.inv() == 0) {
            "LOG remux requires complete, unencrypted samples"
        }
        return if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
    }
}
