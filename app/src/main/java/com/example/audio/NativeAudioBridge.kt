package com.example.audio

interface AudioEngineBridge {
    val isNativeLibraryLoaded: Boolean

    fun startEngine(
        inputDeviceId: Int,
        outputDeviceId: Int,
        requestedSampleRate: Int,
        requestedBufferFrames: Int,
        hasRecordPermission: Boolean
    ): Boolean

    fun stopEngine()

    fun setGlobalState(bypassDsp: Boolean, muteAll: Boolean, testSignalMode: Int)

    fun setInputParams(
        gainDbL: Float,
        gainDbR: Float,
        muteL: Boolean,
        muteR: Boolean,
        invertL: Boolean,
        invertR: Boolean,
        routeSourceL: Int,
        routeSourceR: Int
    )

    fun setEqBypass(channelIndex: Int, bypass: Boolean)

    fun setEqBand(
        channelIndex: Int,
        bandIndex: Int,
        enabled: Boolean,
        filterShape: Int,
        freqHz: Float,
        gainDb: Float,
        q: Float
    )

    fun setCrossoverParams(
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
    )

    fun setDelayParams(enabledL: Boolean, enabledR: Boolean, delayMsL: Float, delayMsR: Float)

    fun setLimiterParams(
        channelIndex: Int,
        linked: Boolean,
        enabled: Boolean,
        thresholdDb: Float,
        attackMs: Float,
        releaseMs: Float
    )

    fun setOutputParams(
        gainDbL: Float,
        gainDbR: Float,
        muteL: Boolean,
        muteR: Boolean,
        invertL: Boolean,
        invertR: Boolean
    )

    fun resetPeakHold()

    fun pollTelemetry(): AudioTelemetry

    fun pushPlayerAudio(stereoInterleaved: FloatArray, numFrames: Int): Int
    fun getPlayerBufferedFrames(): Int
    fun getPlayerConsumedFrames(): Long
    fun setPlayerState(playing: Boolean, gainLinear: Float, mixWithLiveInput: Boolean)
    fun clearPlayerBuffer(resetConsumedCounter: Boolean)
}

class NativeAudioBridge : AudioEngineBridge {

    private val telemetryScratch = FloatArray(23)

    override val isNativeLibraryLoaded: Boolean = nativeLoaded

    override fun startEngine(
        inputDeviceId: Int,
        outputDeviceId: Int,
        requestedSampleRate: Int,
        requestedBufferFrames: Int,
        hasRecordPermission: Boolean
    ): Boolean {
        if (!nativeLoaded) return false
        return nativeStartEngine(
            inputDeviceId,
            outputDeviceId,
            requestedSampleRate,
            requestedBufferFrames,
            hasRecordPermission
        )
    }

    override fun stopEngine() {
        if (!nativeLoaded) return
        nativeStopEngine()
    }

