package com.example.dsp

import com.example.audio.InputSourceRoute

object FactoryPresets {

    val all: List<DspPreset> = listOf(
        DspPreset(
            id = "factory_flat",
            slotNumber = "F01",
            name = "01 FLAT / THRU",
            description = "Transparent stereo reference path (IN1->OUT1, IN2->OUT2) with -1.5 dBFS limiter.",
            isFactory = true,
            parameters = DspParameters.stereoLinked(
                hpfEnabled = false,
                lpfEnabled = false,
                limiterEnabled = true,
                limiterThresholdDb = -1.5f
            )
        ),
        DspPreset(
            id = "factory_pa_fullrange",
            slotNumber = "F02",
            name = "02 FULLRANGE PA",
            description = "45 Hz BW24 subsonic HPF, Low/High Shelf contour, and peak limiter.",
            isFactory = true,
            parameters = DspParameters.stereoLinked(
                hpfEnabled = true,
                hpfFreqHz = 45f,
                hpfFilterType = XoverFilterType.BUTTERWORTH,
                hpfSlopeDb = XoverSlopeDb.SLOPE_24,
                lpfEnabled = false,
                eqBands = listOf(
                    EqBandState(0, "B1 LOW", true, EqFilterShape.LOW_SHELF, 75f, 1.5f, 0.8f),
                    EqBandState(1, "B2 L-MID", true, EqFilterShape.PEAK, 320f, -1.5f, 1.4f),
                    EqBandState(2, "B3 MID", true, EqFilterShape.PEAK, 900f, 0.0f, 1.2f),
                    EqBandState(3, "B4 H-MID", true, EqFilterShape.PEAK, 2400f, 1.0f, 1.2f),
                    EqBandState(4, "B5 HIGH", true, EqFilterShape.HIGH_SHELF, 10000f, 1.5f, 0.8f)
                ),
                limiterEnabled = true,
                limiterThresholdDb = -2.0f,
                limiterAttackMs = 1.5f,
                limiterReleaseMs = 120f
            )
        ),
        DspPreset(
            id = "factory_top_over_sub",
            slotNumber = "F03",
            name = "03 TOPS 100Hz LR24",
            description = "Stereo Top cabinet: 100 Hz Linkwitz-Riley 24 dB/oct HPF.",
            isFactory = true,
            parameters = DspParameters.stereoLinked(
                hpfEnabled = true,
                hpfFreqHz = 100f,
                hpfFilterType = XoverFilterType.LINKWITZ_RILEY,
                hpfSlopeDb = XoverSlopeDb.SLOPE_24,
                lpfEnabled = false,
                eqBands = listOf(
                    EqBandState(0, "B1 LOW", true, EqFilterShape.LOW_SHELF, 110f, 1.0f, 0.8f),
                    EqBandState(1, "B2 L-MID", true, EqFilterShape.PEAK, 400f, -1.0f, 1.5f),
                    EqBandState(2, "B3 MID", true, EqFilterShape.PEAK, 1100f, 0.0f, 1.2f),
                    EqBandState(3, "B4 H-MID", true, EqFilterShape.PEAK, 2800f, 0.5f, 1.2f),
                    EqBandState(4, "B5 HIGH", true, EqFilterShape.HIGH_SHELF, 9500f, 1.5f, 0.8f)
                ),
                limiterEnabled = true,
                limiterThresholdDb = -1.5f
            )
        ),
        DspPreset(
            id = "factory_subwoofer",
            slotNumber = "F04",
            name = "04 SUB 35-100 LR48",
            description = "Steep Subwoofer bandpass: 35 Hz BW24 HPF + 100 Hz Linkwitz-Riley 48 dB/oct LPF.",
            isFactory = true,
            parameters = DspParameters.stereoLinked(
                hpfEnabled = true,
                hpfFreqHz = 35f,
                hpfFilterType = XoverFilterType.BUTTERWORTH,
                hpfSlopeDb = XoverSlopeDb.SLOPE_24,
                lpfEnabled = true,
                lpfFreqHz = 100f,
                lpfFilterType = XoverFilterType.LINKWITZ_RILEY,
                lpfSlopeDb = XoverSlopeDb.SLOPE_48,
                eqBands = listOf(
                    EqBandState(0, "B1 LOW", true, EqFilterShape.PEAK, 52f, 2.5f, 1.3f),
                    EqBandState(1, "B2 L-MID", true, EqFilterShape.PEAK, 85f, -1.5f, 1.6f),
                    EqBandState(2, "B3 MID", false, EqFilterShape.PEAK, 800f, 0f, 1.2f),
                    EqBandState(3, "B4 H-MID", false, EqFilterShape.PEAK, 2800f, 0f, 1.2f),
                    EqBandState(4, "B5 HIGH", false, EqFilterShape.HIGH_SHELF, 9500f, 0f, 0.8f)
                ),
                limiterEnabled = true,
                limiterThresholdDb = -3.0f,
                limiterAttackMs = 3.0f,
                limiterReleaseMs = 200f
            )
        ),
        DspPreset(
            id = "factory_stage_monitor",
            slotNumber = "F05",
            name = "05 STAGE WEDGE",
            description = "Floor monitor: 85 Hz BW24 HPF, anti-feedback notches & fast limiter.",
            isFactory = true,
            parameters = DspParameters.stereoLinked(
                hpfEnabled = true,
                hpfFreqHz = 85f,
                hpfFilterType = XoverFilterType.BUTTERWORTH,
                hpfSlopeDb = XoverSlopeDb.SLOPE_24,
                lpfEnabled = true,
                lpfFreqHz = 16000f,
                lpfFilterType = XoverFilterType.BUTTERWORTH,
                lpfSlopeDb = XoverSlopeDb.SLOPE_12,
                eqBands = listOf(
                    EqBandState(0, "B1 LOW", true, EqFilterShape.LOW_SHELF, 140f, -2.0f, 0.8f),
                    EqBandState(1, "B2 L-MID", true, EqFilterShape.PEAK, 420f, -3.0f, 2.0f),
                    EqBandState(2, "B3 MID", true, EqFilterShape.PEAK, 2200f, 1.5f, 1.2f),
                    EqBandState(3, "B4 H-MID", true, EqFilterShape.PEAK, 4500f, -2.5f, 2.8f),
                    EqBandState(4, "B5 HIGH", true, EqFilterShape.HIGH_SHELF, 10000f, -1.0f, 0.8f)
                ),
                limiterEnabled = true,
                limiterThresholdDb = -4.0f,
                limiterAttackMs = 0.8f,
                limiterReleaseMs = 90f
            )
        ),
        DspPreset(
            id = "factory_2way_sub_top",
            slotNumber = "F06",
            name = "06 2-WAY SUB/TOP",
            description = "Mono Sum (IN1+2) split: OUT 1 (L) = Sub 35-100Hz LR24, OUT 2 (R) = Top 100Hz+ LR24.",
            isFactory = true,
            parameters = DspParameters(
                routeSourceL = InputSourceRoute.SUM_L_R,
                routeSourceR = InputSourceRoute.SUM_L_R,
                eqChannelLinked = false,
                eqBandsL = listOf(
                    EqBandState(0, "B1 LOW", true, EqFilterShape.PEAK, 55f, 2.0f, 1.3f),
                    EqBandState(1, "B2 L-MID", false, EqFilterShape.PEAK, 220f, 0f, 1.2f),
                    EqBandState(2, "B3 MID", false, EqFilterShape.PEAK, 800f, 0f, 1.2f),
                    EqBandState(3, "B4 H-MID", false, EqFilterShape.PEAK, 2800f, 0f, 1.2f),
                    EqBandState(4, "B5 HIGH", false, EqFilterShape.HIGH_SHELF, 9500f, 0f, 0.8f)
                ),
                eqBandsR = listOf(
                    EqBandState(0, "B1 LOW", false, EqFilterShape.LOW_SHELF, 110f, 0f, 0.8f),
                    EqBandState(1, "B2 L-MID", true, EqFilterShape.PEAK, 380f, -1.0f, 1.4f),
                    EqBandState(2, "B3 MID", true, EqFilterShape.PEAK, 1200f, 0.5f, 1.2f),
                    EqBandState(3, "B4 H-MID", true, EqFilterShape.PEAK, 3200f, 1.0f, 1.2f),
                    EqBandState(4, "B5 HIGH", true, EqFilterShape.HIGH_SHELF, 10000f, 1.5f, 0.8f)
                ),
                xoverLinked = false,
                xoverBypassL = false,
                hpfEnabledL = true,
                hpfFreqHzL = 35f,
                hpfFilterTypeL = XoverFilterType.BUTTERWORTH,
                hpfSlopeDbL = XoverSlopeDb.SLOPE_24,
                lpfEnabledL = true,
                lpfFreqHzL = 100f,
                lpfFilterTypeL = XoverFilterType.LINKWITZ_RILEY,
                lpfSlopeDbL = XoverSlopeDb.SLOPE_24,
                xoverBypassR = false,
                hpfEnabledR = true,
                hpfFreqHzR = 100f,
                hpfFilterTypeR = XoverFilterType.LINKWITZ_RILEY,
                hpfSlopeDbR = XoverSlopeDb.SLOPE_24,
                lpfEnabledR = false,
                lpfFreqHzR = 18000f,
                lpfFilterTypeR = XoverFilterType.LINKWITZ_RILEY,
                lpfSlopeDbR = XoverSlopeDb.SLOPE_24,
                limiterLinked = false,
                limiterEnabledL = true,
                limiterThresholdDbL = -3.0f,
                limiterAttackMsL = 3.0f,
                limiterReleaseMsL = 180f,
                limiterEnabledR = true,
                limiterThresholdDbR = -1.5f,
                limiterAttackMsR = 1.5f,
                limiterReleaseMsR = 120f
            )
        )
    )
}
