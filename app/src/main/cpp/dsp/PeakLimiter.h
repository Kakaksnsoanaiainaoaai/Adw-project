#pragma once

namespace adw::dsp {

class PeakLimiter {
public:
    void prepare(double sampleRate) noexcept;
    void reset() noexcept;
    void configure(bool enabled, float thresholdDb, float attackMs, float releaseMs) noexcept;

    inline void processMono(float& sample) noexcept {
        if (!enabled_) {
            lastGrDb_ = 0.0f;
            return;
        }
        const float peak = sample < 0.0f ? -sample : sample;

        if (peak > envelope_) {
            envelope_ = attackCoeff_ * envelope_ + (1.0f - attackCoeff_) * peak;
        } else {
            envelope_ = releaseCoeff_ * envelope_ + (1.0f - releaseCoeff_) * peak;
        }

        float gain = 1.0f;
        if (envelope_ > thresholdLin_ && envelope_ > 1e-9f) {
            gain = thresholdLin_ / envelope_;
        }

        sample *= gain;

        const float outPeak = sample < 0.0f ? -sample : sample;
        if (outPeak > 0.995f) {
            const float clipScale = 0.995f / outPeak;
            sample *= clipScale;
            gain *= clipScale;
        }

        const float grDb = gain < 0.9999f ? 20.0f * log10Approx(gain) : 0.0f;
        lastGrDb_ = grDb;
        if (grDb < blockMinGrDb_) {
            blockMinGrDb_ = grDb;
        }
    }

    inline void processStereo(float& left, float& right) noexcept {
        if (!enabled_) {
            lastGrDb_ = 0.0f;
            return;
        }
        const float absL = left < 0.0f ? -left : left;
        const float absR = right < 0.0f ? -right : right;
        const float peak = absL > absR ? absL : absR;

        if (peak > envelope_) {
            envelope_ = attackCoeff_ * envelope_ + (1.0f - attackCoeff_) * peak;
        } else {
            envelope_ = releaseCoeff_ * envelope_ + (1.0f - releaseCoeff_) * peak;
        }

        float gain = 1.0f;
        if (envelope_ > thresholdLin_ && envelope_ > 1e-9f) {
            gain = thresholdLin_ / envelope_;
        }

        left *= gain;
        right *= gain;

        // Hard protective ceiling at threshold if fast transient slips through attack
        const float outPeak = (left < 0.0f ? -left : left) > (right < 0.0f ? -right : right)
            ? (left < 0.0f ? -left : left)
            : (right < 0.0f ? -right : right);
        if (outPeak > 0.995f) {
            const float clipScale = 0.995f / outPeak;
            left *= clipScale;
            right *= clipScale;
            gain *= clipScale;
        }

        const float grDb = gain < 0.9999f ? 20.0f * log10Approx(gain) : 0.0f;
        lastGrDb_ = grDb;
        if (grDb < blockMinGrDb_) {
            blockMinGrDb_ = grDb;
        }
    }

    void beginBlock() noexcept { blockMinGrDb_ = 0.0f; }
    float consumeBlockMaxReductionDb() const noexcept { return -blockMinGrDb_; }

private:
    static float log10Approx(float x) noexcept;

    double sampleRate_ = 48000.0;
    bool enabled_ = true;
    float thresholdLin_ = 0.89125f; // -1.0 dBFS
    float attackCoeff_ = 0.0f;
    float releaseCoeff_ = 0.99f;
    float envelope_ = 0.0f;
    float lastGrDb_ = 0.0f;
    float blockMinGrDb_ = 0.0f;
};

} // namespace adw::dsp
