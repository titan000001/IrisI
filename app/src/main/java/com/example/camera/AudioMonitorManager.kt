package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import com.example.model.AudioInputDeviceInfo
import com.example.model.AudioLevels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class AudioMonitorManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _inputDevices = MutableStateFlow<List<AudioInputDeviceInfo>>(emptyList())
    val inputDevices: StateFlow<List<AudioInputDeviceInfo>> = _inputDevices.asStateFlow()

    private val _selectedDeviceId = MutableStateFlow<Int?>(null)
    val selectedDeviceId: StateFlow<Int?> = _selectedDeviceId.asStateFlow()

    private val _headphoneConnected = MutableStateFlow(false)
    val headphoneConnected: StateFlow<Boolean> = _headphoneConnected.asStateFlow()

    private val _monitoringEnabled = MutableStateFlow(false)
    val monitoringEnabled: StateFlow<Boolean> = _monitoringEnabled.asStateFlow()

    private val _inputGainDb = MutableStateFlow(0f)
    val inputGainDb: StateFlow<Float> = _inputGainDb.asStateFlow()

    private val _audioLevels = MutableStateFlow(AudioLevels())
    val audioLevels: StateFlow<AudioLevels> = _audioLevels.asStateFlow()

    private var monitorJob: Job? = null
    private var scope: CoroutineScope? = null
    private var hasPermission = false
    private var callbackRegistered = false
    private var isPausedForRecording = false
    private var captureUnavailable = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            captureUnavailable = false
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }
    }

    fun start(coroutineScope: CoroutineScope, audioPermissionGranted: Boolean) {
        scope = coroutineScope
        val permissionChanged = hasPermission != audioPermissionGranted
        hasPermission = audioPermissionGranted
        if (!callbackRegistered) {
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
            callbackRegistered = true
        }
        refreshDevices()
        if (audioPermissionGranted && (permissionChanged || (monitorJob == null && !captureUnavailable))) {
            restartMeteringLoop()
        } else if (!audioPermissionGranted) {
            stopMeteringLoop()
        }
    }

    fun updatePermission(granted: Boolean) {
        hasPermission = granted
        if (granted) {
            captureUnavailable = false
            refreshDevices()
            restartMeteringLoop()
        } else {
            stopMeteringLoop()
        }
    }

    fun setPausedForVideoRecording(paused: Boolean) {
        if (isPausedForRecording == paused) return
        isPausedForRecording = paused
        if (paused) {
            // Release preview AudioRecord so VideoCapture Recorder has exclusive mic access
            stopMeteringLoop()
        } else if (hasPermission && !captureUnavailable) {
            restartMeteringLoop()
        }
    }

    fun updateLevelsFromRecordingAmplitude(amplitude: Double) {
        if (amplitude <= 0.0) {
            _audioLevels.value = AudioLevels()
            return
        }
        val norm = (amplitude / 32767.0).toFloat().coerceIn(0f, 1f)
        val peakDb = if (norm > 1e-5f) (20f * log10(norm)).coerceIn(-60f, 0f) else -60f
        _audioLevels.value = AudioLevels(
            peakDb = peakDb,
            rmsDb = (peakDb - 3f).coerceIn(-60f, 0f),
            leftNormalized = ((peakDb + 60f) / 60f).coerceIn(0f, 1f),
            rightNormalized = (((peakDb + 60f) / 60f) * 0.96f).coerceIn(0f, 1f),
            clipping = norm >= 0.97f
        )
    }

    fun refreshDevices() {
        val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val mapped = rawInputs.map { device ->
            val typeLabel = when (device.type) {
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB-C External"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired TRRS"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth Mic"
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line In"
                else -> "Internal Mic"
            }
            val isExt = device.type != AudioDeviceInfo.TYPE_BUILTIN_MIC &&
                device.type != AudioDeviceInfo.TYPE_TELEPHONY
            AudioInputDeviceInfo(
                id = device.id,
                name = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeLabel,
                typeLabel = typeLabel,
                isExternal = isExt
            )
        }.distinctBy { it.id }

        _inputDevices.value = mapped

        val currentSel = _selectedDeviceId.value
        if (currentSel == null || mapped.none { it.id == currentSel }) {
            val preferred = mapped.firstOrNull { it.isExternal } ?: mapped.firstOrNull()
            _selectedDeviceId.value = preferred?.id
        }

        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasHeadphones = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        _headphoneConnected.value = hasHeadphones
        if (!hasHeadphones && _monitoringEnabled.value) {
            _monitoringEnabled.value = false
        }
    }

    fun selectInputDevice(deviceId: Int) {
        _selectedDeviceId.value = deviceId
        captureUnavailable = false
        if (hasPermission && !isPausedForRecording) {
            restartMeteringLoop()
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        _monitoringEnabled.value = enabled && _headphoneConnected.value
    }

    fun setInputGainDb(db: Float) {
        _inputGainDb.value = db.coerceIn(-12f, 18f)
    }

    private fun isLikelyEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
    }

    @SuppressLint("MissingPermission")
    private fun restartMeteringLoop() {
        stopMeteringLoop()
        val activeScope = scope ?: return
        if (!hasPermission || isPausedForRecording || captureUnavailable) return

        val hasMicFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        if (!hasMicFeature || rawInputs.isEmpty() || isLikelyEmulator()) {
            // Skip native AudioRecord open on virtual emulators without real audio capture HAL
            return
        }

        monitorJob = activeScope.launch(Dispatchers.IO) {
            val targetId = _selectedDeviceId.value
            val targetDevice = rawInputs.firstOrNull { it.id == targetId } ?: rawInputs.firstOrNull()
            val candidateRates = buildList {
                targetDevice?.sampleRates?.sortedDescending()?.forEach { add(it) }
                add(48000)
                add(44100)
                add(16000)
            }.distinct().filter { it > 0 }

            val channelIn = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT

            var chosenRate = 48000
            var chosenMinBuf = -1
            for (rate in candidateRates) {
                val minBuf = AudioRecord.getMinBufferSize(rate, channelIn, encoding)
                if (minBuf > 0) {
                    chosenRate = rate
                    chosenMinBuf = minBuf
                    break
                }
            }
            if (chosenMinBuf <= 0) {
                captureUnavailable = true
                return@launch
            }

            val supportsUnprocessed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val audioSource = if (supportsUnprocessed) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.MIC
            }

            var record: AudioRecord? = null
            var track: AudioTrack? = null
            try {
                record = AudioRecord(
                    audioSource,
                    chosenRate,
                    channelIn,
                    encoding,
                    chosenMinBuf * 2
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    record = null
                    captureUnavailable = true
                    return@launch
                }

                if (targetDevice != null) {
                    record.setPreferredDevice(targetDevice)
                }

                record.startRecording()
                val shortBuf = ShortArray((chosenMinBuf / 2).coerceAtLeast(256))

                while (isActive) {
                    val read = record.read(shortBuf, 0, shortBuf.size)
                    if (read > 0) {
                        val gainLinear = Math.pow(10.0, (_inputGainDb.value / 20.0)).toFloat()
                        var peak = 0f
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val sample = (shortBuf[i] * gainLinear).coerceIn(-32768f, 32767f)
                            if (gainLinear != 1f) {
                                shortBuf[i] = sample.toInt().toShort()
                            }
                            val norm = abs(sample) / 32768f
                            if (norm > peak) peak = norm
                            sumSq += norm * norm
                        }
                        val rms = sqrt(sumSq / read).toFloat()
                        val peakDb = if (peak > 1e-5f) (20f * log10(peak)).coerceIn(-60f, 0f) else -60f
                        val rmsDb = if (rms > 1e-5f) (20f * log10(rms)).coerceIn(-60f, 0f) else -60f

                        _audioLevels.value = AudioLevels(
                            peakDb = peakDb,
                            rmsDb = rmsDb,
                            leftNormalized = ((peakDb + 60f) / 60f).coerceIn(0f, 1f),
                            rightNormalized = (((peakDb + 60f) / 60f) * 0.96f).coerceIn(0f, 1f),
                            clipping = peak >= 0.97f
                        )

                        if (_monitoringEnabled.value && _headphoneConnected.value) {
                            if (track == null) {
                                track = AudioTrack.Builder()
                                    .setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                            .build()
                                    )
                                    .setAudioFormat(
                                        AudioFormat.Builder()
                                            .setEncoding(encoding)
                                            .setSampleRate(chosenRate)
                                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                            .build()
                                    )
                                    .setTransferMode(AudioTrack.MODE_STREAM)
                                    .setBufferSizeInBytes(chosenMinBuf * 2)
                                    .build()
                                track.play()
                            }
                            track.write(shortBuf, 0, read)
                        } else if (track != null) {
                            track.stop()
                            track.release()
                            track = null
                        }
                    } else {
                        delay(25)
                    }
                }
            } catch (_: Throwable) {
                captureUnavailable = true
            } finally {
                runCatching { record?.stop() }
                runCatching { record?.release() }
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }
    }

    private fun stopMeteringLoop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun release() {
        stopMeteringLoop()
        if (callbackRegistered) {
            runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
            callbackRegistered = false
        }
    }
}
