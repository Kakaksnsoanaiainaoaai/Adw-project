#include "BiquadFilter.h"
#include <algorithm>
#include <cmath>

namespace adw::dsp {

namespace {
constexpr double kPi = 3.14159265358979323846;
}

void BiquadFilter::reset() noexcept {
    z1L_ = 0.0;
    z2L_ = 0.0;
    z1R_ = 0.0;
    z2R_ = 0.0;
}

void BiquadFilter::configure(BiquadType type, double sampleRate, double freqHz, double q, double gainDb) noexcept {
    if (type == BiquadType::Bypass || sampleRate <= 0.0) {
        bypassed_ = true;
        b0_ = 1.0;
        b1_ = 0.0;
        b2_ = 0.0;
        a1_ = 0.0;
        a2_ = 0.0;
        return;
    }

    const double clampedFreq = std::clamp(freqHz, 10.0, sampleRate * 0.45);
    const double clampedQ = std::clamp(q, 0.1, 20.0);
    const double omega = 2.0 * kPi * clampedFreq / sampleRate;
    const double sinW = std::sin(omega);
    const double cosW = std::cos(omega);
    const double alpha = sinW / (2.0 * clampedQ);

    double b0 = 1.0, b1 = 0.0, b2 = 0.0;
    double a0 = 1.0, a1 = 0.0, a2 = 0.0;

    switch (type) {
        case BiquadType::LowPass: {
            b0 = (1.0 - cosW) * 0.5;
            b1 = 1.0 - cosW;
            b2 = (1.0 - cosW) * 0.5;
            a0 = 1.0 + alpha;
            a1 = -2.0 * cosW;
            a2 = 1.0 - alpha;
            break;
        }
        case BiquadType::HighPass: {
            b0 = (1.0 + cosW) * 0.5;
            b1 = -(1.0 + cosW);
            b2 = (1.0 + cosW) * 0.5;
            a0 = 1.0 + alpha;
            a1 = -2.0 * cosW;
            a2 = 1.0 - alpha;
            break;
        }
        case BiquadType::PeakingEQ: {
            if (std::abs(gainDb) < 0.01) {
                bypassed_ = true;
                b0_ = 1.0; b1_ = 0.0; b2_ = 0.0; a1_ = 0.0; a2_ = 0.0;
                return;
            }
            const double A = std::pow(10.0, gainDb / 40.0);
            b0 = 1.0 + alpha * A;
            b1 = -2.0 * cosW;
            b2 = 1.0 - alpha * A;
            a0 = 1.0 + alpha / A;
            a1 = -2.0 * cosW;
            a2 = 1.0 - alpha / A;
            break;
        }
        case BiquadType::LowShelf: {
            if (std::abs(gainDb) < 0.01) {
                bypassed_ = true;
                b0_ = 1.0; b1_ = 0.0; b2_ = 0.0; a1_ = 0.0; a2_ = 0.0;
                return;
            }
            const double A = std::pow(10.0, gainDb / 40.0);
            // Shelf slope / Q mapping via RBJ formula
            const double shelfAlpha = (sinW / 2.0) * std::sqrt((A + 1.0 / A) * (1.0 / std::clamp(clampedQ, 0.25, 2.5) - 1.0) + 2.0);
            const double twoSqrtAAlpha = 2.0 * std::sqrt(A) * shelfAlpha;
            b0 = A * ((A + 1.0) - (A - 1.0) * cosW + twoSqrtAAlpha);
            b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosW);
            b2 = A * ((A + 1.0) - (A - 1.0) * cosW - twoSqrtAAlpha);
            a0 = (A + 1.0) + (A - 1.0) * cosW + twoSqrtAAlpha;
            a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosW);
            a2 = (A + 1.0) + (A - 1.0) * cosW - twoSqrtAAlpha;
            break;
        }
        case BiquadType::HighShelf: {
            if (std::abs(gainDb) < 0.01) {
                bypassed_ = true;
                b0_ = 1.0; b1_ = 0.0; b2_ = 0.0; a1_ = 0.0; a2_ = 0.0;
                return;
            }
            const double A = std::pow(10.0, gainDb / 40.0);
            const double shelfAlpha = (sinW / 2.0) * std::sqrt((A + 1.0 / A) * (1.0 / std::clamp(clampedQ, 0.25, 2.5) - 1.0) + 2.0);
            const double twoSqrtAAlpha = 2.0 * std::sqrt(A) * shelfAlpha;
            b0 = A * ((A + 1.0) + (A - 1.0) * cosW + twoSqrtAAlpha);
            b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosW);
            b2 = A * ((A + 1.0) + (A - 1.0) * cosW - twoSqrtAAlpha);
            a0 = (A + 1.0) - (A - 1.0) * cosW + twoSqrtAAlpha;
            a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosW);
            a2 = (A + 1.0) - (A - 1.0) * cosW - twoSqrtAAlpha;
            break;
        }
        case BiquadType::Bypass:
            break;
    }

    const double invA0 = 1.0 / a0;
    b0_ = b0 * invA0;
    b1_ = b1 * invA0;
    b2_ = b2 * invA0;
    a1_ = a1 * invA0;
    a2_ = a2 * invA0;
    bypassed_ = false;
}

void ParametricEqProcessor::prepare(double sampleRate) noexcept {
    sampleRate_ = sampleRate > 0.0 ? sampleRate : 48000.0;
    reset();
}

void ParametricEqProcessor::reset() noexcept {
    for (int i = 0; i < kNumEqBands; ++i) {
        bands_[i].reset();
    }
}

void ParametricEqProcessor::setBand(
    int bandIndex,
    bool enabled,
    EqBandFilterShape shape,
    double freqHz,
    double gainDb,
    double q
) noexcept {
    if (bandIndex < 0 || bandIndex >= kNumEqBands) return;
    if (!enabled) {
        bands_[bandIndex].configure(BiquadType::Bypass, sampleRate_, freqHz, q, 0.0);
        return;
    }
    BiquadType type = BiquadType::PeakingEQ;
    if (shape == EqBandFilterShape::LowShelf) type = BiquadType::LowShelf;
    else if (shape == EqBandFilterShape::HighShelf) type = BiquadType::HighShelf;

    bands_[bandIndex].configure(type, sampleRate_, freqHz, q, gainDb);
}

} // namespace adw::dsp
