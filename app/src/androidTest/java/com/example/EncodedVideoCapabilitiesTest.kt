package com.example

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.DynamicRange
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in declarations probe. Does not open a camera, record, or modify user media. */
@RunWith(AndroidJUnit4::class)
class EncodedVideoCapabilitiesTest {
    @OptIn(ExperimentalCamera2Interop::class)
    @Test fun inspectDeclaredCapabilities() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("inspectEncodedCapabilities") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = ProcessCameraProvider.getInstance(context).get(20, TimeUnit.SECONDS)
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameras = JSONArray()
        val ranges = linkedMapOf(
            "SDR" to DynamicRange.SDR,
            "HLG10" to DynamicRange.HLG_10_BIT,
            "HDR10_PQ" to DynamicRange.HDR10_10_BIT,
            "HDR10_PLUS" to DynamicRange.HDR10_PLUS_10_BIT
        )
        for (info in provider.availableCameraInfos) {
            val id = Camera2CameraInfo.from(info).cameraId
            val characteristics = manager.getCameraCharacteristics(id)
            val capabilities = Recorder.getVideoCapabilities(info)
            val profiles = JSONObject()
            for ((name, range) in ranges) {
                profiles.put(name, JSONObject()
                    .put("cameraAdvertised", info.querySupportedDynamicRanges(setOf(range)).contains(range))
                    .put("recorderAdvertised", capabilities.supportedDynamicRanges.contains(range))
                    .put("qualities", JSONArray(capabilities.getSupportedQualities(range).map { it.toString() })))
            }
            cameras.put(JSONObject().put("cameraId", id).put("lensFacing", info.lensFacing)
                .put("profiles", profiles)
                .put("tonemapModes", JSONArray(characteristics.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)?.toList() ?: emptyList<Int>()))
                .put("maxTonemapCurvePoints", characteristics.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS))
                .put("requestCapabilities", JSONArray(characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList() ?: emptyList<Int>())))
        }
        val encoders = JSONArray()
        for (codec in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { it.isEncoder }) {
            for (mime in codec.supportedTypes.filter { it in setOf("video/avc", "video/hevc", "video/av01") }) {
                val row = JSONObject().put("name", codec.name).put("mime", mime)
                if (Build.VERSION.SDK_INT >= 29) {
                    row.put("hardwareAccelerated", codec.isHardwareAccelerated)
                        .put("softwareOnly", codec.isSoftwareOnly).put("alias", codec.isAlias)
                }
                try {
                    val caps = codec.getCapabilitiesForType(mime)
                    row.put("surfaceInput", caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface))
                        .put("colorFormats", JSONArray(caps.colorFormats.toList()))
                        .put("profiles", JSONArray(caps.profileLevels.map {
                            JSONObject().put("profile", it.profile).put("level", it.level)
                        }))
                    val video = requireNotNull(caps.videoCapabilities) { "Encoder has no video capabilities" }
                    val sizes = JSONArray()
                    for ((width, height) in listOf(1920 to 1080, 3840 to 2160)) {
                        val rates = JSONObject()
                        for (fps in listOf(24, 30, 60)) {
                            rates.put(fps.toString(), video.areSizeAndRateSupported(width, height, fps.toDouble()))
                        }
                        sizes.put(JSONObject().put("width", width).put("height", height).put("fps", rates))
                    }
                    row.put("sizes", sizes).put("widthAlignment", video.widthAlignment)
                        .put("heightAlignment", video.heightAlignment).put("bitrateRange", video.bitrateRange.toString())
                } catch (error: Exception) {
                    row.put("queryError", "${error.javaClass.simpleName}: ${error.message}")
                }
                encoders.put(row)
            }
        }
        assertTrue("At least one camera declaration must be inspected", cameras.length() > 0)
        assertTrue("At least one video encoder declaration must be inspected", encoders.length() > 0)
        val report = JSONObject().put("schema", 1).put("model", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT).put("cameras", cameras).put("encoders", encoders)
            .put("limitations", "Declarations only: not proof of camera/encoder concurrency, successful recording, encoded bit depth, LOG, color accuracy, or sustained throughput.")
        File(context.filesDir, "encoded-video-capabilities.json").writeText(report.toString(2))
    }
}
