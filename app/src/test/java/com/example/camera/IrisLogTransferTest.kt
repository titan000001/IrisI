package com.example.camera

import org.junit.Assert.*
import org.junit.Test

class IrisLogTransferTest {
    @Test fun endpointsMonotonicityAndInverse() {
        assertEquals(0.0, IrisLogTransfer.encode(0.0), 0.0)
        assertEquals(1.0, IrisLogTransfer.encode(1.0), 1e-15)
        var previous = -1.0
        for (i in 0..10000) {
            val x = i / 10000.0
            val encoded = IrisLogTransfer.encode(x)
            assertTrue(encoded > previous)
            assertEquals(x, IrisLogTransfer.decode(encoded), 1e-14)
            previous = encoded
        }
    }

    @Test fun eightAndTenBitFullAndLimitedEndpoints() {
        for (bits in listOf(8, 10)) for (limited in listOf(false, true)) {
            val scale = 1 shl (bits - 8)
            assertEquals(if (limited) 16 * scale else 0, IrisLogTransfer.quantize(0.0, bits, limited))
            assertEquals(if (limited) 235 * scale else (1 shl bits) - 1,
                IrisLogTransfer.quantize(1.0, bits, limited))
            for (i in 0..1000) {
                val y = i / 1000.0
                val low = if (limited) 16 * scale else 0
                val high = if (limited) 235 * scale else (1 shl bits) - 1
                val restored = (IrisLogTransfer.quantize(y, bits, limited) - low).toDouble() / (high - low)
                assertEquals(y, restored, 0.500001 / (high - low))
            }
        }
    }

    @Test fun sampledCurveHasOrderedEndpointsAndBoundedError() {
        val curve = IrisLogTransfer.contrastCurve(64)
        assertEquals(0f, curve.first(), 0f)
        assertEquals(1f, curve.last(), 0f)
        for (i in 1 until 64) {
            val left = curve[(i - 1) * 2].toDouble()
            val right = curve[i * 2].toDouble()
            assertTrue(right > left)
            for (j in 0..10) {
                val t = j / 10.0
                val interpolated = curve[(i - 1) * 2 + 1] * (1 - t) + curve[i * 2 + 1] * t
                assertEquals(IrisLogTransfer.encode(left * (1 - t) + right * t), interpolated, 0.00012)
            }
        }
    }

    @Test fun invalidInputsAreRejectedRatherThanSilentlyClipped() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.01, 1.01)) {
            assertThrows(IllegalArgumentException::class.java) { IrisLogTransfer.encode(value) }
            assertThrows(IllegalArgumentException::class.java) { IrisLogTransfer.decode(value) }
            assertThrows(IllegalArgumentException::class.java) { IrisLogTransfer.quantize(value, 8, false) }
        }
        assertThrows(IllegalArgumentException::class.java) { IrisLogTransfer.quantize(0.5, 12, false) }
        assertThrows(IllegalArgumentException::class.java) { IrisLogTransfer.contrastCurve(1) }
    }
}
