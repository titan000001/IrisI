package com.example.camera

/** Reads geometry and sample precision from Annex-B SPS, not MediaFormat profile labels. */
data class HevcSequenceInfo(val width: Int, val height: Int, val lumaBits: Int, val chromaBits: Int) {
    companion object {
        fun parseAnnexB(bytes: ByteArray): HevcSequenceInfo {
            val starts = mutableListOf<Pair<Int, Int>>()
            var i = 0
            while (i + 3 <= bytes.size) {
                val prefix = when {
                    i + 4 <= bytes.size && bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte() &&
                        bytes[i + 2] == 0.toByte() && bytes[i + 3] == 1.toByte() -> 4
                    bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte() && bytes[i + 2] == 1.toByte() -> 3
                    else -> 0
                }
                if (prefix > 0) { starts.add(i to prefix); i += prefix } else i++
            }
            for ((index, start) in starts.withIndex()) {
                val offset = start.first + start.second
                val end = starts.getOrNull(index + 1)?.first ?: bytes.size
                if (offset + 2 > end || ((bytes[offset].toInt() and 0x7e) shr 1) != 33) continue
                val rbsp = ArrayList<Byte>()
                var zeros = 0
                for (p in offset + 2 until end) {
                    val b = bytes[p].toInt() and 255
                    if (zeros >= 2 && b == 3) { zeros = 0; continue }
                    rbsp.add(bytes[p])
                    zeros = if (b == 0) zeros + 1 else 0
                }
                val reader = Bits(rbsp.toByteArray())
                reader.read(4) // sps_video_parameter_set_id
                val layers = reader.read(3)
                require(layers <= 6) { "Invalid SPS sublayer count" }
                reader.read(1)
                reader.skip(96) // general profile_tier_level
                val profiles = BooleanArray(layers)
                val levels = BooleanArray(layers)
                for (layer in 0 until layers) {
                    profiles[layer] = reader.read(1) != 0
                    levels[layer] = reader.read(1) != 0
                }
                if (layers > 0) reader.skip((8 - layers) * 2)
                for (layer in 0 until layers) {
                    if (profiles[layer]) reader.skip(88)
                    if (levels[layer]) reader.skip(8)
                }
                reader.ue() // sps_seq_parameter_set_id
                val chroma = reader.ue()
                require(chroma in 0..3)
                val separate = chroma == 3 && reader.read(1) == 1
                val width = reader.ue()
                val height = reader.ue()
                var cropX = 0
                var cropY = 0
                if (reader.read(1) != 0) {
                    val left = reader.ue(); val right = reader.ue()
                    val top = reader.ue(); val bottom = reader.ue()
                    val chromaArray = if (separate) 0 else chroma
                    cropX = Math.multiplyExact(Math.addExact(left, right), if (chromaArray == 1 || chromaArray == 2) 2 else 1)
                    cropY = Math.multiplyExact(Math.addExact(top, bottom), if (chromaArray == 1) 2 else 1)
                }
                val luma = reader.ue() + 8
                val color = reader.ue() + 8
                require(width > cropX && height > cropY && luma in 8..16 && color in 8..16)
                return HevcSequenceInfo(width - cropX, height - cropY, luma, color)
            }
            throw IllegalArgumentException("No Annex-B HEVC SPS found")
        }
    }

    private class Bits(val bytes: ByteArray) {
        var position = 0
        fun read(count: Int): Int {
            require(count in 0..30 && position.toLong() + count <= bytes.size.toLong() * 8) { "Truncated SPS" }
            var value = 0
            repeat(count) {
                value = (value shl 1) or ((bytes[position / 8].toInt() ushr (7 - position % 8)) and 1)
                position++
            }
            return value
        }
        fun skip(count: Int) { repeat(count) { read(1) } }
        fun ue(): Int {
            var zeros = 0
            while (read(1) == 0) { zeros++; require(zeros <= 29) { "Oversized SPS integer" } }
            return ((1 shl zeros) - 1) + read(zeros)
        }
    }
}
