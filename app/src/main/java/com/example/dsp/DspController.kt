package com.example.dsp

import com.example.audio.AudioEngineBridge
import com.example.audio.AudioTelemetry
import com.example.audio.DspChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DspController(
    private val bridge: AudioEngineBridge,
    private val scope: CoroutineScope
) {
    private val _telemetry = MutableStateFlow(AudioTelemetry())
    val telemetry: StateFlow<AudioTelemetry> = _telemetry.asStateFlow()

    private var telemetryJob: Job? = null

    private var lastInputDeviceId: Int = 0
    private var lastOutputDeviceId: Int = 0
    private var lastSampleRate: Int = 0
    private var lastBufferFrames: Int = 0
    private var lastHasPermission: Boolean = false
    private var lastParameters: DspParameters = DspParameters()

    val isNativeReady: Boolean
        get() = bridge.isNativeLibraryLoaded

    fun startAudioEngine(
        inputDeviceId: Int,
        outputDeviceId: Int,
        requestedSampleRate: Int,
        requestedBufferFrames: Int,
        hasRecordPermission: Boolean,
        parameters: DspParameters
    ): Boolean {
        lastInputDeviceId = inputDeviceId
        lastOutputDeviceId = outputDeviceId
        lastSampleRate = requestedSampleRate
        lastBufferFrames = requestedBufferFrames
        lastHasPermission = hasRecordPermission
        lastParameters = parameters

        val started = bridge.startEngine(
            inputDeviceId = inputDeviceId,
            outputDeviceId = outputDeviceId,
            requestedSampleRate = requestedSampleRate,
            requestedBufferFrames = requestedBufferFrames,
            hasRecordPermission = hasRecordPermission
        )
        pushAllParameters(parameters)
        startTelemetryLoop()
        return started
    }

    fun stopAudioEngine() {
        telemetryJob?.cancel()
        telemetryJob = null
        bridge.stopEngine()
        _telemetry.value = bridge.pollTelemetry()
    }

    fun resetPeakHold() {
        bridge.resetPeakHold()
    }

    fun pushAllParameters(p: DspParameters) {
        lastParameters = p
        updateGlobal(p)
        updateInput(p)
        updateEq(p)
        updateCrossover(p)
        updateDelay(p)
        updateLimiter(p)
        updateOutput(p)
    }

    fun updateGlobal(p: DspParameters) {
        lastParameters = p
        bridge.setGlobalState(p.bypassDsp, p.muteAll, p.testSignalMode.code)
    }

    fun updateInput(p: DspParameters) {
        lastParameters = p
        bridge.setInputParams(
            gainDbL = p.inputGainDbL,
            gainDbR = p.inputGainDbR,
            muteL = p.inputMuteL,
            muteR = p.inputMuteR,
            invertL = p.inputInvertL,
            invertR = p.inputInvertR,
            routeSourceL = p.routeSourceL.code,
            routeSourceR = p.routeSourceR.code
        )
    }

    fun updateEq(p: DspParameters) {
        lastParameters = p
        bridge.setEqBypass(DspChannel.CH_L.index, p.eqBypassL)
        for (band in p.eqBandsL) {
            bridge.setEqBand(
                channelIndex = DspChannel.CH_L.index,
                bandIndex = band.index,
                enabled = band.enabled,
                filterShape = band.shape.code,
                freqHz = band.freqHz,
                gainDb = band.gainDb,
                q = band.q
            )
        }
        bridge.setEqBypass(DspChannel.CH_R.index, p.eqBypassR)
        for (band in p.eqBandsR) {
            bridge.setEqBand(
                channelIndex = DspChannel.CH_R.index,
                bandIndex = band.index,
                enabled = band.enabled,
                filterShape = band.shape.code,
                freqHz = band.freqHz,
                gainDb = band.gainDb,
                q = band.q
            )
        }
    }

    fun updateCrossover(p: DspParameters) {
        lastParameters = p
        bridge.setCrossoverParams(
            channelIndex = DspChannel.CH_L.index,
            bypass = p.xoverBypassL,
            hpfEnabled = p.hpfEnabledL,
            hpfFreqHz = p.hpfFreqHzL,
            hpfFilterType = p.hpfFilterTypeL.code,
            hpfSlopeDb = p.hpfSlopeDbL.slopeDb,
            lpfEnabled = p.lpfEnabledL,
            lpfFreqHz = p.lpfFreqHzL,
            lpfFilterType = p.lpfFilterTypeL.code,
            lpfSlopeDb = p.lpfSlopeDbL.slopeDb
        )
        bridge.setCrossoverParams(
            channelIndex = DspChannel.CH_R.index,
            bypass = p.xoverBypassR,
            hpfEnabled = p.hpfEnabledR,
            hpfFreqHz = p.hpfFreqHzR,
            hpfFilterType = p.hpfFilterTypeR.code,
            hpfSlopeDb = p.hpfSlopeDbR.slopeDb,
            lpfEnabled = p.lpfEnabledR,
            lpfFreqHz = p.lpfFreqHzR,
            lpfFilterType = p.lpfFilterTypeR.code,
            lpfSlopeDb = p.lpfSlopeDbR.slopeDb
        )
    }

    fun updateDelay(p: DspParameters) {
        lastParameters = p
        bridge.setDelayParams(p.delayEnabledL, p.delayEnabledR, p.delayMsL, p.delayMsR)
    }

    fun updateLimiter(p: DspParameters) {
        lastParameters = p
        bridge.setLimiterParams(
            channelIndex = DspChannel.CH_L.index,
            linked = p.limiterLinked,
            enabled = p.limiterEnabledL,
            thresholdDb = p.limiterThresholdDbL,
            attackMs = p.limiterAttackMsL,
            releaseMs = p.limiterReleaseMsL
        )
        bridge.setLimiterParams(
            channelIndex = DspChannel.CH_R.index,
            linked = p.limiterLinked,
            enabled = p.limiterEnabledR,
            thresholdDb = p.limiterThresholdDbR,
            attackMs = p.limiterAttackMsR,
            releaseMs = p.limiterReleaseMsR
        )
    }

    fun updateOutput(p: DspParameters) {
        lastParameters = p
        bridge.setOutputParams(
            gainDbL = p.outputGainDbL,
            gainDbR = p.outputGainDbR,
            muteL = p.outputMuteL,
            muteR = p.outputMuteR,
            invertL = p.outputInvertL,
            invertR = p.outputInvertR
        )
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val snap = bridge.pollTelemetry()
                _telemetry.value = snap
                if (snap.streamDisconnected) {
                    delay(250L)
                    bridge.startEngine(
                        lastInputDeviceId,
                        lastOutputDeviceId,
                        lastSampleRate,
                        lastBufferFrames,
                        lastHasPermission
                    )
                    pushAllParameters(lastParameters)
                }
                delay(33L)
            }
        }
    }
}
