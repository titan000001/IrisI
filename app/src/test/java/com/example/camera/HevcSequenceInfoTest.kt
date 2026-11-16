package com.example.camera

import org.junit.Assert.*
import org.junit.Test

class HevcSequenceInfoTest {
    private fun sps(depth: Int, layers: Int = 0, crop: Boolean = false): ByteArray {
        val bits = StringBuilder()
        fun put(value: Int, count: Int) { bits.append(value.toString(2).padStart(count, '0')) }
        fun ue(value: Int) {
            val code = (value + 1).toString(2)
            bits.append("0".repeat(code.length - 1)).append(code)
        }
        put(0, 4); put(layers, 3); put(1, 1)
        bits.append("0".repeat(96))
        repeat(layers) { put(1, 1); put(1, 1) }
        if (layers > 0) bits.append("0".repeat((8 - layers) * 2))
        repeat(layers) { bits.append("0".repeat(96)) }
        ue(0); ue(1); ue(1920); ue(1088)
        put(if (crop) 1 else 0, 1)
        if (crop) { ue(0); ue(0); ue(0); ue(4) }
        ue(depth - 8); ue(depth - 8)
        bits.append('1')
        while (bits.length % 8 != 0) bits.append('0')
        val payload = bits.chunked(8).map { it.toInt(2).toByte() }
        val escaped = mutableListOf<Byte>()
        var zeros = 0
        for (b in payload) {
            if (zeros >= 2 && (b.toInt() and 255) <= 3) { escaped.add(3); zeros = 0 }
            escaped.add(b)
            zeros = if (b == 0.toByte()) zeros + 1 else 0
        }
        return byteArrayOf(0, 0, 0, 1, 0x42, 1) + escaped.toByteArray()
    }

    @Test fun parsesEightAndTenBitWithEmulationPrevention() {
        for (depth in listOf(8, 10)) assertEquals(HevcSequenceInfo(1920, 1088, depth, depth),
            HevcSequenceInfo.parseAnnexB(sps(depth)))
    }
    @Test fun skipsSublayerProfilesAndAppliesChromaCropUnits() {
        assertEquals(HevcSequenceInfo(1920, 1080, 10, 10), HevcSequenceInfo.parseAnnexB(sps(10, 2, true)))
    }
    @Test fun rejectsMissingAndTruncatedSps() {
        assertThrows(IllegalArgumentException::class.java) { HevcSequenceInfo.parseAnnexB(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { HevcSequenceInfo.parseAnnexB(sps(10).copyOf(10)) }
    }
}
