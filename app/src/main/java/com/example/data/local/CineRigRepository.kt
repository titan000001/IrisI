package com.example.data.local

import android.content.Context
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "cinerig_prefs")

class CineRigRepository(
    private val context: Context,
    private val dao: CineRigDao
) {
    private val activeLayoutIdKey = longPreferencesKey("active_layout_id")

    val presets: Flow<List<CameraPresetEntity>> = dao.observePresets()

    val layouts: Flow<List<CustomLayoutState>> = dao.observeLayouts().map { list ->
        list.map { it.toDomain() }
    }

    val mediaLogs: Flow<List<CapturedMediaEntity>> = dao.observeMediaLogs()

    val activeLayoutId: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[activeLayoutIdKey] ?: 1L
    }

    suspend fun ensureSeedData() {
        if (dao.countPresets() == 0) {
            defaultPresets().forEach { dao.insertPreset(it) }
        }
        if (dao.countLayouts() == 0) {
            val cinemaId = dao.insertLayout(
                CustomLayoutState(
                    name = "Cinema Rig",
                    placements = CustomLayoutState.defaultPlacements(),
                    fn1Action = AssignableAction.TOGGLE_PEAKING,
                    fn2Action = AssignableAction.TOGGLE_MONITOR_LUT,
                    fn3Action = AssignableAction.CYCLE_STABILIZATION
                ).toEntity()
            )
            dao.insertLayout(
                CustomLayoutState(
                    name = "Run & Gun Action",
                    placements = listOf(
                        WidgetPlacement(HudWidgetId.STABILIZATION_CHIP, HudSlotZone.TOP_BAR, 0),
                        WidgetPlacement(HudWidgetId.FORMAT_BADGE, HudSlotZone.TOP_BAR, 1),
                        WidgetPlacement(HudWidgetId.AUDIO_METER, HudSlotZone.TOP_BAR, 2),
                        WidgetPlacement(HudWidgetId.FN_BUTTON_1, HudSlotZone.RIGHT_DOCK, 0),
                        WidgetPlacement(HudWidgetId.FN_BUTTON_2, HudSlotZone.RIGHT_DOCK, 1),
                        WidgetPlacement(HudWidgetId.HISTOGRAM, HudSlotZone.LEFT_DOCK, 0),
                        WidgetPlacement(HudWidgetId.PEAKING_CHIP, HudSlotZone.HIDDEN, 0),
                        WidgetPlacement(HudWidgetId.MAGNIFIER_CHIP, HudSlotZone.HIDDEN, 1),
                        WidgetPlacement(HudWidgetId.LOG_PROFILE_CHIP, HudSlotZone.HIDDEN, 2),
                        WidgetPlacement(HudWidgetId.NIGHT_BOOST_CHIP, HudSlotZone.HIDDEN, 3),
                        WidgetPlacement(HudWidgetId.GRID_GUIDE_CHIP, HudSlotZone.HIDDEN, 4),
                        WidgetPlacement(HudWidgetId.FN_BUTTON_3, HudSlotZone.HIDDEN, 5)
                    ),
                    fn1Action = AssignableAction.CYCLE_STABILIZATION,
                    fn2Action = AssignableAction.LOCK_AE_AF,
                    fn3Action = AssignableAction.TOGGLE_OPEN_GATE
                ).toEntity()
            )
            dao.insertLayout(
                CustomLayoutState(
                    name = "Pure Minimalist",
                    placements = HudWidgetId.entries.mapIndexed { idx, id ->
                        val zone = when (id) {
                            HudWidgetId.FORMAT_BADGE, HudWidgetId.STABILIZATION_CHIP -> HudSlotZone.TOP_BAR
                            HudWidgetId.FN_BUTTON_1 -> HudSlotZone.RIGHT_DOCK
                            else -> HudSlotZone.HIDDEN
                        }
                        WidgetPlacement(id, zone, idx)
                    },
                    fn1Action = AssignableAction.TOGGLE_HISTOGRAM,
                    fn2Action = AssignableAction.TOGGLE_PEAKING,
                    fn3Action = AssignableAction.OPEN_COLOR_GRADE
                ).toEntity()
            )
            context.dataStore.edit { it[activeLayoutIdKey] = cinemaId }
        }
    }

    suspend fun setActiveLayoutId(id: Long) {
        context.dataStore.edit { it[activeLayoutIdKey] = id }
    }

    suspend fun savePreset(preset: CameraPresetEntity): Long = dao.insertPreset(preset)

    suspend fun deletePreset(id: Long) = dao.deletePreset(id)

    suspend fun saveLayout(layout: CustomLayoutState): Long {
        val id = dao.insertLayout(layout.toEntity())
        setActiveLayoutId(id)
        return id
    }

    suspend fun deleteLayout(id: Long) {
        dao.deleteLayout(id)
        val remaining = dao.observeLayouts().first()
        if (remaining.isNotEmpty()) {
            setActiveLayoutId(remaining.first().id)
        }
    }

    suspend fun logCapturedMedia(entity: CapturedMediaEntity): Long = dao.insertMediaLog(entity)

    suspend fun setMediaFavorite(id: Long, favorite: Boolean) = dao.setMediaFavorite(id, favorite)

    suspend fun deleteMediaLog(id: Long) = dao.deleteMediaLog(id)

    /**
     * Discover IrisI-owned outputs that predate the capture log (or whose log was removed).
     * Intentionally limited to IrisI output folders so no broad media permission is needed.
     * Negative IDs distinguish these MediaStore rows from Room log IDs in the existing UI.
     */
    suspend fun discoverOwnedMedia(): List<CapturedMediaEntity> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext emptyList()
        val resolver = context.contentResolver
        val found = mutableListOf<CapturedMediaEntity>()
        val collections = listOf(
            Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, "DCIM/IrisI/"),
            Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, "Movies/IrisI/")
        )
        for ((collection, isVideo, relativePath) in collections) {
            val columns = buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                add(MediaStore.MediaColumns.DATE_ADDED)
                add(MediaStore.MediaColumns.SIZE)
                if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
            }
            val trashedSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                " AND ${MediaStore.MediaColumns.IS_TRASHED} = 0"
            } else ""
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
                "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND " +
                "${MediaStore.MediaColumns.IS_PENDING} = 0$trashedSelection"
            val cursor = resolver.query(collection, columns.toTypedArray(), selection, arrayOf(relativePath, context.packageName), null)
                ?: throw IllegalStateException("MediaStore did not return a media cursor")
            cursor.use {
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val durationCol = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol).orEmpty()
                    val uri = ContentUris.withAppendedId(collection, mediaId)
                    val ext = name.substringAfterLast('.', "").uppercase()
                    found += CapturedMediaEntity(
                        id = if (isVideo) Long.MIN_VALUE + mediaId.coerceAtLeast(1L) else -mediaId.coerceAtLeast(1L),
                        uriString = uri.toString(),
                        displayName = name,
                        isVideo = isVideo,
                        formatBadge = if (isVideo) "MP4" else if (ext == "DNG") "DNG" else "JPG",
                        resolutionLabel = "Local media",
                        lensLabel = "—",
                        iso = 0,
                        shutterLabel = "—",
                        logProfile = "—",
                        filmPreset = "—",
                        stabilizationMode = "—",
                        durationMs = if (durationCol >= 0 && !cursor.isNull(durationCol)) cursor.getLong(durationCol) else 0L,
                        fileBytes = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol),
                        timestamp = cursor.getLong(dateCol) * 1000L
                    )
                }
            }
        }
        found
    }

    suspend fun deleteOwnedMedia(uriString: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (uriString.startsWith("file:")) {
                val file = java.io.File(android.net.Uri.parse(uriString).path ?: return@runCatching false).canonicalFile
                val root = java.io.File(context.filesDir, "raw_sequences").canonicalFile
                val allowed = file.path.startsWith(root.path + java.io.File.separator) &&
                    (file.isDirectory || file.extension.equals("zip", ignoreCase = true)) &&
                    file.deleteRecursively()
                if (allowed && file.extension.equals("zip", ignoreCase = true)) {
                    java.io.File(file.parentFile, file.nameWithoutExtension).deleteRecursively()
                }
                return@runCatching allowed
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runCatching false
            val uri = android.net.Uri.parse(uriString)
            val isImage = uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())
            val isVideo = uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())
            if (!isImage && !isVideo) return@runCatching false
            val expectedPath = if (isVideo) "Movies/IrisI/" else "DCIM/IrisI/"
            val owned = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
                null, null, null
            )?.use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == expectedPath &&
                    cursor.getString(1) == context.packageName
            } == true
            owned && context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    private fun CustomLayoutState.toEntity(): UiLayoutEntity {
        val array = JSONArray()
        placements.forEach { p ->
            array.put(
                JSONObject().apply {
                    put("id", p.widgetId.name)
                    put("zone", p.zone.name)
                    put("order", p.order)
                }
            )
        }
        return UiLayoutEntity(
            id = id,
            name = name,
            placementsJson = array.toString(),
            fn1Action = fn1Action.name,
            fn2Action = fn2Action.name,
            fn3Action = fn3Action.name,
            volumeUpAction = volumeUpAction.name,
            volumeDownAction = volumeDownAction.name,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun UiLayoutEntity.toDomain(): CustomLayoutState {
        val parsedPlacements = mutableListOf<WidgetPlacement>()
        runCatching {
            val array = JSONArray(placementsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val widgetId = HudWidgetId.valueOf(obj.getString("id"))
                val zone = HudSlotZone.valueOf(obj.getString("zone"))
                val order = obj.getInt("order")
                parsedPlacements.add(WidgetPlacement(widgetId, zone, order))
            }
        }
        val missing = HudWidgetId.entries.filter { id -> parsedPlacements.none { it.widgetId == id } }
        missing.forEachIndexed { idx, id ->
            parsedPlacements.add(WidgetPlacement(id, HudSlotZone.HIDDEN, parsedPlacements.size + idx))
        }
        return CustomLayoutState(
            id = id,
            name = name,
            placements = parsedPlacements,
            fn1Action = enumValueOrDefault(fn1Action, AssignableAction.TOGGLE_PEAKING),
            fn2Action = enumValueOrDefault(fn2Action, AssignableAction.TOGGLE_MONITOR_LUT),
            fn3Action = enumValueOrDefault(fn3Action, AssignableAction.CYCLE_STABILIZATION),
            volumeUpAction = enumValueOrDefault(volumeUpAction, AssignableAction.CAPTURE_TRIGGER),
            volumeDownAction = enumValueOrDefault(volumeDownAction, AssignableAction.TOGGLE_MAGNIFIER)
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private fun defaultPresets(): List<CameraPresetEntity> = listOf(
        CameraPresetEntity(
            name = "Cinema 24p S-Log3",
            description = "180° shutter, S-Log3 flat profile with Rec.709 monitor LUT",
            captureMode = CaptureMode.VIDEO.name,
            photoFormat = PhotoFormat.RAW_PLUS_JPEG.name,
            videoCodecFormat = VideoCodecFormat.HEVC_LOG.name,
            logProfile = LogProfile.SLOG3.name,
            filmPreset = FilmPreset.NONE.name,
            videoResolution = VideoResolution.UHD_4K.name,
            videoFps = VideoFps.FPS_24.name,
            videoBitrate = VideoBitrate.MBPS_150.name,
            stabilizationMode = StabilizationMode.ENHANCED.name,
            isManualExposure = true,
            iso = 800,
            shutterSpeedNs = 20_833_333L,
            isAutoWb = false,
            wbKelvin = 5600,
            lift = 0f,
            gamma = 0f,
            gain = 1f,
            contrast = 1.0f,
            saturation = 1.0f,
            temperatureOffset = 0f,
            tintOffset = 0f,
            vignette = 0.1f,
            grainAmount = 0f,
            grainSize = 1.0f,
            monitoringLutEnabled = true
        ),
        CameraPresetEntity(
            name = "Portra 400 Daylight",
            description = "Warm analog portrait rendering with subtle fine grain & DNG",
            captureMode = CaptureMode.PHOTO.name,
            photoFormat = PhotoFormat.RAW_PLUS_JPEG.name,
            videoCodecFormat = VideoCodecFormat.H264_STANDARD.name,
            logProfile = LogProfile.REC_709.name,
            filmPreset = FilmPreset.PORTRA_400.name,
            videoResolution = VideoResolution.UHD_4K.name,
            videoFps = VideoFps.FPS_24.name,
            videoBitrate = VideoBitrate.MBPS_100.name,
            stabilizationMode = StabilizationMode.STANDARD.name,
            isManualExposure = false,
            iso = 400,
            shutterSpeedNs = 8_000_000L,
            isAutoWb = false,
            wbKelvin = 5500,
            lift = 0.02f,
            gamma = 0.04f,
            gain = 1.02f,
            contrast = 0.96f,
            saturation = 0.98f,
            temperatureOffset = 0.12f,
            tintOffset = 0.03f,
            vignette = 0.18f,
            grainAmount = 0.25f,
            grainSize = 1.1f,
            monitoringLutEnabled = true
        ),
        CameraPresetEntity(
            name = "CineStill 800T Night",
            description = "Tungsten low-light film emulation with cool shadows",
            captureMode = CaptureMode.NIGHT.name,
            photoFormat = PhotoFormat.RAW_PLUS_JPEG.name,
            videoCodecFormat = VideoCodecFormat.HEVC_LOG.name,
            logProfile = LogProfile.CLOG2.name,
            filmPreset = FilmPreset.CINESTILL_800T.name,
            videoResolution = VideoResolution.UHD_4K.name,
            videoFps = VideoFps.FPS_24.name,
            videoBitrate = VideoBitrate.MBPS_100.name,
            stabilizationMode = StabilizationMode.ENHANCED.name,
            isManualExposure = true,
            iso = 1600,
            shutterSpeedNs = 40_000_000L,
            isAutoWb = false,
            wbKelvin = 3400,
            lift = -0.02f,
            gamma = 0.03f,
            gain = 1.05f,
            contrast = 1.10f,
            saturation = 1.06f,
            temperatureOffset = -0.15f,
            tintOffset = -0.05f,
            vignette = 0.28f,
            grainAmount = 0.38f,
            grainSize = 1.4f,
            monitoringLutEnabled = true
        ),
        CameraPresetEntity(
            name = "Action Open Gate 60p",
            description = "Full 4:3 sensor readout with Action stabilization & fast shutter",
            captureMode = CaptureMode.VIDEO.name,
            photoFormat = PhotoFormat.JPEG.name,
            videoCodecFormat = VideoCodecFormat.HLG_10BIT.name,
            logProfile = LogProfile.VLOG.name,
            filmPreset = FilmPreset.TEAL_ORANGE.name,
            videoResolution = VideoResolution.OPEN_GATE_4_3.name,
            videoFps = VideoFps.FPS_60.name,
            videoBitrate = VideoBitrate.MBPS_200.name,
            stabilizationMode = StabilizationMode.ACTION.name,
            isManualExposure = true,
            iso = 400,
            shutterSpeedNs = 4_166_666L,
            isAutoWb = true,
            wbKelvin = 5600,
            lift = 0f,
            gamma = 0f,
            gain = 1.0f,
            contrast = 1.08f,
            saturation = 1.05f,
            temperatureOffset = 0.04f,
            tintOffset = 0f,
            vignette = 0.08f,
            grainAmount = 0.12f,
            grainSize = 1.0f,
            monitoringLutEnabled = true
        )
    )
}
