package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioDeviceManager
import com.example.audio.AudioEngine
import com.example.audio.AudioEngineState
import com.example.audio.AudioHardwareInventory
import com.example.audio.AudioTelemetry
import com.example.audio.DspChannel
import com.example.audio.InputSourceRoute
import com.example.audio.LocalAudioPlayer
import com.example.audio.MediaPlayerUiState
import com.example.audio.NativeAudioBridge
import com.example.audio.TestSignalMode
import com.example.dsp.DspController
import com.example.dsp.DspModuleTab
import com.example.dsp.DspParameters
import com.example.dsp.DspPreset
import com.example.dsp.EqBandState
import com.example.dsp.EqFilterShape
import com.example.dsp.FactoryPresets
import com.example.dsp.PresetManager
import com.example.dsp.XoverFilterType
import com.example.dsp.XoverSlopeDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VdlmsUiState(
    val activeTab: DspModuleTab = DspModuleTab.INPUT,
    val selectedDspChannel: DspChannel = DspChannel.CH_L,
    val selectedEqBandIndex: Int = 0,
    val selectedEncoderId: String? = null,
    val parameters: DspParameters = FactoryPresets.all.first().parameters,
    val presets: List<DspPreset> = FactoryPresets.all,
    val activePresetId: String = FactoryPresets.all.first().id,
    val activePresetName: String = FactoryPresets.all.first().name,
    val isPresetModified: Boolean = false,
    val telemetry: AudioTelemetry = AudioTelemetry(),
    val hardwareInventory: AudioHardwareInventory = AudioHardwareInventory(),
    val mediaPlayer: MediaPlayerUiState = MediaPlayerUiState(),
    val selectedInputDeviceId: Int = 0,  // 0 = Auto (prioritizes USB if connected)
    val selectedOutputDeviceId: Int = 0, // 0 = Auto (prioritizes USB if connected)
    val requestedSampleRate: Int = 0,    // 0 = Device Optimal
    val requestedBufferFrames: Int = 0,  // 0 = Auto (2 bursts)
    val hasRecordPermission: Boolean = false,
    val isEngineActive: Boolean = false,
    val isNativeLoaded: Boolean = true
)

class VdlmsViewModel(application: Application) : AndroidViewModel(application) {

    private val bridge = NativeAudioBridge()
    private val deviceManager = AudioDeviceManager(application)
    private val audioEngine = AudioEngine(deviceManager, bridge)
    private val presetManager = PresetManager(application)
    private val dspController = DspController(bridge, viewModelScope)
    private val localAudioPlayer = LocalAudioPlayer(
        context = application,
        bridge = bridge,
        scope = viewModelScope,
        engineSampleRateProvider = { dspController.telemetry.value.sampleRate },
        isOutputStreamReadyProvider = {
            val st = dspController.telemetry.value.engineState
            st == AudioEngineState.RUNNING_DUPLEX || st == AudioEngineState.RUNNING_OUTPUT_ONLY
        }
    )

