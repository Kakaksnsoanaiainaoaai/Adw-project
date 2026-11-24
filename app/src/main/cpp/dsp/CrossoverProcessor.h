#pragma once

#include "BiquadFilter.h"

namespace adw::dsp {

enum class XoverFilterType : int {
    Butterworth = 0,
    LinkwitzRiley = 1
};

enum class XoverSlopeDb : int {
    Off = 0,
    Slope12 = 12,
    Slope24 = 24,
    Slope48 = 48
};

// Supports up to 8th-order (48 dB/oct = 4 cascaded 2nd-order biquad stages) per HPF and LPF.
class CrossoverProcessor {
public:
    static constexpr int kMaxStages = 4;

    void reset() noexcept;

    void configure(
        double sampleRate,
        bool hpfEnabled,
        double hpfFreqHz,
        XoverFilterType hpfType,
        XoverSlopeDb hpfSlope,
        bool lpfEnabled,
        double lpfFreqHz,
        XoverFilterType lpfType,
        XoverSlopeDb lpfSlope
    ) noexcept;

    inline void processMono(float& sample) noexcept {
        for (int i = 0; i < kMaxStages; ++i) {
            hpfStages_[i].processMono(sample);
        }
        for (int i = 0; i < kMaxStages; ++i) {
            lpfStages_[i].processMono(sample);
        }
    }

    inline void processStereo(float& left, float& right) noexcept {
        for (int i = 0; i < kMaxStages; ++i) {
            hpfStages_[i].processStereo(left, right);
        }
        for (int i = 0; i < kMaxStages; ++i) {
            lpfStages_[i].processStereo(left, right);
        }
    }

private:
    void configureSide(
        BiquadFilter* stages,
        BiquadType biquadType,
        double sampleRate,
        bool enabled,
        double freqHz,
        XoverFilterType filterType,
        XoverSlopeDb slope
    ) noexcept;

    BiquadFilter hpfStages_[kMaxStages];
    BiquadFilter lpfStages_[kMaxStages];
};

} // namespace adw::dsp
