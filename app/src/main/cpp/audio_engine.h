#pragma once

#include "audio/LockFreeRingBuffer.h"
#include "dsp/DspChain.h"
#include <array>
#include <atomic>
#include <memory>
#include <mutex>
#include <oboe/Oboe.h>

namespace adw::audio {

enum class EngineState : int32_t {
    Stopped = 0,
    RunningDuplex = 1,
    RunningOutputOnly = 2,
    DevFallback = 3
};

struct EngineTelemetry {
    adw::dsp::MeterSnapshot meters;
    int32_t sampleRate = 48000;
    int32_t bufferSizeFrames = 0;
    int32_t framesPerBurst = 0;
    int32_t inputChannels = 0;
    int32_t outputChannels = 2;
    int32_t xRunCount = 0;
    int32_t engineState = 0;
    int32_t audioApi = 0; // 0 = Auto, 1 = AAudio, 2 = OpenSL ES
    float cpuLoadPercent = 0.0f;
    bool streamDisconnected = false;
};

class OboeAudioEngine : public oboe::AudioStreamDataCallback,
                        public oboe::AudioStreamErrorCallback {
public:
    OboeAudioEngine();
    ~OboeAudioEngine() override;

    bool start(
        int32_t inputDeviceId,
        int32_t outputDeviceId,
        int32_t requestedSampleRate,
        int32_t requestedBufferFrames,
        bool hasRecordPermission
    );
    void stop();

    adw::dsp::DspChain& dsp() noexcept { return dspChain_; }
    EngineTelemetry getTelemetry() const noexcept;

    int32_t pushPlayerAudio(const float* stereoInterleaved, int32_t numFrames) noexcept;
    int32_t getPlayerBufferedFrames() const noexcept;
    int64_t getPlayerConsumedFrames() const noexcept;
    void setPlayerState(bool playing, float gainLinear, bool mixWithLiveInput) noexcept;
    void clearPlayerBuffer(bool resetConsumedCounter) noexcept;

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* oboeStream,
        void* audioData,
        int32_t numFrames
    ) override;

    void onErrorAfterClose(oboe::AudioStream* oboeStream, oboe::Result error) override;

private:
    class InputCallback : public oboe::AudioStreamDataCallback {
    public:
        explicit InputCallback(OboeAudioEngine* owner) : owner_(owner) {}
        oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* oboeStream,
            void* audioData,
            int32_t numFrames
        ) override;
    private:
        OboeAudioEngine* owner_;
        std::array<float, 4096 * 2> upmixScratch_{};
    };

    oboe::Result openOutputStream(
        int32_t outputDeviceId,
        int32_t requestedSampleRate,
        int32_t requestedBufferFrames
    );
    oboe::Result openInputStream(int32_t inputDeviceId, int32_t sampleRate);

    std::mutex lifecycleMutex_;
    std::shared_ptr<oboe::AudioStream> outputStream_;
    std::shared_ptr<oboe::AudioStream> inputStream_;
    std::unique_ptr<InputCallback> inputCallback_;

    adw::dsp::DspChain dspChain_;
    LockFreeStereoRingBuffer<16384> ringBuffer_;
    LockFreeStereoRingBuffer<65536> playerRingBuffer_;
    std::array<float, 4096 * 2> outputInputScratch_{};
    std::array<float, 4096 * 2> playerScratch_{};

    std::atomic<int32_t> engineState_{static_cast<int32_t>(EngineState::Stopped)};
    std::atomic<int32_t> sampleRate_{48000};
    std::atomic<int32_t> bufferSizeFrames_{0};
    std::atomic<int32_t> framesPerBurst_{0};
    std::atomic<int32_t> inputChannels_{0};
    std::atomic<int32_t> outputChannels_{2};
    std::atomic<int32_t> xRunCount_{0};
    std::atomic<int32_t> audioApi_{0};
    std::atomic<float> cpuLoadPercent_{0.0f};
    std::atomic<bool> streamDisconnected_{false};
    std::atomic<bool> inputActive_{false};
    std::atomic<bool> ringPrimed_{false};

    std::atomic<bool> playerPlaying_{false};
    std::atomic<bool> playerMixWithLiveInput_{false};
    std::atomic<float> playerGainLinear_{1.0f};
    std::atomic<uint64_t> playerFramesConsumed_{0};
    std::atomic<bool> inPlayerRead_{false};

    int32_t inputChannelCount_ = 2;
};

} // namespace adw::audio
