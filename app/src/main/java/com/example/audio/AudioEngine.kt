package com.example.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates AudioDeviceManager (USB + Android device inventory) with NativeAudioBridge
 * so connected USB Audio Interfaces are automatically prioritized when Auto routing is selected.
 */
class AudioEngine(
    private val deviceManager: AudioDeviceManager,
    val bridge: AudioEngineBridge
) {
    val hardwareInventory: StateFlow<AudioHardwareInventory> = deviceManager.inventory

    fun start(
        requestedInputId: Int,
        requestedOutputId: Int,
        requestedSampleRate: Int,
        requestedBufferFrames: Int,
        hasRecordPermission: Boolean
    ): Boolean {
        deviceManager.refreshDevices()
        val resolvedInput = deviceManager.resolvePreferredInputDeviceId(requestedInputId)
        val resolvedOutput = deviceManager.resolvePreferredOutputDeviceId(requestedOutputId)

        val started = bridge.startEngine(
            inputDeviceId = resolvedInput,
            outputDeviceId = resolvedOutput,
            requestedSampleRate = requestedSampleRate,
            requestedBufferFrames = requestedBufferFrames,
            hasRecordPermission = hasRecordPermission
        )
        if (!started && (resolvedInput != 0 || resolvedOutput != 0)) {
            // Safe fallback to default Android routing if USB endpoint failed to open
            return bridge.startEngine(
                inputDeviceId = 0,
                outputDeviceId = 0,
                requestedSampleRate = requestedSampleRate,
                requestedBufferFrames = requestedBufferFrames,
                hasRecordPermission = hasRecordPermission
            )
        }
        return started
    }

    fun stop() {
        bridge.stopEngine()
    }

    fun refreshDevices() {
        deviceManager.refreshDevices()
    }

    fun release() {
        bridge.stopEngine()
        deviceManager.release()
    }
}
