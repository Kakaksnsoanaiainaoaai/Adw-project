#include "PeakLimiter.h"
#include <algorithm>
#include <cmath>

namespace adw::dsp {

void PeakLimiter::prepare(double sampleRate) noexcept {
    sampleRate_ = sampleRate > 0.0 ? sampleRate : 48000.0;
    reset();
}

void PeakLimiter::reset() noexcept {
    envelope_ = 0.0f;
    lastGrDb_ = 0.0f;
    blockMinGrDb_ = 0.0f;
}

void PeakLimiter::configure(bool enabled, float thresholdDb, float attackMs, float releaseMs) noexcept {
    enabled_ = enabled;
    const float clampedThresh = std::clamp(thresholdDb, -24.0f, 0.0f);
    thresholdLin_ = std::pow(10.0f, clampedThresh / 20.0f);

    const double atkSec = std::clamp(static_cast<double>(attackMs), 0.1, 100.0) * 0.001;
    const double relSec = std::clamp(static_cast<double>(releaseMs), 10.0, 2000.0) * 0.001;
    attackCoeff_ = static_cast<float>(std::exp(-1.0 / (atkSec * sampleRate_)));
    releaseCoeff_ = static_cast<float>(std::exp(-1.0 / (relSec * sampleRate_)));
}

float PeakLimiter::log10Approx(float x) noexcept {
    return std::log10(std::max(x, 1e-6f));
}

} // namespace adw::dsp
