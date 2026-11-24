#pragma once

#include <algorithm>
#include <array>
#include <atomic>
#include <cstddef>
#include <cstring>

namespace adw::audio {

// Lock-free Single-Producer Single-Consumer ring buffer for interleaved stereo float frames.
// Fixed capacity avoids any heap allocations on audio callbacks.
template <size_t CapacityFrames>
class LockFreeStereoRingBuffer {
public:
    static_assert((CapacityFrames & (CapacityFrames - 1)) == 0, "CapacityFrames must be power of two");

    void clear() noexcept {
        readIndex_.store(0, std::memory_order_relaxed);
        writeIndex_.store(0, std::memory_order_relaxed);
    }

    size_t availableToRead() const noexcept {
        const size_t w = writeIndex_.load(std::memory_order_acquire);
        const size_t r = readIndex_.load(std::memory_order_relaxed);
        return w - r;
    }

    size_t availableToWrite() const noexcept {
        const size_t w = writeIndex_.load(std::memory_order_relaxed);
        const size_t r = readIndex_.load(std::memory_order_acquire);
        return CapacityFrames - (w - r);
    }

    size_t write(const float* stereoInterleaved, size_t frames) noexcept {
        const size_t avail = availableToWrite();
        const size_t toWrite = std::min(frames, avail);
        if (toWrite == 0) {
            return 0;
        }
        const size_t w = writeIndex_.load(std::memory_order_relaxed);
        const size_t idx = w & kMask;
        const size_t firstPart = std::min(toWrite, CapacityFrames - idx);
        std::memcpy(&buffer_[idx * 2], stereoInterleaved, firstPart * 2 * sizeof(float));
        if (toWrite > firstPart) {
            std::memcpy(&buffer_[0], stereoInterleaved + firstPart * 2, (toWrite - firstPart) * 2 * sizeof(float));
        }
        writeIndex_.store(w + toWrite, std::memory_order_release);
        return toWrite;
    }

    size_t read(float* stereoInterleaved, size_t frames) noexcept {
        const size_t avail = availableToRead();
        const size_t toRead = std::min(frames, avail);
        if (toRead == 0) {
            return 0;
        }
        const size_t r = readIndex_.load(std::memory_order_relaxed);
        const size_t idx = r & kMask;
        const size_t firstPart = std::min(toRead, CapacityFrames - idx);
        std::memcpy(stereoInterleaved, &buffer_[idx * 2], firstPart * 2 * sizeof(float));
        if (toRead > firstPart) {
            std::memcpy(stereoInterleaved + firstPart * 2, &buffer_[0], (toRead - firstPart) * 2 * sizeof(float));
        }
        readIndex_.store(r + toRead, std::memory_order_release);
        return toRead;
    }

private:
    static constexpr size_t kMask = CapacityFrames - 1;
    std::array<float, CapacityFrames * 2> buffer_{};
    alignas(64) std::atomic<size_t> readIndex_{0};
    alignas(64) std::atomic<size_t> writeIndex_{0};
};

} // namespace adw::audio
