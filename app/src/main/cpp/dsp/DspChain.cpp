#include "DspChain.h"
#include <algorithm>
#include <cmath>

namespace adw::dsp {

namespace {
constexpr double kTwoPi = 6.28318530717958647692;
} // namespace

DspChain::DspChain() {
    eqBandsAtomic_[0].shape.store(static_cast<int>(EqBandFilterShape::LowShelf), std::memory_order_relaxed);
    eqBandsAtomic_[0].freqHz.store(65.0f, std::memory_order_relaxed);
    eqBandsAtomic_[0].q.store(0.8f, std::memory_order_relaxed);

    eqBandsAtomic_[1].shape.store(static_cast<int>(EqBandFilterShape::Peak), std::memory_order_relaxed);
    eqBandsAtomic_[1].freqHz.store(220.0f, std::memory_order_relaxed);
    eqBandsAtomic_[1].q.store(1.2f, std::memory_order_relaxed);

    eqBandsAtomic_[2].shape.store(static_cast<int>(EqBandFilterShape::Peak), std::memory_order_relaxed);
    eqBandsAtomic_[2].freqHz.store(800.0f, std::memory_order_relaxed);
    eqBandsAtomic_[2].q.store(1.2f, std::memory_order_relaxed);

    eqBandsAtomic_[3].shape.store(static_cast<int>(EqBandFilterShape::Peak), std::memory_order_relaxed);
    eqBandsAtomic_[3].freqHz.store(2800.0f, std::memory_order_relaxed);
    eqBandsAtomic_[3].q.store(1.2f, std::memory_order_relaxed);

    eqBandsAtomic_[4].shape.store(static_cast<int>(EqBandFilterShape::HighShelf), std::memory_order_relaxed);
    eqBandsAtomic_[4].freqHz.store(9500.0f, std::memory_order_relaxed);
    eqBandsAtomic_[4].q.store(0.8f, std::memory_order_relaxed);
}

void DspChain::prepare(double sampleRate) noexcept {
    sampleRate_ = sampleRate > 0.0 ? sampleRate : 48000.0;
    inputGainStage_.prepare(sampleRate_);
    eqProcessor_.prepare(sampleRate_);
    delayLine_.prepare(sampleRate_);
    limiter_.prepare(sampleRate_);
    outputGainStage_.prepare(sampleRate_);
    reset();
    syncedVersion_ = 0;
    syncParametersOnAudioThread();
}

void DspChain::reset() noexcept {
    inputGainStage_.reset();
    eqProcessor_.reset();
    crossover_.reset();
    delayLine_.reset();
    limiter_.reset();
    outputGainStage_.reset();
    sinePhase_ = 0.0;
    decayInPeakL_ = 0.0f;
    decayInPeakR_ = 0.0f;
    decayOutPeakL_ = 0.0f;
    decayOutPeakR_ = 0.0f;
    holdInL_ = 0.0f;
    holdInR_ = 0.0f;
    holdOutL_ = 0.0f;
    holdOutR_ = 0.0f;
    decayGrDb_ = 0.0f;
}

void DspChain::syncParametersOnAudioThread() noexcept {
    const uint32_t resetSeq = peakHoldResetCounter_.load(std::memory_order_acquire);
    if (resetSeq != syncedPeakReset_) {
        syncedPeakReset_ = resetSeq;
        holdInL_ = 0.0f;
        holdInR_ = 0.0f;
        holdOutL_ = 0.0f;
        holdOutR_ = 0.0f;
    }

    const uint64_t version = paramVersion_.load(std::memory_order_acquire);
    if (version == syncedVersion_) {
        return;
    }
    syncedVersion_ = version;

    localBypassDsp_ = bypassDsp_.load(std::memory_order_relaxed);
    localMuteAll_ = muteAll_.load(std::memory_order_relaxed);
    localTestSignalMode_ = testSignalMode_.load(std::memory_order_relaxed);
    localEqBypass_ = eqBypass_.load(std::memory_order_relaxed);
    localXoverBypass_ = xoverBypass_.load(std::memory_order_relaxed);
    localDelayEnabled_ = delayEnabled_.load(std::memory_order_relaxed);

    const bool inMuted = inputMute_.load(std::memory_order_relaxed);
    inputGainStage_.configure(
        inputGainDb_.load(std::memory_order_relaxed),
        inMuted,
        inMuted,
        inputInvertL_.load(std::memory_order_relaxed),
        inputInvertR_.load(std::memory_order_relaxed)
    );

    for (int i = 0; i < kNumEqBands; ++i) {
        eqProcessor_.setBand(
            i,
            eqBandsAtomic_[i].enabled.load(std::memory_order_relaxed),
            static_cast<EqBandFilterShape>(eqBandsAtomic_[i].shape.load(std::memory_order_relaxed)),
            eqBandsAtomic_[i].freqHz.load(std::memory_order_relaxed),
            eqBandsAtomic_[i].gainDb.load(std::memory_order_relaxed),
            eqBandsAtomic_[i].q.load(std::memory_order_relaxed)
        );
    }

    crossover_.configure(
        sampleRate_,
        hpfEnabled_.load(std::memory_order_relaxed),
        hpfFreqHz_.load(std::memory_order_relaxed),
        static_cast<XoverFilterType>(hpfFilterType_.load(std::memory_order_relaxed)),
        static_cast<XoverSlopeDb>(hpfSlopeDb_.load(std::memory_order_relaxed)),
        lpfEnabled_.load(std::memory_order_relaxed),
        lpfFreqHz_.load(std::memory_order_relaxed),
        static_cast<XoverFilterType>(lpfFilterType_.load(std::memory_order_relaxed)),
        static_cast<XoverSlopeDb>(lpfSlopeDb_.load(std::memory_order_relaxed))
    );

    delayLine_.setDelayMs(
        delayMsL_.load(std::memory_order_relaxed),
        delayMsR_.load(std::memory_order_relaxed)
    );

    limiter_.configure(
        limiterEnabled_.load(std::memory_order_relaxed),
        limiterThresholdDb_.load(std::memory_order_relaxed),
        limiterAttackMs_.load(std::memory_order_relaxed),
        limiterReleaseMs_.load(std::memory_order_relaxed)
    );

    outputGainStage_.configure(
        outputGainDb_.load(std::memory_order_relaxed),
        localMuteAll_ || outputMuteL_.load(std::memory_order_relaxed),
        localMuteAll_ || outputMuteR_.load(std::memory_order_relaxed),
        outputInvertL_.load(std::memory_order_relaxed),
        outputInvertR_.load(std::memory_order_relaxed)
    );
}

void DspChain::generateTestSignal(float& left, float& right) noexcept {
    if (localTestSignalMode_ == 1) {
        const double phaseInc = kTwoPi * 1000.0 / sampleRate_;
        const float s = static_cast<float>(std::sin(sinePhase_) * 0.1258925);
        sinePhase_ += phaseInc;
        if (sinePhase_ >= kTwoPi) {
            sinePhase_ -= kTwoPi;
        }
        left = s;
        right = s;
    } else if (localTestSignalMode_ == 2) {
        rngState_ = rngState_ * 1664525u + 1013904223u;
        const float white = (static_cast<float>(rngState_ >> 8) * (1.0f / 8388608.0f)) - 1.0f;
        pinkB0_ = 0.99886f * pinkB0_ + white * 0.0555179f;
        pinkB1_ = 0.99332f * pinkB1_ + white * 0.0750759f;
        pinkB2_ = 0.96900f * pinkB2_ + white * 0.1538520f;
        pinkB3_ = 0.86650f * pinkB3_ + white * 0.3104856f;
        pinkB4_ = 0.55000f * pinkB4_ + white * 0.5329522f;
        pinkB5_ = -0.7616f * pinkB5_ - white * 0.0168980f;
        const float pink = (pinkB0_ + pinkB1_ + pinkB2_ + pinkB3_ + pinkB4_ + pinkB5_ + pinkB6_ + white * 0.5362f) * 0.025f;
        pinkB6_ = white * 0.115926f;
        left = pink;
        right = pink;
    }
}

void DspChain::processBlock(const float* inInterleavedStereo, float* outInterleavedStereo, int32_t numFrames) noexcept {
    if (numFrames <= 0 || outInterleavedStereo == nullptr) {
        return;
    }

    syncParametersOnAudioThread();
    limiter_.beginBlock();

    float inPeakL = 0.0f, inPeakR = 0.0f;
    float outPeakL = 0.0f, outPeakR = 0.0f;
    double inSumSqL = 0.0, inSumSqR = 0.0;
    double outSumSqL = 0.0, outSumSqR = 0.0;

    for (int32_t i = 0; i < numFrames; ++i) {
        float l = 0.0f;
        float r = 0.0f;

        if (localTestSignalMode_ != 0) {
            generateTestSignal(l, r);
        } else if (inInterleavedStereo != nullptr) {
            l = inInterleavedStereo[i * 2];
            r = inInterleavedStereo[i * 2 + 1];
        }

        // 1. Input Gain / Mute / Polarity
        inputGainStage_.processStereo(l, r);

        const float absInL = l < 0.0f ? -l : l;
        const float absInR = r < 0.0f ? -r : r;
        if (absInL > inPeakL) inPeakL = absInL;
        if (absInR > inPeakR) inPeakR = absInR;
        inSumSqL += static_cast<double>(l) * static_cast<double>(l);
        inSumSqR += static_cast<double>(r) * static_cast<double>(r);

        if (!localBypassDsp_) {
            // 2. Parametric EQ (5 bands: Peak / Low Shelf / High Shelf)
            if (!localEqBypass_) {
                eqProcessor_.processStereo(l, r);
            }

            // 3. Crossover (HPF / LPF with BW/LR 12/24/48 dB/oct)
            if (!localXoverBypass_) {
                crossover_.processStereo(l, r);
            }

            // 4. Per-Channel Delay
            if (localDelayEnabled_) {
                delayLine_.processStereo(l, r);
            } else {
                float dummyL = l, dummyR = r;
                delayLine_.processStereo(dummyL, dummyR);
            }

            // 5. Peak Limiter
            limiter_.processStereo(l, r);
        }

        // 6. Output Gain / Mute / Polarity
        outputGainStage_.processStereo(l, r);

        if (!std::isfinite(l)) l = 0.0f;
        if (!std::isfinite(r)) r = 0.0f;
        l = std::clamp(l, -1.0f, 1.0f);
        r = std::clamp(r, -1.0f, 1.0f);

        const float absOutL = l < 0.0f ? -l : l;
        const float absOutR = r < 0.0f ? -r : r;
        if (absOutL > outPeakL) outPeakL = absOutL;
        if (absOutR > outPeakR) outPeakR = absOutR;
        outSumSqL += static_cast<double>(l) * static_cast<double>(l);
        outSumSqR += static_cast<double>(r) * static_cast<double>(r);

        outInterleavedStereo[i * 2] = l;
        outInterleavedStereo[i * 2 + 1] = r;
    }

    const float invFrames = 1.0f / static_cast<float>(numFrames);
    const float inRmsL = static_cast<float>(std::sqrt(inSumSqL * invFrames));
    const float inRmsR = static_cast<float>(std::sqrt(inSumSqR * invFrames));
    const float outRmsL = static_cast<float>(std::sqrt(outSumSqL * invFrames));
    const float outRmsR = static_cast<float>(std::sqrt(outSumSqR * invFrames));

    const float blockSec = static_cast<float>(numFrames) / static_cast<float>(sampleRate_);
    const float decayCoeff = std::exp(-blockSec / 0.14f);
    const float holdDecayCoeff = std::exp(-blockSec / 1.8f);

    decayInPeakL_ = std::max(inPeakL, decayInPeakL_ * decayCoeff);
    decayInPeakR_ = std::max(inPeakR, decayInPeakR_ * decayCoeff);
    decayOutPeakL_ = std::max(outPeakL, decayOutPeakL_ * decayCoeff);
    decayOutPeakR_ = std::max(outPeakR, decayOutPeakR_ * decayCoeff);

    holdInL_ = std::max(inPeakL, holdInL_ * holdDecayCoeff);
    holdInR_ = std::max(inPeakR, holdInR_ * holdDecayCoeff);
    holdOutL_ = std::max(outPeakL, holdOutL_ * holdDecayCoeff);
    holdOutR_ = std::max(outPeakR, holdOutR_ * holdDecayCoeff);

    const float blockGr = localBypassDsp_ ? 0.0f : limiter_.consumeBlockMaxReductionDb();
    decayGrDb_ = std::max(blockGr, decayGrDb_ * decayCoeff);

    meterInPeakL_.store(decayInPeakL_, std::memory_order_relaxed);
    meterInPeakR_.store(decayInPeakR_, std::memory_order_relaxed);
    meterInRmsL_.store(inRmsL, std::memory_order_relaxed);
    meterInRmsR_.store(inRmsR, std::memory_order_relaxed);
    meterOutPeakL_.store(decayOutPeakL_, std::memory_order_relaxed);
    meterOutPeakR_.store(decayOutPeakR_, std::memory_order_relaxed);
    meterOutRmsL_.store(outRmsL, std::memory_order_relaxed);
    meterOutRmsR_.store(outRmsR, std::memory_order_relaxed);
    meterInHoldL_.store(holdInL_, std::memory_order_relaxed);
    meterInHoldR_.store(holdInR_, std::memory_order_relaxed);
    meterOutHoldL_.store(holdOutL_, std::memory_order_relaxed);
    meterOutHoldR_.store(holdOutR_, std::memory_order_relaxed);
    meterLimiterGrDb_.store(decayGrDb_, std::memory_order_relaxed);
}

void DspChain::setGlobalState(bool bypassDsp, bool muteAll, int testSignalMode) noexcept {
    bypassDsp_.store(bypassDsp, std::memory_order_relaxed);
    muteAll_.store(muteAll, std::memory_order_relaxed);
    testSignalMode_.store(testSignalMode, std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setInputParams(float gainDb, bool mute, bool invertL, bool invertR) noexcept {
    inputGainDb_.store(std::clamp(gainDb, -24.0f, 12.0f), std::memory_order_relaxed);
    inputMute_.store(mute, std::memory_order_relaxed);
    inputInvertL_.store(invertL, std::memory_order_relaxed);
    inputInvertR_.store(invertR, std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setEqBypass(bool bypass) noexcept {
    eqBypass_.store(bypass, std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setEqBand(int bandIndex, bool enabled, int filterShape, float freqHz, float gainDb, float q) noexcept {
    if (bandIndex < 0 || bandIndex >= kNumEqBands) return;
    eqBandsAtomic_[bandIndex].enabled.store(enabled, std::memory_order_relaxed);
    eqBandsAtomic_[bandIndex].shape.store(std::clamp(filterShape, 0, 2), std::memory_order_relaxed);
    eqBandsAtomic_[bandIndex].freqHz.store(std::clamp(freqHz, 20.0f, 20000.0f), std::memory_order_relaxed);
    eqBandsAtomic_[bandIndex].gainDb.store(std::clamp(gainDb, -15.0f, 15.0f), std::memory_order_relaxed);
    eqBandsAtomic_[bandIndex].q.store(std::clamp(q, 0.3f, 12.0f), std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setCrossoverParams(
    bool bypass,
    bool hpfEnabled,
    float hpfFreqHz,
    int hpfFilterType,
    int hpfSlopeDb,
    bool lpfEnabled,
    float lpfFreqHz,
    int lpfFilterType,
    int lpfSlopeDb
) noexcept {
    xoverBypass_.store(bypass, std::memory_order_relaxed);
    hpfEnabled_.store(hpfEnabled, std::memory_order_relaxed);
    hpfFreqHz_.store(std::clamp(hpfFreqHz, 20.0f, 5000.0f), std::memory_order_relaxed);
    hpfFilterType_.store(std::clamp(hpfFilterType, 0, 1), std::memory_order_relaxed);
    hpfSlopeDb_.store(hpfSlopeDb, std::memory_order_relaxed);
    lpfEnabled_.store(lpfEnabled, std::memory_order_relaxed);
    lpfFreqHz_.store(std::clamp(lpfFreqHz, 200.0f, 20000.0f), std::memory_order_relaxed);
    lpfFilterType_.store(std::clamp(lpfFilterType, 0, 1), std::memory_order_relaxed);
    lpfSlopeDb_.store(lpfSlopeDb, std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setDelayParams(bool enabled, float delayMsL, float delayMsR) noexcept {
    delayEnabled_.store(enabled, std::memory_order_relaxed);
    delayMsL_.store(std::clamp(delayMsL, 0.0f, 100.0f), std::memory_order_relaxed);
    delayMsR_.store(std::clamp(delayMsR, 0.0f, 100.0f), std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setLimiterParams(bool enabled, float thresholdDb, float attackMs, float releaseMs) noexcept {
    limiterEnabled_.store(enabled, std::memory_order_relaxed);
    limiterThresholdDb_.store(std::clamp(thresholdDb, -24.0f, 0.0f), std::memory_order_relaxed);
    limiterAttackMs_.store(std::clamp(attackMs, 0.5f, 50.0f), std::memory_order_relaxed);
    limiterReleaseMs_.store(std::clamp(releaseMs, 20.0f, 1000.0f), std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setOutputParams(float gainDb, bool muteL, bool muteR, bool invertL, bool invertR) noexcept {
    outputGainDb_.store(std::clamp(gainDb, -40.0f, 12.0f), std::memory_order_relaxed);
    outputMuteL_.store(muteL, std::memory_order_relaxed);
    outputMuteR_.store(muteR, std::memory_order_relaxed);
    outputInvertL_.store(invertL, std::memory_order_relaxed);
    outputInvertR_.store(invertR, std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::resetPeakHold() noexcept {
    peakHoldResetCounter_.fetch_add(1, std::memory_order_release);
}

MeterSnapshot DspChain::readMeters() const noexcept {
    MeterSnapshot snap;
    snap.inputPeakL = meterInPeakL_.load(std::memory_order_relaxed);
    snap.inputPeakR = meterInPeakR_.load(std::memory_order_relaxed);
    snap.inputRmsL = meterInRmsL_.load(std::memory_order_relaxed);
    snap.inputRmsR = meterInRmsR_.load(std::memory_order_relaxed);
    snap.outputPeakL = meterOutPeakL_.load(std::memory_order_relaxed);
    snap.outputPeakR = meterOutPeakR_.load(std::memory_order_relaxed);
    snap.outputRmsL = meterOutRmsL_.load(std::memory_order_relaxed);
    snap.outputRmsR = meterOutRmsR_.load(std::memory_order_relaxed);
    snap.inputHoldL = meterInHoldL_.load(std::memory_order_relaxed);
    snap.inputHoldR = meterInHoldR_.load(std::memory_order_relaxed);
    snap.outputHoldL = meterOutHoldL_.load(std::memory_order_relaxed);
    snap.outputHoldR = meterOutHoldR_.load(std::memory_order_relaxed);
    snap.limiterGrDb = meterLimiterGrDb_.load(std::memory_order_relaxed);
    return snap;
}

} // namespace adw::dsp
