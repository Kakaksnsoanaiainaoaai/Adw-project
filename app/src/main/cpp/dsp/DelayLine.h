#pragma once

#include <vector>

namespace adw::dsp {

class DelayLine {
public:
    static constexpr double kMaxDelayMs = 200.0;

    void prepare(double sampleRate) noexcept;
    void reset() noexcept;
    void setDelayMs(double delayMsL, double delayMsR) noexcept;

    inline void processStereo(float& left, float& right) noexcept {
        if (bufferSize_ == 0) {
            return;
        }
        // Smooth slew toward target delay samples to prevent zipper noise
        currentSamplesL_ += (targetSamplesL_ - currentSamplesL_) * slewCoeff_;
        currentSamplesR_ += (targetSamplesR_ - currentSamplesR_) * slewCoeff_;

        bufferL_[writeIndex_] = left;
        bufferR_[writeIndex_] = right;

        left = readInterpolated(bufferL_.data(), currentSamplesL_);
        right = readInterpolated(bufferR_.data(), currentSamplesR_);

        writeIndex_ = (writeIndex_ + 1) % bufferSize_;
    }

private:
    inline float readInterpolated(const float* buf, double delaySamples) const noexcept {
        const double clamped = delaySamples < 0.0 ? 0.0 : (delaySamples >= static_cast<double>(bufferSize_ - 2) ? static_cast<double>(bufferSize_ - 2) : delaySamples);
        const size_t intPart = static_cast<size_t>(clamped);
        const float frac = static_cast<float>(clamped - static_cast<double>(intPart));

        const size_t idx0 = (writeIndex_ + bufferSize_ - intPart) % bufferSize_;
        const size_t idx1 = (idx0 + bufferSize_ - 1) % bufferSize_;
        const float s0 = buf[idx0];
        const float s1 = buf[idx1];
        return s0 + frac * (s1 - s0);
    }

    std::vector<float> bufferL_;
    std::vector<float> bufferR_;
    size_t bufferSize_ = 0;
    size_t writeIndex_ = 0;
    double sampleRate_ = 48000.0;
    double targetSamplesL_ = 0.0;
    double targetSamplesR_ = 0.0;
    double currentSamplesL_ = 0.0;
    double currentSamplesR_ = 0.0;
    double slewCoeff_ = 0.002;
};

} // namespace adw::dsp
