// FieldNotes — whisper_jni.cpp
// Authored by: whisper module | Implements: 05_WHISPER_MODULE.md
// JNI bridge to whisper.cpp (v1.8.x). Fully offline transcription.
#include <jni.h>
#include <string>
#include <chrono>
#include <atomic>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 0–100, updated by whisper's progress_callback during a run. The engine serialises transcriptions
// on a mutex (one at a time), so a single global tracks "the current run" — Kotlin polls it for the
// progress UI rather than calling back into the JVM from a native thread.
static std::atomic<int> g_progress{0};

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
    // 4 threads maps to the Tensor G3's four fast A715 cores. Going to 6 drags in the slow A510
    // efficiency cores, and OpenMP's spin-wait barriers then stall the fast cores waiting on them —
    // which made even the Tiny model no faster than Base (sync-bound, not compute-bound).
    params.n_threads = 4;
    params.single_segment = false;
    params.token_timestamps = false;

    // Report 0–100 progress into g_progress for the polling progress UI. whisper invokes this on its
    // worker thread between windows; just store the value (no JVM calls here).
    g_progress.store(0);
    params.progress_callback = [](struct whisper_context*, struct whisper_state*, int progress, void*) {
        g_progress.store(progress);
    };
    params.progress_callback_user_data = nullptr;

    float duration_s = numSamples / 16000.0f;

    // Whisper's encoder otherwise always processes a full 30s window (1500 frames) even for a 3s
    // clip — the dominant cost for short voice notes. Shrink audio_ctx to ~the real audio length
    // (~50 encoder frames/sec) plus ~3s of headroom so trailing speech isn't clipped. Capped at the
    // 1500 default for clips near/over 30s.
    int audio_ctx = (int)((duration_s + 3.0f) * 50.0f);
    if (audio_ctx < 128) audio_ctx = 128;
    if (audio_ctx > 1500) audio_ctx = 1500;
    params.audio_ctx = audio_ctx;

    LOGI("starting inference: %.1fs of audio, n_threads=%d, audio_ctx=%d",
         duration_s, params.n_threads, audio_ctx);
    // Reset so the breakdown below is for this call only (the context is shared/reused across runs).
    whisper_reset_timings(ctx);
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

    // Breakdown so we can tell whether long-form (>30s, multi-window) time is encode-bound
    // (audio_ctx / SIMD kernels) or decode-bound (rolling prompt context + temperature fallbacks).
    // whisper's own logs don't reach logcat on Android, so read the public timings and log them here.
    whisper_timings* tm = whisper_get_timings(ctx);
    if (tm != nullptr) {
        LOGI("timings: encode=%.0fms decode=%.0fms batchd=%.0fms prompt=%.0fms sample=%.0fms",
             tm->encode_ms, tm->decode_ms, tm->batchd_ms, tm->prompt_ms, tm->sample_ms);
    }

    g_progress.store(100);

    std::string output;
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        output += whisper_full_get_segment_text(ctx, i);
        if (i < n_segments - 1) output += " ";
    }
    return env->NewStringUTF(output.c_str());
}

JNIEXPORT jint JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_currentProgress(
        JNIEnv* /* env */, jobject /* obj */) {
    return g_progress.load();
}

JNIEXPORT void JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_freeContext(
        JNIEnv* /* env */, jobject /* obj */, jlong ctxPtr) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx) whisper_free(ctx);
}

} // extern "C"
