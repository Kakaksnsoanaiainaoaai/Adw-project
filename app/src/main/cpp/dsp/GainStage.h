#pragma once

namespace adw::dsp {

// Dedicated stereo gain, mute, and independent L/R polarity invert block
// with click-free exponential gain smoothing.
class GainStage {
public:
    void prepare(double sampleRate) noexcept;
    void reset() noexcept;

    void configure(
        float gainDb,
        bool muteL,
        bool muteR,
        bool invertL,
        bool invertR
    ) noexcept;

    inline void processStereo(float& left, float& right) noexcept {
        currentGainL_ += (targetGainL_ - currentGainL_) * smoothCoeff_;
        currentGainR_ += (targetGainR_ - currentGainR_) * smoothCoeff_;
        left *= currentGainL_;
        right *= currentGainR_;
    }

private:
    double sampleRate_ = 48000.0;
    float smoothCoeff_ = 0.005f;
    float targetGainL_ = 1.0f;
    float targetGainR_ = 1.0f;
    float currentGainL_ = 1.0f;
    float currentGainR_ = 1.0f;
};

} // namespace adw::dsp
