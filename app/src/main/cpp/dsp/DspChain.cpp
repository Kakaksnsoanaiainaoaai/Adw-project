#include "DspChain.h"
#include <algorithm>
#include <cmath>

namespace adw::dsp {

namespace {
constexpr double kTwoPi = 6.28318530717958647692;
} // namespace

DspChain::DspChain() {
    for (int ch = 0; ch < kNumChannels; ++ch) {
        eqAtomic_[ch].bands[0].shape.store(static_cast<int>(EqBandFilterShape::LowShelf), std::memory_order_relaxed);
        eqAtomic_[ch].bands[0].freqHz.store(65.0f, std::memory_order_relaxed);
        eqAtomic_[ch].bands[0].q.store(0.8f, std::memory_order_relaxed);

        eqAtomic_[ch].bands[1].shape.store(static_cast<int>(EqBandFilterShape::Peak), std::memory_order_relaxed);
        eqAtomic_[ch].bands[1].freqHz.store(220.0f, std::memory_order_relaxed);
        eqAtomic_[ch].bands[1].q.store(1.2f, std::memory_order_relaxed);

        eqAtomic_[ch].bands[2].shape.store(static_cast<int>(EqBandFilterShape::Peak), std::memory_order_relaxed);
        eqAtomic_[ch].bands[2].freqHz.store(800.0f, std::memory_order_relaxed);
        eqAtomic_[ch].bands[2].q.store(1.2f, std::memory_order_relaxed);

        eqAtomic_[ch].bands[3].shape.store(static_cast<int>(EqBandFilterShape::Peak), std::memory_order_relaxed);
        eqAtomic_[ch].bands[3].freqHz.store(2800.0f, std::memory_order_relaxed);
        eqAtomic_[ch].bands[3].q.store(1.2f, std::memory_order_relaxed);

        eqAtomic_[ch].bands[4].shape.store(static_cast<int>(EqBandFilterShape::HighShelf), std::memory_order_relaxed);
        eqAtomic_[ch].bands[4].freqHz.store(9500.0f, std::memory_order_relaxed);
        eqAtomic_[ch].bands[4].q.store(0.8f, std::memory_order_relaxed);
    }
}

void DspChain::prepare(double sampleRate) noexcept {
    sampleRate_ = sampleRate > 0.0 ? sampleRate : 48000.0;
    inputGainStage_.prepare(sampleRate_);
    eqProcessorL_.prepare(sampleRate_);
    eqProcessorR_.prepare(sampleRate_);
    delayLine_.prepare(sampleRate_);
    limiterL_.prepare(sampleRate_);
    limiterR_.prepare(sampleRate_);
    outputGainStage_.prepare(sampleRate_);
    reset();
    syncedVersion_ = 0;
    syncParametersOnAudioThread();
}

void DspChain::reset() noexcept {
    inputGainStage_.reset();
    eqProcessorL_.reset();
    eqProcessorR_.reset();
    crossoverL_.reset();
    crossoverR_.reset();
    delayLine_.reset();
    limiterL_.reset();
    limiterR_.reset();
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
    decayGrDbL_ = 0.0f;
    decayGrDbR_ = 0.0f;
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

    localRouteSourceL_ = routeSourceL_.load(std::memory_order_relaxed);
    localRouteSourceR_ = routeSourceR_.load(std::memory_order_relaxed);

    localEqBypassL_ = eqAtomic_[0].bypass.load(std::memory_order_relaxed);
    localEqBypassR_ = eqAtomic_[1].bypass.load(std::memory_order_relaxed);
    localXoverBypassL_ = xoverAtomic_[0].bypass.load(std::memory_order_relaxed);
    localXoverBypassR_ = xoverAtomic_[1].bypass.load(std::memory_order_relaxed);
    localDelayEnabledL_ = delayEnabledL_.load(std::memory_order_relaxed);
    localDelayEnabledR_ = delayEnabledR_.load(std::memory_order_relaxed);
    localLimiterLinked_ = limiterLinked_.load(std::memory_order_relaxed);

    inputGainStage_.configure(
        inputGainDbL_.load(std::memory_order_relaxed),
        inputGainDbR_.load(std::memory_order_relaxed),
        inputMuteL_.load(std::memory_order_relaxed),
        inputMuteR_.load(std::memory_order_relaxed),
        inputInvertL_.load(std::memory_order_relaxed),
        inputInvertR_.load(std::memory_order_relaxed)
    );

    for (int i = 0; i < kNumEqBands; ++i) {
        eqProcessorL_.setBand(
            i,
            eqAtomic_[0].bands[i].enabled.load(std::memory_order_relaxed),
            static_cast<EqBandFilterShape>(eqAtomic_[0].bands[i].shape.load(std::memory_order_relaxed)),
            eqAtomic_[0].bands[i].freqHz.load(std::memory_order_relaxed),
            eqAtomic_[0].bands[i].gainDb.load(std::memory_order_relaxed),
            eqAtomic_[0].bands[i].q.load(std::memory_order_relaxed)
        );
        eqProcessorR_.setBand(
            i,
            eqAtomic_[1].bands[i].enabled.load(std::memory_order_relaxed),
            static_cast<EqBandFilterShape>(eqAtomic_[1].bands[i].shape.load(std::memory_order_relaxed)),
            eqAtomic_[1].bands[i].freqHz.load(std::memory_order_relaxed),
            eqAtomic_[1].bands[i].gainDb.load(std::memory_order_relaxed),
            eqAtomic_[1].bands[i].q.load(std::memory_order_relaxed)
        );
    }

    crossoverL_.configure(
        sampleRate_,
        xoverAtomic_[0].hpfEnabled.load(std::memory_order_relaxed),
        xoverAtomic_[0].hpfFreqHz.load(std::memory_order_relaxed),
        static_cast<XoverFilterType>(xoverAtomic_[0].hpfFilterType.load(std::memory_order_relaxed)),
        static_cast<XoverSlopeDb>(xoverAtomic_[0].hpfSlopeDb.load(std::memory_order_relaxed)),
        xoverAtomic_[0].lpfEnabled.load(std::memory_order_relaxed),
        xoverAtomic_[0].lpfFreqHz.load(std::memory_order_relaxed),
        static_cast<XoverFilterType>(xoverAtomic_[0].lpfFilterType.load(std::memory_order_relaxed)),
        static_cast<XoverSlopeDb>(xoverAtomic_[0].lpfSlopeDb.load(std::memory_order_relaxed))
    );

    crossoverR_.configure(
        sampleRate_,
        xoverAtomic_[1].hpfEnabled.load(std::memory_order_relaxed),
        xoverAtomic_[1].hpfFreqHz.load(std::memory_order_relaxed),
        static_cast<XoverFilterType>(xoverAtomic_[1].hpfFilterType.load(std::memory_order_relaxed)),
        static_cast<XoverSlopeDb>(xoverAtomic_[1].hpfSlopeDb.load(std::memory_order_relaxed)),
        xoverAtomic_[1].lpfEnabled.load(std::memory_order_relaxed),
        xoverAtomic_[1].lpfFreqHz.load(std::memory_order_relaxed),
        static_cast<XoverFilterType>(xoverAtomic_[1].lpfFilterType.load(std::memory_order_relaxed)),
        static_cast<XoverSlopeDb>(xoverAtomic_[1].lpfSlopeDb.load(std::memory_order_relaxed))
    );

    delayLine_.setDelayMs(
        delayMsL_.load(std::memory_order_relaxed),
        delayMsR_.load(std::memory_order_relaxed)
    );

    limiterL_.configure(
        limiterAtomic_[0].enabled.load(std::memory_order_relaxed),
        limiterAtomic_[0].thresholdDb.load(std::memory_order_relaxed),
        limiterAtomic_[0].attackMs.load(std::memory_order_relaxed),
        limiterAtomic_[0].releaseMs.load(std::memory_order_relaxed)
    );

    limiterR_.configure(
        limiterAtomic_[1].enabled.load(std::memory_order_relaxed),
        limiterAtomic_[1].thresholdDb.load(std::memory_order_relaxed),
        limiterAtomic_[1].attackMs.load(std::memory_order_relaxed),
        limiterAtomic_[1].releaseMs.load(std::memory_order_relaxed)
    );

    outputGainStage_.configure(
        outputGainDbL_.load(std::memory_order_relaxed),
        outputGainDbR_.load(std::memory_order_relaxed),
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
    limiterL_.beginBlock();
    limiterR_.beginBlock();

    float inPeakL = 0.0f, inPeakR = 0.0f;
    float outPeakL = 0.0f, outPeakR = 0.0f;
    double inSumSqL = 0.0, inSumSqR = 0.0;
    double outSumSqL = 0.0, outSumSqR = 0.0;

    auto routeSample = [](float in1, float in2, int source) noexcept -> float {
        if (source == 1) return in2;
        if (source == 2) return 0.5f * (in1 + in2);
        return in1;
    };

    for (int32_t i = 0; i < numFrames; ++i) {
        float inL = 0.0f;
        float inR = 0.0f;

        if (localTestSignalMode_ != 0) {
            generateTestSignal(inL, inR);
        } else if (inInterleavedStereo != nullptr) {
            inL = inInterleavedStereo[i * 2];
            inR = inInterleavedStereo[i * 2 + 1];
        }

        // 1. Input Stage: Independent IN 1 (L) & IN 2 (R) Gain / Mute / Polarity
        inputGainStage_.processStereo(inL, inR);

        const float absInL = inL < 0.0f ? -inL : inL;
        const float absInR = inR < 0.0f ? -inR : inR;
        if (absInL > inPeakL) inPeakL = absInL;
        if (absInR > inPeakR) inPeakR = absInR;
        inSumSqL += static_cast<double>(inL) * static_cast<double>(inL);
        inSumSqR += static_cast<double>(inR) * static_cast<double>(inR);

        // 2. Input Routing Matrix: Select source per DSP channel (IN 1 / IN 2 / SUM L+R)
        float dspL = routeSample(inL, inR, localRouteSourceL_);
        float dspR = routeSample(inL, inR, localRouteSourceR_);

        if (!localBypassDsp_) {
            // 3. Per-Channel 5-Band Parametric EQ
            if (!localEqBypassL_) {
                eqProcessorL_.processMono(dspL);
            }
            if (!localEqBypassR_) {
                eqProcessorR_.processMono(dspR);
            }

            // 4. Per-Channel Crossover (HPF / LPF with BW/LR 12/24/48 dB/oct)
            if (!localXoverBypassL_) {
                crossoverL_.processMono(dspL);
            }
            if (!localXoverBypassR_) {
                crossoverR_.processMono(dspR);
            }

            // 5. Per-Channel Time Alignment Delay
            float dlyL = dspL;
            float dlyR = dspR;
            delayLine_.processStereo(dlyL, dlyR);
            if (localDelayEnabledL_) {
                dspL = dlyL;
            }
            if (localDelayEnabledR_) {
                dspR = dlyR;
            }

            // 6. Dynamics / Peak Limiter (Stereo-Linked or Independent L/R)
            if (localLimiterLinked_) {
                limiterL_.processStereo(dspL, dspR);
            } else {
                limiterL_.processMono(dspL);
                limiterR_.processMono(dspR);
            }
        }

        // 7. Output Stage: Independent OUT 1 (L) & OUT 2 (R) Gain / Mute / Polarity
        outputGainStage_.processStereo(dspL, dspR);

        if (!std::isfinite(dspL)) dspL = 0.0f;
        if (!std::isfinite(dspR)) dspR = 0.0f;
        dspL = std::clamp(dspL, -1.0f, 1.0f);
        dspR = std::clamp(dspR, -1.0f, 1.0f);

        const float absOutL = dspL < 0.0f ? -dspL : dspL;
        const float absOutR = dspR < 0.0f ? -dspR : dspR;
        if (absOutL > outPeakL) outPeakL = absOutL;
        if (absOutR > outPeakR) outPeakR = absOutR;
        outSumSqL += static_cast<double>(dspL) * static_cast<double>(dspL);
        outSumSqR += static_cast<double>(dspR) * static_cast<double>(dspR);

        outInterleavedStereo[i * 2] = dspL;
        outInterleavedStereo[i * 2 + 1] = dspR;
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

    const float blockGrL = localBypassDsp_ ? 0.0f : limiterL_.consumeBlockMaxReductionDb();
    const float blockGrR = localBypassDsp_
        ? 0.0f
        : (localLimiterLinked_ ? blockGrL : limiterR_.consumeBlockMaxReductionDb());
    decayGrDbL_ = std::max(blockGrL, decayGrDbL_ * decayCoeff);
    decayGrDbR_ = std::max(blockGrR, decayGrDbR_ * decayCoeff);

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
    meterLimiterGrDbL_.store(decayGrDbL_, std::memory_order_relaxed);
    meterLimiterGrDbR_.store(decayGrDbR_, std::memory_order_relaxed);
    meterLimiterGrDb_.store(std::max(decayGrDbL_, decayGrDbR_), std::memory_order_relaxed);
}

void DspChain::setGlobalState(bool bypassDsp, bool muteAll, int testSignalMode) noexcept {
    bypassDsp_.store(bypassDsp, std::memory_order_relaxed);
    muteAll_.store(muteAll, std::memory_order_relaxed);
    testSignalMode_.store(testSignalMode, std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setInputParams(
    float gainDbL,
    float gainDbR,
    bool muteL,
    bool muteR,
    bool invertL,
    bool invertR,
    int routeSourceL,
    int routeSourceR
) noexcept {
    inputGainDbL_.store(std::clamp(gainDbL, -24.0f, 12.0f), std::memory_order_relaxed);
    inputGainDbR_.store(std::clamp(gainDbR, -24.0f, 12.0f), std::memory_order_relaxed);
    inputMuteL_.store(muteL, std::memory_order_relaxed);
    inputMuteR_.store(muteR, std::memory_order_relaxed);
    inputInvertL_.store(invertL, std::memory_order_relaxed);
    inputInvertR_.store(invertR, std::memory_order_relaxed);
    routeSourceL_.store(std::clamp(routeSourceL, 0, 2), std::memory_order_relaxed);
    routeSourceR_.store(std::clamp(routeSourceR, 0, 2), std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setEqBypass(int channelIndex, bool bypass) noexcept {
    if (channelIndex < 0 || channelIndex >= kNumChannels) return;
    eqAtomic_[channelIndex].bypass.store(bypass, std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setEqBand(
    int channelIndex,
    int bandIndex,
    bool enabled,
    int filterShape,
    float freqHz,
    float gainDb,
    float q
) noexcept {
    if (channelIndex < 0 || channelIndex >= kNumChannels) return;
    if (bandIndex < 0 || bandIndex >= kNumEqBands) return;
    auto& band = eqAtomic_[channelIndex].bands[bandIndex];
    band.enabled.store(enabled, std::memory_order_relaxed);
    band.shape.store(std::clamp(filterShape, 0, 2), std::memory_order_relaxed);
    band.freqHz.store(std::clamp(freqHz, 20.0f, 20000.0f), std::memory_order_relaxed);
    band.gainDb.store(std::clamp(gainDb, -15.0f, 15.0f), std::memory_order_relaxed);
    band.q.store(std::clamp(q, 0.3f, 12.0f), std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setCrossoverParams(
    int channelIndex,
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
    if (channelIndex < 0 || channelIndex >= kNumChannels) return;
    auto& xo = xoverAtomic_[channelIndex];
    xo.bypass.store(bypass, std::memory_order_relaxed);
    xo.hpfEnabled.store(hpfEnabled, std::memory_order_relaxed);
    xo.hpfFreqHz.store(std::clamp(hpfFreqHz, 20.0f, 5000.0f), std::memory_order_relaxed);
    xo.hpfFilterType.store(std::clamp(hpfFilterType, 0, 1), std::memory_order_relaxed);
    xo.hpfSlopeDb.store(hpfSlopeDb, std::memory_order_relaxed);
    xo.lpfEnabled.store(lpfEnabled, std::memory_order_relaxed);
    xo.lpfFreqHz.store(std::clamp(lpfFreqHz, 200.0f, 20000.0f), std::memory_order_relaxed);
    xo.lpfFilterType.store(std::clamp(lpfFilterType, 0, 1), std::memory_order_relaxed);
    xo.lpfSlopeDb.store(lpfSlopeDb, std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setDelayParams(bool enabledL, bool enabledR, float delayMsL, float delayMsR) noexcept {
    delayEnabledL_.store(enabledL, std::memory_order_relaxed);
    delayEnabledR_.store(enabledR, std::memory_order_relaxed);
    delayMsL_.store(std::clamp(delayMsL, 0.0f, 100.0f), std::memory_order_relaxed);
    delayMsR_.store(std::clamp(delayMsR, 0.0f, 100.0f), std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setLimiterParams(
    int channelIndex,
    bool linked,
    bool enabled,
    float thresholdDb,
    float attackMs,
    float releaseMs
) noexcept {
    if (channelIndex < 0 || channelIndex >= kNumChannels) return;
    limiterLinked_.store(linked, std::memory_order_relaxed);
    auto& lim = limiterAtomic_[channelIndex];
    lim.enabled.store(enabled, std::memory_order_relaxed);
    lim.thresholdDb.store(std::clamp(thresholdDb, -24.0f, 0.0f), std::memory_order_relaxed);
    lim.attackMs.store(std::clamp(attackMs, 0.5f, 50.0f), std::memory_order_relaxed);
    lim.releaseMs.store(std::clamp(releaseMs, 20.0f, 1000.0f), std::memory_order_relaxed);
    paramVersion_.fetch_add(1, std::memory_order_release);
}

void DspChain::setOutputParams(
    float gainDbL,
    float gainDbR,
    bool muteL,
    bool muteR,
    bool invertL,
    bool invertR
) noexcept {
    outputGainDbL_.store(std::clamp(gainDbL, -40.0f, 12.0f), std::memory_order_relaxed);
    outputGainDbR_.store(std::clamp(gainDbR, -40.0f, 12.0f), std::memory_order_relaxed);
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
    snap.limiterGrDbL = meterLimiterGrDbL_.load(std::memory_order_relaxed);
    snap.limiterGrDbR = meterLimiterGrDbR_.load(std::memory_order_relaxed);
    return snap;
}

} // namespace adw::dsp
