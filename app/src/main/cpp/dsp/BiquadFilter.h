#pragma once

namespace adw::dsp {

enum class BiquadType : int {
    Bypass = 0,
    LowPass = 1,
    HighPass = 2,
    PeakingEQ = 3,
    LowShelf = 4,
    HighShelf = 5
};

class BiquadFilter {
public:
    void reset() noexcept;

    void configure(BiquadType type, double sampleRate, double freqHz, double q, double gainDb = 0.0) noexcept;

    inline void processStereo(float& left, float& right) noexcept {
        if (bypassed_) {
            return;
        }
        const double xL = static_cast<double>(left);
        const double yL = b0_ * xL + z1L_;
        z1L_ = b1_ * xL - a1_ * yL + z2L_;
        z2L_ = b2_ * xL - a2_ * yL;
        left = static_cast<float>(yL);

        const double xR = static_cast<double>(right);
        const double yR = b0_ * xR + z1R_;
        z1R_ = b1_ * xR - a1_ * yR + z2R_;
        z2R_ = b2_ * xR - a2_ * yR;
        right = static_cast<float>(yR);
    }

    bool isBypassed() const noexcept { return bypassed_; }

private:
    bool bypassed_ = true;
    double b0_ = 1.0, b1_ = 0.0, b2_ = 0.0;
    double a1_ = 0.0, a2_ = 0.0;
    double z1L_ = 0.0, z2L_ = 0.0;
    double z1R_ = 0.0, z2R_ = 0.0;
};

static constexpr int kNumEqBands = 5;

enum class EqBandFilterShape : int {
    Peak = 0,
    LowShelf = 1,
    HighShelf = 2
};

class ParametricEqProcessor {
public:
    void prepare(double sampleRate) noexcept;
    void reset() noexcept;

    void setBand(
        int bandIndex,
        bool enabled,
        EqBandFilterShape shape,
        double freqHz,
        double gainDb,
        double q
    ) noexcept;

    inline void processStereo(float& left, float& right) noexcept {
        for (int i = 0; i < kNumEqBands; ++i) {
            bands_[i].processStereo(left, right);
        }
    }

private:
    double sampleRate_ = 48000.0;
    BiquadFilter bands_[kNumEqBands];
};

} // namespace adw::dsp
