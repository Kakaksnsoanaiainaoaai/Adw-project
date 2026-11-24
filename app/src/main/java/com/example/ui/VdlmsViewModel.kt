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

    // Input Stage
    fun setInputGain(gainDb: Float) {
        updateParameters(markModified = true) { it.copy(inputGainDb = gainDb.coerceIn(-24f, 12f)) }
        dspController.updateInput(_uiState.value.parameters)
    }

    fun toggleInputMute() {
        updateParameters(markModified = true) { it.copy(inputMute = !it.inputMute) }
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

    // Parametric EQ Stage (5 Bands: PEAK / LOW_SHELF / HIGH_SHELF)
    fun toggleEqBypass() {
        updateParameters(markModified = true) { it.copy(eqBypass = !it.eqBypass) }
        dspController.updateEq(_uiState.value.parameters)
    }

    fun updateEqBand(index: Int, transform: (EqBandState) -> EqBandState) {
        updateParameters(markModified = true) { params ->
            val updated = params.eqBands.map { b ->
                if (b.index == index) {
                    val next = transform(b)
                    next.copy(
                        freqHz = next.freqHz.coerceIn(20f, 20000f),
                        gainDb = next.gainDb.coerceIn(-15f, 15f),
                        q = next.q.coerceIn(0.3f, 12f)
                    )
                } else b
            }
            params.copy(eqBands = updated)
        }
        dspController.updateEq(_uiState.value.parameters)
    }

    fun setEqBandShape(index: Int, shape: EqFilterShape) {
        updateEqBand(index) { it.copy(shape = shape) }
    }

    // Crossover Stage
    fun toggleXoverBypass() {
        updateParameters(markModified = true) { it.copy(xoverBypass = !it.xoverBypass) }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    fun toggleHpfEnabled() {
        updateParameters(markModified = true) { it.copy(hpfEnabled = !it.hpfEnabled) }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    fun setHpfFreq(freqHz: Float) {
        updateParameters(markModified = true) { it.copy(hpfFreqHz = freqHz.coerceIn(20f, 5000f)) }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    fun setHpfFilterType(type: XoverFilterType) {
        updateParameters(markModified = true) { it.copy(hpfFilterType = type) }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    fun setHpfSlopeDb(slope: XoverSlopeDb) {
        updateParameters(markModified = true) { it.copy(hpfSlopeDb = slope) }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    fun toggleLpfEnabled() {
        updateParameters(markModified = true) { it.copy(lpfEnabled = !it.lpfEnabled) }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    fun setLpfFreq(freqHz: Float) {
        updateParameters(markModified = true) { it.copy(lpfFreqHz = freqHz.coerceIn(200f, 20000f)) }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    fun setLpfFilterType(type: XoverFilterType) {
        updateParameters(markModified = true) { it.copy(lpfFilterType = type) }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    fun setLpfSlopeDb(slope: XoverSlopeDb) {
        updateParameters(markModified = true) { it.copy(lpfSlopeDb = slope) }
        dspController.updateCrossover(_uiState.value.parameters)
    }

    // Delay Stage
    fun toggleDelayEnabled() {
        updateParameters(markModified = true) { it.copy(delayEnabled = !it.delayEnabled) }
        dspController.updateDelay(_uiState.value.parameters)
    }

    fun toggleDelayLinked() {
        updateParameters(markModified = true) { p ->
            val nextLinked = !p.delayLinked
            p.copy(delayLinked = nextLinked, delayMsR = if (nextLinked) p.delayMsL else p.delayMsR)
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

    // Limiter Stage
    fun toggleLimiterEnabled() {
        updateParameters(markModified = true) { it.copy(limiterEnabled = !it.limiterEnabled) }
        dspController.updateLimiter(_uiState.value.parameters)
    }

    fun setLimiterThreshold(db: Float) {
        updateParameters(markModified = true) { it.copy(limiterThresholdDb = db.coerceIn(-24f, 0f)) }
        dspController.updateLimiter(_uiState.value.parameters)
    }

    fun setLimiterAttack(ms: Float) {
        updateParameters(markModified = true) { it.copy(limiterAttackMs = ms.coerceIn(0.5f, 50f)) }
        dspController.updateLimiter(_uiState.value.parameters)
    }

    fun setLimiterRelease(ms: Float) {
        updateParameters(markModified = true) { it.copy(limiterReleaseMs = ms.coerceIn(20f, 1000f)) }
        dspController.updateLimiter(_uiState.value.parameters)
    }

    // Output Stage
    fun setOutputGain(gainDb: Float) {
        updateParameters(markModified = true) { it.copy(outputGainDb = gainDb.coerceIn(-40f, 12f)) }
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
