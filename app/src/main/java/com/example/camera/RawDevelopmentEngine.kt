package com.example.camera

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Classic II/MM TIFF DNG 1.x preview: uncompressed strips or padded tiles, unsigned 8/10/12/14/16-bit,
 * rectangular 2x2 RGB Bayer. Walks SubIFDs and next links (16 unique IFDs total),
 * selecting the first CFA IFD in depth-first SubIFD-before-next order. The whole
 * reachable graph is checked, including links after the selected image.
 *
 * Only GLOBAL_TAGS inherit along a path; duplicate inherited values must have
 * identical type/count/encoded payload. Raster and crop metadata never inherit.
 * Missing Compression/SamplesPerPixel/Predictor/FillOrder/SampleFormat/CFALayout
 * default to 1; missing BitsPerSample defaults to TIFF's 1 and is then unsupported.
 * Missing RowsPerStrip covers the raster; black defaults to zero at the declared
 * repeat size (default 1x1), white to the bit-depth maximum, orientation to 1,
 * active area/crop to the raster/active area and CFAPlaneColor to RGB.
 * Black repeats/deltas use ActiveArea-relative coordinates; CFA phase remains
 * raster-relative. Demosaic neighborhoods exclude masked pixels outside ActiveArea.
 * Tiles use full TileWidth/TileLength storage even at image edges, with byte-aligned
 * scanlines and MSB-first packed samples (16-bit samples follow TIFF byte order).
 * Optional SHORT LinearizationTable must cover the entire stored sample domain,
 * be nondecreasing, and is applied before black/white normalization. Shorter tables
 * and nonmonotonic response curves are intentionally unsupported.
 *
 * Optional ColorMatrix1 requires AsShotNeutral and explicit illuminant 17 (D50)
 * or 21 (D65). This single-profile, neutral-preserving preview omits DNG profile
 * interpolation/look tables and is not a calibrated color rendering claim.
 * Without ColorMatrix1, camera RGB with optional neutral WB is uncalibrated.
 */
object RawDevelopmentEngine {
    const val MAX_FILE_BYTES = 128L * 1024 * 1024
    private const val MAX_OUTPUT_PIXELS = 12_000_000L
    private const val MAX_IFDS = 16
    private const val MAX_TAG_VALUES = 1_000_000L
    private const val MAX_DECODED_VALUES = 2_000_000L
    private const val HEAP_RESERVE_BYTES = 32L * 1024 * 1024
    // Only these image-global values inherit. CFA, strips, levels and crops describe their own raster.
    private val GLOBAL_TAGS = setOf(274, 50706, 50721, 50728, 50778,
        50723, 50727, 50729, 50722, 50724, 50779, 50964, 50965)
    private val DECODED_TAGS = GLOBAL_TAGS + setOf(256, 257, 258, 259, 262, 266, 273, 277,
        278, 279, 317, 322, 323, 324, 325, 330, 33421, 33422, 339, 50710, 50711, 50712, 50713, 50714,
        50715, 50716, 50717, 50719, 50720, 50829)

