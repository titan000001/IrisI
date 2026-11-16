package com.example.camera

import android.os.Handler
import android.os.HandlerThread
import androidx.camera.core.CameraEffect
import androidx.camera.core.ProcessingException
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import java.util.concurrent.Executor

/** Public CameraX effect API: only recorder frames get LOG, not preview or analysis.
 * Recorder retains ownership of audio, timestamps, EOS and lifecycle capture.
 */
class LogVideoEffect(private val onError: (Throwable) -> Unit) {
    private val thread = HandlerThread("IrisILogEffect").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { command -> check(handler.post(command)) }
    private var input: SurfaceRequest? = null
    private var output: SurfaceOutput? = null
    private var renderer: LogSurfaceRenderer? = null
    private var inputReleased = true
    private var outputReleased = true
    private var retired = false
    private var failed = false

    private fun fail(error: Throwable) {
        if (failed) return
        failed = true
        input?.invalidate()
        onError(error)
    }

    private fun connect() {
        val request = input ?: return
        val target = output ?: return
        if (renderer != null || retired || failed) return
        try {
            check(request.dynamicRange.bitDepth == 8) { "8-bit LOG effect cannot accept HDR/10-bit input" }
            val surface = target.getSurface(executor) {
                outputReleased = true
                cleanup()
            }
            val gpu = LogSurfaceRenderer(surface, target.size.width, target.size.height)
            renderer = gpu
            gpu.inputTexture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            gpu.inputTexture.setOnFrameAvailableListener({
                if (!retired && !failed && !inputReleased && !outputReleased) {
                    try {
                        gpu.drawCameraFrame { original -> FloatArray(16).also { target.updateTransformMatrix(it, original) } }
                    } catch (error: Throwable) { fail(error) }
                }
            }, handler)
            inputReleased = false
            request.provideSurface(gpu.inputSurface, executor) {
                inputReleased = true
                gpu.inputTexture.setOnFrameAvailableListener(null)
                cleanup()
            }
        } catch (error: Throwable) {
            request.willNotProvideSurface()
            fail(error)
            throw ProcessingException().apply { initCause(error) }
        }
    }

    private fun cleanup() {
        if (inputReleased && outputReleased) {
            renderer?.close(); renderer = null
            output?.close(); output = null
            input = null
            if (retired) thread.quitSafely()
        } else if (outputReleased) {
            // Stop drawing immediately and acknowledge the output independently.
            output?.close()
        }
    }

    private val processor = object : SurfaceProcessor {
        override fun onInputSurface(request: SurfaceRequest) {
            if (retired || failed) { request.willNotProvideSurface(); return }
            if (input != null) { request.willNotProvideSurface(); fail(IllegalStateException("Overlapping LOG input surfaces")); return }
            input = request
            connect()
        }
        override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
            if (retired || failed) { surfaceOutput.close(); return }
            if (output != null) { surfaceOutput.close(); fail(IllegalStateException("Overlapping LOG output surfaces")); return }
            output = surfaceOutput
            outputReleased = false
            connect()
        }
    }
    val effect: CameraEffect = object : CameraEffect(VIDEO_CAPTURE, executor, processor, { fail(it) }) {}

    /** Call after unbinding; resources are retained until CameraX releases both sides. */
    fun retire() { executor.execute {
        retired = true
        if (renderer == null) {
            input?.willNotProvideSurface(); input = null
            output?.close(); output = null
            outputReleased = true
        } else input?.invalidate()
        cleanup()
    } }
}