    private val _uiState = MutableStateFlow(
        VdlmsUiState(isNativeLoaded = dspController.isNativeReady)
    )
    val uiState: StateFlow<VdlmsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val (loadedPresets, lastId) = presetManager.loadAllPresets()
            val chosen = loadedPresets.firstOrNull { it.id == lastId } ?: loadedPresets.first()
            _uiState.update { state ->
                state.copy(
                    presets = loadedPresets,
                    activePresetId = chosen.id,
                    activePresetName = chosen.name,
                    parameters = chosen.parameters,
                    isPresetModified = false
                )
            }
            dspController.pushAllParameters(chosen.parameters)
        }

        viewModelScope.launch {
            dspController.telemetry.collect { snap ->
                _uiState.update { it.copy(telemetry = snap) }
            }
        }

        viewModelScope.launch {
            localAudioPlayer.state.collect { playerSnap ->
                _uiState.update { it.copy(mediaPlayer = playerSnap) }
            }
        }

        viewModelScope.launch {
            audioEngine.hardwareInventory.collect { inv ->
                val hadUsb = _uiState.value.hardwareInventory.hasUsbAudioInterface
                _uiState.update { it.copy(hardwareInventory = inv) }
                // Auto-switch to/from USB audio interface if hotplugged in Auto routing mode
                if (hadUsb != inv.hasUsbAudioInterface &&
                    _uiState.value.isEngineActive &&
                    (_uiState.value.selectedInputDeviceId == 0 || _uiState.value.selectedOutputDeviceId == 0)
                ) {
                    restartAudioEngine()
                }
            }
        }
    }

    fun onPermissionAndStart(hasRecordPermission: Boolean) {
        val current = _uiState.value
        val changed = current.hasRecordPermission != hasRecordPermission || !current.isEngineActive
        _uiState.update { it.copy(hasRecordPermission = hasRecordPermission) }
        if (changed) {
            restartAudioEngine()
        }
    }

    fun restartAudioEngine() {
        val s = _uiState.value
        audioEngine.refreshDevices()
        val resolvedIn = deviceManager.resolvePreferredInputDeviceId(s.selectedInputDeviceId)
        val resolvedOut = deviceManager.resolvePreferredOutputDeviceId(s.selectedOutputDeviceId)
        dspController.startAudioEngine(
            inputDeviceId = resolvedIn,
            outputDeviceId = resolvedOut,
            requestedSampleRate = s.requestedSampleRate,
            requestedBufferFrames = s.requestedBufferFrames,
            hasRecordPermission = s.hasRecordPermission,
            parameters = s.parameters
        )
        _uiState.update { it.copy(isEngineActive = true) }
        localAudioPlayer.onEngineRestarted(dspController.telemetry.value.sampleRate)
    }

    fun stopAudioEngine() {
        localAudioPlayer.stop()
        dspController.stopAudioEngine()
        _uiState.update { it.copy(isEngineActive = false) }
    }

    fun resetPeakHold() {
        dspController.resetPeakHold()
    }

    fun selectTab(tab: DspModuleTab) {
        _uiState.update { it.copy(activeTab = tab, selectedEncoderId = null) }
    }

    fun selectDspChannel(channel: DspChannel) {
        _uiState.update { it.copy(selectedDspChannel = channel, selectedEncoderId = null) }
    }

    fun selectEncoder(id: String?) {
        _uiState.update { it.copy(selectedEncoderId = id) }
    }

    fun selectEqBand(index: Int) {
        _uiState.update { it.copy(selectedEqBandIndex = index.coerceIn(0, 4)) }
    }

    fun toggleBypassDsp() {
        updateParameters(markModified = true) { it.copy(bypassDsp = !it.bypassDsp) }
        dspController.updateGlobal(_uiState.value.parameters)
    }

    fun toggleMuteAll() {
        updateParameters(markModified = false) { it.copy(muteAll = !it.muteAll) }
        dspController.updateGlobal(_uiState.value.parameters)
    }

    fun setTestSignalMode(mode: TestSignalMode) {
        updateParameters(markModified = false) { it.copy(testSignalMode = mode) }
        dspController.updateGlobal(_uiState.value.parameters)
    }

    // Local Media Player (routed directly through native C++ DSP chain)
    private fun ensureLivePathForPlayer() {
        if (_uiState.value.parameters.testSignalMode != TestSignalMode.LIVE_INPUT) {
            setTestSignalMode(TestSignalMode.LIVE_INPUT)
        }
    }

    fun loadLocalAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        ensureLivePathForPlayer()
        localAudioPlayer.addLocalAudioUris(uris, autoPlayFirstLoaded = true)
    }

    fun playLocalTrack(index: Int) {
        ensureLivePathForPlayer()
        localAudioPlayer.playTrackAt(index, 0L)
    }

    fun togglePlayerPlayPause() {
        ensureLivePathForPlayer()
        localAudioPlayer.togglePlayPause()
    }

    fun stopLocalPlayer() {
        localAudioPlayer.stop()
    }

    fun seekLocalPlayer(positionMs: Long) {
        localAudioPlayer.seekTo(positionMs)
    }

    fun nextLocalTrack() {
        ensureLivePathForPlayer()
        localAudioPlayer.nextTrack()
    }

    fun previousLocalTrack() {
        ensureLivePathForPlayer()
        localAudioPlayer.previousTrack()
    }

    fun removeLocalTrack(trackId: String) {
        localAudioPlayer.removeTrack(trackId)
    }

    fun clearLocalPlaylist() {
        localAudioPlayer.clearPlaylist()
    }

    fun setLocalPlayerGainDb(gainDb: Float) {
        localAudioPlayer.setPlayerGainDb(gainDb)
    }

    fun togglePlayerMixWithLiveInput() {
        localAudioPlayer.toggleMixWithLiveInput()
    }

    fun cyclePlayerRepeatMode() {
        localAudioPlayer.cycleRepeatMode()
    }

    // Input Stage & Input Source Routing Matrix
    fun toggleInputLinked() {
        updateParameters(markModified = true) { p ->
            val next = !p.inputLinked
            p.copy(
                inputLinked = next,
                inputGainDbR = if (next) p.inputGainDbL else p.inputGainDbR
            )
        }
        dspController.updateInput(_uiState.value.parameters)
    }

    fun setInputGainL(gainDb: Float) {
        val clamped = gainDb.coerceIn(-24f, 12f)
        updateParameters(markModified = true) { p ->
            p.copy(
                inputGainDbL = clamped,
                inputGainDbR = if (p.inputLinked) clamped else p.inputGainDbR
            )
        }
        dspController.updateInput(_uiState.value.parameters)
    }

    fun setInputGainR(gainDb: Float) {
        val clamped = gainDb.coerceIn(-24f, 12f)
        updateParameters(markModified = true) { p ->
            p.copy(
                inputGainDbR = clamped,
                inputGainDbL = if (p.inputLinked) clamped else p.inputGainDbL
            )
        }
        dspController.updateInput(_uiState.value.parameters)
    }

    fun toggleInputMuteL() {
        updateParameters(markModified = true) { it.copy(inputMuteL = !it.inputMuteL) }
        dspController.updateInput(_uiState.value.parameters)
    }

    fun toggleInputMuteR() {
        updateParameters(markModified = true) { it.copy(inputMuteR = !it.inputMuteR) }
        dspController.updateInput(_uiState.value.parameters)
    }

    fun toggleInputInvertL() {
        updateParameters(markModified = true) { it.copy(inputInvertL = !it.inputInvertL) }
        dspController.updateInput(_uiState.value.parameters)
    }

    fun toggleInputInvertR() {
        updateParameters(markModified = true) { it.copy(inputInvertR = !it.inputInvertR) }
        dspController.updateInput(_uiState.value.parameters)
    }

    fun setRouteSourceL(route: InputSourceRoute) {
        updateParameters(markModified = true) { it.copy(routeSourceL = route) }
        dspController.updateInput(_uiState.value.parameters)
    }

    fun setRouteSourceR(route: InputSourceRoute) {
        updateParameters(markModified = true) { it.copy(routeSourceR = route) }
        dspController.updateInput(_uiState.value.parameters)
    }

    // Per-Channel Parametric EQ Stage (5 Bands: PEAK / LOW_SHELF / HIGH_SHELF)
    fun toggleEqChannelLinked() {
        val activeCh = _uiState.value.selectedDspChannel
        updateParameters(markModified = true) { p ->
            val next = !p.eqChannelLinked
            if (!next) {
                p.copy(eqChannelLinked = false)
            } else {
                val srcBypass = p.eqBypassFor(activeCh)
                val srcBands = p.eqBandsFor(activeCh)
                p.copy(
                    eqChannelLinked = true,
                    eqBypassL = srcBypass,
                    eqBypassR = srcBypass,
                    eqBandsL = srcBands,
                    eqBandsR = srcBands
                )
            }
        }
        dspController.updateEq(_uiState.value.parameters)
    }

    fun toggleEqBypass() {
        val activeCh = _uiState.value.selectedDspChannel
        updateParameters(markModified = true) { p ->
            val next = !p.eqBypassFor(activeCh)
            when {
                p.eqChannelLinked -> p.copy(eqBypassL = next, eqBypassR = next)
                activeCh == DspChannel.CH_L -> p.copy(eqBypassL = next)
                else -> p.copy(eqBypassR = next)
            }
        }
        dspController.updateEq(_uiState.value.parameters)
    }

    fun updateEqBand(index: Int, transform: (EqBandState) -> EqBandState) {
        val activeCh = _uiState.value.selectedDspChannel
        updateParameters(markModified = true) { params ->
            fun applyToList(list: List<EqBandState>): List<EqBandState> = list.map { b ->
                if (b.index == index) {
                    val next = transform(b)
                    next.copy(
                        freqHz = next.freqHz.coerceIn(20f, 20000f),
                        gainDb = next.gainDb.coerceIn(-15f, 15f),
                        q = next.q.coerceIn(0.3f, 12f)
                    )
                } else b
            }
            if (params.eqChannelLinked) {
                val updated = applyToList(params.eqBandsFor(activeCh))
                params.copy(eqBandsL = updated, eqBandsR = updated)
            } else if (activeCh == DspChannel.CH_L) {
                params.copy(eqBandsL = applyToList(params.eqBandsL))
            } else {
                params.copy(eqBandsR = applyToList(params.eqBandsR))
            }
        }
        dspController.updateEq(_uiState.value.parameters)
    }

    fun setEqBandShape(index: Int, shape: EqFilterShape) {
        updateEqBand(index) { it.copy(shape = shape) }
    }

    // Per-Channel Crossover Stage
    fun toggleXoverLinked() {
        val activeCh = _uiState.value.selectedDspChannel
        updateParameters(markModified = true) { p ->
            val next = !p.xoverLinked
            if (!next) {
                p.copy(xoverLinked = false)
            } else {
                val byp = p.xoverBypassFor(activeCh)
                val hpfEn = p.hpfEnabledFor(activeCh)
                val hpfFreq = p.hpfFreqHzFor(activeCh)
                val hpfType = p.hpfFilterTypeFor(activeCh)
                val hpfSlope = p.hpfSlopeDbFor(activeCh)
                val lpfEn = p.lpfEnabledFor(activeCh)
                val lpfFreq = p.lpfFreqHzFor(activeCh)
                val lpfType = p.lpfFilterTypeFor(activeCh)
                val lpfSlope = p.lpfSlopeDbFor(activeCh)
                p.copy(
                    xoverLinked = true,
                    xoverBypassL = byp,
                    hpfEnabledL = hpfEn,
                    hpfFreqHzL = hpfFreq,
                    hpfFilterTypeL = hpfType,
                    hpfSlopeDbL = hpfSlope,
                    lpfEnabledL = lpfEn,
                    lpfFreqHzL = lpfFreq,
                    lpfFilterTypeL = lpfType,
                    lpfSlopeDbL = lpfSlope,
                    xoverBypassR = byp,
                    hpfEnabledR = hpfEn,
                    hpfFreqHzR = hpfFreq,
                    hpfFilterTypeR = hpfType,
                    hpfSlopeDbR = hpfSlope,
                    lpfEnabledR = lpfEn,
                    lpfFreqHzR = lpfFreq,
                    lpfFilterTypeR = lpfType,
                    lpfSlopeDbR = lpfSlope
                )
            }
        }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    private fun updateActiveXover(transformL: (DspParameters) -> DspParameters, transformR: (DspParameters) -> DspParameters) {
        val activeCh = _uiState.value.selectedDspChannel
        updateParameters(markModified = true) { p ->
            when {
                p.xoverLinked -> transformR(transformL(p))
                activeCh == DspChannel.CH_L -> transformL(p)
                else -> transformR(p)
            }
        }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    fun toggleXoverBypass() {
        val next = !_uiState.value.parameters.xoverBypassFor(_uiState.value.selectedDspChannel)
        updateActiveXover({ it.copy(xoverBypassL = next) }, { it.copy(xoverBypassR = next) })
    }

    fun toggleHpfEnabled() {
        val next = !_uiState.value.parameters.hpfEnabledFor(_uiState.value.selectedDspChannel)
        updateActiveXover({ it.copy(hpfEnabledL = next) }, { it.copy(hpfEnabledR = next) })
    }

    fun setHpfFreq(freqHz: Float) {
        val clamped = freqHz.coerceIn(20f, 5000f)
        updateActiveXover({ it.copy(hpfFreqHzL = clamped) }, { it.copy(hpfFreqHzR = clamped) })
    }

    fun setHpfFilterType(type: XoverFilterType) {
        updateActiveXover({ it.copy(hpfFilterTypeL = type) }, { it.copy(hpfFilterTypeR = type) })
    }

    fun setHpfSlopeDb(slope: XoverSlopeDb) {
        updateActiveXover({ it.copy(hpfSlopeDbL = slope) }, { it.copy(hpfSlopeDbR = slope) })
    }

    fun toggleLpfEnabled() {
        val next = !_uiState.value.parameters.lpfEnabledFor(_uiState.value.selectedDspChannel)
        updateActiveXover({ it.copy(lpfEnabledL = next) }, { it.copy(lpfEnabledR = next) })
    }

    fun setLpfFreq(freqHz: Float) {
        val clamped = freqHz.coerceIn(200f, 20000f)
        updateActiveXover({ it.copy(lpfFreqHzL = clamped) }, { it.copy(lpfFreqHzR = clamped) })
    }

    fun setLpfFilterType(type: XoverFilterType) {
        updateActiveXover({ it.copy(lpfFilterTypeL = type) }, { it.copy(lpfFilterTypeR = type) })
    }

    fun setLpfSlopeDb(slope: XoverSlopeDb) {
        updateActiveXover({ it.copy(lpfSlopeDbL = slope) }, { it.copy(lpfSlopeDbR = slope) })
    }

    // Per-Channel Delay Stage
    fun toggleDelayEnabledL() {
        updateParameters(markModified = true) { p ->
            val next = !p.delayEnabledL
            p.copy(
                delayEnabledL = next,
                delayEnabledR = if (p.delayLinked) next else p.delayEnabledR
            )
        }
        dspController.updateDelay(_uiState.value.parameters)
    }

    fun toggleDelayEnabledR() {
        updateParameters(markModified = true) { p ->
            val next = !p.delayEnabledR
            p.copy(
                delayEnabledR = next,
                delayEnabledL = if (p.delayLinked) next else p.delayEnabledL
            )
        }
        dspController.updateDelay(_uiState.value.parameters)
    }

    fun toggleDelayLinked() {
        updateParameters(markModified = true) { p ->
            val nextLinked = !p.delayLinked
            p.copy(
                delayLinked = nextLinked,
                delayEnabledR = if (nextLinked) p.delayEnabledL else p.delayEnabledR,
                delayMsR = if (nextLinked) p.delayMsL else p.delayMsR
            )
        }
        dspController.updateDelay(_uiState.value.parameters)
    }

    fun setDelayMsL(ms: Float) {
        val clamped = ms.coerceIn(0f, 100f)
        updateParameters(markModified = true) { p ->
            p.copy(delayMsL = clamped, delayMsR = if (p.delayLinked) clamped else p.delayMsR)
        }
        dspController.updateDelay(_uiState.value.parameters)
    }

    fun setDelayMsR(ms: Float) {
        val clamped = ms.coerceIn(0f, 100f)
        updateParameters(markModified = true) { p ->
            p.copy(delayMsR = clamped, delayMsL = if (p.delayLinked) clamped else p.delayMsL)
        }
        dspController.updateDelay(_uiState.value.parameters)
    }

    // Per-Channel / Linked Limiter Stage
    fun toggleLimiterLinked() {
        val activeCh = _uiState.value.selectedDspChannel
        updateParameters(markModified = true) { p ->
            val next = !p.limiterLinked
            if (!next) {
                p.copy(limiterLinked = false)
            } else {
                val en = p.limiterEnabledFor(activeCh)
                val thr = p.limiterThresholdDbFor(activeCh)
                val atk = p.limiterAttackMsFor(activeCh)
                val rel = p.limiterReleaseMsFor(activeCh)
                p.copy(
                    limiterLinked = true,
                    limiterEnabledL = en,
                    limiterThresholdDbL = thr,
                    limiterAttackMsL = atk,
                    limiterReleaseMsL = rel,
                    limiterEnabledR = en,
                    limiterThresholdDbR = thr,
                    limiterAttackMsR = atk,
                    limiterReleaseMsR = rel
                )
            }
        }
        dspController.updateLimiter(_uiState.value.parameters)
    }

    private fun updateActiveLimiter(transformL: (DspParameters) -> DspParameters, transformR: (DspParameters) -> DspParameters) {
        val activeCh = _uiState.value.selectedDspChannel
        updateParameters(markModified = true) { p ->
            when {
                p.limiterLinked -> transformR(transformL(p))
                activeCh == DspChannel.CH_L -> transformL(p)
                else -> transformR(p)
            }
        }
        dspController.updateLimiter(_uiState.value.parameters)
    }

    fun toggleLimiterEnabled() {
        val next = !_uiState.value.parameters.limiterEnabledFor(_uiState.value.selectedDspChannel)
        updateActiveLimiter({ it.copy(limiterEnabledL = next) }, { it.copy(limiterEnabledR = next) })
    }

    fun setLimiterThreshold(db: Float) {
        val clamped = db.coerceIn(-24f, 0f)
        updateActiveLimiter({ it.copy(limiterThresholdDbL = clamped) }, { it.copy(limiterThresholdDbR = clamped) })
    }

    fun setLimiterAttack(ms: Float) {
        val clamped = ms.coerceIn(0.5f, 50f)
        updateActiveLimiter({ it.copy(limiterAttackMsL = clamped) }, { it.copy(limiterAttackMsR = clamped) })
    }

    fun setLimiterRelease(ms: Float) {
        val clamped = ms.coerceIn(20f, 1000f)
        updateActiveLimiter({ it.copy(limiterReleaseMsL = clamped) }, { it.copy(limiterReleaseMsR = clamped) })
    }

    // Output Stage (Independent L/R + Link)
    fun toggleOutputLinked() {
        updateParameters(markModified = true) { p ->
            val next = !p.outputLinked
            p.copy(
                outputLinked = next,
                outputGainDbR = if (next) p.outputGainDbL else p.outputGainDbR
            )
        }
        dspController.updateOutput(_uiState.value.parameters)
    }

    fun setOutputGainL(gainDb: Float) {
        val clamped = gainDb.coerceIn(-40f, 12f)
        updateParameters(markModified = true) { p ->
            p.copy(
                outputGainDbL = clamped,
                outputGainDbR = if (p.outputLinked) clamped else p.outputGainDbR
            )
        }
        dspController.updateOutput(_uiState.value.parameters)
    }

    fun setOutputGainR(gainDb: Float) {
        val clamped = gainDb.coerceIn(-40f, 12f)
        updateParameters(markModified = true) { p ->
            p.copy(
                outputGainDbR = clamped,
                outputGainDbL = if (p.outputLinked) clamped else p.outputGainDbL
            )
        }
        dspController.updateOutput(_uiState.value.parameters)
    }

    fun toggleOutputMuteL() {
        updateParameters(markModified = true) { it.copy(outputMuteL = !it.outputMuteL) }
        dspController.updateOutput(_uiState.value.parameters)
    }

    fun toggleOutputMuteR() {
        updateParameters(markModified = true) { it.copy(outputMuteR = !it.outputMuteR) }
        dspController.updateOutput(_uiState.value.parameters)
    }

    fun toggleOutputInvertL() {
        updateParameters(markModified = true) { it.copy(outputInvertL = !it.outputInvertL) }
        dspController.updateOutput(_uiState.value.parameters)
    }

    fun toggleOutputInvertR() {
        updateParameters(markModified = true) { it.copy(outputInvertR = !it.outputInvertR) }
        dspController.updateOutput(_uiState.value.parameters)
    }

    // Preset Management (Save, Load, Rename, Overwrite, Delete, Reset)
    fun recallPreset(preset: DspPreset) {
        val currentTestSignal = _uiState.value.parameters.testSignalMode
        val nextParams = preset.parameters.copy(testSignalMode = currentTestSignal)
        _uiState.update {
            it.copy(
                parameters = nextParams,
                activePresetId = preset.id,
                activePresetName = preset.name,
                isPresetModified = false
            )
        }
        dspController.pushAllParameters(nextParams)
        viewModelScope.launch { presetManager.setLastPresetId(preset.id) }
    }

    fun resetActivePreset() {
        val active = _uiState.value.presets.firstOrNull { it.id == _uiState.value.activePresetId }
            ?: FactoryPresets.all.first()
        recallPreset(active)
    }

    fun saveUserPreset(name: String, description: String) {
        val currentParams = _uiState.value.parameters
        viewModelScope.launch {
            val updated = presetManager.saveUserPreset(name, description, currentParams)
            val created = updated.last()
            _uiState.update {
                it.copy(
                    presets = updated,
                    activePresetId = created.id,
                    activePresetName = created.name,
                    isPresetModified = false
                )
            }
        }
    }

    fun renameUserPreset(id: String, newName: String, newDescription: String) {
        viewModelScope.launch {
            val updated = presetManager.renameUserPreset(id, newName, newDescription)
            val renamed = updated.firstOrNull { it.id == id }
            _uiState.update { state ->
                state.copy(
                    presets = updated,
                    activePresetName = if (state.activePresetId == id && renamed != null) renamed.name else state.activePresetName
                )
            }
        }
    }

    fun overwriteUserPreset(id: String) {
        val currentParams = _uiState.value.parameters
        viewModelScope.launch {
            val updated = presetManager.overwriteUserPreset(id, currentParams)
            _uiState.update {
                it.copy(presets = updated, isPresetModified = false)
            }
        }
    }

    fun deleteUserPreset(id: String) {
        viewModelScope.launch {
            val updated = presetManager.deleteUserPreset(id)
            _uiState.update { state ->
                val activeStillExists = updated.any { it.id == state.activePresetId }
                if (activeStillExists) {
                    state.copy(presets = updated)
                } else {
                    val fallback = updated.first()
                    dspController.pushAllParameters(fallback.parameters)
                    state.copy(
                        presets = updated,
                        activePresetId = fallback.id,
                        activePresetName = fallback.name,
                        parameters = fallback.parameters,
                        isPresetModified = false
                    )
                }
            }
        }
    }

    fun resetAllPresetsToFactory() {
        viewModelScope.launch {
            val factoryOnly = presetManager.resetAllUserPresets()
            val flat = factoryOnly.first()
            val nextParams = flat.parameters.copy(testSignalMode = _uiState.value.parameters.testSignalMode)
            _uiState.update {
                it.copy(
                    presets = factoryOnly,
                    activePresetId = flat.id,
                    activePresetName = flat.name,
                    parameters = nextParams,
                    isPresetModified = false
                )
            }
            dspController.pushAllParameters(nextParams)
        }
    }

    // System / Audio Device Routing
    fun selectInputDevice(deviceId: Int) {
        _uiState.update { it.copy(selectedInputDeviceId = deviceId) }
        restartAudioEngine()
    }

    fun selectOutputDevice(deviceId: Int) {
        _uiState.update { it.copy(selectedOutputDeviceId = deviceId) }
        restartAudioEngine()
    }

    fun selectSampleRate(sampleRate: Int) {
        _uiState.update { it.copy(requestedSampleRate = sampleRate) }
        restartAudioEngine()
    }

    fun selectBufferFrames(frames: Int) {
        _uiState.update { it.copy(requestedBufferFrames = frames) }
        restartAudioEngine()
    }

    fun refreshHardwareDevices() {
        audioEngine.refreshDevices()
    }

    private fun updateParameters(markModified: Boolean, transform: (DspParameters) -> DspParameters) {
        _uiState.update { state ->
            state.copy(
                parameters = transform(state.parameters),
                isPresetModified = if (markModified) true else state.isPresetModified
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        localAudioPlayer.release()
        dspController.stopAudioEngine()
        audioEngine.release()
    }
}
