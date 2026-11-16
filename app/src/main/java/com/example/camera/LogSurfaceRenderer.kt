package com.example.camera

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Single-thread-owned 8-bit encoder renderer. Never closes the caller's output Surface.
 * Camera input is ISP SDR RGB, not RAW. All rendering and release calls stay on its EGL thread.
 * No 10-bit claim: this intentionally requests an RGBA8888 EGL config.
 */
class LogSurfaceRenderer(output: Surface, private val width: Int, private val height: Int) : AutoCloseable {
    private val owner = Thread.currentThread()
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var texture = 0
    private var sourceTexture: SurfaceTexture? = null
    private var sourceSurface: Surface? = null
    private var closed = false
    private val transform = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val vertices = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    val inputTexture: SurfaceTexture get() = requireNotNull(sourceTexture)
    val inputSurface: Surface get() = requireNotNull(sourceSurface)

    init {
        require(width > 0 && height > 0)
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY)
            val versions = IntArray(2)
            check(EGL14.eglInitialize(display, versions, 0, versions, 1))
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            ), 0, configs, 0, 1, count, 0) && count[0] > 0)
            val config = requireNotNull(configs[0])
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT)
            window = EGL14.eglCreateWindowSurface(display, config, output, intArrayOf(EGL14.EGL_NONE), 0)
            check(window != EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display, window, window, context))
            program = createProgram()
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            texture = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            sourceTexture = SurfaceTexture(texture).apply { setDefaultBufferSize(width, height) }
            sourceSurface = Surface(sourceTexture)
            checkGl()
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    /** Deterministic linear-light neutral ramp for encoded-output validation. */
    fun drawLinearRamp(timestampNs: Long) = draw(timestampNs, ramp = true)

    /** Latches and transforms an ISP SDR camera buffer. Camera dataspace must be verified separately. */
    fun drawCameraFrame(adjustTransform: ((FloatArray) -> FloatArray)? = null) {
        checkThread()
        inputTexture.updateTexImage()
        inputTexture.getTransformMatrix(transform)
        adjustTransform?.invoke(transform)?.copyInto(transform)
        draw(inputTexture.timestamp, ramp = false)
    }

    private fun draw(timestampNs: Long, ramp: Boolean) {
        checkThread()
        require(timestampNs >= 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "source"), 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "ramp"), if (ramp) 1 else 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "transform"), 1, false, transform, 0)
        val position = GLES20.glGetAttribLocation(program, "position")
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        checkGl()
        check(EGLExt.eglPresentationTimeANDROID(display, window, timestampNs))
        check(EGL14.eglSwapBuffers(display, window)) { "Encoder surface swap failed" }
    }

    private fun createProgram(): Int {
        fun shader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            try {
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val status = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
                check(status[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
                return shader
            } catch (error: Throwable) { GLES20.glDeleteShader(shader); throw error }
        }
        val vertex = shader(GLES20.GL_VERTEX_SHADER, VERTEX)
        var fragment = 0
        val result = GLES20.glCreateProgram()
        try {
            fragment = shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
            GLES20.glAttachShader(result, vertex)
            GLES20.glAttachShader(result, fragment)
            GLES20.glLinkProgram(result)
            val status = IntArray(1)
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { GLES20.glGetProgramInfoLog(result) }
            return result
        } catch (error: Throwable) { GLES20.glDeleteProgram(result); throw error }
        finally { GLES20.glDeleteShader(vertex); if (fragment != 0) GLES20.glDeleteShader(fragment) }
    }

    private fun checkThread() {
        check(Thread.currentThread() === owner) { "EGL ownership violation" }
        check(!closed)
    }

    private fun checkGl() { check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "LOG renderer GL failure" } }

    override fun close() {
        check(Thread.currentThread() === owner) { "EGL release must run on owner thread" }
        if (closed) return
        closed = true
        sourceSurface?.release(); sourceSurface = null
        sourceTexture?.release(); sourceTexture = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (context != EGL14.EGL_NO_CONTEXT && window != EGL14.EGL_NO_SURFACE &&
                EGL14.eglMakeCurrent(display, window, window, context)) {
                if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
                if (program != 0) GLES20.glDeleteProgram(program)
            }
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY; context = EGL14.EGL_NO_CONTEXT; window = EGL14.EGL_NO_SURFACE
    }

    companion object {
        private const val VERTEX = """
            attribute vec2 position;
            uniform mat4 transform;
            varying vec2 uv;
            varying vec2 cameraUv;
            void main() {
                gl_Position = vec4(position, 0.0, 1.0);
                uv = position * 0.5 + 0.5;
                cameraUv = (transform * vec4(uv, 0.0, 1.0)).xy;
            }
        """
        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            uniform samplerExternalOES source;
            uniform bool ramp;
            varying vec2 uv;
            varying vec2 cameraUv;
            vec3 inverse709(vec3 signal) {
                vec3 low = signal / 4.5;
                vec3 high = pow((signal + 0.099) / 1.099, vec3(1.0 / 0.45));
                return mix(low, high, step(vec3(0.081), signal));
            }
            void main() {
                vec3 linear = ramp ? vec3(uv.x) : inverse709(texture2D(source, cameraUv).rgb);
                vec3 encoded = log(vec3(1.0) + 31.0 * clamp(linear, 0.0, 1.0)) / log(32.0);
                gl_FragColor = vec4(encoded, 1.0);
            }
        """
    }
}
