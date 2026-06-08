// FieldNotes — whisper_jni.cpp
// Authored by: whisper module | Implements: 05_WHISPER_MODULE.md
// JNI bridge to whisper.cpp (v1.8.x). Fully offline transcription.
#include <jni.h>
#include <string>
#include <chrono>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static long now_ms() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_initContext(
        JNIEnv* env, jobject /* obj */, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("loading model: %s", path);
    long t0 = now_ms();
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // Android GPU not supported by whisper.cpp here
    whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) {
        LOGE("whisper_init_from_file_with_params returned null");
    } else {
        LOGI("model loaded in %ldms", now_ms() - t0);
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_transcribeAudio(
        JNIEnv* env, jobject /* obj */, jlong ctxPtr,
        jfloatArray audioData, jint numSamples) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx == nullptr) {
        return env->NewStringUTF("[Whisper not initialised]");
    }
    jfloat* samples = env->GetFloatArrayElements(audioData, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.language = "en";
    params.n_threads = 6; // 4 perf + 2 prime/efficiency; sweet spot on Pixel 8's 8-core layout
    params.single_segment = false;
    params.token_timestamps = false;

    float duration_s = numSamples / 16000.0f;
    LOGI("starting inference: %.1fs of audio, n_threads=%d", duration_s, params.n_threads);
    long t0 = now_ms();
    int result = whisper_full(ctx, params, samples, numSamples);
    long elapsed = now_ms() - t0;
    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);

    if (result != 0) {
        LOGE("whisper_full failed: %d (elapsed %ldms)", result, elapsed);
        return env->NewStringUTF("[Transcription failed]");
    }
    // Report speed against the 30s mel window whisper always processes, not just the audio length,
    // so the figure is comparable across short clips.
    float window_s = (numSamples < 30 * 16000) ? 30.0f : duration_s;
    LOGI("inference done: %ldms for %.1fs audio (%.2fx realtime on %.0fs window)",
         elapsed, duration_s, window_s * 1000.0f / (float)elapsed, window_s);

    std::string output;
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        output += whisper_full_get_segment_text(ctx, i);
        if (i < n_segments - 1) output += " ";
    }
    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_freeContext(
        JNIEnv* /* env */, jobject /* obj */, jlong ctxPtr) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx) whisper_free(ctx);
}

} // extern "C"
