#include "audio_engine.h"
#include <algorithm>
#include <android/log.h>
#include <chrono>
#include <thread>

#define LOG_TAG "ADW_VDLMS_AUDIO"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace adw::audio {

OboeAudioEngine::OboeAudioEngine() {
    inputCallback_ = std::make_unique<InputCallback>(this);
}

OboeAudioEngine::~OboeAudioEngine() {
    stop();
}

bool OboeAudioEngine::start(
    int32_t inputDeviceId,
    int32_t outputDeviceId,
    int32_t requestedSampleRate,
    int32_t requestedBufferFrames,
    bool hasRecordPermission
) {
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    stop();

    streamDisconnected_.store(false, std::memory_order_relaxed);
    xRunCount_.store(0, std::memory_order_relaxed);
    cpuLoadPercent_.store(0.0f, std::memory_order_relaxed);
    ringBuffer_.clear();
    ringPrimed_.store(false, std::memory_order_relaxed);

    const oboe::Result outResult = openOutputStream(
        outputDeviceId,
        requestedSampleRate,
        requestedBufferFrames
    );

    if (outResult != oboe::Result::OK || !outputStream_) {
        LOGW("Failed to open Oboe output stream (%s). Entering DEV_FALLBACK.", oboe::convertToText(outResult));
        sampleRate_.store(requestedSampleRate > 0 ? requestedSampleRate : 48000, std::memory_order_relaxed);
        inputChannels_.store(0, std::memory_order_relaxed);
        outputChannels_.store(2, std::memory_order_relaxed);
        dspChain_.prepare(static_cast<double>(sampleRate_.load(std::memory_order_relaxed)));
        engineState_.store(static_cast<int32_t>(EngineState::DevFallback), std::memory_order_release);
        return false;
    }

    const int32_t actualSampleRate = outputStream_->getSampleRate();
    const int32_t burst = outputStream_->getFramesPerBurst();
    sampleRate_.store(actualSampleRate, std::memory_order_relaxed);
    framesPerBurst_.store(burst, std::memory_order_relaxed);
    bufferSizeFrames_.store(outputStream_->getBufferSizeInFrames(), std::memory_order_relaxed);
    outputChannels_.store(outputStream_->getChannelCount(), std::memory_order_relaxed);
    audioApi_.store(
        outputStream_->getAudioApi() == oboe::AudioApi::AAudio ? 1 : 2,
        std::memory_order_relaxed
    );

    dspChain_.prepare(static_cast<double>(actualSampleRate));

    bool duplexReady = false;
    if (hasRecordPermission) {
        const oboe::Result inResult = openInputStream(inputDeviceId, actualSampleRate);
        if (inResult == oboe::Result::OK && inputStream_) {
            duplexReady = true;
            inputChannels_.store(inputStream_->getChannelCount(), std::memory_order_relaxed);
        } else {
            LOGW("Input stream unavailable (%s). Continuing in Output-Only mode.", oboe::convertToText(inResult));
            inputChannels_.store(0, std::memory_order_relaxed);
            if (inputStream_) {
                inputStream_->close();
                inputStream_.reset();
            }
        }
    } else {
        inputChannels_.store(0, std::memory_order_relaxed);
    }

    inputActive_.store(duplexReady, std::memory_order_relaxed);

    if (duplexReady && inputStream_) {
        const oboe::Result startIn = inputStream_->requestStart();
        if (startIn != oboe::Result::OK) {
            LOGW("Failed to start input stream (%s). Falling back to Output-Only.", oboe::convertToText(startIn));
            inputStream_->close();
            inputStream_.reset();
            duplexReady = false;
            inputActive_.store(false, std::memory_order_relaxed);
            inputChannels_.store(0, std::memory_order_relaxed);
        }
    }

    const oboe::Result startOut = outputStream_->requestStart();
    if (startOut != oboe::Result::OK) {
        LOGW("Failed to start output stream (%s). Entering DEV_FALLBACK.", oboe::convertToText(startOut));
        if (inputStream_) {
            inputStream_->stop();
            inputStream_->close();
            inputStream_.reset();
        }
        outputStream_->close();
        outputStream_.reset();
        engineState_.store(static_cast<int32_t>(EngineState::DevFallback), std::memory_order_release);
        return false;
    }

    engineState_.store(
        static_cast<int32_t>(duplexReady ? EngineState::RunningDuplex : EngineState::RunningOutputOnly),
        std::memory_order_release
    );
    LOGI("AudioEngine started: state=%d sr=%d burst=%d inCh=%d outCh=%d",
         engineState_.load(), actualSampleRate, burst, inputChannels_.load(), outputChannels_.load());
    return true;
}

