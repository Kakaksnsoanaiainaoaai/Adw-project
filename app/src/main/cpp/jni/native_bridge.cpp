#include <jni.h>
#include <memory>
#include "../audio_engine.h"

namespace {
std::unique_ptr<adw::audio::OboeAudioEngine> gEngine;

adw::audio::OboeAudioEngine& ensureEngine() {
    if (!gEngine) {
        gEngine = std::make_unique<adw::audio::OboeAudioEngine>();
    }
    return *gEngine;
}
} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioBridge_nativeStartEngine(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint inputDeviceId,
    jint outputDeviceId,
    jint requestedSampleRate,
    jint requestedBufferFrames,
    jboolean hasRecordPermission
) {
    return ensureEngine().start(
        inputDeviceId,
        outputDeviceId,
        requestedSampleRate,
        requestedBufferFrames,
        hasRecordPermission == JNI_TRUE
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeStopEngine(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    if (gEngine) {
        gEngine->stop();
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeSetGlobalState(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jboolean bypassDsp,
    jboolean muteAll,
    jint testSignalMode
) {
    ensureEngine().dsp().setGlobalState(
        bypassDsp == JNI_TRUE,
        muteAll == JNI_TRUE,
        testSignalMode
    );
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeSetInputParams(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jfloat gainDb,
    jboolean mute,
    jboolean invertL,
    jboolean invertR
) {
    ensureEngine().dsp().setInputParams(
        gainDb,
        mute == JNI_TRUE,
        invertL == JNI_TRUE,
        invertR == JNI_TRUE
    );
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeSetEqBypass(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jboolean bypass
) {
    ensureEngine().dsp().setEqBypass(bypass == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeSetEqBand(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint bandIndex,
    jboolean enabled,
    jint filterShape,
    jfloat freqHz,
    jfloat gainDb,
    jfloat q
) {
    ensureEngine().dsp().setEqBand(
        bandIndex,
        enabled == JNI_TRUE,
        filterShape,
        freqHz,
        gainDb,
        q
    );
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeSetCrossoverParams(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jboolean bypass,
    jboolean hpfEnabled,
    jfloat hpfFreqHz,
    jint hpfFilterType,
    jint hpfSlopeDb,
    jboolean lpfEnabled,
    jfloat lpfFreqHz,
    jint lpfFilterType,
    jint lpfSlopeDb
) {
    ensureEngine().dsp().setCrossoverParams(
        bypass == JNI_TRUE,
        hpfEnabled == JNI_TRUE,
        hpfFreqHz,
        hpfFilterType,
        hpfSlopeDb,
        lpfEnabled == JNI_TRUE,
        lpfFreqHz,
        lpfFilterType,
        lpfSlopeDb
    );
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeSetDelayParams(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jboolean enabled,
    jfloat delayMsL,
    jfloat delayMsR
) {
    ensureEngine().dsp().setDelayParams(
        enabled == JNI_TRUE,
        delayMsL,
        delayMsR
    );
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeSetLimiterParams(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jboolean enabled,
    jfloat thresholdDb,
    jfloat attackMs,
    jfloat releaseMs
) {
    ensureEngine().dsp().setLimiterParams(
        enabled == JNI_TRUE,
        thresholdDb,
        attackMs,
        releaseMs
    );
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeSetOutputParams(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jfloat gainDb,
    jboolean muteL,
    jboolean muteR,
    jboolean invertL,
    jboolean invertR
) {
    ensureEngine().dsp().setOutputParams(
        gainDb,
        muteL == JNI_TRUE,
        muteR == JNI_TRUE,
        invertL == JNI_TRUE,
        invertR == JNI_TRUE
    );
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeResetPeakHold(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    ensureEngine().dsp().resetPeakHold();
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeGetTelemetry(
    JNIEnv* env,
    jobject /*thiz*/,
    jfloatArray outArray
) {
    if (outArray == nullptr) return;
    const jsize len = env->GetArrayLength(outArray);
    if (len < 21) return;

    const auto t = ensureEngine().getTelemetry();
    jfloat buf[21];
    buf[0] = t.meters.inputPeakL;
    buf[1] = t.meters.inputPeakR;
    buf[2] = t.meters.inputRmsL;
    buf[3] = t.meters.inputRmsR;
    buf[4] = t.meters.outputPeakL;
    buf[5] = t.meters.outputPeakR;
    buf[6] = t.meters.outputRmsL;
    buf[7] = t.meters.outputRmsR;
    buf[8] = t.meters.inputHoldL;
    buf[9] = t.meters.inputHoldR;
    buf[10] = t.meters.outputHoldL;
    buf[11] = t.meters.outputHoldR;
    buf[12] = t.meters.limiterGrDb;
    buf[13] = static_cast<float>(t.sampleRate);
    buf[14] = static_cast<float>(t.bufferSizeFrames);
    buf[15] = static_cast<float>(t.framesPerBurst);
    buf[16] = static_cast<float>(t.inputChannels);
    buf[17] = static_cast<float>(t.outputChannels);
    buf[18] = static_cast<float>(t.xRunCount);
    buf[19] = static_cast<float>(t.engineState);
    buf[20] = t.streamDisconnected ? -1.0f : static_cast<float>(t.audioApi);

    env->SetFloatArrayRegion(outArray, 0, 21, buf);
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_NativeAudioBridge_nativeGetCpuLoad(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    return ensureEngine().getTelemetry().cpuLoadPercent;
}

JNIEXPORT jint JNICALL
Java_com_example_audio_NativeAudioBridge_nativePushPlayerAudio(
    JNIEnv* env,
    jobject /*thiz*/,
    jfloatArray stereoInterleaved,
    jint numFrames
) {
    if (stereoInterleaved == nullptr || numFrames <= 0) {
        return 0;
    }
    const jsize arrayLen = env->GetArrayLength(stereoInterleaved);
    const jint maxFrames = std::min(numFrames, arrayLen / 2);
    if (maxFrames <= 0) {
        return 0;
    }
    constexpr jint kChunkFrames = 4096;
    jfloat localBuf[kChunkFrames * 2];
    jint totalWritten = 0;
    jint offset = 0;
    auto& engine = ensureEngine();
    while (offset < maxFrames) {
        const jint chunk = std::min(maxFrames - offset, kChunkFrames);
        env->GetFloatArrayRegion(stereoInterleaved, offset * 2, chunk * 2, localBuf);
        const int32_t written = engine.pushPlayerAudio(localBuf, chunk);
        totalWritten += written;
        if (written < chunk) {
            break;
        }
        offset += chunk;
    }
    return totalWritten;
}

JNIEXPORT jint JNICALL
Java_com_example_audio_NativeAudioBridge_nativeGetPlayerBufferedFrames(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    return ensureEngine().getPlayerBufferedFrames();
}

JNIEXPORT jlong JNICALL
Java_com_example_audio_NativeAudioBridge_nativeGetPlayerConsumedFrames(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    return static_cast<jlong>(ensureEngine().getPlayerConsumedFrames());
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeSetPlayerState(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jboolean playing,
    jfloat gainLinear,
    jboolean mixWithLiveInput
) {
    ensureEngine().setPlayerState(
        playing == JNI_TRUE,
        gainLinear,
        mixWithLiveInput == JNI_TRUE
    );
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeClearPlayerBuffer(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jboolean resetConsumedCounter
) {
    ensureEngine().clearPlayerBuffer(resetConsumedCounter == JNI_TRUE);
}

} // extern "C"
