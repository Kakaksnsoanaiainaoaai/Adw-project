#include "GainStage.h"
#include <algorithm>
#include <cmath>

namespace adw::dsp {

void GainStage::prepare(double sampleRate) noexcept {
    sampleRate_ = sampleRate > 0.0 ? sampleRate : 48000.0;
    smoothCoeff_ = static_cast<float>(1.0 - std::exp(-1.0 / (0.008 * sampleRate_)));
    reset();
}

void GainStage::reset() noexcept {
    currentGainL_ = targetGainL_;
    currentGainR_ = targetGainR_;
}

void GainStage::configure(
    float gainDbL,
    float gainDbR,
    bool muteL,
    bool muteR,
    bool invertL,
    bool invertR
) noexcept {
    const float clampedDbL = std::clamp(gainDbL, -48.0f, 12.0f);
    const float clampedDbR = std::clamp(gainDbR, -48.0f, 12.0f);
    const float linearL = std::pow(10.0f, clampedDbL / 20.0f);
    const float linearR = std::pow(10.0f, clampedDbR / 20.0f);

    const float magL = muteL ? 0.0f : linearL;
    const float magR = muteR ? 0.0f : linearR;

    targetGainL_ = invertL ? -magL : magL;
    targetGainR_ = invertR ? -magR : magR;
}

} // namespace adw::dsp
