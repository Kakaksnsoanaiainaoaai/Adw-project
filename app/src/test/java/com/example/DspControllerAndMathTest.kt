package com.example

import com.example.audio.AudioEngineBridge
import com.example.audio.AudioEngineState
import com.example.audio.AudioTelemetry
import com.example.dsp.DspController
import com.example.dsp.DspParameters
import com.example.dsp.EqBandState
import com.example.dsp.EqFilterShape
import com.example.dsp.EqResponseCalculator
import com.example.dsp.FactoryPresets
import com.example.dsp.XoverFilterType
import com.example.dsp.XoverSlopeDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DspControllerAndMathTest {

    @Test
    fun linkwitzRiley24And48HpfAreMinus6DbAtCutoff() {
        val lr24 = DspParameters(
            bypassDsp = false,
            eqBypass = true,
            xoverBypass = false,
            hpfEnabled = true,
            hpfFreqHz = 100f,
            hpfFilterType = XoverFilterType.LINKWITZ_RILEY,
            hpfSlopeDb = XoverSlopeDb.SLOPE_24,
            lpfEnabled = false
        )
        assertEquals(-6.02f, EqResponseCalculator.evaluateMagnitudeDb(100.0, 48000.0, lr24), 0.25f)

        val lr48 = lr24.copy(hpfSlopeDb = XoverSlopeDb.SLOPE_48)
        assertEquals(-6.02f, EqResponseCalculator.evaluateMagnitudeDb(100.0, 48000.0, lr48), 0.30f)
        // 48 dB/oct one octave below cutoff (50 Hz) should be ~ -48 dB (clamped at -48 dB in plot)
        val at50Hz = EqResponseCalculator.evaluateMagnitudeDb(50.0, 48000.0, lr48)
        assertTrue("Expected steep LR48 rolloff <= -40 dB at 50 Hz, got $at50Hz", at50Hz <= -40.0f)
    }

    @Test
    fun peakingAndShelfEqShapesProduceExpectedGain() {
        val params = DspParameters(
            bypassDsp = false,
            eqBypass = false,
            xoverBypass = true,
            eqBands = listOf(
                EqBandState(0, "B1", true, EqFilterShape.LOW_SHELF, 80f, 6.0f, 0.8f),
                EqBandState(1, "B2", true, EqFilterShape.PEAK, 1000f, -6.0f, 2.0f),
                EqBandState(2, "B3", false, EqFilterShape.PEAK, 800f, 0f, 1.2f),
                EqBandState(3, "B4", false, EqFilterShape.PEAK, 2800f, 0f, 1.2f),
                EqBandState(4, "B5", true, EqFilterShape.HIGH_SHELF, 8000f, 4.0f, 0.8f)
            )
        )
        assertEquals(6.0f, EqResponseCalculator.evaluateMagnitudeDb(20.0, 48000.0, params), 0.4f)
        assertEquals(-6.0f, EqResponseCalculator.evaluateMagnitudeDb(1000.0, 48000.0, params), 0.2f)
        assertEquals(4.0f, EqResponseCalculator.evaluateMagnitudeDb(18000.0, 48000.0, params), 0.5f)
    }

    @Test
    fun bypassDspProducesFlatResponse() {
        val params = FactoryPresets.all[3].parameters.copy(bypassDsp = true)
        val mag = EqResponseCalculator.evaluateMagnitudeDb(100.0, 48000.0, params)
        assertEquals(0.0f, mag, 0.001f)
    }

    @Test
    fun dspControllerPropagatesV2StagesToBridge() {
        val fakeBridge = RecordingAudioEngineBridge()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = DspController(fakeBridge, scope)

        val preset = FactoryPresets.all[3].parameters // SUB 35-100 LR48
        controller.startAudioEngine(
            inputDeviceId = 0,
            outputDeviceId = 0,
            requestedSampleRate = 48000,
            requestedBufferFrames = 192,
            hasRecordPermission = true,
            parameters = preset
        )

        assertTrue(fakeBridge.startCalled)
        assertEquals(5, fakeBridge.eqBandUpdates)
        assertEquals(48, fakeBridge.lastLpfSlopeDb)
        assertEquals(preset.hpfFreqHz, fakeBridge.lastHpfFreqHz, 0.01f)
        assertEquals(preset.limiterThresholdDb, fakeBridge.lastLimiterThreshDb, 0.01f)

        controller.stopAudioEngine()
        assertTrue(fakeBridge.stopCalled)
        scope.cancel()
    }

    private class RecordingAudioEngineBridge : AudioEngineBridge {
        override val isNativeLibraryLoaded: Boolean = true
        var startCalled = false
        var stopCalled = false
        var eqBandUpdates = 0
        var lastHpfFreqHz = 0f
        var lastLpfSlopeDb = 0
        var lastLimiterThreshDb = 0f

        override fun startEngine(
            inputDeviceId: Int,
            outputDeviceId: Int,
            requestedSampleRate: Int,
            requestedBufferFrames: Int,
            hasRecordPermission: Boolean
        ): Boolean {
            startCalled = true
            return true
        }

        override fun stopEngine() {
            stopCalled = true
        }

        override fun setGlobalState(bypassDsp: Boolean, muteAll: Boolean, testSignalMode: Int) {}
        override fun setInputParams(gainDb: Float, mute: Boolean, invertL: Boolean, invertR: Boolean) {}
        override fun setEqBypass(bypass: Boolean) {}
        override fun setEqBand(
            bandIndex: Int,
            enabled: Boolean,
            filterShape: Int,
            freqHz: Float,
            gainDb: Float,
            q: Float
        ) {
            eqBandUpdates++
        }

        override fun setCrossoverParams(
            bypass: Boolean,
            hpfEnabled: Boolean,
            hpfFreqHz: Float,
            hpfFilterType: Int,
            hpfSlopeDb: Int,
            lpfEnabled: Boolean,
            lpfFreqHz: Float,
            lpfFilterType: Int,
            lpfSlopeDb: Int
        ) {
            lastHpfFreqHz = hpfFreqHz
            lastLpfSlopeDb = lpfSlopeDb
        }

        override fun setDelayParams(enabled: Boolean, delayMsL: Float, delayMsR: Float) {}
        override fun setLimiterParams(enabled: Boolean, thresholdDb: Float, attackMs: Float, releaseMs: Float) {
            lastLimiterThreshDb = thresholdDb
        }

        override fun setOutputParams(
            gainDb: Float,
            muteL: Boolean,
            muteR: Boolean,
            invertL: Boolean,
            invertR: Boolean
        ) {}

        override fun resetPeakHold() {}

        override fun pollTelemetry(): AudioTelemetry = AudioTelemetry(engineState = AudioEngineState.RUNNING_DUPLEX)

        override fun pushPlayerAudio(stereoInterleaved: FloatArray, numFrames: Int): Int = numFrames
        override fun getPlayerBufferedFrames(): Int = 0
        override fun getPlayerConsumedFrames(): Long = 0L
        override fun setPlayerState(playing: Boolean, gainLinear: Float, mixWithLiveInput: Boolean) {}
        override fun clearPlayerBuffer(resetConsumedCounter: Boolean) {}
    }
}