void OboeAudioEngine::stop() {
    if (inputStream_) {
        inputStream_->stop();
        inputStream_->close();
        inputStream_.reset();
    }
    if (outputStream_) {
        outputStream_->stop();
        outputStream_->close();
        outputStream_.reset();
    }
    inputActive_.store(false, std::memory_order_relaxed);
    ringPrimed_.store(false, std::memory_order_relaxed);
    inputChannels_.store(0, std::memory_order_relaxed);
    ringBuffer_.clear();
    engineState_.store(static_cast<int32_t>(EngineState::Stopped), std::memory_order_release);
}

oboe::Result OboeAudioEngine::openOutputStream(
    int32_t outputDeviceId,
    int32_t requestedSampleRate,
    int32_t requestedBufferFrames
) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    if (outputDeviceId > 0) {
        builder.setDeviceId(outputDeviceId);
    }
    if (requestedSampleRate > 0) {
        builder.setSampleRate(requestedSampleRate);
        builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);
    }
    if (requestedBufferFrames > 0) {
        builder.setFramesPerDataCallback(requestedBufferFrames);
    }

    oboe::Result result = builder.openStream(outputStream_);
    if (result != oboe::Result::OK) {
        oboe::AudioStreamBuilder fallbackBuilder;
        fallbackBuilder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setDataCallback(this)
            ->setErrorCallback(this);
        if (requestedSampleRate > 0) {
            fallbackBuilder.setSampleRate(requestedSampleRate);
        }
        result = fallbackBuilder.openStream(outputStream_);
    }

    if (result == oboe::Result::OK && outputStream_) {
        const int32_t burst = outputStream_->getFramesPerBurst();
        if (burst > 0) {
            const int32_t desiredBuffer = requestedBufferFrames > 0
                ? std::max(burst * 2, requestedBufferFrames * 2)
                : burst * 2;
            outputStream_->setBufferSizeInFrames(desiredBuffer);
        }
    }
    return result;
}