    override fun setGlobalState(bypassDsp: Boolean, muteAll: Boolean, testSignalMode: Int) {
        if (!nativeLoaded) return
        nativeSetGlobalState(bypassDsp, muteAll, testSignalMode)
    }

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
        if (!nativeLoaded) return
        nativeSetInputParams(gainDbL, gainDbR, muteL, muteR, invertL, invertR, routeSourceL, routeSourceR)
    }

    override fun setEqBypass(channelIndex: Int, bypass: Boolean) {
        if (!nativeLoaded) return
        nativeSetEqBypass(channelIndex, bypass)
    }

    override fun setEqBand(
        channelIndex: Int,
        bandIndex: Int,
        enabled: Boolean,
        filterShape: Int,
        freqHz: Float,
        gainDb: Float,
        q: Float
    ) {
        if (!nativeLoaded) return
        nativeSetEqBand(channelIndex, bandIndex, enabled, filterShape, freqHz, gainDb, q)
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
        if (!nativeLoaded) return
        nativeSetCrossoverParams(
            channelIndex,
            bypass,
            hpfEnabled,
            hpfFreqHz,
            hpfFilterType,
            hpfSlopeDb,
            lpfEnabled,
            lpfFreqHz,
            lpfFilterType,
            lpfSlopeDb
        )
    }

    override fun setDelayParams(enabledL: Boolean, enabledR: Boolean, delayMsL: Float, delayMsR: Float) {
        if (!nativeLoaded) return
        nativeSetDelayParams(enabledL, enabledR, delayMsL, delayMsR)
    }

    override fun setLimiterParams(
        channelIndex: Int,
        linked: Boolean,
        enabled: Boolean,
        thresholdDb: Float,
        attackMs: Float,
        releaseMs: Float
    ) {
        if (!nativeLoaded) return
        nativeSetLimiterParams(channelIndex, linked, enabled, thresholdDb, attackMs, releaseMs)
    }

    override fun setOutputParams(
        gainDbL: Float,
        gainDbR: Float,
        muteL: Boolean,
        muteR: Boolean,
        invertL: Boolean,
        invertR: Boolean
    ) {
        if (!nativeLoaded) return
        nativeSetOutputParams(gainDbL, gainDbR, muteL, muteR, invertL, invertR)
    }

    override fun resetPeakHold() {
        if (!nativeLoaded) return
        nativeResetPeakHold()
    }

    override fun pollTelemetry(): AudioTelemetry {
        if (!nativeLoaded) {
            return AudioTelemetry(engineState = AudioEngineState.DEV_FALLBACK)
        }
        nativeGetTelemetry(telemetryScratch)
        val cpuLoad = nativeGetCpuLoad()
        val apiRaw = telemetryScratch[20].toInt()
        val disconnected = telemetryScratch[20] < 0f
        return AudioTelemetry(
            inputPeakL = telemetryScratch[0],
            inputPeakR = telemetryScratch[1],
            inputRmsL = telemetryScratch[2],
            inputRmsR = telemetryScratch[3],
            outputPeakL = telemetryScratch[4],
            outputPeakR = telemetryScratch[5],
            outputRmsL = telemetryScratch[6],
            outputRmsR = telemetryScratch[7],
            inputHoldL = telemetryScratch[8],
            inputHoldR = telemetryScratch[9],
            outputHoldL = telemetryScratch[10],
            outputHoldR = telemetryScratch[11],
            limiterGrDb = telemetryScratch[12],
            sampleRate = telemetryScratch[13].toInt().coerceAtLeast(8000),
            bufferSizeFrames = telemetryScratch[14].toInt().coerceAtLeast(0),
            framesPerBurst = telemetryScratch[15].toInt().coerceAtLeast(0),
            inputChannels = telemetryScratch[16].toInt().coerceAtLeast(0),
            outputChannels = telemetryScratch[17].toInt().coerceAtLeast(0),
            xRunCount = telemetryScratch[18].toInt().coerceAtLeast(0),
            engineState = AudioEngineState.fromCode(telemetryScratch[19].toInt()),
            audioApi = if (disconnected) AudioApiType.UNSPECIFIED else AudioApiType.fromCode(apiRaw),
            cpuLoadPercent = cpuLoad,
            streamDisconnected = disconnected,
            limiterGrDbL = telemetryScratch[21],
            limiterGrDbR = telemetryScratch[22]
        )
    }

    override fun pushPlayerAudio(stereoInterleaved: FloatArray, numFrames: Int): Int {
        if (!nativeLoaded || numFrames <= 0) return 0
        return nativePushPlayerAudio(stereoInterleaved, numFrames)
    }

    override fun getPlayerBufferedFrames(): Int {
        if (!nativeLoaded) return 0
        return nativeGetPlayerBufferedFrames()
    }

    override fun getPlayerConsumedFrames(): Long {
        if (!nativeLoaded) return 0L
        return nativeGetPlayerConsumedFrames()
    }

    override fun setPlayerState(playing: Boolean, gainLinear: Float, mixWithLiveInput: Boolean) {
        if (!nativeLoaded) return
        nativeSetPlayerState(playing, gainLinear, mixWithLiveInput)
    }

    override fun clearPlayerBuffer(resetConsumedCounter: Boolean) {
        if (!nativeLoaded) return
        nativeClearPlayerBuffer(resetConsumedCounter)
    }

    private external fun nativeStartEngine(
        inputDeviceId: Int,
        outputDeviceId: Int,
        requestedSampleRate: Int,
        requestedBufferFrames: Int,
        hasRecordPermission: Boolean
    ): Boolean

    private external fun nativeStopEngine()
    private external fun nativeSetGlobalState(bypassDsp: Boolean, muteAll: Boolean, testSignalMode: Int)
    private external fun nativeSetInputParams(
        gainDbL: Float,
        gainDbR: Float,
        muteL: Boolean,
        muteR: Boolean,
        invertL: Boolean,
        invertR: Boolean,
        routeSourceL: Int,
        routeSourceR: Int
    )
    private external fun nativeSetEqBypass(channelIndex: Int, bypass: Boolean)
    private external fun nativeSetEqBand(
        channelIndex: Int,
        bandIndex: Int,
        enabled: Boolean,
        filterShape: Int,
        freqHz: Float,
        gainDb: Float,
        q: Float
    )
    private external fun nativeSetCrossoverParams(
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
    )
    private external fun nativeSetDelayParams(
        enabledL: Boolean,
        enabledR: Boolean,
        delayMsL: Float,
        delayMsR: Float
    )
    private external fun nativeSetLimiterParams(
        channelIndex: Int,
        linked: Boolean,
        enabled: Boolean,
        thresholdDb: Float,
        attackMs: Float,
        releaseMs: Float
    )
    private external fun nativeSetOutputParams(
        gainDbL: Float,
        gainDbR: Float,
        muteL: Boolean,
        muteR: Boolean,
        invertL: Boolean,
        invertR: Boolean
    )
    private external fun nativeResetPeakHold()
    private external fun nativeGetTelemetry(outArray: FloatArray)
    private external fun nativeGetCpuLoad(): Float
    private external fun nativePushPlayerAudio(stereoInterleaved: FloatArray, numFrames: Int): Int
    private external fun nativeGetPlayerBufferedFrames(): Int
    private external fun nativeGetPlayerConsumedFrames(): Long
    private external fun nativeSetPlayerState(playing: Boolean, gainLinear: Float, mixWithLiveInput: Boolean)
    private external fun nativeClearPlayerBuffer(resetConsumedCounter: Boolean)

    companion object {
        private val nativeLoaded: Boolean = try {
            System.loadLibrary("adw_vdlms_engine")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }
}
