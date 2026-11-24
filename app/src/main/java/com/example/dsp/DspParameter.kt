package com.example.dsp

import com.example.audio.DspChannel
import com.example.audio.InputSourceRoute
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

    // Input Stage (2 Physical/Logical Inputs: IN 1 = LEFT, IN 2 = RIGHT + Routing Matrix)
    val inputGainDbL: Float = 0f,
    val inputGainDbR: Float = 0f,
    val inputLinked: Boolean = true,
    val inputMuteL: Boolean = false,
    val inputMuteR: Boolean = false,
    val inputInvertL: Boolean = false,
    val inputInvertR: Boolean = false,
    val routeSourceL: InputSourceRoute = InputSourceRoute.IN_1_LEFT,
    val routeSourceR: InputSourceRoute = InputSourceRoute.IN_2_RIGHT,

    // Per-Channel 5-Band Parametric EQ (Channel L & Channel R)
    val eqChannelLinked: Boolean = true,
    val eqBypassL: Boolean = false,
    val eqBypassR: Boolean = false,
    val eqBandsL: List<EqBandState> = defaultEqBands(),
    val eqBandsR: List<EqBandState> = defaultEqBands(),

    // Per-Channel Crossover (Channel L & Channel R: HPF + LPF with BW/LR + 12/24/48 dB/oct)
    val xoverLinked: Boolean = true,
    val xoverBypassL: Boolean = false,
    val hpfEnabledL: Boolean = true,
    val hpfFreqHzL: Float = 40f,
    val hpfFilterTypeL: XoverFilterType = XoverFilterType.LINKWITZ_RILEY,
    val hpfSlopeDbL: XoverSlopeDb = XoverSlopeDb.SLOPE_24,
    val lpfEnabledL: Boolean = false,
    val lpfFreqHzL: Float = 18000f,
    val lpfFilterTypeL: XoverFilterType = XoverFilterType.LINKWITZ_RILEY,
    val lpfSlopeDbL: XoverSlopeDb = XoverSlopeDb.SLOPE_24,

    val xoverBypassR: Boolean = false,
    val hpfEnabledR: Boolean = true,
    val hpfFreqHzR: Float = 40f,
    val hpfFilterTypeR: XoverFilterType = XoverFilterType.LINKWITZ_RILEY,
    val hpfSlopeDbR: XoverSlopeDb = XoverSlopeDb.SLOPE_24,
    val lpfEnabledR: Boolean = false,
    val lpfFreqHzR: Float = 18000f,
    val lpfFilterTypeR: XoverFilterType = XoverFilterType.LINKWITZ_RILEY,
    val lpfSlopeDbR: XoverSlopeDb = XoverSlopeDb.SLOPE_24,

    // Per-Channel Delay
    val delayEnabledL: Boolean = false,
    val delayEnabledR: Boolean = false,
    val delayLinked: Boolean = true,
    val delayMsL: Float = 0f,
    val delayMsR: Float = 0f,

    // Per-Channel / Linked Limiter
    val limiterLinked: Boolean = true,
    val limiterEnabledL: Boolean = true,
    val limiterThresholdDbL: Float = -1.5f,
    val limiterAttackMsL: Float = 2.0f,
    val limiterReleaseMsL: Float = 140f,
    val limiterEnabledR: Boolean = true,
    val limiterThresholdDbR: Float = -1.5f,
    val limiterAttackMsR: Float = 2.0f,
    val limiterReleaseMsR: Float = 140f,

    // Output Stage (2 Outputs: OUT 1 = LEFT, OUT 2 = RIGHT)
    val outputGainDbL: Float = 0f,
    val outputGainDbR: Float = 0f,
    val outputLinked: Boolean = true,
    val outputMuteL: Boolean = false,
    val outputMuteR: Boolean = false,
    val outputInvertL: Boolean = false,
    val outputInvertR: Boolean = false
) {
    fun routeSourceFor(channel: DspChannel): InputSourceRoute =
        if (channel == DspChannel.CH_L) routeSourceL else routeSourceR

    fun eqBypassFor(channel: DspChannel): Boolean =
        if (channel == DspChannel.CH_L) eqBypassL else eqBypassR

    fun eqBandsFor(channel: DspChannel): List<EqBandState> =
        if (channel == DspChannel.CH_L) eqBandsL else eqBandsR

    fun xoverBypassFor(channel: DspChannel): Boolean =
        if (channel == DspChannel.CH_L) xoverBypassL else xoverBypassR

    fun hpfEnabledFor(channel: DspChannel): Boolean =
        if (channel == DspChannel.CH_L) hpfEnabledL else hpfEnabledR

    fun hpfFreqHzFor(channel: DspChannel): Float =
        if (channel == DspChannel.CH_L) hpfFreqHzL else hpfFreqHzR

    fun hpfFilterTypeFor(channel: DspChannel): XoverFilterType =
        if (channel == DspChannel.CH_L) hpfFilterTypeL else hpfFilterTypeR

    fun hpfSlopeDbFor(channel: DspChannel): XoverSlopeDb =
        if (channel == DspChannel.CH_L) hpfSlopeDbL else hpfSlopeDbR

    fun lpfEnabledFor(channel: DspChannel): Boolean =
        if (channel == DspChannel.CH_L) lpfEnabledL else lpfEnabledR

    fun lpfFreqHzFor(channel: DspChannel): Float =
        if (channel == DspChannel.CH_L) lpfFreqHzL else lpfFreqHzR

    fun lpfFilterTypeFor(channel: DspChannel): XoverFilterType =
        if (channel == DspChannel.CH_L) lpfFilterTypeL else lpfFilterTypeR

    fun lpfSlopeDbFor(channel: DspChannel): XoverSlopeDb =
        if (channel == DspChannel.CH_L) lpfSlopeDbL else lpfSlopeDbR

    fun limiterEnabledFor(channel: DspChannel): Boolean =
        if (channel == DspChannel.CH_L || limiterLinked) limiterEnabledL else limiterEnabledR

    fun limiterThresholdDbFor(channel: DspChannel): Float =
        if (channel == DspChannel.CH_L || limiterLinked) limiterThresholdDbL else limiterThresholdDbR

    fun limiterAttackMsFor(channel: DspChannel): Float =
        if (channel == DspChannel.CH_L || limiterLinked) limiterAttackMsL else limiterAttackMsR

    fun limiterReleaseMsFor(channel: DspChannel): Float =
        if (channel == DspChannel.CH_L || limiterLinked) limiterReleaseMsL else limiterReleaseMsR

    val anyEqActive: Boolean
        get() = !eqBypassL || !eqBypassR

    val anyXoverActive: Boolean
        get() = (!xoverBypassL && (hpfEnabledL || lpfEnabledL)) ||
            (!xoverBypassR && (hpfEnabledR || lpfEnabledR))

    val anyDelayActive: Boolean
        get() = delayEnabledL || delayEnabledR

    val anyLimiterActive: Boolean
        get() = limiterEnabledL || (!limiterLinked && limiterEnabledR)

    val anyInputActive: Boolean
        get() = !inputMuteL || !inputMuteR

    companion object {
        fun defaultEqBands(): List<EqBandState> = listOf(
            EqBandState(0, "B1 LOW", true, EqFilterShape.LOW_SHELF, 65f, 0f, 0.8f),
            EqBandState(1, "B2 L-MID", true, EqFilterShape.PEAK, 220f, 0f, 1.2f),
            EqBandState(2, "B3 MID", true, EqFilterShape.PEAK, 800f, 0f, 1.2f),
            EqBandState(3, "B4 H-MID", true, EqFilterShape.PEAK, 2800f, 0f, 1.2f),
            EqBandState(4, "B5 HIGH", true, EqFilterShape.HIGH_SHELF, 9500f, 0f, 0.8f)
        )

        fun stereoLinked(
            bypassDsp: Boolean = false,
            muteAll: Boolean = false,
            eqBypass: Boolean = false,
            eqBands: List<EqBandState> = defaultEqBands(),
            xoverBypass: Boolean = false,
            hpfEnabled: Boolean = true,
            hpfFreqHz: Float = 40f,
            hpfFilterType: XoverFilterType = XoverFilterType.LINKWITZ_RILEY,
            hpfSlopeDb: XoverSlopeDb = XoverSlopeDb.SLOPE_24,
            lpfEnabled: Boolean = false,
            lpfFreqHz: Float = 18000f,
            lpfFilterType: XoverFilterType = XoverFilterType.LINKWITZ_RILEY,
            lpfSlopeDb: XoverSlopeDb = XoverSlopeDb.SLOPE_24,
            limiterEnabled: Boolean = true,
            limiterThresholdDb: Float = -1.5f,
            limiterAttackMs: Float = 2.0f,
            limiterReleaseMs: Float = 140f
        ): DspParameters = DspParameters(
            bypassDsp = bypassDsp,
            muteAll = muteAll,
            eqChannelLinked = true,
            eqBypassL = eqBypass,
            eqBypassR = eqBypass,
            eqBandsL = eqBands,
            eqBandsR = eqBands,
            xoverLinked = true,
            xoverBypassL = xoverBypass,
            hpfEnabledL = hpfEnabled,
            hpfFreqHzL = hpfFreqHz,
            hpfFilterTypeL = hpfFilterType,
            hpfSlopeDbL = hpfSlopeDb,
            lpfEnabledL = lpfEnabled,
            lpfFreqHzL = lpfFreqHz,
            lpfFilterTypeL = lpfFilterType,
            lpfSlopeDbL = lpfSlopeDb,
            xoverBypassR = xoverBypass,
            hpfEnabledR = hpfEnabled,
            hpfFreqHzR = hpfFreqHz,
            hpfFilterTypeR = hpfFilterType,
            hpfSlopeDbR = hpfSlopeDb,
            lpfEnabledR = lpfEnabled,
            lpfFreqHzR = lpfFreqHz,
            lpfFilterTypeR = lpfFilterType,
            lpfSlopeDbR = lpfSlopeDb,
            limiterLinked = true,
            limiterEnabledL = limiterEnabled,
            limiterThresholdDbL = limiterThresholdDb,
            limiterAttackMsL = limiterAttackMs,
            limiterReleaseMsL = limiterReleaseMs,
            limiterEnabledR = limiterEnabled,
            limiterThresholdDbR = limiterThresholdDb,
            limiterAttackMsR = limiterAttackMs,
            limiterReleaseMsR = limiterReleaseMs
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
