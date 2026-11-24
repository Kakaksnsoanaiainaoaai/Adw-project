#pragma once

#include "BiquadFilter.h"
#include "CrossoverProcessor.h"
#include "DelayLine.h"
#include "GainStage.h"
#include "PeakLimiter.h"
#include <atomic>
#include <cstdint>

namespace adw::dsp {

struct MeterSnapshot {
    float inputPeakL = 0.0f;
    float inputPeakR = 0.0f;
    float inputRmsL = 0.0f;
    float inputRmsR = 0.0f;
    float outputPeakL = 0.0f;
    float outputPeakR = 0.0f;
    float outputRmsL = 0.0f;
    float outputRmsR = 0.0f;
    float inputHoldL = 0.0f;
    float inputHoldR = 0.0f;
    float outputHoldL = 0.0f;
    float outputHoldR = 0.0f;
    float limiterGrDb = 0.0f;
};

class DspChain {
public:
    DspChain();

    void prepare(double sampleRate) noexcept;
    void reset() noexcept;

    // Real-time audio thread entry point (lock-free, allocation-free)
    void processBlock(const float* inInterleavedStereo, float* outInterleavedStereo, int32_t numFrames) noexcept;

    void setGlobalState(bool bypassDsp, bool muteAll, int testSignalMode) noexcept;
    void setInputParams(float gainDb, bool mute, bool invertL, bool invertR) noexcept;
    void setEqBypass(bool bypass) noexcept;
    void setEqBand(int bandIndex, bool enabled, int filterShape, float freqHz, float gainDb, float q) noexcept;
    void setCrossoverParams(
        bool bypass,
        bool hpfEnabled,
        float hpfFreqHz,
        int hpfFilterType,
        int hpfSlopeDb,
        bool lpfEnabled,
        float lpfFreqHz,
        int lpfFilterType,
        int lpfSlopeDb
    ) noexcept;
    void setDelayParams(bool enabled, float delayMsL, float delayMsR) noexcept;
    void setLimiterParams(bool enabled, float thresholdDb, float attackMs, float releaseMs) noexcept;
    void setOutputParams(float gainDb, bool muteL, bool muteR, bool invertL, bool invertR) noexcept;
    void resetPeakHold() noexcept;

    MeterSnapshot readMeters() const noexcept;

private:
    void syncParametersOnAudioThread() noexcept;
    void generateTestSignal(float& left, float& right) noexcept;

    double sampleRate_ = 48000.0;

    std::atomic<uint64_t> paramVersion_{1};
    uint64_t syncedVersion_ = 0;

    std::atomic<bool> bypassDsp_{false};
    std::atomic<bool> muteAll_{false};
    std::atomic<int> testSignalMode_{0};

    std::atomic<float> inputGainDb_{0.0f};
    std::atomic<bool> inputMute_{false};
    std::atomic<bool> inputInvertL_{false};
    std::atomic<bool> inputInvertR_{false};

    std::atomic<bool> eqBypass_{false};
    struct EqBandAtomic {
        std::atomic<bool> enabled{true};
        std::atomic<int> shape{0}; // 0=Peak, 1=LowShelf, 2=HighShelf
        std::atomic<float> freqHz{1000.0f};
        std::atomic<float> gainDb{0.0f};
        std::atomic<float> q{1.0f};
    };
    EqBandAtomic eqBandsAtomic_[kNumEqBands];

    std::atomic<bool> xoverBypass_{false};
    std::atomic<bool> hpfEnabled_{true};
    std::atomic<float> hpfFreqHz_{40.0f};
    std::atomic<int> hpfFilterType_{1}; // 0=BW, 1=LR
    std::atomic<int> hpfSlopeDb_{24};
    std::atomic<bool> lpfEnabled_{false};
    std::atomic<float> lpfFreqHz_{18000.0f};
    std::atomic<int> lpfFilterType_{1};
    std::atomic<int> lpfSlopeDb_{24};

    std::atomic<bool> delayEnabled_{false};
    std::atomic<float> delayMsL_{0.0f};
    std::atomic<float> delayMsR_{0.0f};

    std::atomic<bool> limiterEnabled_{true};
    std::atomic<float> limiterThresholdDb_{-1.5f};
    std::atomic<float> limiterAttackMs_{2.0f};
    std::atomic<float> limiterReleaseMs_{140.0f};

    std::atomic<float> outputGainDb_{0.0f};
    std::atomic<bool> outputMuteL_{false};
    std::atomic<bool> outputMuteR_{false};
    std::atomic<bool> outputInvertL_{false};
    std::atomic<bool> outputInvertR_{false};

    std::atomic<uint32_t> peakHoldResetCounter_{0};
    uint32_t syncedPeakReset_ = 0;

    // Separated native DSP modules
    GainStage inputGainStage_;
    ParametricEqProcessor eqProcessor_;
    CrossoverProcessor crossover_;
    DelayLine delayLine_;
    PeakLimiter limiter_;
    GainStage outputGainStage_;

    bool localBypassDsp_ = false;
    bool localMuteAll_ = false;
    int localTestSignalMode_ = 0;
    bool localEqBypass_ = false;
    bool localXoverBypass_ = false;
    bool localDelayEnabled_ = false;

    double sinePhase_ = 0.0;
    float pinkB0_ = 0.0f, pinkB1_ = 0.0f, pinkB2_ = 0.0f;
    float pinkB3_ = 0.0f, pinkB4_ = 0.0f, pinkB5_ = 0.0f, pinkB6_ = 0.0f;
    uint32_t rngState_ = 0x12345678u;

    std::atomic<float> meterInPeakL_{0.0f};
    std::atomic<float> meterInPeakR_{0.0f};
    std::atomic<float> meterInRmsL_{0.0f};
    std::atomic<float> meterInRmsR_{0.0f};
    std::atomic<float> meterOutPeakL_{0.0f};
    std::atomic<float> meterOutPeakR_{0.0f};
    std::atomic<float> meterOutRmsL_{0.0f};
    std::atomic<float> meterOutRmsR_{0.0f};
    std::atomic<float> meterInHoldL_{0.0f};
    std::atomic<float> meterInHoldR_{0.0f};
    std::atomic<float> meterOutHoldL_{0.0f};
    std::atomic<float> meterOutHoldR_{0.0f};
    std::atomic<float> meterLimiterGrDb_{0.0f};

    float decayInPeakL_ = 0.0f, decayInPeakR_ = 0.0f;
    float decayOutPeakL_ = 0.0f, decayOutPeakR_ = 0.0f;
    float holdInL_ = 0.0f, holdInR_ = 0.0f;
    float holdOutL_ = 0.0f, holdOutR_ = 0.0f;
    float decayGrDb_ = 0.0f;
};

} // namespace adw::dsp
