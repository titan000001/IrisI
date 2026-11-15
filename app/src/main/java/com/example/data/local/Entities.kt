package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "camera_presets")
data class CameraPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val captureMode: String,
    val photoFormat: String,
    val videoCodecFormat: String,
    val logProfile: String,
    val filmPreset: String,
    val videoResolution: String,
    val videoFps: String,
    val videoBitrate: String,
    val stabilizationMode: String,
    val isManualExposure: Boolean,
    val iso: Int,
    val shutterSpeedNs: Long,
    val isAutoWb: Boolean,
    val wbKelvin: Int,
    val lift: Float,
    val gamma: Float,
    val gain: Float,
    val contrast: Float,
    val saturation: Float,
    val temperatureOffset: Float,
    val tintOffset: Float,
    val vignette: Float,
    val grainAmount: Float,
    val grainSize: Float,
    val monitoringLutEnabled: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ui_layouts")
data class UiLayoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val placementsJson: String,
    val fn1Action: String,
    val fn2Action: String,
    val fn3Action: String,
    val volumeUpAction: String,
    val volumeDownAction: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "captured_media_logs")
data class CapturedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uriString: String,
    val displayName: String,
    val isVideo: Boolean,
    val formatBadge: String,
    val resolutionLabel: String,
    val lensLabel: String,
    val iso: Int,
    val shutterLabel: String,
    val logProfile: String,
    val filmPreset: String,
    val stabilizationMode: String,
    val durationMs: Long = 0L,
    val album: String = "Camera Roll",
    val isFavorite: Boolean = false,
    val pairedUriString: String? = null,
    val fileBytes: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
