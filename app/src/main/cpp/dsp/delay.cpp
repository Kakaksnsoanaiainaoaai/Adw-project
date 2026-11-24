#include "DelayLine.h"
#include <algorithm>
#include <cmath>

namespace adw::dsp {

void DelayLine::prepare(double sampleRate) noexcept {
    sampleRate_ = sampleRate > 0.0 ? sampleRate : 48000.0;
    const size_t required = static_cast<size_t>(std::ceil(sampleRate_ * (kMaxDelayMs / 1000.0))) + 16;
    if (bufferL_.size() != required) {
        bufferL_.assign(required, 0.0f);
        bufferR_.assign(required, 0.0f);
    } else {
        std::fill(bufferL_.begin(), bufferL_.end(), 0.0f);
        std::fill(bufferR_.begin(), bufferR_.end(), 0.0f);
    }
    bufferSize_ = required;
    writeIndex_ = 0;
    currentSamplesL_ = targetSamplesL_;
    currentSamplesR_ = targetSamplesR_;
    slewCoeff_ = 1.0 - std::exp(-1.0 / (0.020 * sampleRate_));
}

void DelayLine::reset() noexcept {
    std::fill(bufferL_.begin(), bufferL_.end(), 0.0f);
    std::fill(bufferR_.begin(), bufferR_.end(), 0.0f);
    writeIndex_ = 0;
    currentSamplesL_ = targetSamplesL_;
    currentSamplesR_ = targetSamplesR_;
}

void DelayLine::setDelayMs(double delayMsL, double delayMsR) noexcept {
    const double clampedL = std::clamp(delayMsL, 0.0, kMaxDelayMs);
    const double clampedR = std::clamp(delayMsR, 0.0, kMaxDelayMs);
    targetSamplesL_ = clampedL * 0.001 * sampleRate_;
    targetSamplesR_ = clampedR * 0.001 * sampleRate_;
}

} // namespace adw::dsp
