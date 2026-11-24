#include "CrossoverProcessor.h"

namespace adw::dsp {

namespace {
constexpr double kInvSqrt2 = 0.70710678118654752440;
constexpr double kBw4Q[2] = { 0.541196100146197, 1.3065629648763766 };
constexpr double kBw8Q[4] = { 0.5097955791041592, 0.6013448869350453, 0.8999762231364156, 2.562915447741506 };
} // namespace

void CrossoverProcessor::reset() noexcept {
    for (int i = 0; i < kMaxStages; ++i) {
        hpfStages_[i].reset();
        lpfStages_[i].reset();
    }
}

void CrossoverProcessor::configure(
    double sampleRate,
    bool hpfEnabled,
    double hpfFreqHz,
    XoverFilterType hpfType,
    XoverSlopeDb hpfSlope,
    bool lpfEnabled,
    double lpfFreqHz,
    XoverFilterType lpfType,
    XoverSlopeDb lpfSlope
) noexcept {
    configureSide(hpfStages_, BiquadType::HighPass, sampleRate, hpfEnabled, hpfFreqHz, hpfType, hpfSlope);
    configureSide(lpfStages_, BiquadType::LowPass, sampleRate, lpfEnabled, lpfFreqHz, lpfType, lpfSlope);
}

void CrossoverProcessor::configureSide(
    BiquadFilter* stages,
    BiquadType biquadType,
    double sampleRate,
    bool enabled,
    double freqHz,
    XoverFilterType filterType,
    XoverSlopeDb slope
) noexcept {
    for (int i = 0; i < kMaxStages; ++i) {
        stages[i].configure(BiquadType::Bypass, sampleRate, freqHz, kInvSqrt2);
    }

    if (!enabled || slope == XoverSlopeDb::Off) {
        return;
    }

    if (slope == XoverSlopeDb::Slope12) {
        // LR12 = 2x 1st-order -> Q = 0.50 (-6 dB @ fc); BW12 = Q = 0.7071 (-3 dB @ fc)
        const double q = (filterType == XoverFilterType::LinkwitzRiley) ? 0.5 : kInvSqrt2;
        stages[0].configure(biquadType, sampleRate, freqHz, q);
    } else if (slope == XoverSlopeDb::Slope24) {
        if (filterType == XoverFilterType::LinkwitzRiley) {
            stages[0].configure(biquadType, sampleRate, freqHz, kInvSqrt2);
            stages[1].configure(biquadType, sampleRate, freqHz, kInvSqrt2);
        } else {
            stages[0].configure(biquadType, sampleRate, freqHz, kBw4Q[0]);
            stages[1].configure(biquadType, sampleRate, freqHz, kBw4Q[1]);
        }
    } else if (slope == XoverSlopeDb::Slope48) {
        if (filterType == XoverFilterType::LinkwitzRiley) {
            // LR48 = two cascaded 4th-order Butterworth filters (-6 dB @ fc)
            stages[0].configure(biquadType, sampleRate, freqHz, kBw4Q[0]);
            stages[1].configure(biquadType, sampleRate, freqHz, kBw4Q[1]);
            stages[2].configure(biquadType, sampleRate, freqHz, kBw4Q[0]);
            stages[3].configure(biquadType, sampleRate, freqHz, kBw4Q[1]);
        } else {
            // BW48 = 8th-order Butterworth
            for (int i = 0; i < 4; ++i) {
                stages[i].configure(biquadType, sampleRate, freqHz, kBw8Q[i]);
            }
        }
    }
}

} // namespace adw::dsp
