package com.example.camera

import android.content.Context
import android.content.ContextWrapper
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.HandlerThread
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowCameraCharacteristics
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@LooperMode(LooperMode.Mode.PAUSED)
class RawSequenceRecorderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixtures = mutableListOf<RecorderFixture>()

    @After
    fun releaseWorkersEvenIfAnAssertionFails() {
        fixtures.forEach { fixture ->
            fixture.recorder.stop()
            if (fixture.thread.isAlive) {
                val looper = shadowOf(fixture.looper)
                if (looper.isPaused) looper.unPause()
                fixture.thread.join(5_000)
            }
            // Fallback only: individual tests assert production cleanup before reaching here.
            fixture.writer.shutdownNow()
            if (fixture.thread.isAlive) {
                fixture.thread.quit()
                fixture.thread.join(5_000)
            }
        }
    }

    @Test
    fun stopBeforeQueuedSetupSkipsCameraAndCompletesExactlyOnce() {
        val requests = AtomicInteger()
        val unavailableContext = cameraContext {
            requests.incrementAndGet()
            error("Camera service must not be reached after stop")
        }
        val fixture = recorder(unavailableContext)
        fixture.start()

        repeat(3) { fixture.recorder.stop() }
        fixture.recorder.close()
        fixture.runToCompletion()

        assertEquals(0, requests.get())
        assertEquals(0, fixture.started.get())
        assertEquals(1, fixture.results.size)
        assertEquals(0, fixture.results.single().frames)
        assertEquals(context.filesDir, fixture.results.single().directory)
        assertEquals("RAW sequence did not start", fixture.results.single().error)
        fixture.assertWorkersReleased()

        repeat(3) { fixture.recorder.stop(); fixture.recorder.close() }
        assertEquals(1, fixture.results.size)
    }

    @Test
    fun setupExceptionReportsOriginalErrorAndReleasesWorkers() {
        val fixture = recorder(cameraContext { throw SecurityException("RAW permission denied") })
        fixture.start()
        fixture.runToCompletion()

        assertEquals(0, fixture.started.get())
        assertEquals(1, fixture.results.size)
        assertEquals(0, fixture.results.single().frames)
        assertEquals("RAW permission denied", fixture.results.single().error)
        fixture.assertWorkersReleased()
    }

    @Test
    fun stopRacingWithSetupExceptionDoesNotLoseErrorOrDuplicateCallback() {
        lateinit var fixture: RecorderFixture
        fixture = recorder(cameraContext {
            // Queue stop cleanup before the catch block queues error cleanup.
            fixture.recorder.stop()
            throw IllegalStateException("Setup failed while stopping")
        })
        fixture.start()
        fixture.runToCompletion()

        assertEquals(0, fixture.started.get())
        assertEquals(1, fixture.results.size)
        assertEquals("Setup failed while stopping", fixture.results.single().error)
        fixture.assertWorkersReleased()
    }

    @Test
    fun cameraWithoutRawCapabilityCompletesWithoutStartingOrLeakingWorkers() {
        assertUnsupportedRaw(intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE))
    }

    @Test
    fun cameraWithMissingCapabilitiesCompletesWithoutStartingOrLeakingWorkers() {
        assertUnsupportedRaw(null)
    }

    @Test
    fun repeatedStopWithoutStartStillReleasesWorkers() {
        val fixture = recorder()
        repeat(3) { fixture.recorder.stop() }
        fixture.recorder.close()
        shadowOf(fixture.looper).unPause()
        fixture.assertWorkersReleased()
        assertTrue(fixture.results.isEmpty())
    }

    @Test
    fun startAfterStopWithoutAnyStartIsRejectedAndWorkersStayReleased() {
        val fixture = recorder()
        fixture.recorder.stop()
        shadowOf(fixture.looper).unPause()
        fixture.assertWorkersReleased()

        assertThrows(IllegalStateException::class.java) { fixture.start() }

        assertEquals(0, fixture.started.get())
        assertTrue(fixture.results.isEmpty())
        fixture.assertWorkersReleased()
    }

    private fun assertUnsupportedRaw(capabilities: IntArray?) {
        val characteristics = ShadowCameraCharacteristics.newCameraCharacteristics()
        if (capabilities != null) {
            shadowOf(characteristics).set(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES, capabilities)
        }
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        shadowOf(manager).addCamera("no-raw", characteristics)
        val fixture = recorder()
        fixture.start("no-raw")
        fixture.runToCompletion()

        assertEquals(0, fixture.started.get())
        assertEquals(1, fixture.results.size)
        assertEquals(0, fixture.results.single().frames)
        assertEquals(context.filesDir, fixture.results.single().directory)
        assertEquals("Camera does not support RAW_SENSOR", fixture.results.single().error)
        fixture.recorder.stop()
        fixture.recorder.close()
        assertEquals(1, fixture.results.size)
        fixture.assertWorkersReleased()
    }

    private fun cameraContext(cameraService: () -> Any?): Context = object : ContextWrapper(context) {
        override fun getSystemService(name: String): Any? =
            if (name == Context.CAMERA_SERVICE) cameraService() else super.getSystemService(name)
    }

    private fun recorder(context: Context = this.context): RecorderFixture =
        RecorderFixture(RawSequenceRecorder(context)).also { fixtures += it }

    // Inspect ownership only; all lifecycle transitions are driven through the public API.
    private class RecorderFixture(val recorder: RawSequenceRecorder) {
        val thread: HandlerThread = ReflectionHelpers.getField(recorder, "thread")
        val writer: ExecutorService = ReflectionHelpers.getField(recorder, "writer")
        val looper = thread.looper
        val results = CopyOnWriteArrayList<RawSequenceRecorder.Result>()
        val started = AtomicInteger()
        private val finished = CountDownLatch(1)

        init {
            // PAUSED mode only pauses the main looper by default. Explicitly gate this
            // background looper so stop-before-setup is deterministic, not a timing race.
            shadowOf(looper).pause()
            // Start the otherwise lazy executor, so termination checks cover a real
            // writer thread rather than only an unused ExecutorService's flag.
            writer.submit {}.get(5, TimeUnit.SECONDS)
        }

        fun start(cameraId: String = "unused") {
            recorder.start(cameraId, RawSequenceRecorder.Config(640, 480),
                onStarted = { started.incrementAndGet() },
                onFinished = {
                    results += it
                    // Reentrant stop/close must not cause another completion.
                    recorder.stop()
                    recorder.close()
                    finished.countDown()
                })
        }

        fun runToCompletion() {
            shadowOf(looper).unPause()
            assertTrue("RAW completion callback was not delivered", finished.await(5, TimeUnit.SECONDS))
            // The callback precedes releaseWorkers(); join also observes any duplicate
            // completion queued by competing stop/error paths before quitSafely().
            thread.join(5_000)
        }

        fun assertWorkersReleased() {
            thread.join(5_000)
            assertFalse("RAW handler thread leaked", thread.isAlive)
            assertTrue("RAW writer was not shut down", writer.isShutdown)
            assertTrue("RAW writer did not terminate", writer.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
