package com.example.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioHardwareInventory(
    val inputDevices: List<AudioDeviceEndpoint> = emptyList(),
    val outputDevices: List<AudioDeviceEndpoint> = emptyList(),
    val hasUsbInput: Boolean = false,
    val hasUsbOutput: Boolean = false,
    val usbDeviceName: String? = null,
    val preferredSampleRate: Int = 48000,
    val preferredFramesPerBurst: Int = 192,
    val supportsLowLatencyFeature: Boolean = false,
    val supportsProAudioFeature: Boolean = false
) {
    val hasUsbAudioInterface: Boolean get() = hasUsbInput || hasUsbOutput
}

class AudioDeviceManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _inventory = MutableStateFlow(AudioHardwareInventory())
    val inventory: StateFlow<AudioHardwareInventory> = _inventory.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }
    }

    init {
        refreshDevices()
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Throwable) {
            // Safe ignore on teardown
        }
    }

    fun resolvePreferredInputDeviceId(requestedId: Int): Int {
        if (requestedId > 0 && _inventory.value.inputDevices.any { it.id == requestedId }) {
            return requestedId
        }
        return _inventory.value.inputDevices.firstOrNull { it.isUsb }?.id ?: 0
    }

    fun resolvePreferredOutputDeviceId(requestedId: Int): Int {
        if (requestedId > 0 && _inventory.value.outputDevices.any { it.id == requestedId }) {
            return requestedId
        }
        return _inventory.value.outputDevices.firstOrNull { it.isUsb }?.id ?: 0
    }

    fun refreshDevices() {
        val inputs = mutableListOf<AudioDeviceEndpoint>()
        val outputs = mutableListOf<AudioDeviceEndpoint>()
        var hasUsbIn = false
        var hasUsbOut = false
        var usbName: String? = null

        val pm = appContext.packageManager
        val lowLatencyFeature = pm.hasSystemFeature("android.hardware.audio.low_latency")
        val proAudioFeature = pm.hasSystemFeature("android.hardware.audio.pro")

        val nativeSr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: 48000
        val nativeBurst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull() ?: 192

        val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        for (dev in rawInputs) {
            if (isInternalTelephonyOrSco(dev.type)) continue
            val isUsb = isUsbAudioType(dev.type)
            val name = buildDeviceName(dev, isInput = true)
            if (isUsb) {
                hasUsbIn = true
                if (usbName == null) usbName = name
            }
            inputs += AudioDeviceEndpoint(
                id = dev.id,
                name = name,
                typeLabel = deviceTypeLabel(dev.type),
                isUsb = isUsb,
                isInput = true,
                sampleRates = dev.sampleRates.toList(),
                channelCounts = dev.channelCounts.toList()
            )
        }

        val rawOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (dev in rawOutputs) {
            if (isInternalTelephonyOrSco(dev.type)) continue
            val isUsb = isUsbAudioType(dev.type)
            val name = buildDeviceName(dev, isInput = false)
            if (isUsb) {
                hasUsbOut = true
                if (usbName == null) usbName = name
            }
            outputs += AudioDeviceEndpoint(
                id = dev.id,
                name = name,
                typeLabel = deviceTypeLabel(dev.type),
                isUsb = isUsb,
                isInput = false,
                sampleRates = dev.sampleRates.toList(),
                channelCounts = dev.channelCounts.toList()
            )
        }

        _inventory.value = AudioHardwareInventory(
            inputDevices = inputs.sortedByDescending { it.isUsb },
            outputDevices = outputs.sortedByDescending { it.isUsb },
            hasUsbInput = hasUsbIn,
            hasUsbOutput = hasUsbOut,
            usbDeviceName = usbName,
            preferredSampleRate = nativeSr,
            preferredFramesPerBurst = nativeBurst,
            supportsLowLatencyFeature = lowLatencyFeature,
            supportsProAudioFeature = proAudioFeature
        )
    }

    private fun isUsbAudioType(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && type == AudioDeviceInfo.TYPE_USB_HEADSET)
    }

    private fun isInternalTelephonyOrSco(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_TELEPHONY ||
            type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    }

    private fun buildDeviceName(dev: AudioDeviceInfo, isInput: Boolean): String {
        val product = dev.productName?.toString()?.trim().orEmpty()
        val typeTag = deviceTypeLabel(dev.type)
        return if (product.isNotEmpty() && !product.equals("Android", ignoreCase = true)) {
            "$product ($typeTag)"
        } else {
            val dir = if (isInput) "Input" else "Output"
            "$typeTag $dir #${dev.id}"
        }
    }

    private fun deviceTypeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Acc"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Line / Phones"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT SCO"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Analog Line"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Digital Line"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                "USB Headset"
            } else {
                "Audio Device"
            }
        }
    }
}