oboe::Result OboeAudioEngine::openInputStream(int32_t inputDeviceId, int32_t sampleRate) {
    auto tryOpen = [&](oboe::ChannelCount channels, oboe::InputPreset preset, int32_t devId) -> oboe::Result {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(channels)
            ->setSampleRate(sampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setInputPreset(preset)
            ->setDataCallback(inputCallback_.get())
            ->setErrorCallback(this);
        if (devId > 0) {
            builder.setDeviceId(devId);
        }
        oboe::Result r = builder.openStream(inputStream_);
        if (r == oboe::Result::OK && inputStream_) {
            inputChannelCount_ = inputStream_->getChannelCount();
        }
        return r;
    };

    oboe::Result r = tryOpen(oboe::ChannelCount::Stereo, oboe::InputPreset::Unprocessed, inputDeviceId);
    if (r == oboe::Result::OK) return r;

    r = tryOpen(oboe::ChannelCount::Mono, oboe::InputPreset::Unprocessed, inputDeviceId);
    if (r == oboe::Result::OK) return r;

    r = tryOpen(oboe::ChannelCount::Stereo, oboe::InputPreset::Generic, inputDeviceId);
    if (r == oboe::Result::OK) return r;

    return tryOpen(oboe::ChannelCount::Mono, oboe::InputPreset::Generic, inputDeviceId);
}

oboe::DataCallbackResult OboeAudioEngine::InputCallback::onAudioReady(
    oboe::AudioStream* /*oboeStream*/,
    void* audioData,
    int32_t numFrames
) {
    if (audioData == nullptr || numFrames <= 0) {
        return oboe::DataCallbackResult::Continue;
    }
    const auto* inPtr = static_cast<const float*>(audioData);
    const int32_t safeFrames = std::min(numFrames, 4096);

    if (owner_->inputChannelCount_ == 2) {
        owner_->ringBuffer_.write(inPtr, static_cast<size_t>(safeFrames));
    } else if (owner_->inputChannelCount_ == 1) {
        for (int32_t i = 0; i < safeFrames; ++i) {
            const float mono = inPtr[i];
            upmixScratch_[i * 2] = mono;
            upmixScratch_[i * 2 + 1] = mono;
        }
        owner_->ringBuffer_.write(upmixScratch_.data(), static_cast<size_t>(safeFrames));
    } else {
        const int ch = std::max(1, owner_->inputChannelCount_);
        for (int32_t i = 0; i < safeFrames; ++i) {
            upmixScratch_[i * 2] = inPtr[i * ch];
            upmixScratch_[i * 2 + 1] = inPtr[i * ch + 1];
        }
        owner_->ringBuffer_.write(upmixScratch_.data(), static_cast<size_t>(safeFrames));
    }

    return oboe::DataCallbackResult::Continue;
}

oboe::DataCallbackResult OboeAudioEngine::onAudioReady(
    oboe::AudioStream* /*oboeStream*/,
    void* audioData,
    int32_t numFrames
) {
    if (audioData == nullptr || numFrames <= 0) {
        return oboe::DataCallbackResult::Continue;
    }

    const auto tStart = std::chrono::steady_clock::now();
    auto* outPtr = static_cast<float*>(audioData);
    int32_t remaining = numFrames;
    int32_t offsetFrames = 0;

    while (remaining > 0) {
        const int32_t chunk = std::min(remaining, 4096);
        float* inBuf = outputInputScratch_.data();

        if (inputActive_.load(std::memory_order_relaxed)) {
            const size_t avail = ringBuffer_.availableToRead();
            if (!ringPrimed_.load(std::memory_order_relaxed)) {
                if (avail >= static_cast<size_t>(chunk)) {
                    ringPrimed_.store(true, std::memory_order_relaxed);
                    ringBuffer_.read(inBuf, static_cast<size_t>(chunk));
                } else {
                    std::fill_n(inBuf, chunk * 2, 0.0f);
                }
            } else {
                const size_t got = ringBuffer_.read(inBuf, static_cast<size_t>(chunk));
                if (got < static_cast<size_t>(chunk)) {
                    std::fill_n(inBuf + got * 2, (chunk - got) * 2, 0.0f);
                    xRunCount_.fetch_add(1, std::memory_order_relaxed);
                }
            }
        } else {
            std::fill_n(inBuf, chunk * 2, 0.0f);
        }

        if (playerPlaying_.load(std::memory_order_acquire)) {
            inPlayerRead_.store(true, std::memory_order_release);
            size_t gotPlayer = 0;
            if (playerPlaying_.load(std::memory_order_acquire)) {
                gotPlayer = playerRingBuffer_.read(playerScratch_.data(), static_cast<size_t>(chunk));
            }
            inPlayerRead_.store(false, std::memory_order_release);

            if (gotPlayer > 0) {
                playerFramesConsumed_.fetch_add(gotPlayer, std::memory_order_relaxed);
            }
            const float pGain = playerGainLinear_.load(std::memory_order_relaxed);
            const bool mixLive = playerMixWithLiveInput_.load(std::memory_order_relaxed);
            if (mixLive && inputActive_.load(std::memory_order_relaxed)) {
                for (size_t k = 0; k < gotPlayer * 2; ++k) {
                    inBuf[k] += playerScratch_[k] * pGain;
                }
            } else {
                for (size_t k = 0; k < gotPlayer * 2; ++k) {
                    inBuf[k] = playerScratch_[k] * pGain;
                }
                if (gotPlayer < static_cast<size_t>(chunk)) {
                    std::fill_n(inBuf + gotPlayer * 2, (chunk - gotPlayer) * 2, 0.0f);
                }
            }
        }

        dspChain_.processBlock(inBuf, outPtr + offsetFrames * 2, chunk);
        remaining -= chunk;
        offsetFrames += chunk;
    }

    const auto tEnd = std::chrono::steady_clock::now();
    const double elapsedNs = static_cast<double>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(tEnd - tStart).count()
    );
    const double sr = static_cast<double>(std::max(1, sampleRate_.load(std::memory_order_relaxed)));
    const double budgetNs = (static_cast<double>(numFrames) / sr) * 1e9;
    const float loadPct = budgetNs > 0.0 ? static_cast<float>((elapsedNs / budgetNs) * 100.0) : 0.0f;
    const float prev = cpuLoadPercent_.load(std::memory_order_relaxed);
    cpuLoadPercent_.store(prev * 0.85f + loadPct * 0.15f, std::memory_order_relaxed);

    return oboe::DataCallbackResult::Continue;
}

