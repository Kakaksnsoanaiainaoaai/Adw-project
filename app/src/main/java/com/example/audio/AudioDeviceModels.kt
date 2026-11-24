package com.example.audio

enum class AudioEngineState(val code: Int, val displayLabel: String) {
    STOPPED(0, "STOPPED"),
    RUNNING_DUPLEX(1, "LIVE DUPLEX"),
    RUNNING_OUTPUT_ONLY(2, "OUT-ONLY / TEST"),
    DEV_FALLBACK(3, "DEV FALLBACK");

    companion object {
        fun fromCode(code: Int): AudioEngineState =
            entries.firstOrNull { it.code == code } ?: STOPPED
    }
}

enum class AudioApiType(val code: Int, val label: String) {
    UNSPECIFIED(0, "AUTO"),
    AAUDIO(1, "AAudio"),
    OPENSL_ES(2, "OpenSL ES");

    companion object {
        fun fromCode(code: Int): AudioApiType =
            entries.firstOrNull { it.code == code } ?: UNSPECIFIED
    }
}

enum class TestSignalMode(val code: Int, val label: String) {
    LIVE_INPUT(0, "LIVE INPUT"),
    SINE_1KHZ(1, "1kHz SINE (-18 dBFS)"),
    PINK_NOISE(2, "PINK NOISE (-18 dBFS)")
}

data class AudioDeviceEndpoint(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isUsb: Boolean,
    val isInput: Boolean,
    val sampleRates: List<Int>,
    val channelCounts: List<Int>
)

data class AudioTelemetry(
    val inputPeakL: Float = 0f,
    val inputPeakR: Float = 0f,
    val inputRmsL: Float = 0f,
    val inputRmsR: Float = 0f,
    val outputPeakL: Float = 0f,
    val outputPeakR: Float = 0f,
    val outputRmsL: Float = 0f,
    val outputRmsR: Float = 0f,
    val inputHoldL: Float = 0f,
    val inputHoldR: Float = 0f,
    val outputHoldL: Float = 0f,
    val outputHoldR: Float = 0f,
    val limiterGrDb: Float = 0f,
    val sampleRate: Int = 48000,
    val bufferSizeFrames: Int = 0,
    val framesPerBurst: Int = 0,
    val inputChannels: Int = 0,
    val outputChannels: Int = 2,
    val xRunCount: Int = 0,
    val engineState: AudioEngineState = AudioEngineState.STOPPED,
    val audioApi: AudioApiType = AudioApiType.UNSPECIFIED,
    val cpuLoadPercent: Float = 0f,
    val streamDisconnected: Boolean = false
)
