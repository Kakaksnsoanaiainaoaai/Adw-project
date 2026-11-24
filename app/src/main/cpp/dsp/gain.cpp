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
    float gainDb,
    bool muteL,
    bool muteR,
    bool invertL,
    bool invertR
) noexcept {
    const float clampedDb = std::clamp(gainDb, -48.0f, 12.0f);
    const float linear = std::pow(10.0f, clampedDb / 20.0f);

    const float magL = muteL ? 0.0f : linear;
    const float magR = muteR ? 0.0f : linear;

    targetGainL_ = invertL ? -magL : magL;
    targetGainR_ = invertR ? -magR : magR;
}

} // namespace adw::dsp
