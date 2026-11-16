package com.example.camera

import kotlin.math.expm1
import kotlin.math.ln1p
import kotlin.math.roundToInt

/** Independent IrisI LOG v1, normalized linear RGB in [0,1], not a vendor LOG. */
object IrisLogTransfer {
    const val ID = "irisi-log-v1"
    private const val SHAPE = 31.0
    private val normalization = ln1p(SHAPE)

    fun encode(linear: Double): Double {
        require(linear.isFinite() && linear in 0.0..1.0)
        return ln1p(SHAPE * linear) / normalization
    }

    fun decode(encoded: Double): Double {
        require(encoded.isFinite() && encoded in 0.0..1.0)
        return expm1(encoded * normalization) / SHAPE
    }

    /** RGB signal quantization, not YUV packing. No intermediate 8-bit truncation. */
    fun quantize(encoded: Double, bits: Int, limited: Boolean): Int {
        require(bits == 8 || bits == 10)
        require(encoded.isFinite() && encoded in 0.0..1.0)
        val scale = 1 shl (bits - 8)
        val black = if (limited) 16 * scale else 0
        val white = if (limited) 235 * scale else (1 shl bits) - 1
        return (black + encoded * (white - black)).roundToInt()
    }

    /** Piecewise-linear ISP experiment; response must be confirmed in CaptureResult. */
    fun contrastCurve(points: Int): FloatArray {
        require(points in 2..4096)
        return FloatArray(points * 2).also { curve ->
            for (i in 0 until points) {
                // More control points near black, where curvature is greatest.
                val encoded = i.toDouble() / (points - 1)
                curve[2 * i] = decode(encoded).toFloat()
                curve[2 * i + 1] = encoded.toFloat()
            }
        }
    }
}
