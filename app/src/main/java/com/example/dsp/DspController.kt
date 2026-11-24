package com.example.dsp

import com.example.audio.AudioEngineBridge
import com.example.audio.AudioTelemetry
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
        bridge.setGlobalState(p.bypassDsp, p.muteAll, p.testSignalMode.code)
        bridge.setInputParams(p.inputGainDb, p.inputMute, p.inputInvertL, p.inputInvertR)
        bridge.setEqBypass(p.eqBypass)
        for (band in p.eqBands) {
            bridge.setEqBand(
                bandIndex = band.index,
                enabled = band.enabled,
                filterShape = band.shape.code,
                freqHz = band.freqHz,
                gainDb = band.gainDb,
                q = band.q
            )
        }
        bridge.setCrossoverParams(
            bypass = p.xoverBypass,
            hpfEnabled = p.hpfEnabled,
            hpfFreqHz = p.hpfFreqHz,
            hpfFilterType = p.hpfFilterType.code,
            hpfSlopeDb = p.hpfSlopeDb.slopeDb,
            lpfEnabled = p.lpfEnabled,
            lpfFreqHz = p.lpfFreqHz,
            lpfFilterType = p.lpfFilterType.code,
            lpfSlopeDb = p.lpfSlopeDb.slopeDb
        )
        bridge.setDelayParams(p.delayEnabled, p.delayMsL, p.delayMsR)
        bridge.setLimiterParams(
            p.limiterEnabled,
            p.limiterThresholdDb,
            p.limiterAttackMs,
            p.limiterReleaseMs
        )
        bridge.setOutputParams(p.outputGainDb, p.outputMuteL, p.outputMuteR, p.outputInvertL, p.outputInvertR)
    }

    fun updateGlobal(p: DspParameters) {
        lastParameters = p
        bridge.setGlobalState(p.bypassDsp, p.muteAll, p.testSignalMode.code)
    }

    fun updateInput(p: DspParameters) {
        lastParameters = p
        bridge.setInputParams(p.inputGainDb, p.inputMute, p.inputInvertL, p.inputInvertR)
    }

    fun updateEq(p: DspParameters) {
        lastParameters = p
        bridge.setEqBypass(p.eqBypass)
        for (band in p.eqBands) {
            bridge.setEqBand(
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
            bypass = p.xoverBypass,
            hpfEnabled = p.hpfEnabled,
            hpfFreqHz = p.hpfFreqHz,
            hpfFilterType = p.hpfFilterType.code,
            hpfSlopeDb = p.hpfSlopeDb.slopeDb,
            lpfEnabled = p.lpfEnabled,
            lpfFreqHz = p.lpfFreqHz,
            lpfFilterType = p.lpfFilterType.code,
            lpfSlopeDb = p.lpfSlopeDb.slopeDb
        )
    }

    fun updateDelay(p: DspParameters) {
        lastParameters = p
        bridge.setDelayParams(p.delayEnabled, p.delayMsL, p.delayMsR)
    }

    fun updateLimiter(p: DspParameters) {
        lastParameters = p
        bridge.setLimiterParams(
            p.limiterEnabled,
            p.limiterThresholdDb,
            p.limiterAttackMs,
            p.limiterReleaseMs
        )
    }

    fun updateOutput(p: DspParameters) {
        lastParameters = p
        bridge.setOutputParams(p.outputGainDb, p.outputMuteL, p.outputMuteR, p.outputInvertL, p.outputInvertR)
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
