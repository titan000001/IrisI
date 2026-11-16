package com.example.camera

/** Changes only an existing AVC SPS VUI transfer code to ISO/IEC unspecified (2).
 * It never relabels SDR pixels as LOG; callers must already have applied the curve.
 * Rejects missing VUI/color description instead of guessing offsets or rebuilding an SPS.
 */
object AvcLogSignaling {
    fun unspecifiedTransfer(annexB: ByteArray): ByteArray {
        val starts = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i + 3 <= annexB.size) {
            val prefix = when {
                i + 4 <= annexB.size && annexB[i] == 0.toByte() && annexB[i + 1] == 0.toByte() &&
                    annexB[i + 2] == 0.toByte() && annexB[i + 3] == 1.toByte() -> 4
                annexB[i] == 0.toByte() && annexB[i + 1] == 0.toByte() && annexB[i + 2] == 1.toByte() -> 3
                else -> 0
            }
            if (prefix > 0) { starts.add(i to prefix); i += prefix } else i++
        }
        require(starts.isNotEmpty() && starts.first().first == 0) { "Annex-B SPS required" }
        val output = java.io.ByteArrayOutputStream()
        var changed = false
        starts.forEachIndexed { index, (start, prefix) ->
            val end = starts.getOrNull(index + 1)?.first ?: annexB.size
            val nal = start + prefix
            require(nal < end)
            output.write(annexB, start, prefix)
            if (annexB[nal].toInt() and 31 != 7) output.write(annexB, nal, end - nal)
            else {
                output.write(annexB[nal].toInt())
                val rbsp = java.io.ByteArrayOutputStream()
                var zeros = 0
                for (p in nal + 1 until end) {
                    val b = annexB[p].toInt() and 255
                    if (zeros >= 2 && b == 3) { zeros = 0; continue }
                    rbsp.write(b); zeros = if (b == 0) zeros + 1 else 0
                }
                val bytes = rbsp.toByteArray()
                val bits = Bits(bytes)
                val profile = bits.read(8)
                bits.read(8); bits.read(8); bits.ue()
                if (profile in listOf(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135)) {
                    val chroma = bits.ue(); require(chroma in 0..3)
                    if (chroma == 3) bits.read(1)
                    bits.ue(); bits.ue(); bits.read(1)
                    if (bits.read(1) == 1) repeat(if (chroma == 3) 12 else 8) { list ->
                        if (bits.read(1) == 1) {
                            var last = 8; var next = 8
                            repeat(if (list < 6) 16 else 64) {
                                if (next != 0) next = (last + bits.se() + 256) % 256
                                if (next != 0) last = next
                            }
                        }
                    }
                }
                bits.ue()
                when (val order = bits.ue()) {
                    0 -> bits.ue()
                    1 -> { bits.read(1); bits.se(); bits.se(); val n = bits.ue(); require(n <= 256); repeat(n) { bits.se() } }
                    else -> require(order == 2)
                }
                bits.ue(); bits.read(1); bits.ue(); bits.ue()
                if (bits.read(1) == 0) bits.read(1)
                bits.read(1)
                if (bits.read(1) == 1) repeat(4) { bits.ue() }
                require(bits.read(1) == 1) { "SPS has no VUI" }
                if (bits.read(1) == 1) { if (bits.read(8) == 255) { bits.read(16); bits.read(16) } }
                if (bits.read(1) == 1) bits.read(1)
                require(bits.read(1) == 1) { "SPS has no video signal type" }
                bits.read(3); bits.read(1)
                require(bits.read(1) == 1) { "SPS has no color description" }
                bits.read(8) // Keep primaries and matrix, only transfer is unspecified.
                val position = bits.position
                bits.read(8)
                for (bit in 0..7) {
                    val offset = position + bit
                    val mask = 1 shl (7 - offset % 8)
                    val value = bytes[offset / 8].toInt() and mask.inv()
                    bytes[offset / 8] = (value or (((2 shr (7 - bit)) and 1) * mask)).toByte()
                }
                zeros = 0
                bytes.forEach { byte ->
                    val b = byte.toInt() and 255
                    if (zeros >= 2 && b <= 3) { output.write(3); zeros = 0 }
                    output.write(b); zeros = if (b == 0) zeros + 1 else 0
                }
                changed = true
            }
        }
        require(changed) { "No SPS to update" }
        return output.toByteArray()
    }

    private class Bits(val bytes: ByteArray) {
        var position = 0
        fun read(count: Int): Int {
            require(count in 0..30 && position.toLong() + count <= bytes.size.toLong() * 8) { "Truncated SPS" }
            var value = 0
            repeat(count) { value = (value shl 1) or ((bytes[position / 8].toInt() ushr (7 - position % 8)) and 1); position++ }
            return value
        }
        fun ue(): Int { var z = 0; while (read(1) == 0) { z++; require(z <= 28) }; return (1 shl z) - 1 + read(z) }
        fun se(): Int { val v = ue(); return if (v % 2 == 0) -v / 2 else (v + 1) / 2 }
    }
}
