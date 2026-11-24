package com.example

import com.example.audio.AudioEngineBridge
import com.example.audio.AudioEngineState
import com.example.audio.AudioTelemetry
import com.example.audio.DspChannel
import com.example.audio.InputSourceRoute
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
        val lr24 = DspParameters.stereoLinked(
            bypassDsp = false,
            eqBypass = true,
            xoverBypass = false,
            hpfEnabled = true,
            hpfFreqHz = 100f,
            hpfFilterType = XoverFilterType.LINKWITZ_RILEY,
            hpfSlopeDb = XoverSlopeDb.SLOPE_24,
            lpfEnabled = false
        )
        assertEquals(-6.02f, EqResponseCalculator.evaluateMagnitudeDb(100.0, 48000.0, lr24, DspChannel.CH_L), 0.25f)

        val lr48 = lr24.copy(hpfSlopeDbL = XoverSlopeDb.SLOPE_48, hpfSlopeDbR = XoverSlopeDb.SLOPE_48)
        assertEquals(-6.02f, EqResponseCalculator.evaluateMagnitudeDb(100.0, 48000.0, lr48, DspChannel.CH_R), 0.30f)
        val at50Hz = EqResponseCalculator.evaluateMagnitudeDb(50.0, 48000.0, lr48, DspChannel.CH_L)
        assertTrue("Expected steep LR48 rolloff <= -40 dB at 50 Hz, got $at50Hz", at50Hz <= -40.0f)
    }

    @Test
    fun peakingAndShelfEqShapesProduceExpectedGain() {
        val bands = listOf(
            EqBandState(0, "B1", true, EqFilterShape.LOW_SHELF, 80f, 6.0f, 0.8f),
            EqBandState(1, "B2", true, EqFilterShape.PEAK, 1000f, -6.0f, 2.0f),
            EqBandState(2, "B3", false, EqFilterShape.PEAK, 800f, 0f, 1.2f),
            EqBandState(3, "B4", false, EqFilterShape.PEAK, 2800f, 0f, 1.2f),
            EqBandState(4, "B5", true, EqFilterShape.HIGH_SHELF, 8000f, 4.0f, 0.8f)
        )
        val params = DspParameters.stereoLinked(
            bypassDsp = false,
            eqBypass = false,
            xoverBypass = true,
            eqBands = bands
        )
        assertEquals(6.0f, EqResponseCalculator.evaluateMagnitudeDb(20.0, 48000.0, params, DspChannel.CH_L), 0.4f)
        assertEquals(-6.0f, EqResponseCalculator.evaluateMagnitudeDb(1000.0, 48000.0, params, DspChannel.CH_L), 0.2f)
        assertEquals(4.0f, EqResponseCalculator.evaluateMagnitudeDb(18000.0, 48000.0, params, DspChannel.CH_R), 0.5f)
    }

    @Test
    fun twoWaySubTopPresetSplitsChannelsIndependently() {
        val split = FactoryPresets.all.first { it.id == "factory_2way_sub_top" }.parameters
        assertEquals(InputSourceRoute.SUM_L_R, split.routeSourceL)
        assertEquals(InputSourceRoute.SUM_L_R, split.routeSourceR)

        // At 60 Hz: CH_L (Sub) passes (~+2 dB boost), CH_R (Top) is attenuated by 100 Hz LR24 HPF
        val subAt60 = EqResponseCalculator.evaluateMagnitudeDb(60.0, 48000.0, split, DspChannel.CH_L)
        val topAt60 = EqResponseCalculator.evaluateMagnitudeDb(60.0, 48000.0, split, DspChannel.CH_R)
        assertTrue("Sub CH_L at 60Hz should be near passband, got $subAt60", subAt60 > -2.0f)
        assertTrue("Top CH_R at 60Hz should be attenuated <= -15 dB, got $topAt60", topAt60 <= -15.0f)

        // At 2 kHz: CH_L (Sub) is steeply attenuated, CH_R (Top) is in passband
        val subAt2k = EqResponseCalculator.evaluateMagnitudeDb(2000.0, 48000.0, split, DspChannel.CH_L)
        val topAt2k = EqResponseCalculator.evaluateMagnitudeDb(2000.0, 48000.0, split, DspChannel.CH_R)
        assertTrue("Sub CH_L at 2kHz should be <= -40 dB, got $subAt2k", subAt2k <= -40.0f)
        assertTrue("Top CH_R at 2kHz should be near 0 dB, got $topAt2k", topAt2k > -3.0f)
    }

    @Test
    fun bypassDspProducesFlatResponse() {
        val params = FactoryPresets.all[3].parameters.copy(bypassDsp = true)
        val mag = EqResponseCalculator.evaluateMagnitudeDb(100.0, 48000.0, params, DspChannel.CH_L)
        assertEquals(0.0f, mag, 0.001f)
    }

    @Test
    fun dspControllerPropagatesPerChannelStagesAndRoutingToBridge() {
        val fakeBridge = RecordingAudioEngineBridge()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = DspController(fakeBridge, scope)

        val preset = FactoryPresets.all.first { it.id == "factory_2way_sub_top" }.parameters
        controller.startAudioEngine(
            inputDeviceId = 0,
            outputDeviceId = 0,
            requestedSampleRate = 48000,
            requestedBufferFrames = 192,
            hasRecordPermission = true,
            parameters = preset
        )

        assertTrue(fakeBridge.startCalled)
        assertEquals(10, fakeBridge.eqBandUpdates) // 5 bands L + 5 bands R
        assertEquals(InputSourceRoute.SUM_L_R.code, fakeBridge.lastRouteL)
        assertEquals(InputSourceRoute.SUM_L_R.code, fakeBridge.lastRouteR)
        assertEquals(100f, fakeBridge.lastLpfFreqByChannel[0] ?: 0f, 0.01f)
        assertEquals(100f, fakeBridge.lastHpfFreqByChannel[1] ?: 0f, 0.01f)
        assertEquals(-3.0f, fakeBridge.lastLimiterThreshByChannel[0] ?: 0f, 0.01f)
        assertEquals(-1.5f, fakeBridge.lastLimiterThreshByChannel[1] ?: 0f, 0.01f)

        controller.stopAudioEngine()
        assertTrue(fakeBridge.stopCalled)
        scope.cancel()
    }

    private class RecordingAudioEngineBridge : AudioEngineBridge {
        override val isNativeLibraryLoaded: Boolean = true
        var startCalled = false
        var stopCalled = false
        var eqBandUpdates = 0
        var lastRouteL = -1
        var lastRouteR = -1
        val lastHpfFreqByChannel = mutableMapOf<Int, Float>()
        val lastLpfFreqByChannel = mutableMapOf<Int, Float>()
        val lastLimiterThreshByChannel = mutableMapOf<Int, Float>()

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

        override fun setInputParams(
            gainDbL: Float,
            gainDbR: Float,
            muteL: Boolean,
            muteR: Boolean,
            invertL: Boolean,
            invertR: Boolean,
            routeSourceL: Int,
            routeSourceR: Int
        ) {
            lastRouteL = routeSourceL
            lastRouteR = routeSourceR
        }

        override fun setEqBypass(channelIndex: Int, bypass: Boolean) {}

        override fun setEqBand(
            channelIndex: Int,
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
            channelIndex: Int,
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
            lastHpfFreqByChannel[channelIndex] = hpfFreqHz
            lastLpfFreqByChannel[channelIndex] = lpfFreqHz
        }

        override fun setDelayParams(enabledL: Boolean, enabledR: Boolean, delayMsL: Float, delayMsR: Float) {}

        override fun setLimiterParams(
            channelIndex: Int,
            linked: Boolean,
            enabled: Boolean,
            thresholdDb: Float,
            attackMs: Float,
            releaseMs: Float
        ) {
            lastLimiterThreshByChannel[channelIndex] = thresholdDb
        }

        override fun setOutputParams(
            gainDbL: Float,
            gainDbR: Float,
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
