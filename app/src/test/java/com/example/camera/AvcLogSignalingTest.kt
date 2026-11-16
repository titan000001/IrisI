package com.example.camera

import org.junit.Assert.*
import org.junit.Test

class AvcLogSignalingTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun pixelSpsBecomesUnspecifiedAndIsIdempotent() {
        val original = bytes("0000000167640032acb403c0113f2cdc0404041e285540")
        val result = AvcLogSignaling.unspecifiedTransfer(original)
        assertFalse(original.contentEquals(result))
        assertArrayEquals(result, AvcLogSignaling.unspecifiedTransfer(result))
        assertArrayEquals(bytes("0000000167640032acb403c0113f2cdc0404041e285540"), original)
    }

    @Test fun malformedAndMissingSpsAreRejected() {
        for (input in listOf(byteArrayOf(), bytes("0000000168ff"), bytes("00000001676400"))) {
            assertThrows(IllegalArgumentException::class.java) { AvcLogSignaling.unspecifiedTransfer(input) }
        }
    }
}