void OboeAudioEngine::onErrorAfterClose(oboe::AudioStream* /*oboeStream*/, oboe::Result /*error*/) {
    // Signal lock-free disconnect; avoid logging or heavy work on audio error thread
    streamDisconnected_.store(true, std::memory_order_release);
}

int32_t OboeAudioEngine::pushPlayerAudio(const float* stereoInterleaved, int32_t numFrames) noexcept {
    if (stereoInterleaved == nullptr || numFrames <= 0) {
        return 0;
    }
    return static_cast<int32_t>(
        playerRingBuffer_.write(stereoInterleaved, static_cast<size_t>(numFrames))
    );
}

int32_t OboeAudioEngine::getPlayerBufferedFrames() const noexcept {
    return static_cast<int32_t>(playerRingBuffer_.availableToRead());
}

int64_t OboeAudioEngine::getPlayerConsumedFrames() const noexcept {
    return static_cast<int64_t>(playerFramesConsumed_.load(std::memory_order_acquire));
}

void OboeAudioEngine::setPlayerState(bool playing, float gainLinear, bool mixWithLiveInput) noexcept {
    playerGainLinear_.store(std::clamp(gainLinear, 0.0f, 2.0f), std::memory_order_relaxed);
    playerMixWithLiveInput_.store(mixWithLiveInput, std::memory_order_relaxed);
    playerPlaying_.store(playing, std::memory_order_release);
}

void OboeAudioEngine::clearPlayerBuffer(bool resetConsumedCounter) noexcept {
    const bool wasPlaying = playerPlaying_.exchange(false, std::memory_order_acq_rel);
    while (inPlayerRead_.load(std::memory_order_acquire)) {
        std::this_thread::yield();
    }
    playerRingBuffer_.clear();
    if (resetConsumedCounter) {
        playerFramesConsumed_.store(0, std::memory_order_release);
    }
    if (wasPlaying) {
        // Caller explicitly resumes with setPlayerState(true, ...) after seeking if needed
    }
}

EngineTelemetry OboeAudioEngine::getTelemetry() const noexcept {
    EngineTelemetry t;
    t.meters = dspChain_.readMeters();
    t.sampleRate = sampleRate_.load(std::memory_order_relaxed);
    t.bufferSizeFrames = bufferSizeFrames_.load(std::memory_order_relaxed);
    t.framesPerBurst = framesPerBurst_.load(std::memory_order_relaxed);
    t.inputChannels = inputChannels_.load(std::memory_order_relaxed);
    t.outputChannels = outputChannels_.load(std::memory_order_relaxed);
    t.xRunCount = xRunCount_.load(std::memory_order_relaxed);
    t.engineState = engineState_.load(std::memory_order_relaxed);
    t.audioApi = audioApi_.load(std::memory_order_relaxed);
    t.cpuLoadPercent = cpuLoadPercent_.load(std::memory_order_relaxed);
    t.streamDisconnected = streamDisconnected_.load(std::memory_order_relaxed);
    return t;
}

} // namespace adw::audio
