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
    float limiterGrDbL = 0.0f;
    float limiterGrDbR = 0.0f;
};

class DspChain {
public:
    static constexpr int kNumChannels = 2;

    DspChain();

    void prepare(double sampleRate) noexcept;
    void reset() noexcept;

    // Real-time audio thread entry point (lock-free, allocation-free)
    void processBlock(const float* inInterleavedStereo, float* outInterleavedStereo, int32_t numFrames) noexcept;

    void setGlobalState(bool bypassDsp, bool muteAll, int testSignalMode) noexcept;
    void setInputParams(
        float gainDbL,
        float gainDbR,
        bool muteL,
        bool muteR,
        bool invertL,
        bool invertR,
        int routeSourceL,
        int routeSourceR
    ) noexcept;
    void setEqBypass(int channelIndex, bool bypass) noexcept;
    void setEqBand(
        int channelIndex,
        int bandIndex,
        bool enabled,
        int filterShape,
        float freqHz,
        float gainDb,
        float q
    ) noexcept;
    void setCrossoverParams(
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
    ) noexcept;
    void setDelayParams(bool enabledL, bool enabledR, float delayMsL, float delayMsR) noexcept;
    void setLimiterParams(
        int channelIndex,
        bool linked,
        bool enabled,
        float thresholdDb,
        float attackMs,
        float releaseMs
    ) noexcept;
    void setOutputParams(
        float gainDbL,
        float gainDbR,
        bool muteL,
        bool muteR,
        bool invertL,
        bool invertR
    ) noexcept;
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

    // Input stage + Input Routing Matrix (0 = IN1/L, 1 = IN2/R, 2 = SUM L+R)
    std::atomic<float> inputGainDbL_{0.0f};
    std::atomic<float> inputGainDbR_{0.0f};
    std::atomic<bool> inputMuteL_{false};
    std::atomic<bool> inputMuteR_{false};
    std::atomic<bool> inputInvertL_{false};
    std::atomic<bool> inputInvertR_{false};
    std::atomic<int> routeSourceL_{0};
    std::atomic<int> routeSourceR_{1};

    struct EqBandAtomic {
        std::atomic<bool> enabled{true};
        std::atomic<int> shape{0}; // 0=Peak, 1=LowShelf, 2=HighShelf
        std::atomic<float> freqHz{1000.0f};
        std::atomic<float> gainDb{0.0f};
        std::atomic<float> q{1.0f};
    };

    struct ChannelEqAtomic {
        std::atomic<bool> bypass{false};
        EqBandAtomic bands[kNumEqBands];
    };
    ChannelEqAtomic eqAtomic_[kNumChannels];

    struct ChannelXoverAtomic {
        std::atomic<bool> bypass{false};
        std::atomic<bool> hpfEnabled{true};
        std::atomic<float> hpfFreqHz{40.0f};
        std::atomic<int> hpfFilterType{1}; // 0=BW, 1=LR
        std::atomic<int> hpfSlopeDb{24};
        std::atomic<bool> lpfEnabled{false};
        std::atomic<float> lpfFreqHz{18000.0f};
        std::atomic<int> lpfFilterType{1};
        std::atomic<int> lpfSlopeDb{24};
    };
    ChannelXoverAtomic xoverAtomic_[kNumChannels];

    std::atomic<bool> delayEnabledL_{false};
    std::atomic<bool> delayEnabledR_{false};
    std::atomic<float> delayMsL_{0.0f};
    std::atomic<float> delayMsR_{0.0f};

    std::atomic<bool> limiterLinked_{true};
    struct ChannelLimiterAtomic {
        std::atomic<bool> enabled{true};
        std::atomic<float> thresholdDb{-1.5f};
        std::atomic<float> attackMs{2.0f};
        std::atomic<float> releaseMs{140.0f};
    };
    ChannelLimiterAtomic limiterAtomic_[kNumChannels];

    std::atomic<float> outputGainDbL_{0.0f};
    std::atomic<float> outputGainDbR_{0.0f};
    std::atomic<bool> outputMuteL_{false};
    std::atomic<bool> outputMuteR_{false};
    std::atomic<bool> outputInvertL_{false};
    std::atomic<bool> outputInvertR_{false};

    std::atomic<uint32_t> peakHoldResetCounter_{0};
    uint32_t syncedPeakReset_ = 0;

    // Independent per-channel native DSP processors
    GainStage inputGainStage_;
    ParametricEqProcessor eqProcessorL_;
    ParametricEqProcessor eqProcessorR_;
    CrossoverProcessor crossoverL_;
    CrossoverProcessor crossoverR_;
    DelayLine delayLine_;
    PeakLimiter limiterL_;
    PeakLimiter limiterR_;
    GainStage outputGainStage_;

    bool localBypassDsp_ = false;
    bool localMuteAll_ = false;
    int localTestSignalMode_ = 0;
    int localRouteSourceL_ = 0;
    int localRouteSourceR_ = 1;
    bool localEqBypassL_ = false;
    bool localEqBypassR_ = false;
    bool localXoverBypassL_ = false;
    bool localXoverBypassR_ = false;
    bool localDelayEnabledL_ = false;
    bool localDelayEnabledR_ = false;
    bool localLimiterLinked_ = true;

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
    std::atomic<float> meterLimiterGrDbL_{0.0f};
    std::atomic<float> meterLimiterGrDbR_{0.0f};

    float decayInPeakL_ = 0.0f, decayInPeakR_ = 0.0f;
    float decayOutPeakL_ = 0.0f, decayOutPeakR_ = 0.0f;
    float holdInL_ = 0.0f, holdInR_ = 0.0f;
    float holdOutL_ = 0.0f, holdOutR_ = 0.0f;
    float decayGrDbL_ = 0.0f;
    float decayGrDbR_ = 0.0f;
};

} // namespace adw::dsp
