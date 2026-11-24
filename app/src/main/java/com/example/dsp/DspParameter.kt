package com.example.dsp

import com.example.audio.TestSignalMode

enum class DspModuleTab(val label: String, val shortCode: String) {
    INPUT("INPUT", "IN"),
    EQ("EQ", "PEQ"),
    XOVER("XOVER", "XO"),
    DELAY("DELAY", "DLY"),
    LIMITER("LIMITER", "LIM"),
    OUTPUT("OUTPUT", "OUT"),
    PLAYER("PLAYER", "PLY"),
    PRESET("PRESET", "MEM"),
    SYSTEM("SYSTEM", "SYS")
}

enum class EqFilterShape(val code: Int, val label: String, val shortLabel: String) {
    PEAK(0, "Peak", "PEAK"),
    LOW_SHELF(1, "Low Shelf", "L-SHF"),
    HIGH_SHELF(2, "High Shelf", "H-SHF")
}

enum class XoverFilterType(val code: Int, val label: String, val shortLabel: String) {
    BUTTERWORTH(0, "Butterworth", "BW"),
    LINKWITZ_RILEY(1, "Linkwitz-Riley", "LR")
}

enum class XoverSlopeDb(val slopeDb: Int, val label: String, val shortLabel: String) {
    OFF(0, "Bypass / Off", "OFF"),
    SLOPE_12(12, "12 dB/oct", "12dB"),
    SLOPE_24(24, "24 dB/oct", "24dB"),
    SLOPE_48(48, "48 dB/oct", "48dB")
}

data class EqBandState(
    val index: Int,
    val name: String,
    val enabled: Boolean = true,
    val shape: EqFilterShape = EqFilterShape.PEAK,
    val freqHz: Float,
    val gainDb: Float = 0f,
    val q: Float = 1.1f
)

data class DspParameters(
    val bypassDsp: Boolean = false,
    val muteAll: Boolean = false,
    val testSignalMode: TestSignalMode = TestSignalMode.LIVE_INPUT,

    // Input Stage
    val inputGainDb: Float = 0f,
    val inputMute: Boolean = false,
    val inputInvertL: Boolean = false,
    val inputInvertR: Boolean = false,

    // 5-Band Parametric EQ (Peak / Low Shelf / High Shelf)
    val eqBypass: Boolean = false,
    val eqBands: List<EqBandState> = defaultEqBands(),

    // Crossover (HPF + LPF with Filter Type + 12/24/48 dB/oct Slope)
    val xoverBypass: Boolean = false,
    val hpfEnabled: Boolean = true,
    val hpfFreqHz: Float = 40f,
    val hpfFilterType: XoverFilterType = XoverFilterType.LINKWITZ_RILEY,
    val hpfSlopeDb: XoverSlopeDb = XoverSlopeDb.SLOPE_24,
    val lpfEnabled: Boolean = false,
    val lpfFreqHz: Float = 18000f,
    val lpfFilterType: XoverFilterType = XoverFilterType.LINKWITZ_RILEY,
    val lpfSlopeDb: XoverSlopeDb = XoverSlopeDb.SLOPE_24,

    // Per-Channel Delay
    val delayEnabled: Boolean = false,
    val delayLinked: Boolean = true,
    val delayMsL: Float = 0f,
    val delayMsR: Float = 0f,

    // Limiter
    val limiterEnabled: Boolean = true,
    val limiterThresholdDb: Float = -1.5f,
    val limiterAttackMs: Float = 2.0f,
    val limiterReleaseMs: Float = 140f,

    // Output Stage
    val outputGainDb: Float = 0f,
    val outputMuteL: Boolean = false,
    val outputMuteR: Boolean = false,
    val outputInvertL: Boolean = false,
    val outputInvertR: Boolean = false
) {
    companion object {
        fun defaultEqBands(): List<EqBandState> = listOf(
            EqBandState(0, "B1 LOW", true, EqFilterShape.LOW_SHELF, 65f, 0f, 0.8f),
            EqBandState(1, "B2 L-MID", true, EqFilterShape.PEAK, 220f, 0f, 1.2f),
            EqBandState(2, "B3 MID", true, EqFilterShape.PEAK, 800f, 0f, 1.2f),
            EqBandState(3, "B4 H-MID", true, EqFilterShape.PEAK, 2800f, 0f, 1.2f),
            EqBandState(4, "B5 HIGH", true, EqFilterShape.HIGH_SHELF, 9500f, 0f, 0.8f)
        )
    }
}

data class DspPreset(
    val id: String,
    val slotNumber: String,
    val name: String,
    val description: String,
    val isFactory: Boolean,
    val parameters: DspParameters
)