    suspend fun decodeFile(file: File, maxPixels: Long): Bitmap = withContext(Dispatchers.IO) {
        if (maxPixels <= 0L) throw IllegalArgumentException("maxPixels must be positive.")
        if (!file.isFile) throw IOException("DNG file does not exist or is not a regular file.")
        val length = file.length()
        if (length < 8L || length > MAX_FILE_BYTES) {
            throw IOException("DNG file must be between 8 bytes and 128 MiB.")
        }
        val runtime = Runtime.getRuntime()
        val availableHeap = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val decodePixelBudget = outputPixelBudget(availableHeap, length)
        val bytes = try {
            FileInputStream(file).use { input ->
                ByteArray(length.toInt()).also { buffer ->
                    var offset = 0
                    while (offset < buffer.size) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer, offset, buffer.size - offset)
                        if (count < 0) throw IOException("DNG ended while it was being read.")
                        offset += count
                    }
                    if (input.read() != -1) throw IOException("DNG grew beyond the 128 MiB input limit while being read.")
                }
            }
        } catch (error: OutOfMemoryError) {
            throw IOException("Not enough memory to read this DNG.", error)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error is IOException) throw error
            throw IOException("Unable to read DNG file.", error)
        }
        if (bytes.size.toLong() != length) throw IOException("DNG file changed while being read.")
        val image = try {
            parse(bytes)
        } catch (error: OutOfMemoryError) {
            throw IOException("Not enough memory to inspect DNG metadata.", error)
        }
        currentCoroutineContext().ensureActive()
        val remainingHeap = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        render(image, minOf(maxPixels, decodePixelBudget, outputPixelBudget(remainingHeap, 0)), currentCoroutineContext())
    }

    /** Conservative headroom for metadata, scanlines and downstream bitmap operations; not a heap reservation. */
    internal fun outputPixelBudget(availableHeap: Long, inputBytes: Long): Long {
        require(inputBytes in 0..MAX_FILE_BYTES)
        if (availableHeap <= HEAP_RESERVE_BYTES || inputBytes >= availableHeap - HEAP_RESERVE_BYTES) {
            throw IOException("Not enough memory to decode this DNG.")
        }
        val pixels = minOf((availableHeap - HEAP_RESERVE_BYTES - inputBytes) / 8L, MAX_OUTPUT_PIXELS)
        if (pixels <= 0L) throw IOException("Not enough memory to decode this DNG.")
        return pixels
    }

    internal data class RawImage(
        val bytes: ByteArray,
        val littleEndian: Boolean,
        val width: Int,
        val height: Int,
        val bits: Int,
        val offsets: LongArray,
        val byteCounts: LongArray,
        val rowsPerStrip: Int,
        val cfa: IntArray,
        val black: DoubleArray,
        val blackRepeatRows: Int,
        val blackRepeatColumns: Int,
        val blackDeltaH: DoubleArray?,
        val blackDeltaV: DoubleArray?,
        val white: Double,
        val orientation: Int,
        val neutral: DoubleArray?,
        val activeLeft: Int,
        val activeTop: Int,
        val activeWidth: Int,
        val activeHeight: Int,
        val cropLeft: Int,
        val cropTop: Int,
        val cropWidth: Int,
        val cropHeight: Int,
        /** Row-normalized transform of white-balanced camera RGB; null means uncalibrated preview. */
        val cameraToLinearSrgb: DoubleArray?,
        /** Zero means strip organization; tile offsets/counts share the bounded arrays above. */
        val tileWidth: Int = 0,
        val tileHeight: Int = 0,
        val linearizationTable: IntArray? = null
    ) {
        val pixelCount: Long get() = width.toLong() * height
    }

    internal fun parse(bytes: ByteArray): RawImage {
        if (bytes.size < 8 || bytes.size.toLong() > MAX_FILE_BYTES) throw IOException("Invalid or oversized TIFF/DNG data.")
        val little = when {
            bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte() -> true
            bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte() -> false
            else -> throw IOException("Not a TIFF/DNG byte order marker.")
        }
        val reader = TiffReader(bytes, little)
        if (reader.u16(2) != 42) throw UnsupportedOperationException("BigTIFF and non-classic TIFF are not supported.")
        val rootOffset = reader.u32(4)
        val inheritedTags = GLOBAL_TAGS
        var selected: Map<Int, TiffValue>? = null
        val visited = HashSet<Long>()
        val active = HashSet<Long>()
        var ifdCount = 0

        fun sameValue(left: TiffValue, right: TiffValue): Boolean =
            left.sameEncodedValue(right)

        fun visit(offset: Long, inherited: Map<Int, TiffValue>) {
            if (offset == 0L) return
            if (!active.add(offset)) throw IOException("Cyclic TIFF IFD graph.")
            if (!visited.add(offset)) throw IOException("TIFF IFD is referenced more than once.")
            if (++ifdCount > MAX_IFDS) throw IOException("TIFF contains more than $MAX_IFDS unique IFDs.")
            val ifd = reader.readIfd(offset)
            val effective = LinkedHashMap(inherited)
            for (tag in inheritedTags) {
                val current = ifd.tags[tag]
                if (current != null) {
                    val previous = inherited[tag]
                    if (previous != null && !sameValue(previous, current)) {
                        throw IOException("Conflicting inherited metadata tag $tag.")
                    }
                    effective[tag] = current
                }
            }
            val candidate = HashMap(ifd.tags)
            effective.forEach { (tag, value) -> candidate.putIfAbsent(tag, value) }
            val photometric = candidate[262]?.firstLongOrNull() ?: -1L
            if (photometric == 32803L && candidate[33422] != null && selected == null) {
                selected = candidate
            }
            val subIfds = ifd.tags[330]
            if (subIfds != null) {
                subIfds.longs().forEach { visit(it, effective) }
            }
            visit(ifd.next, effective)
            active.remove(offset)
        }
        visit(rootOffset, emptyMap())
        val tags = selected ?: throw UnsupportedOperationException("No Bayer CFA RAW image IFD was found.")
        val versionValue = tags[50706]
            ?: throw UnsupportedOperationException("TIFF image has no DNGVersion tag.")
        if (versionValue.type !in setOf(1, 7)) throw IOException("DNGVersion must be encoded as byte data.")
        val dngVersion = versionValue.bytesValue()
        if (dngVersion.size != 4 || dngVersion[0] != 1) throw UnsupportedOperationException("Unsupported DNG version declaration.")
        fun scalar(tag: Int, name: String, default: Long? = null): Long {
            val value = tags[tag] ?: return default ?: throw IOException("DNG is missing required $name tag ($tag).")
            if (value.count != 1) throw IOException("DNG $name tag ($tag) must contain exactly one value.")
            return value.firstLong()
        }
        val width = scalar(256, "ImageWidth").toIntChecked("width")
        val height = scalar(257, "ImageLength").toIntChecked("height")
        val bits = scalar(258, "BitsPerSample", 1).toIntChecked("bits per sample")
        val compression = scalar(259, "Compression", 1)
        val photo = scalar(262, "PhotometricInterpretation")
        val samples = scalar(277, "SamplesPerPixel", 1)
        val predictor = scalar(317, "Predictor", 1)
        val fillOrder = scalar(266, "FillOrder", 1)
        val sampleFormat = scalar(339, "SampleFormat", 1)
        if (compression != 1L) throw UnsupportedOperationException("Only uncompressed DNG (Compression=1) is supported; compression $compression is unsupported.")
        if (photo != 32803L) throw UnsupportedOperationException("Only CFA/RAW_SENSOR photometric data is supported.")
        if (samples != 1L) throw UnsupportedOperationException("Only single-sample CFA DNG is supported.")
        if (predictor != 1L || fillOrder != 1L || sampleFormat != 1L) throw UnsupportedOperationException("TIFF predictor, reversed fill order, and signed/floating samples are unsupported.")
        if (scalar(50711, "CFALayout", 1) != 1L) throw UnsupportedOperationException("Only rectangular CFALayout=1 is supported.")
        if (bits !in setOf(8, 10, 12, 14, 16)) throw UnsupportedOperationException("Unsupported CFA bit depth: $bits.")
        if (width <= 1 || height <= 1 || width.toLong() * height > MAX_FILE_BYTES * 8) throw IOException("DNG dimensions are invalid or exceed the decoder limit.")
        val tiled = listOf(322, 323, 324, 325).any { tags.containsKey(it) }
        if (tiled && listOf(273, 278, 279).any { tags.containsKey(it) }) {
            throw IOException("DNG must not mix strip and tile organization.")
        }
        val tileWidth = if (tiled) scalar(322, "TileWidth").toIntChecked("tile width") else 0
        val tileHeight = if (tiled) scalar(323, "TileLength").toIntChecked("tile height") else 0
        val offsets = tags[if (tiled) 324 else 273]?.longs()
            ?: throw IOException("DNG is missing ${if (tiled) "TileOffsets" else "StripOffsets"}.")
        val counts = tags[if (tiled) 325 else 279]?.longs()
            ?: throw IOException("DNG is missing ${if (tiled) "TileByteCounts" else "StripByteCounts"}.")
        val declaredRows = if (tiled) height.toLong() else scalar(278, "RowsPerStrip", height.toLong())
        if (declaredRows == 0L) throw IOException("RowsPerStrip must be positive.")
        val rows = minOf(declaredRows, height.toLong()).toIntChecked("rows per strip")
        val blocksAcross = if (tiled) (width.toLong() + tileWidth - 1) / tileWidth else 1L
        val blocksDown = if (tiled) (height.toLong() + tileHeight - 1) / tileHeight
            else (height.toLong() + rows - 1) / rows
        val expectedBlocks = blocksAcross * blocksDown
        if (expectedBlocks > MAX_TAG_VALUES || offsets.size.toLong() != expectedBlocks || offsets.size != counts.size) {
            throw IOException("Invalid or incomplete TIFF ${if (tiled) "tile" else "strip"} layout.")
        }
        // Long arithmetic throughout: malicious tile dimensions must not overflow or allocate a tile.
        val rowBytes = ((if (tiled) tileWidth else width).toLong() * bits + 7) / 8
        for (i in offsets.indices) {
            val blockRows = if (tiled) tileHeight else minOf(rows, height - i * rows)
            if (rowBytes > MAX_FILE_BYTES || blockRows.toLong() > MAX_FILE_BYTES / rowBytes ||
                counts[i] < rowBytes * blockRows) throw IOException("DNG block $i is truncated or exceeds the input limit.")
        }
        for (i in offsets.indices) reader.requireRange(offsets[i], counts[i])
        val linearization = tags[50712]?.let { table ->
            if (table.count != (1 shl bits)) {
                throw UnsupportedOperationException("LinearizationTable must cover all ${1 shl bits} stored sample values.")
            }
            val encoded = table.longs()
            val values = IntArray(encoded.size) { encoded[it].toInt() }
            if ((1 until values.size).any { values[it] < values[it - 1] }) {
                throw UnsupportedOperationException("Nonmonotonic LinearizationTable is unsupported.")
            }
            values
        }
        val repeat = tags[33421]?.longs() ?: throw IOException("DNG is missing CFARepeatPatternDim.")
        if (repeat.size != 2 || repeat[0] != 2L || repeat[1] != 2L) throw UnsupportedOperationException("Only 2x2 Bayer CFA patterns are supported.")
        val patternValue = tags[33422] ?: throw IOException("DNG is missing CFAPattern.")
        if (patternValue.type !in setOf(1, 7)) throw UnsupportedOperationException("CFAPattern must be encoded as byte data.")
        val pattern = patternValue.bytesValue()
        if (pattern.size != 4 || pattern.any { it !in 0..2 }) throw UnsupportedOperationException("Unsupported or malformed Bayer CFA pattern.")
        val planeTag = tags[50710]
        if (planeTag != null && planeTag.type != 1) throw IOException("CFAPlaneColor must use BYTE values.")
        val planes = planeTag?.bytesValue() ?: intArrayOf(0, 1, 2)
        if (planes.size != 3 || planes.toSet() != setOf(0, 1, 2)) throw UnsupportedOperationException("Only three-plane RGB CFAPlaneColor mappings are supported.")
        for (index in pattern.indices) {
            if (pattern[index] !in planes.indices) throw IOException("CFAPattern references an unavailable CFAPlaneColor index.")
            pattern[index] = planes[pattern[index]]
        }
        if (pattern.count { it == 0 } != 1 || pattern.count { it == 2 } != 1 || pattern.count { it == 1 } != 2) {
            throw UnsupportedOperationException("Only RGB Bayer CFA patterns are supported.")
        }
        if (pattern[0] != pattern[3] && pattern[1] != pattern[2]) {
            throw UnsupportedOperationException("Bayer CFA must have diagonally opposite green samples.")
        }
        val whiteTag = tags[50717]
        if (whiteTag != null && whiteTag.count != 1) throw IOException("WhiteLevel must contain exactly one value for this single-sample DNG.")
        val white = whiteTag?.firstDouble() ?: ((1L shl bits) - 1).toDouble()
        val maximumLinearValue = if (linearization != null) 65535.0 else ((1L shl bits) - 1).toDouble()
        if (!white.isFinite() || white <= 0.0 || white > maximumLinearValue) throw IOException("Invalid WhiteLevel for the declared sample domain.")
        val blackRepeat = tags[50713]?.longs()
        val repeatRows = blackRepeat?.getOrNull(0)?.toIntNonNegative("BlackLevelRepeatDim rows") ?: 1
        val repeatColumns = blackRepeat?.getOrNull(1)?.toIntNonNegative("BlackLevelRepeatDim columns") ?: 1
        if (blackRepeat != null && blackRepeat.size != 2) throw IOException("BlackLevelRepeatDim must contain two values.")
        if (repeatRows !in 1..16 || repeatColumns !in 1..16) {
            throw UnsupportedOperationException("BlackLevelRepeatDim must be 1..16 by 1..16.")
        }
        val black = tags[50714]?.doubles() ?: DoubleArray(repeatRows * repeatColumns)
        if (black.size != repeatRows * repeatColumns || black.any { !it.isFinite() || it < 0.0 || it >= white }) {
            throw UnsupportedOperationException("BlackLevel must match BlackLevelRepeatDim and contain valid finite values.")
        }
        val orientation = scalar(274, "Orientation", 1).toIntChecked("orientation")
        if (orientation !in 1..8) throw IOException("Invalid TIFF Orientation.")
        val neutral = tags[50728]?.doubles()
        if (neutral != null && neutral.size != 3) throw IOException("AsShotNeutral must contain three values.")
        if (neutral != null && neutral.any { !it.isFinite() || it <= 0.0 }) throw IOException("Invalid AsShotNeutral values.")
        if (neutral != null && (neutral[1] / neutral[0] !in 1e-6..1e6 || neutral[1] / neutral[2] !in 1e-6..1e6)) {
            throw UnsupportedOperationException("AsShotNeutral requires gains outside the supported range.")
        }
        if (listOf(50723, 50727, 50729, 50722, 50724, 50779, 50964, 50965).any { tags.containsKey(it) }) {
            throw UnsupportedOperationException("Additional calibration, analog balance, white XY, dual illuminants and forward matrices are unsupported.")
        }
        val cameraToSrgb = tags[50721]?.let { value ->
            if (neutral == null) throw UnsupportedOperationException("ColorMatrix1 development requires AsShotNeutral.")
            if (value.count != 9) throw IOException("ColorMatrix1 must contain nine values.")
            val illuminant = scalar(50778, "CalibrationIlluminant1")
            if (illuminant != 17L && illuminant != 21L) {
                throw UnsupportedOperationException("ColorMatrix1 is supported only with D50 or D65 CalibrationIlluminant1.")
            }
            val values = value.doubles()
            if (values.any { !it.isFinite() || kotlin.math.abs(it) > 100.0 }) {
                throw IOException("ColorMatrix1 contains invalid coefficients.")
            }
            cameraToSrgb(values, illuminant)
        }
        val activeArea = tags[50829]?.longs()
        val (activeTop, activeLeft, activeBottom, activeRight) = if (activeArea != null) {
            if (activeArea.size != 4) throw IOException("ActiveArea must contain top, left, bottom and right.")
            val top = activeArea[0].toIntNonNegative("active-area top")
            val left = activeArea[1].toIntNonNegative("active-area left")
            val bottom = activeArea[2].toIntNonNegative("active-area bottom")
            val right = activeArea[3].toIntNonNegative("active-area right")
            if (top >= bottom || left >= right || bottom > height || right > width) throw IOException("ActiveArea lies outside the RAW raster.")
            intArrayOf(top, left, bottom, right)
        } else intArrayOf(0, 0, height, width)
        val cropOrigin = tags[50719]?.doubles()?.takeIf { it.size == 2 } ?: doubleArrayOf(0.0, 0.0)
        val cropSize = tags[50720]?.doubles()?.takeIf { it.size == 2 }
            ?: doubleArrayOf((activeRight - activeLeft).toDouble(), (activeBottom - activeTop).toDouble())
        if ((tags.containsKey(50719) && tags[50719]?.count != 2) ||
            (tags.containsKey(50720) && tags[50720]?.count != 2) ||
            cropOrigin.any { !it.isFinite() || it < 0.0 } || cropSize.any { !it.isFinite() || it <= 0.0 }) {
            throw IOException("DefaultCropOrigin or DefaultCropSize is invalid.")
        }
        if ((cropOrigin + cropSize).any { it != kotlin.math.floor(it) }) {
            throw UnsupportedOperationException("Fractional default crop coordinates are not supported.")
        }
        if (cropOrigin[0] + cropSize[0] > activeRight - activeLeft ||
            cropOrigin[1] + cropSize[1] > activeBottom - activeTop) {
            throw IOException("Default crop lies outside the active RAW area.")
        }
        val cropLeft = activeLeft + cropOrigin[0].toInt()
        val cropTop = activeTop + cropOrigin[1].toInt()
        val cropRight = cropLeft + cropSize[0].toInt()
        val cropBottom = cropTop + cropSize[1].toInt()
        val blackDeltaH = tags[50715]?.doubles()
        val blackDeltaV = tags[50716]?.doubles()
        if (blackDeltaH != null && blackDeltaH.size != activeRight - activeLeft ||
            blackDeltaV != null && blackDeltaV.size != activeBottom - activeTop) {
            throw UnsupportedOperationException("BlackLevelDeltaH/V must match the active-area dimensions.")
        }
        if (blackDeltaH?.any { !it.isFinite() } == true || blackDeltaV?.any { !it.isFinite() } == true) {
            throw IOException("BlackLevelDeltaH/V contains a non-finite correction.")
        }
        if (black.any { it >= white }) throw IOException("WhiteLevel must exceed every BlackLevel value.")
        return RawImage(bytes, little, width, height, bits, offsets, counts, rows, pattern, black,
            repeatRows, repeatColumns, blackDeltaH, blackDeltaV, white,
            orientation, neutral, activeLeft, activeTop, activeRight - activeLeft, activeBottom - activeTop,
            cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop, cameraToSrgb,
            tileWidth, tileHeight, linearization)
    }

    /** Read a stored unsigned sample without allocating a full raster, strip or tile. */
    internal fun storedSampleAt(raw: RawImage, x: Int, y: Int): Int {
        require(x in 0 until raw.width && y in 0 until raw.height)
        val tiled = raw.tileWidth > 0
        val storageWidth = if (tiled) raw.tileWidth else raw.width
        val block = if (tiled) {
            val across = (raw.width.toLong() + raw.tileWidth - 1) / raw.tileWidth
            ((y / raw.tileHeight) * across + x / raw.tileWidth).toInt()
        } else y / raw.rowsPerStrip
        val localX = if (tiled) x % raw.tileWidth else x
        val localY = if (tiled) y % raw.tileHeight else y % raw.rowsPerStrip
        val rowBytes = (storageWidth.toLong() * raw.bits + 7) / 8
        val rowStart = raw.offsets[block] + localY.toLong() * rowBytes
        if (raw.bits == 8) return raw.bytes[(rowStart + localX).toInt()].toInt() and 255
        if (raw.bits == 16) {
            val p = (rowStart + localX.toLong() * 2).toInt()
            val a = raw.bytes[p].toInt() and 255
            val b = raw.bytes[p + 1].toInt() and 255
            return if (raw.littleEndian) a or (b shl 8) else (a shl 8) or b
        }
        val startBit = localX.toLong() * raw.bits
        var value = 0
        for (i in 0 until raw.bits) {
            val bit = startBit + i
            value = (value shl 1) or ((raw.bytes[(rowStart + bit / 8).toInt()].toInt() ushr
                (7 - (bit % 8).toInt())) and 1)
        }
        return value
    }

    internal fun linearSampleAt(raw: RawImage, x: Int, y: Int): Int {
        val stored = storedSampleAt(raw, x, y)
        return raw.linearizationTable?.get(stored) ?: stored
    }

    /** DNG black repeats and deltas are relative to ActiveArea, not DefaultCropOrigin. */
    internal fun blackLevelAt(raw: RawImage, x: Int, y: Int): Double {
        val column = x - raw.activeLeft
        val row = y - raw.activeTop
        require(column in 0 until raw.activeWidth && row in 0 until raw.activeHeight)
        val index = (row % raw.blackRepeatRows) * raw.blackRepeatColumns + column % raw.blackRepeatColumns
        val black = raw.black[index] + (raw.blackDeltaH?.get(column) ?: 0.0) + (raw.blackDeltaV?.get(row) ?: 0.0)
        if (!black.isFinite() || black >= raw.white) throw IOException("Black-level corrections exceed WhiteLevel.")
        return black
    }

    internal fun normalizedSample(value: Int, black: Double, white: Double): Float =
        ((value.toDouble() - black) / (white - black)).coerceIn(0.0, 1.0).toFloat()

    internal fun bilinearChannel(
        cfa: IntArray,
        x: Int,
        y: Int,
        channel: Int,
        sampleWidth: Int = Int.MAX_VALUE,
        sampleHeight: Int = Int.MAX_VALUE,
        sampleLeft: Int = 0,
        sampleTop: Int = 0,
        sample: (Int, Int) -> Float
    ): Float {
        require(sampleLeft >= 0 && sampleTop >= 0 && sampleWidth > sampleLeft && sampleHeight > sampleTop)
        val color = cfa[(y and 1) * 2 + (x and 1)]
        if (color == channel) return sample(x, y)
        val radius = if (color == 1 && channel != 1) 1 else 2
        var sum = 0f
        var count = 0
        for (dy in -radius..radius) for (dx in -radius..radius) {
            if (dx == 0 && dy == 0) continue
            val sx = (x + dx).coerceIn(sampleLeft, sampleWidth - 1)
            val sy = (y + dy).coerceIn(sampleTop, sampleHeight - 1)
            if (cfa[(sy and 1) * 2 + (sx and 1)] == channel) {
                sum += sample(sx, sy)
                count++
            }
        }
        if (count == 0) return sample(x, y)
        return sum / count
    }

    private fun render(raw: RawImage, maxPixels: Long, context: CoroutineContext): Bitmap {
        val swaps = raw.orientation in 5..8
        val sourceWidth = if (swaps) raw.cropHeight else raw.cropWidth
        val sourceHeight = if (swaps) raw.cropWidth else raw.cropHeight
        val outputLimit = minOf(maxPixels, MAX_OUTPUT_PIXELS)
        val activePixelCount = raw.cropWidth.toLong() * raw.cropHeight
        var low = maxOf(1, ceil(sqrt(activePixelCount.toDouble() / outputLimit.toDouble())).toInt())
        var high = maxOf(sourceWidth, sourceHeight)
        while (low < high) {
            val mid = low + (high - low) / 2
            val pixels = ceil(sourceWidth.toDouble() / mid).toLong() * ceil(sourceHeight.toDouble() / mid).toLong()
            if (pixels <= outputLimit) high = mid else low = mid + 1
        }
        val scale = low
        val outWidth = ceil(sourceWidth.toDouble() / scale).toInt()
        val outHeight = ceil(sourceHeight.toDouble() / scale).toInt()
        var bitmap: Bitmap? = null
        val whiteBalance = raw.neutral?.let { doubleArrayOf(it[1] / it[0], 1.0, it[1] / it[2]) }
            ?: doubleArrayOf(1.0, 1.0, 1.0)
        val gains = whiteBalance
        val cameraRgb = DoubleArray(3)
        val displayRgb = DoubleArray(3)
        fun rawAt(x0: Int, y0: Int): Float {
            val x = x0.coerceIn(raw.activeLeft, raw.activeLeft + raw.activeWidth - 1)
            val y = y0.coerceIn(raw.activeTop, raw.activeTop + raw.activeHeight - 1)
            val value = linearSampleAt(raw, x, y)
            val black = blackLevelAt(raw, x, y)
            return normalizedSample(value, black, raw.white)
        }
        try {
            val decodedBitmap = try {
                Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            } catch (error: OutOfMemoryError) {
                throw IOException("Not enough memory to allocate the DNG preview.", error)
            }
            bitmap = decodedBitmap
            val row = IntArray(outWidth)
            for (oy in 0 until outHeight) {
                if ((oy and 15) == 0) context.ensureActive()
                for (ox in 0 until outWidth) {
                    if ((ox and 1023) == 0) context.ensureActive()
                    val dx = minOf(ox * scale + scale / 2, sourceWidth - 1)
                    val dy = minOf(oy * scale + scale / 2, sourceHeight - 1)
                    val (cropX, cropY) = sourceCoordinate(dx, dy, raw.cropWidth, raw.cropHeight, raw.orientation)
                    val sx = cropX + raw.cropLeft
                    val sy = cropY + raw.cropTop
                    for (channel in 0..2) {
                        cameraRgb[channel] = bilinearChannel(raw.cfa, sx, sy, channel,
                            raw.activeLeft + raw.activeWidth, raw.activeTop + raw.activeHeight,
                            raw.activeLeft, raw.activeTop, ::rawAt).toDouble() * gains[channel]
                    }
                    if (raw.cameraToLinearSrgb != null) applyColorTransform(raw.cameraToLinearSrgb, cameraRgb, displayRgb)
                    else {
                        displayRgb[0] = cameraRgb[0]
                        displayRgb[1] = cameraRgb[1]
                        displayRgb[2] = cameraRgb[2]
                    }
                    val r = srgb(displayRgb[0].coerceIn(0.0, 1.0).toFloat())
                    val g = srgb(displayRgb[1].coerceIn(0.0, 1.0).toFloat())
                    val b = srgb(displayRgb[2].coerceIn(0.0, 1.0).toFloat())
                    row[ox] = Color.rgb((r * 255f + .5f).toInt(), (g * 255f + .5f).toInt(), (b * 255f + .5f).toInt())
                }
                decodedBitmap.setPixels(row, 0, outWidth, 0, oy, outWidth, 1)
            }
            return decodedBitmap
        } catch (error: Throwable) {
            bitmap?.recycle()
            if (error is OutOfMemoryError) throw IOException("Not enough memory to develop this DNG.", error)
            throw error
        }
    }

    private fun sourceCoordinate(x: Int, y: Int, width: Int, height: Int, orientation: Int): Pair<Int, Int> = when (orientation) {
        2 -> width - 1 - x to y
        3 -> width - 1 - x to height - 1 - y
        4 -> x to height - 1 - y
        5 -> y to x
        6 -> y to height - 1 - x
        7 -> width - 1 - y to height - 1 - x
        8 -> width - 1 - y to x
        else -> x to y
    }

    private fun srgb(linear: Float): Float = if (linear <= 0.0031308f) linear * 12.92f else 1.055f * linear.pow(1f / 2.4f) - 0.055f

    private fun invert3x3(m: DoubleArray): DoubleArray {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]
        val cofactors = doubleArrayOf(
            e * i - f * h, c * h - b * i, b * f - c * e,
            f * g - d * i, a * i - c * g, c * d - a * f,
            d * h - e * g, b * g - a * h, a * e - b * d
        )
        val determinant = a * cofactors[0] + b * cofactors[3] + c * cofactors[6]
        if (!determinant.isFinite() || kotlin.math.abs(determinant) < 1e-12) {
            throw IOException("ColorMatrix1 is singular and cannot be converted to display RGB.")
        }
        return DoubleArray(9) { cofactors[it] / determinant }
    }

    private fun cameraToSrgb(colorMatrix: DoubleArray, illuminant: Long): DoubleArray {
        val cameraToXyz = invert3x3(colorMatrix)
        // ColorMatrix maps XYZ under its declared calibration light into camera space.
        // Restore that light's camera neutral after scene WB, then adapt XYZ to D65.
        // Row normalization preserves a neutral axis; this is a single-profile preview,
        // not DNG SDK rendering or a claim of colorimetric scene reconstruction.
        val white = if (illuminant == 17L) doubleArrayOf(0.96422, 1.0, 0.82521)
            else doubleArrayOf(0.95047, 1.0, 1.08883)
        val calibrationNeutral = DoubleArray(3) { row -> (0..2).sumOf { column -> colorMatrix[row * 3 + column] * white[column] } }
        if (calibrationNeutral.any { !it.isFinite() || it <= 1e-12 }) throw IOException("ColorMatrix1 has an invalid calibration neutral.")
        val adaptation = if (illuminant == 17L) doubleArrayOf(
            0.9555766, -0.0230393, 0.0631636,
            -0.0282895, 1.0099416, 0.0210077,
            0.0122982, -0.0204830, 1.3299098
        ) else doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        val xyzToSrgb = doubleArrayOf(
            3.2404542, -1.5371385, -0.4985314,
            -0.9692660, 1.8760108, 0.0415560,
            0.0556434, -0.2040259, 1.0572252
        )
        val result = DoubleArray(9)
        for (row in 0..2) for (column in 0..2) {
            result[row * 3 + column] = (0..2).sumOf { k ->
                xyzToSrgb[row * 3 + k] * (0..2).sumOf { j -> adaptation[k * 3 + j] * cameraToXyz[j * 3 + column] } * calibrationNeutral[column]
            }
        }
        for (row in 0..2) {
            val rowSum = result[row * 3] + result[row * 3 + 1] + result[row * 3 + 2]
            if (!rowSum.isFinite() || kotlin.math.abs(rowSum) < 1e-12) throw IOException("ColorMatrix1 produces an invalid sRGB transform.")
            for (column in 0..2) result[row * 3 + column] /= rowSum
        }
        return result
    }

    private fun applyColorTransform(transform: DoubleArray, camera: DoubleArray, output: DoubleArray) {
        output[0] = transform[0] * camera[0] + transform[1] * camera[1] + transform[2] * camera[2]
        output[1] = transform[3] * camera[0] + transform[4] * camera[1] + transform[5] * camera[2]
        output[2] = transform[6] * camera[0] + transform[7] * camera[1] + transform[8] * camera[2]
    }

    private class TiffReader(private val data: ByteArray, private val little: Boolean) {
        private var decodedValues = 0L
        fun requireRange(offset: Long, length: Long) {
            if (offset < 0 || length < 0 || offset > data.size || length > data.size - offset) throw IOException("TIFF offset/count is outside the file.")
        }
        fun u16(offset: Long): Int {
            requireRange(offset, 2)
            val a = data[offset.toInt()].toInt() and 255
            val b = data[offset.toInt() + 1].toInt() and 255
            return if (little) a or (b shl 8) else (a shl 8) or b
        }
        fun u32(offset: Long): Long {
            val a = u16(offset).toLong(); val b = u16(offset + 2).toLong()
            return if (little) a or (b shl 16) else (a shl 16) or b
        }
        fun readIfd(offset: Long): Ifd {
            requireRange(offset, 2)
            val count = u16(offset)
            if (count > 4096) throw IOException("TIFF IFD has too many entries.")
            requireRange(offset + 2, count.toLong() * 12 + 4)
            val tags = LinkedHashMap<Int, TiffValue>()
            val seen = HashSet<Int>()
            repeat(count) { index ->
                val entry = offset + 2 + index * 12L
                val tag = u16(entry)
                val type = u16(entry + 2)
                val items = u32(entry + 4)
                if (!seen.add(tag)) throw IOException("TIFF IFD contains duplicate tag $tag.")
                val unit = when (type) { 1, 2, 6, 7 -> 1; 3, 8 -> 2; 4, 9, 11, 13 -> 4; 5, 10, 12 -> 8; else -> null }
                if (unit == null) {
                    if (tag in DECODED_TAGS) throw UnsupportedOperationException("TIFF tag $tag has unsupported field type $type.")
                    return@repeat
                }
                if (tag in DECODED_TAGS) {
                    val allowed = when (tag) {
                        50706, 33422 -> setOf(1, 7)
                         50710 -> setOf(1)
                         50712 -> setOf(3)
                        330 -> setOf(4, 13)
                        50721, 50722, 50723, 50724, 50964, 50965 -> setOf(10)
                        50714, 50719, 50720, 50727, 50728, 50729 -> setOf(3, 4, 5)
                        50715, 50716 -> setOf(10)
                        else -> setOf(3, 4)
                    }
                    if (type !in allowed) throw UnsupportedOperationException("TIFF tag $tag has unsupported field type $type.")
                    if (items == 0L) throw IOException("TIFF tag $tag has no values.")
                    val maximum = when (tag) {
                         273, 279, 324, 325, 50715, 50716 -> MAX_TAG_VALUES
                        330 -> MAX_IFDS.toLong()
                        50712 -> 65536L
                        50714 -> 256L
                        50721, 50722, 50723, 50724, 50964, 50965 -> 9L
                        50706, 33422, 50829 -> 4L
                        50710, 50727, 50728 -> 3L
                        33421, 50713, 50719, 50720, 50729 -> 2L
                        else -> 1L
                    }
                    if (items > maximum) throw IOException("TIFF tag $tag exceeds its supported value count ($maximum).")
                    decodedValues += items
                    if (decodedValues > MAX_DECODED_VALUES) throw IOException("TIFF decoded metadata exceeds the aggregate value limit.")
                }
                if (items > MAX_TAG_VALUES || items * unit > Int.MAX_VALUE) throw IOException("TIFF tag $tag exceeds the value limit.")
                val size = items * unit
                val valueOffset = if (size <= 4) entry + 8 else u32(entry + 8)
                requireRange(valueOffset, size)
                if (tag in DECODED_TAGS) tags[tag] = TiffValue(data, little, type, items.toInt(), valueOffset)
            }
            return Ifd(tags, u32(offset + 2 + count * 12L))
        }
    }

    private data class Ifd(
        val tags: Map<Int, TiffValue>,
        val next: Long
    )

    private data class TiffValue(val data: ByteArray, val little: Boolean, val type: Int, val count: Int, val offset: Long) {
        private fun unsigned16(at: Int): Int {
            val a = data[at].toInt() and 255; val b = data[at + 1].toInt() and 255
            return if (little) a or (b shl 8) else (a shl 8) or b
        }
        private fun unsigned32(at: Int): Long {
            val a = unsigned16(at); val b = unsigned16(at + 2)
            return if (little) a.toLong() or (b.toLong() shl 16) else (a.toLong() shl 16) or b.toLong()
        }
        private fun integerAt(i: Int): Long = when (type) {
            1, 6, 7 -> data[offset.toInt() + i].toLong() and 255
            3, 8 -> unsigned16(offset.toInt() + i * 2).toLong()
            4, 9, 13 -> unsigned32(offset.toInt() + i * 4)
            else -> throw IOException("TIFF value type $type is not an integer.")
        }
        fun longs(): LongArray = LongArray(count) { i -> integerAt(i) }
        fun firstLong(): Long = if (count > 0) integerAt(0) else throw IOException("Empty TIFF value.")
        fun firstLongOrNull(): Long? = if (count == 0) null else firstLong()
        fun doubles(): DoubleArray = DoubleArray(count) { i -> doubleAt(i) }
        private fun doubleAt(i: Int): Double = when (type) {
            5, 10 -> {
                val n = if (type == 10) signed32(offset.toInt() + i * 8) else unsigned32(offset.toInt() + i * 8)
                val d = if (type == 10) signed32(offset.toInt() + i * 8 + 4) else unsigned32(offset.toInt() + i * 8 + 4)
                if (d == 0L) throw IOException("Invalid zero-denominator TIFF rational.")
                n.toDouble() / d
            }
            else -> integerAt(i).toDouble()
        }
        fun firstDouble(): Double = if (count > 0) doubleAt(0) else throw IOException("Empty TIFF value.")
        fun bytesValue(): IntArray = IntArray(count) { data[offset.toInt() + it].toInt() and 255 }
        fun sameEncodedValue(other: TiffValue): Boolean {
            if (type != other.type || count != other.count) return false
            val length = count * when (type) {
            1, 2, 6, 7 -> 1
            3, 8 -> 2
            4, 9, 11, 13 -> 4
            5, 10, 12 -> 8
            else -> 0
            }
            for (i in 0 until length) {
                if (data[offset.toInt() + i] != other.data[other.offset.toInt() + i]) return false
            }
            return true
        }
        private fun signed32(at: Int): Long = unsigned32(at).toInt().toLong()
    }

private fun Long.toIntChecked(label: String): Int = if (this in 1..Int.MAX_VALUE.toLong()) toInt() else throw IOException("Invalid $label value.")

private fun Long.toIntNonNegative(label: String): Int = if (this in 0..Int.MAX_VALUE.toLong()) toInt() else throw IOException("Invalid $label value.")
}

private fun Float.pow(exponent: Float): Float = kotlin.math.exp(kotlin.math.ln(this.toDouble()) * exponent).toFloat()
